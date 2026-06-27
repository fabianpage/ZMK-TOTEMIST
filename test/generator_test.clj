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

(deftest totem-config-generates-captured-baseline
  (let [config (generator/load-config "totem_config.edn")
        template (slurp "totem_template.keymap")
        expected (slurp "examples/totem_generated_baseline.keymap")
        generated (generator/generate-keymap template config)]
    (is (= expected generated)
        "totem_config.edn + totem_template.keymap must regenerate the captured pre-refactor keymap baseline exactly.")))

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

(deftest binding-dsl-compiles-press-release-tap-wrappers
  (is (= "&macro_press &kp A" (generator/binding->str [:press :A])))
  (is (= "&macro_release &kp B" (generator/binding->str [:release :B])))
  (is (= "&macro_tap &kp C" (generator/binding->str [:tap :C])))
  ;; wrappers compose with vector bindings
  (is (= "&macro_press &mo 2" (generator/binding->str [:press [:mo 2]])))
  (is (= "&macro_release &lt 3 DE_S" (generator/binding->str [:release [:lt 3 :DE_S]])))
  (is (= "&macro_tap &bt BT_SEL 0" (generator/binding->str [:tap [:bt :BT_SEL 0]]))))

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

(deftest render-macro-0-param-generates-macro-node
  (let [rendered (generator/render-macro {:name "hello"
                                          :type :macro
                                          :body [:H :E :L :L :O]}
                                         2)]
    (is (str/includes? rendered "hello: hello {"))
    (is (str/includes? rendered "compatible = \"zmk,behavior-macro\";"))
    (is (str/includes? rendered "#binding-cells = <0>;"))
    (is (str/includes? rendered "bindings = <&kp H &kp E &kp L &kp L &kp O>;"))
    (is (not (str/includes? rendered "wait-ms")))
    (is (not (str/includes? rendered "tap-ms")))))

(deftest render-macro-emits-wait-ms-only
  (let [rendered (generator/render-macro {:name "slow-wait"
                                          :type :macro
                                          :body [:A :B]
                                          :wait-ms 80}
                                         2)]
    (is (str/includes? rendered "wait-ms = <80>;"))
    (is (not (str/includes? rendered "tap-ms")))))

(deftest render-macro-emits-tap-ms-only
  (let [rendered (generator/render-macro {:name "slow-tap"
                                          :type :macro
                                          :body [:A :B]
                                          :tap-ms 20}
                                         2)]
    (is (str/includes? rendered "tap-ms = <20>;"))
    (is (not (str/includes? rendered "wait-ms")))))

(deftest render-macro-with-wrapper-bindings
  (let [rendered (generator/render-macro {:name "ctrl_a"
                                          :type :macro
                                          :body [[:press :LCTRL] :A [:release :LCTRL]]}
                                         2)]
    (is (str/includes? rendered "bindings = <&macro_press &kp LCTRL &kp A &macro_release &kp LCTRL>;"))))

(deftest render-macro-emits-wait-ms-and-tap-ms
  (let [rendered (generator/render-macro {:name "slow"
                                          :type :macro
                                          :body [:A :B]
                                          :wait-ms 40
                                          :tap-ms 30}
                                         2)]
    (is (str/includes? rendered "wait-ms = <40>;"))
    (is (str/includes? rendered "tap-ms = <30>;"))))

