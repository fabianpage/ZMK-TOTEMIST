(ns generator-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [generator :as generator]
            [com.mjdowney.rich-comment-tests.test-runner :as test-runner]))

(defn ^:private tokenize
  "Split a string on any whitespace, returning a sequence of non-empty tokens.";
  [s]
  (->> (str/split s #"\s+")
       (remove str/blank?)))

(defn ^:private discover-examples
  "Find all example configs in examples/ and return a seq of
   {:num <n> :config <path> :in <path> :out <path>} maps.";
  []
  (let [dir (io/file "examples")
        edn-files (sort (.listFiles dir
                         (reify java.io.FilenameFilter
                           (accept [_ _ name]
                             (.endsWith name ".edn")))))]
    (for [f edn-files
          :let [name (.getName f)
                num-str (first (str/split name #"\."))
                num (parse-long num-str)]]
      {:num num
       :config (.getPath f)
       :in (str "examples/" num "_in.keymap")
       :out (str "examples/" num "_out.keymap")})))

(defn ^:private node-block
  [node-name rendered]
  (let [pattern (re-pattern (str "(?s)\\n\\s*" (java.util.regex.Pattern/quote node-name) " \\{.*?\\n\\s*\\};"))]
    (re-find pattern rendered)))

(defn ^:private region-body
  [region rendered]
  (let [quoted-region (java.util.regex.Pattern/quote (name region))
        pattern (re-pattern (str "(?s)// BEGIN " quoted-region "\\n(.*?)\\n\\s*// END " quoted-region))]
    (second (re-find pattern rendered))))

(defn ^:private combo-node-blocks
  [rendered]
  (let [body (region-body :combos rendered)
        pattern #"(?ms)^\s*([A-Za-z0-9_]+)\s+\{.*?^\s*\};"]
    (into {}
          (map (fn [[block name]] [name block]))
          (re-seq pattern body))))

(defn ^:private balanced-braces?
  [s]
  (zero? (reduce (fn [depth ch]
                   (cond
                     (neg? depth) (reduced depth)
                     (= ch \{) (inc depth)
                     (= ch \}) (dec depth)
                     :else depth))
                 0
                 s)))

(defmacro ^:private deftest-examples
  "Generate one deftest per discovered example at macro-expansion time."
  []
  `(do
     ~@(for [{:keys [num config in out]} (discover-examples)
             :let [test-name (symbol (str "example-" num "-generates-expected-keymap"))]]
         `(deftest ~test-name
            (let [cfg# (generator/load-config ~config)
                  template# (slurp ~in)
                  expected# (slurp ~out)
                  generated# (generator/generate-keymap template# cfg#)]
              (is (= (tokenize expected#)
                     (tokenize generated#))
                                     (str "Example " ~config " did not generate expected output (whitespace-agnostic comparison)")))))))

(deftest-examples)

(deftest missing-markers-throws
  (let [config {:regions [[:keymap {:raw-body? true
                                    :nodes [{:name "base_layer"
                                             :body ["    display-name = \"BASE\";"]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Could not find markers in template"
         (generator/generate-keymap "keymap {}" config)))))

(deftest binding-dsl-compiles-cells
  (is (= "&kp P" (generator/binding->str :P)))
  (is (= "&lt 3 DE_S" (generator/binding->str [:lt 3 :DE_S])))
  (is (= "&bt BT_SEL 0" (generator/binding->str [:bt :BT_SEL 0])))
  (is (= "&trans" (generator/binding->str :trans)))
     (is (= "&none" (generator/binding->str :none))))

(deftest resolve-alias-expands-keywords-recursively
  (let [aliases {:_ :trans :trans :none :S [:lt 3 :DE_S]}]
    (is (= :none (generator/resolve-alias aliases :_)))
    (is (= [:lt 3 :DE_S] (generator/resolve-alias aliases :S)))
    (is (= :P (generator/resolve-alias aliases :P)))))

(deftest aliases-expand-in-keymap-bindings
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:aliases {:_ :trans :S [:lt 3 :DE_S]}
                :regions [[:keymap {:nodes [{:name "BASE"
                                             :bindings [[:S :A :_]]}]}]]}]
    (is (re-find #"BASE \{" (generator/generate-keymap template config)))
    (is (re-find #"&lt 3 DE_S &kp A &trans"
                 (generator/generate-keymap template config)))))

(deftest layer-generates-display-name-from-name
  (let [rendered (generator/render-layer {:name "BASE"
                                          :bindings [[:P :O]
                                                     [[:lt 3 :DE_S] :A]]}
                                         2)]
    (is (re-find #"BASE \{" rendered))
    (is (re-find #"display-name = \"BASE\";" rendered))
    (is (re-find #"&kp P &kp O" rendered))
    (is (re-find #"&lt 3 DE_S &kp A" rendered))))

(deftest combo-layer-generates-combos
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3]
                                      :pattern [[0 0] [1 1]]
                                      :bindings [[:Q :W :E]
                                                 [:A :S :D]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "diag_0_0"))
    (is (str/includes? generated "key-positions = <0 4>;"))
    (is (str/includes? generated "bindings = <&kp Q>;"))
    (is (str/includes? generated "diag_0_1"))
    (is (str/includes? generated "key-positions = <1 5>;"))
    (is (str/includes? generated "bindings = <&kp W>;"))
    (is (not (str/includes? generated "diag_1_0")))
    (is (not (str/includes? generated "diag_1_1")))
    (is (not (str/includes? generated "diag_0_2")))
    (is (not (str/includes? generated "diag_1_2")))))

(deftest combo-layer-skips-none-and-trans
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3]
                                      :pattern [[0 0] [1 1]]
                                      :bindings [[:Q :none :trans]
                                                 [:trans :S :none]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :none :trans]
                                                [:trans :S :none]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "diag_0_0"))
    (is (not (str/includes? generated "diag_0_1")))
    (is (not (str/includes? generated "diag_0_2")))
    (is (not (str/includes? generated "diag_1_0")))
    (is (not (str/includes? generated "diag_1_1")))
    (is (not (str/includes? generated "diag_1_2")))))

(deftest combo-layer-resolves-layer-names
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3]
                                      :pattern [[0 0] [1 1]]
                                      :bindings [[:Q :W :E]
                                                 [:A :S :D]]
                                      :layers [:BASE]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "layers = <0>;"))))

(deftest raw-node-resolves-layer-names
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:raw-body? true
                            :nodes [{:name "raw_combo"
                                     :body ["bindings = <&kp ESC>;"
                                            "key-positions = <0 1>;"
                                            "timeout-ms = <30>;"]
                                     :layers [:BASE]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "raw_combo {"))
    (is (str/includes? generated "bindings = <&kp ESC>;"))
    (is (str/includes? generated "layers = <0>;"))))

(deftest raw-node-without-layers-remains-supported
  (let [template "    // BEGIN combos
    // END combos
"
        config {:regions [[:combos
                           {:raw-body? true
                            :nodes [{:name "raw_combo"
                                     :body ["bindings = <&kp ESC>;"
                                            "key-positions = <0 1>;"
                                            "timeout-ms = <30>;"]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "bindings = <&kp ESC>;"))
    (is (not (str/includes? generated "layers = <")))))

(deftest raw-node-unknown-layer-name-throws
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:raw-body? true
                            :nodes [{:name "raw_combo"
                                     :body ["bindings = <&kp ESC>;"
                                            "key-positions = <0 1>;"]
                                     :layers [:NOT_A_LAYER]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unknown layer name"
         (generator/generate-keymap template config)))))

(deftest horizontal-base-combos-render-from-base-scoped-combo-layers
  (let [generated (generator/generate-keymap (slurp "examples/1_in.keymap")
                                             (generator/load-config "examples/1.edn"))
        horizontal-combos [{:name "horizontal_rtl_0_4"
                            :binding "bindings = <&kp DE_Z>;"
                            :key-positions "key-positions = <4 3>;"}
                           {:name "horizontal_rtl_0_3"
                            :binding "bindings = <&kp DE_M>;"
                            :key-positions "key-positions = <3 2>;"}
                           {:name "horizontal_ltr_0_1"
                            :binding "bindings = <&kp DE_W>;"
                            :key-positions "key-positions = <1 2>;"}
                           {:name "horizontal_ltr_0_0"
                            :binding "bindings = <&kp DE_X>;"
                            :key-positions "key-positions = <0 1>;"}
                           {:name "horizontal_ltr_1_3"
                            :binding "bindings = <&kp DE_G>;"
                            :key-positions "key-positions = <13 14>;"}
                           {:name "horizontal_rtl_1_3"
                            :binding "bindings = <&kp DE_V>;"
                            :key-positions "key-positions = <13 12>;"}
                           {:name "horizontal_ltr_1_1"
                            :binding "bindings = <&kp TAB>;"
                            :key-positions "key-positions = <11 12>;"}
                           {:name "horizontal_ltr_1_0"
                            :binding "bindings = <&kp DE_Q>;"
                            :key-positions "key-positions = <10 11>;"}
                           {:name "horizontal_rtl_2_5"
                            :binding "bindings = <&kp DE_B>;"
                            :key-positions "key-positions = <25 24>;"}
                           {:name "horizontal_rtl_2_4"
                            :binding "bindings = <&kp DE_J>;"
                            :key-positions "key-positions = <24 23>;"}
                           {:name "horizontal_ltr_2_2"
                            :binding "bindings = <&kp DE_K>;"
                            :key-positions "key-positions = <22 23>;"}
                           {:name "horizontal_ltr_2_1"
                            :binding "bindings = <&kp DE_Y>;"
                            :key-positions "key-positions = <21 22>;"}]
        old-raw-names ["kpz" "kpm" "kpw" "kpx" "kpg" "kpv"
                       "kptap" "kpq" "kpb" "kpj" "kpk" "kpy"]]
    (doseq [{:keys [name binding key-positions]} horizontal-combos]
      (let [block (node-block name generated)]
        (is (str/starts-with? name "horizontal_") (str name " has horizontal-oriented prefix"))
        (is block (str name " generated combo node is present"))
        (when block
          (is (str/includes? block binding) (str name " renders the expected binding"))
          (is (str/includes? block key-positions) (str name " preserves key positions"))
          (is (str/includes? block "layers = <0>;") (str name " is scoped to BASE only")))))
    (doseq [old-name old-raw-names]
      (is (nil? (node-block old-name generated))
          (str old-name " old raw horizontal combo node is not rendered")))))

(deftest vertical-base-combos-render-from-base-scoped-combo-layer
  (let [generated (generator/generate-keymap (slurp "examples/1_in.keymap")
                                             (generator/load-config "examples/1.edn"))
        vertical-combos [{:name "vertical_0_4"
                          :binding "bindings = <&sk LEFT_GUI>;"
                          :key-positions "key-positions = <4 14>;"}
                         {:name "vertical_0_3"
                          :binding "bindings = <&sk LEFT_ALT>;"
                          :key-positions "key-positions = <3 13>;"}
                         {:name "vertical_0_2"
                          :binding "bindings = <&esc_layerreset>;"
                          :key-positions "key-positions = <2 12>;"}
                         {:name "vertical_0_1"
                          :binding "bindings = <&round_brackets>;"
                          :key-positions "key-positions = <1 11>;"}
                         {:name "vertical_0_0"
                          :binding "bindings = <&backspace_delete>;"
                          :key-positions "key-positions = <0 10>;"}]
        old-raw-names ["kpgui" "kpalt" "kpesc" "round_brackets" "backspace_delete"]]
    (doseq [{:keys [name binding key-positions]} vertical-combos]
      (let [block (node-block name generated)]
        (is (str/starts-with? name "vertical_") (str name " has vertical-oriented prefix"))
        (is block (str name " generated combo node is present"))
        (when block
          (is (str/includes? block binding) (str name " renders the expected binding"))
          (is (str/includes? block key-positions) (str name " preserves key positions"))
          (is (str/includes? block "layers = <0>;") (str name " is scoped to BASE only")))))
    (doseq [old-name old-raw-names]
      (is (nil? (node-block old-name generated))
          (str old-name " old raw vertical combo node is not rendered")))))

(deftest diagonal-base-combos-render-from-base-scoped-combo-layers
  (let [generated (generator/generate-keymap (slurp "examples/1_in.keymap")
                                             (generator/load-config "examples/1.edn"))
        diagonal-combos [{:name "diagonal_down_right_1_4"
                          :binding "bindings = <&sk LCTRL>;"
                          :key-positions "key-positions = <14 25>;"}
                         {:name "diagonal_down_right_1_3"
                          :binding "bindings = <&caps_word>;"
                          :key-positions "key-positions = <13 24>;"}
                         {:name "diagonal_down_right_reverse_2_3"
                          :binding "bindings = <&kp SPACE>;"
                          :key-positions "key-positions = <23 12>;"}
                         {:name "diagonal_down_right_1_1"
                          :binding "bindings = <&square_brackets>;"
                          :key-positions "key-positions = <11 22>;"}
                         {:name "diagonal_down_right_1_0"
                          :binding "bindings = <&kp ENTER>;"
                          :key-positions "key-positions = <10 21>;"}
                         {:name "diagonal_down_right_reverse_1_4"
                          :binding "bindings = <&curly_brackets>;"
                          :key-positions "key-positions = <14 3>;"}
                         {:name "diagonal_down_right_0_2"
                          :binding "bindings = <&punkt_doppelpunkt>;"
                          :key-positions "key-positions = <2 13>;"}]
        old-raw-names ["kpctrl" "kpcapsword" "kpspace" "square_brackets"
                       "enter" "curly_brackets" "punkt_doppelpunkt"]]
    (doseq [{:keys [name binding key-positions]} diagonal-combos]
      (let [block (node-block name generated)]
        (is (str/starts-with? name "diagonal_") (str name " has diagonal-oriented prefix"))
        (is block (str name " generated combo node is present"))
        (when block
          (is (str/includes? block binding) (str name " renders the expected binding"))
          (is (str/includes? block key-positions) (str name " preserves key positions"))
          (is (str/includes? block "layers = <0>;") (str name " is scoped to BASE only")))))
    (doseq [old-name old-raw-names]
      (is (nil? (node-block old-name generated))
          (str old-name " old raw diagonal combo node is not rendered")))))

(deftest retained-irregular-combos-render-as-base-scoped-raw-nodes
  (let [generated (generator/generate-keymap (slurp "examples/1_in.keymap")
                                             (generator/load-config "examples/1.edn"))
        retained-combos [{:name "angled_brackets"
                          :binding "bindings = <&angled_brackets>;"
                          :key-positions "key-positions = <25 13>;"}
                         {:name "komma_strichpunkt"
                          :binding "bindings = <&komma_strickpunkt>;"
                          :key-positions "key-positions = <24 12>;"}
                         {:name "toBT"
                          :binding "bindings = <&to 4>;"
                          :key-positions "key-positions = <1 2 3 4>;"}
                         {:name "ae"
                          :binding "bindings = <&M_UPPER_AEOEUE DE_A_UMLAUT>;"
                          :key-positions "key-positions = <33 21 32>;"}]]
    (doseq [{:keys [name binding key-positions]} retained-combos]
      (let [block (node-block name generated)]
        (is block (str name " raw combo node is present"))
        (is (str/includes? block binding) (str name " preserves binding"))
        (is (str/includes? block key-positions) (str name " preserves key positions"))
        (is (str/includes? block "layers = <0>;") (str name " is scoped to BASE only"))))))

(deftest complete-base-scoped-combo-migration-renders-only-inventory-combos
  (let [generated (generator/generate-keymap (slurp "examples/1_in.keymap")
                                             (generator/load-config "examples/1.edn"))
        expected-combos [{:name "horizontal_rtl_0_4" :group :horizontal :binding "bindings = <&kp DE_Z>;" :key-positions "key-positions = <4 3>;"}
                         {:name "horizontal_rtl_0_3" :group :horizontal :binding "bindings = <&kp DE_M>;" :key-positions "key-positions = <3 2>;"}
                         {:name "horizontal_ltr_0_1" :group :horizontal :binding "bindings = <&kp DE_W>;" :key-positions "key-positions = <1 2>;"}
                         {:name "horizontal_ltr_0_0" :group :horizontal :binding "bindings = <&kp DE_X>;" :key-positions "key-positions = <0 1>;"}
                         {:name "horizontal_ltr_1_3" :group :horizontal :binding "bindings = <&kp DE_G>;" :key-positions "key-positions = <13 14>;"}
                         {:name "horizontal_rtl_1_3" :group :horizontal :binding "bindings = <&kp DE_V>;" :key-positions "key-positions = <13 12>;"}
                         {:name "horizontal_ltr_1_1" :group :horizontal :binding "bindings = <&kp TAB>;" :key-positions "key-positions = <11 12>;"}
                         {:name "horizontal_ltr_1_0" :group :horizontal :binding "bindings = <&kp DE_Q>;" :key-positions "key-positions = <10 11>;"}
                         {:name "horizontal_rtl_2_5" :group :horizontal :binding "bindings = <&kp DE_B>;" :key-positions "key-positions = <25 24>;"}
                         {:name "horizontal_rtl_2_4" :group :horizontal :binding "bindings = <&kp DE_J>;" :key-positions "key-positions = <24 23>;"}
                         {:name "horizontal_ltr_2_2" :group :horizontal :binding "bindings = <&kp DE_K>;" :key-positions "key-positions = <22 23>;"}
                         {:name "horizontal_ltr_2_1" :group :horizontal :binding "bindings = <&kp DE_Y>;" :key-positions "key-positions = <21 22>;"}
                         {:name "vertical_0_4" :group :vertical :binding "bindings = <&sk LEFT_GUI>;" :key-positions "key-positions = <4 14>;"}
                         {:name "vertical_0_3" :group :vertical :binding "bindings = <&sk LEFT_ALT>;" :key-positions "key-positions = <3 13>;"}
                         {:name "vertical_0_2" :group :vertical :binding "bindings = <&esc_layerreset>;" :key-positions "key-positions = <2 12>;"}
                         {:name "vertical_0_1" :group :vertical :binding "bindings = <&round_brackets>;" :key-positions "key-positions = <1 11>;"}
                         {:name "vertical_0_0" :group :vertical :binding "bindings = <&backspace_delete>;" :key-positions "key-positions = <0 10>;"}
                         {:name "diagonal_down_right_1_4" :group :diagonal :binding "bindings = <&sk LCTRL>;" :key-positions "key-positions = <14 25>;"}
                         {:name "diagonal_down_right_1_3" :group :diagonal :binding "bindings = <&caps_word>;" :key-positions "key-positions = <13 24>;"}
                         {:name "diagonal_down_right_reverse_2_3" :group :diagonal :binding "bindings = <&kp SPACE>;" :key-positions "key-positions = <23 12>;"}
                         {:name "diagonal_down_right_1_1" :group :diagonal :binding "bindings = <&square_brackets>;" :key-positions "key-positions = <11 22>;"}
                         {:name "diagonal_down_right_1_0" :group :diagonal :binding "bindings = <&kp ENTER>;" :key-positions "key-positions = <10 21>;"}
                         {:name "diagonal_down_right_reverse_1_4" :group :diagonal :binding "bindings = <&curly_brackets>;" :key-positions "key-positions = <14 3>;"}
                         {:name "diagonal_down_right_0_2" :group :diagonal :binding "bindings = <&punkt_doppelpunkt>;" :key-positions "key-positions = <2 13>;"}
                         {:name "angled_brackets" :group :raw :binding "bindings = <&angled_brackets>;" :key-positions "key-positions = <25 13>;"}
                         {:name "komma_strichpunkt" :group :raw :binding "bindings = <&komma_strickpunkt>;" :key-positions "key-positions = <24 12>;"}
                         {:name "toBT" :group :raw :binding "bindings = <&to 4>;" :key-positions "key-positions = <1 2 3 4>;"}
                         {:name "ae" :group :raw :binding "bindings = <&M_UPPER_AEOEUE DE_A_UMLAUT>;" :key-positions "key-positions = <33 21 32>;"}]
        combo-blocks (combo-node-blocks generated)
        expected-names (set (map :name expected-combos))
        generated-names (set (keys combo-blocks))
        generated-combo-names (remove #(= :raw (:group %)) expected-combos)
        raw-combo-names (->> expected-combos (filter #(= :raw (:group %))) (map :name) set)]
    (is (balanced-braces? generated) "rendered keymap has balanced devicetree braces")
    (is (= 28 (count combo-blocks)) "rendered combos match the 28-combo migration inventory count")
    (is (= expected-names generated-names) "rendered combos contain the migration inventory and no extras")
    (is (= #{"angled_brackets" "komma_strichpunkt" "toBT" "ae"}
           raw-combo-names)
        "retained irregular raw combos preserve semantic node names")
    (doseq [{:keys [name group binding key-positions]} expected-combos]
      (let [block (get combo-blocks name)]
        (is block (str name " is rendered"))
        (is (str/includes? block binding) (str name " preserves the inventory binding"))
        (is (str/includes? block key-positions) (str name " preserves the inventory key positions"))
        (is (str/includes? block "layers = <0>;") (str name " is scoped to BASE"))
        (is (not (re-find #"layers\s*=\s*<\s*>" block)) (str name " is not global"))
        (case group
          :horizontal (is (str/starts-with? name "horizontal_") (str name " uses a horizontal group prefix"))
          :vertical (is (str/starts-with? name "vertical_") (str name " uses a vertical group prefix"))
          :diagonal (is (str/starts-with? name "diagonal_") (str name " uses a diagonal group prefix"))
          :raw (is (contains? raw-combo-names name) (str name " remains a retained raw combo")))))
    (is (= 24 (count generated-combo-names)) "horizontal, vertical, and diagonal Combo-layers account for 24 generated combos")
    (is (empty? (filter #(re-find #"(?i)^(nav|num|bt)[_-]" (:name %)) generated-combo-names))
        "no Nav-, Num-, or BT-specific generated Combo-layer names are introduced")))

(deftest combo-layer-skips-out-of-bounds
  (let [template "    // BEGIN combos\n    // END combos\n    // BEGIN keymap\n    // END keymap\n"
        config {:regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3 3]
                                      :pattern [[0 0] [1 1] [2 2]]
                                      :bindings [[:Q :W :E]
                                                 [:A :S :D]
                                                 [:Z :X :C]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]
                                                [:Z :X :C]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "diag_0_0"))
    (is (not (str/includes? generated "diag_0_1")))
    (is (not (str/includes? generated "diag_0_2")))
    (is (not (str/includes? generated "diag_1_0")))
    (is (not (str/includes? generated "diag_1_1")))
    (is (not (str/includes? generated "diag_1_2")))
    (is (not (str/includes? generated "diag_2_0")))
    (is (not (str/includes? generated "diag_2_1")))
    (is (not (str/includes? generated "diag_2_2")))))

(deftest placements-mirror-horizontal
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]
                                            [:D :E :F]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [6 6]
                                     :placements [{:tile :alpha :pos [0 0]}
                                                  {:tile :alpha :pos [3 0] :mirror :horizontal}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &kp B &kp C &kp C &kp B &kp A" generated))
    (is (re-find #"&kp D &kp E &kp F &kp F &kp E &kp D" generated))))

(deftest placements-clip-per-placement
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [4]
                                     :placements [{:tile :alpha :pos [2 0] :mirror :horizontal :clip? true}]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; mirrored [:C :B :A] placed at col 2: C at 2, B at 3, A at 4 (oob clipped)
    (is (re-find #"&trans &trans &kp C &kp B" generated))))

(deftest placements-clip-at-placement-overrides-node
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2]
                                     :clip? false
                                     :placements [{:tile :alpha :pos [0 0] :clip? true}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &kp B" generated))
    (is (not (re-find #"&kp C" generated)))))

(deftest placements-mirror-and-clip-compose
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]
                                            [:D :E :F]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [4 4]
                                     :placements [{:tile :alpha :pos [0 0]}
                                                  {:tile :alpha :pos [2 0] :mirror :horizontal :clip? true}]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; first alpha: A B C trans
    ;; second mirrored alpha [:C :B :A] at col 2: C at 2, B at 3, A at 4 (oob clipped)
    ;; row 0 result: A B C B
    (is (re-find #"&kp A &kp B &kp C &kp B" generated))
    ;; row 1: D E F from first, mirrored row 1 [:F :E :D]: F at 2, E at 3, D at 4 (clipped)
    ;; row 1 result: D E F E
    (is (re-find #"&kp D &kp E &kp F &kp E" generated))))

(deftest combo-layer-expands-aliases
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:aliases {:_ :trans}
                :regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3]
                                      :pattern [[0 0] [1 1]]
                                      :bindings [[:Q :_ :E]
                                                 [:A :S :D]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (str/includes? generated "diag_0_0"))
    (is (not (str/includes? generated "diag_0_1")))))

(deftest placements-assemble-layer-bindings
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B]
                                            [:C :D]]}
                         :num   {:bindings [[:N1]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [3 3]
                                     :placements [{:tile :alpha :pos [0 0]}
                                                  {:tile :num :pos [2 1]}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"BASE \{" generated))
    (is (re-find #"&kp A &kp B &trans" generated))
    (is (re-find #"&kp C &kp D &kp N1" generated))))

(deftest placements-overlap-last-wins
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]]}
                         :beta  {:bindings [[:X :Y]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [3]
                                     :placements [{:tile :alpha :pos [0 0]}
                                                  {:tile :beta  :pos [1 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &kp X &kp Y" generated))))

(deftest placements-oob-throws
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2]
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"out of bounds"
         (generator/generate-keymap template config)))))

(deftest placements-clip-skips-oob
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2]
                                     :clip? true
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &kp B" generated))
    (is (not (re-find #"&kp C" generated)))))

(deftest placements-empty-defaults-to-trans
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2 2]
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &trans" generated))
    (is (re-find #"&trans &trans" generated))))

(deftest placements-empty-custom
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2 2]
                                     :empty :none
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    (is (re-find #"&kp A &none" generated))
    (is (re-find #"&none &none" generated))))

(deftest placements-unknown-tile-throws
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2]
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unknown tile"
         (generator/generate-keymap template config)))))

(deftest placements-requires-row-widths
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B]]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :placements [{:tile :alpha :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":row-widths is required"
         (generator/generate-keymap template config)))))

(deftest combo-layer-requires-row-widths
  (let [template "    // BEGIN combos
    // END combos
    // BEGIN keymap
    // END keymap
"
        config {:regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :pattern [[0 0] [1 1]]
                                      :bindings [[:Q]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q]]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":row-widths is required"
         (generator/generate-keymap template config)))))

; (deftest rich-comment-tests
 ; (test-runner/run-tests-in-file-tree! :dirs #{"./"} ))




(defn run
  []
  (let [{:keys [fail error] :as result} (run-tests 'generator-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" result)))
    result))

(comment
  (run)
  )
