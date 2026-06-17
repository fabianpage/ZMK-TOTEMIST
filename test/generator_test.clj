(ns generator-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [generator :as generator]))

(deftest example-config-generates-expected-keymap
  (let [config (generator/load-config "examples/1.edn")
        template (slurp "examples/1_in.keymap")
        expected (slurp "examples/1_out.keymap")]
    (is (= expected
           (generator/generate-keymap template config)))))

(deftest missing-keymap-markers-throws
  (let [config {:keymap {:nodes [{:name "base_layer"
                                  :body ["display-name = \"BASE\";"]}]}}]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Could not find keymap markers"
         (generator/generate-keymap "keymap {}" config)))))

(defn run
  []
  (let [{:keys [fail error] :as result} (run-tests 'generator-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "Tests failed" result)))
    result))