(deftest aliases-expand-inside-macro-bodies
  (let [template "    // BEGIN macros
    // END macros
    // BEGIN keymap
    // END keymap
"
        config {:aliases {:ESC :ESCAPE :CTRL [:lt 2 :LCTRL]}
                :regions [[:macros
                           {:nodes [{:name "esc_macro"
                                     :type :macro
                                     :body [:ESC [:press :CTRL] [:wait 30] [:release :CTRL]]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :E]
                                                [:A :S :D]]}]}]]}]
    (is (re-find #"bindings = <&kp ESCAPE &macro_press &lt 2 LCTRL &macro_wait_time 30 &macro_release &lt 2 LCTRL>"
                 (generator/generate-keymap template config)))
    (is (re-find #"esc_macro" (generator/generate-keymap template config)))))

(deftest raw-body-macro-nodes-render-backward-compatible
  (let [template "    // BEGIN macros
    // END macros
"
        config {:regions [[:macros
                           {:nodes [{:name "legacy"
                                     :label "LEGACY"
                                     :body ["compatible = \"zmk,behavior-macro\";"
                                            "#binding-cells = <0>;"
                                            "bindings = <&kp A &kp B>;"]}]}]]}]
    (let [generated (generator/generate-keymap template config)]
      (is (str/includes? generated "legacy: LEGACY {"))
      (is (str/includes? generated "    compatible = \"zmk,behavior-macro\";"))
      (is (str/includes? generated "    #binding-cells = <0>;"))
      (is (str/includes? generated "    bindings = <&kp A &kp B>;")))))


(deftest binding-dsl-compiles-macro-timing-steps
  (is (= "&macro_wait_time 30" (generator/binding->str [:wait 30])))
  (is (= "&macro_tap_time 50" (generator/binding->str [:tap-time 50])))
  (is (= "&macro_pause_for_release" (generator/binding->str [:pause]))))

(deftest render-macro-with-mixed-timing-bindings-and-wrappers
  (let [rendered (generator/render-macro {:name "combo_macro"
                                          :type :macro
                                          :body [[:press :LCTRL]
                                                 [:wait 30]
                                                 :A
                                                 [:tap-time 50]
                                                 [:pause]
                                                 :B
                                                 [:release :LCTRL]]}
                                         2)]
    (is (str/includes? rendered "bindings = <&macro_press &kp LCTRL &macro_wait_time 30 &kp A &macro_tap_time 50 &macro_pause_for_release &kp B &macro_release &kp LCTRL>;"))))

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

(deftest tiles-can-be-composed-recursively
  ;; :alpha is a simple tile, :beta is a tile made OF tiles
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B]]}
                         :beta  {:row-widths [4]
                                 :placements [{:tile :alpha :pos [0 0]}
                                              {:tile :alpha :pos [2 0] :mirror :horizontal}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [4]
                                     :placements [{:tile :beta :pos [0 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; beta = [A B] + mirrored [A B] = [A B B A]
    ;; layer places beta at [0 0] -> row is A B B A
    (is (re-find #"&kp A &kp B &kp B &kp A" generated))))

(deftest tile-cycle-throws
  ;; :a references :b, :b references :a — infinite recursion
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:a {:row-widths [2]
                            :placements [{:tile :b :pos [0 0]}]}
                         :b {:row-widths [2]
                            :placements [{:tile :a :pos [0 0]}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [2]
                                     :placements [{:tile :a :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Tile cycle detected"
         (generator/generate-keymap template config)))))

(deftest tile-direct-self-reference-throws
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:a {:row-widths [1]
                            :placements [{:tile :a :pos [0 0]}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [1]
                                     :placements [{:tile :a :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Tile cycle detected"
         (generator/generate-keymap template config)))))

(deftest tiles-deep-nesting-works
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:col1 {:bindings [[:A]]}
                         :col2 {:bindings [[:B]]}
                         :half {:row-widths [2]
                                :placements [{:tile :col1 :pos [0 0]}
                                             {:tile :col2 :pos [1 0]}]}
                         :full {:row-widths [4]
                                :placements [{:tile :half :pos [0 0]}
                                             {:tile :half :pos [2 0] :mirror :horizontal}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [4]
                                     :placements [{:tile :full :pos [0 0]}]}]}]]}]
    (is (re-find #"&kp A &kp B &kp B &kp A"
                 (generator/generate-keymap template config)))))

(deftest recursive-tile-missing-row-widths-throws
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:bad {:placements [{:tile :ignore :pos [0 0]}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [1]
                                     :placements [{:tile :bad :pos [0 0]}]}]}]]}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #":placements but no :row-widths"
         (generator/generate-keymap template config)))))

