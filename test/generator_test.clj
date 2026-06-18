(ns generator-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [generator :as generator]))

(defn ^:private normalize-whitespace [s]
  (->> (str/split-lines s)
       (map str/trim)
       (remove str/blank?)
       (str/join "\n")))

(deftest example-config-generates-expected-keymap
  (let [config (generator/load-config "examples/1.edn")
        template (slurp "examples/1_in.keymap")
        expected (slurp "examples/1_out.keymap")]
    (is (= (normalize-whitespace expected)
           (normalize-whitespace
             (generator/generate-keymap template config))))))

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

(deftest layer-generates-display-name-from-name
  (let [rendered (generator/render-layer {:name "BASE"
                                          :bindings [[:P :O]
                                                     [[:lt 3 :DE_S] :A]]}
                                         2)]
    (is (re-find #"BASE \{" rendered))
    (is (re-find #"display-name = \"BASE\";" rendered))
    (is (re-find #"&kp P &kp O" rendered))
    (is (re-find #"&lt 3 DE_S &kp A" rendered))))

(defn run
  []
  (let [{:keys [fail error] :as result} (run-tests 'generator-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" result)))
    result))
