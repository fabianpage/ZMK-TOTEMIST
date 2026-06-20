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

(deftest all-examples-generate-expected-keymaps
  (doseq [{:keys [config in out]} (discover-examples)]
    (let [cfg (generator/load-config config)
          template (slurp in)
          expected (slurp out)
          generated (generator/generate-keymap template cfg)]
      (is (= (tokenize expected)
             (tokenize generated))
          (str "Example " config " did not generate expected output (whitespace-agnostic comparison)")))))

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