(deftest recursive-tile-clip-respected
  (let [template "    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:A :B :C :D]]}
                         :beta  {:row-widths [3]
                                 :placements [{:tile :alpha :pos [1 0] :clip? true}]}}
                :regions [[:keymap
                           {:nodes [{:name "BASE"
                                     :row-widths [3]
                                     :placements [{:tile :beta :pos [0 0]}]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; beta places [:A :B :C :D] starting at col 1 in a width-3 grid:
    ;; A->col1, B->col2, C->col3 (oob clipped), D->col4 (oob clipped)
    (is (re-find #"&trans &kp A &kp B" generated))))

(deftest combo-layer-with-placements-builds-from-tiles
  (let [template "    // BEGIN combos\n    // END combos\n    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:alpha {:bindings [[:Q :W :E]
                                            [:A :S :D]]}}
                :regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [3 3]
                                      :pattern [[0 0] [1 1]]
                                      :placements [{:tile :alpha :pos [0 0]}]}]}]
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
    (is (str/includes? generated "bindings = <&kp W>;"))))

(deftest combo-layer-placements-respect-mirror-and-clip
  (let [template "    // BEGIN combos\n    // END combos\n    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:left  {:bindings [[:Q :W] [:A :S]]}
                         :right {:bindings [[:O :P] [:L :X]]}}
                :regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [4 4]
                                      :pattern [[0 0] [1 1]]
                                      :placements [{:tile :left  :pos [0 0]}
                                                   {:tile :right :pos [2 0] :mirror :horizontal :clip? true}]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :O :P]
                                                [:A :S :L :X]]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; left:   [[Q W] [A S]] at col 0
    ;; right:  mirrored from [[O P] [L X]] -> [[P O] [X L]] at col 2
    ;; assembled grid: row0 [Q W P O], row1 [A S X L]
    ;; clipping: nothing OOB because right is only 2 cols wide at col 2
    ;; diag_0_0 (Q), diag_0_1 (W+X), diag_0_2 (P+L)
    (is (str/includes? generated "diag_0_0"))
    (is (str/includes? generated "diag_0_1"))
    (is (str/includes? generated "diag_0_2"))
    (is (str/includes? generated "bindings = <&kp P>;"))
    (is (str/includes? generated "key-positions = <2 7>;"))))

(deftest combo-layer-placements-overlap-last-wins
  (let [template "    // BEGIN combos\n    // END combos\n    // BEGIN keymap\n    // END keymap\n"
        config {:tiles {:left  {:bindings [[:Q :W] [:A :S]]}
                         :right {:bindings [[:O :P] [:L :X]]}}
                :regions [[:combos
                           {:nodes [{:name "diag"
                                      :type :combo-layer
                                      :row-widths [4 4]
                                      :pattern [[0 0] [1 1]]
                                      :placements [{:tile :left  :pos [0 0]}
                                                   {:tile :right :pos [1 0]}]}]}]
                          [:keymap
                           {:nodes [{:name "BASE"
                                     :bindings [[:Q :W :O :P]
                                                [:A :S :L :X]]}]}]]}
        generated (generator/generate-keymap template config)]
    ;; left at [0 0]: row0 [Q W . .], right at [1 0]: row0 [O P . .] starting at col 1
    ;; overlap at col 1: O overwrites W, P overwrites trans
    ;; assembled grid: row0 [Q O P trans], row1 [A L X trans]
    ;; Combos are generated for non-trans cells
    (is (str/includes? generated "diag_0_0"))
    (is (str/includes? generated "diag_0_1"))
    (is (str/includes? generated "diag_0_2"))
    ;; O won the overlap at [0,1], so combo uses O not W
    (is (str/includes? generated "bindings = <&kp O>;"))
    (is (not (str/includes? generated "bindings = <&kp W>;")))))

; (deftest rich-comment-tests
 (deftest replace-placeholder-swaps-placeholder
  (is (= :MACRO_PLACEHOLDER (generator/replace-placeholder :_placeholder)))
  (is (= [:kp :MACRO_PLACEHOLDER] (generator/replace-placeholder [:kp :_placeholder])))
  (is (= [:macro_tap [:kp :MACRO_PLACEHOLDER]] (generator/replace-placeholder [:macro_tap [:kp :_placeholder]]))))

(deftest binding-dsl-compiles-param-ops
  (is (= "&macro_param_1to1" (generator/binding->str :param-1to1)))
  (is (= "&macro_param_1to2" (generator/binding->str :param-1to2)))
  (is (= "&macro_param_2to1" (generator/binding->str :param-2to1)))
  (is (= "&macro_param_2to2" (generator/binding->str :param-2to2))))

(deftest render-macro-one-param-generates-expected-output
  (let [rendered (generator/render-macro {:name "upper"
                                          :type :macro-one-param
                                          :body [:CAPSLOCK [:pause] [:param-1to1 [:kp :_placeholder]] :CAPSLOCK]
                                          :wait-ms 80
                                          :tap-ms 80}
                                         2)]
    (is (str/includes? rendered "compatible = \"zmk,behavior-macro-one-param\";"))
    (is (str/includes? rendered "#binding-cells = <1>;"))
    (is (str/includes? rendered "<&kp CAPSLOCK>,"))
    (is (str/includes? rendered "<&macro_pause_for_release>,"))
    (is (str/includes? rendered "<&macro_param_1to1>,"))
    (is (str/includes? rendered "<&kp MACRO_PLACEHOLDER>,"))
    (is (str/includes? rendered "<&kp CAPSLOCK>;"))
    (is (str/includes? rendered "wait-ms = <80>;"))
    (is (str/includes? rendered "tap-ms = <80>;"))))

(deftest render-macro-two-param-generates-expected-output
  (let [rendered (generator/render-macro {:name "swap"
                                          :type :macro-two-param
                                          :body [[:param-2to1 [:kp :_placeholder]] [:param-2to2 [:kp :_placeholder]]]
                                          :wait-ms 20}
                                         2)]
    (is (str/includes? rendered "compatible = \"zmk,behavior-macro-two-param\";"))
    (is (str/includes? rendered "#binding-cells = <2>;"))
    (is (str/includes? rendered "<&macro_param_2to1>,"))
    (is (str/includes? rendered "<&kp MACRO_PLACEHOLDER>,"))
    (is (str/includes? rendered "<&macro_param_2to2>,"))
    (is (str/includes? rendered "<&kp MACRO_PLACEHOLDER>;"))))

(deftest param-wrappers-compose-with-macro-tap
  (let [rendered (generator/render-macro {:name "tap_param"
                                          :type :macro-one-param
                                          :body [[:param-1to1 [:macro_tap :_placeholder]]]}
                                         2)]
    (is (str/includes? rendered "<&macro_param_1to1>,"))
    (is (str/includes? rendered "<&macro_tap MACRO_PLACEHOLDER>;"))))

(deftest param-1to2-with-macro-tap-emits-expected-groups
  (let [rendered (generator/render-macro {:name "param12"
                                          :type :macro-one-param
                                          :body [[:param-1to2 [:macro_tap :_placeholder]]]}
                                         2)]
    (is (str/includes? rendered "<&macro_param_1to2>,"))
    (is (str/includes? rendered "<&macro_tap MACRO_PLACEHOLDER>;"))))

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
