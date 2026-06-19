(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {aero/aero {:mvn/version "1.1.6"}}})

(ns generator
  (:require [aero.core :as aero]
            [babashka.cli :as cli]
            [clojure.string :as str])
  (:import [java.util.regex Pattern]))

(defn indent
  [level]
  (str/join (repeat level "    ")))

(defn render-line
  [level line]
  (if (str/blank? line)
    ""
    (str (indent level) line)))

(defn token->str
  [token]
  (if (keyword? token)
    (name token)
    (str token)))

(defn resolve-alias
  "Recursively resolve a binding cell through the aliases map.
   :_ -> :trans -> &trans (one or more levels). Vectors and non-alias keywords are returned as-is."
  [aliases cell]
  (if (and (keyword? cell) (contains? aliases cell))
    (recur aliases (get aliases cell))
    cell))

(defn expand-aliases
  "Walk the config and expand alias keywords inside :bindings vectors of layer nodes.
   Other node types (raw :body strings) are left untouched."
  [config]
  (if-let [aliases (not-empty (:aliases config))]
    (letfn [(resolve [cell] (resolve-alias aliases cell))]
      (update config :regions
              (fn [regions]
                (mapv (fn [[region spec]]
                        [region
                         (update spec :nodes
                                 (fn [nodes]
                                   (mapv (fn [node]
                                           (if (:bindings node)
                                             (update node :bindings
                                                     (fn [rows]
                                                       (mapv (fn [row]
                                                               (mapv resolve row))
                                                             rows)))
                                             node))
                                         nodes)))])
                      regions))))
    config))

(defn binding->str
  "Compile one keymap cell into a ZMK binding string.
   :P              -> &kp P
   [:lt 3 :DE_S]   -> &lt 3 DE_S
   :trans/:none  -> &trans / &none (special case)"
  [cell]
  (cond
    (vector? cell)
    (str "&" (token->str (first cell))
         (when (seq (rest cell))
           (str " " (str/join " " (map token->str (rest cell))))))

    (keyword? cell)
    (case cell
      :trans "&trans"
      :none "&none"
      (str "&kp " (name cell)))

    :else
    (str cell)))

(defn render-layer
  "Render a keymap layer node. The :name doubles as the DT node id and the
   generated display-name. :bindings is a vector of rows, each a vector of cells."
  [{:keys [name bindings]} level]
  (str/join
   "\n"
   (concat [(str (indent level) name " {")
            (str (indent (inc level)) "display-name = \"" name "\";")
            (str (indent (inc level)) "bindings = <")]
           (map (fn [row] (str/join " " (map binding->str row))) bindings)
           [(str (indent (inc level)) ">;")
            (str (indent level) "};")])))

(defn render-node
  [{:keys [name label body bindings] :as node} level raw-body?]
  (if bindings
    (render-layer node level)
    (str/join
     "\n"
     (concat [(str (indent level)
                   name
                   (when label
                     (str ": " label))
                   " {")]
             (if raw-body?
               body
               (map #(render-line (inc level) %) body))
             [(str (indent level) "};")]))))

(defn render-nodes
  [nodes level raw-body?]
  (str/join "\n" (interpose "" (map #(render-node % level raw-body?) nodes))))

(defn replace-between-markers
  [text region nodes raw-body?]
  (let [begin (str "// BEGIN " (name region))
        end (str "// END " (name region))
        pattern (re-pattern (str "(?sm)^([ \\t]*)" (Pattern/quote begin)
                                 ".*?^([ \\t]*)" (Pattern/quote end)))
        match (re-find pattern text)]
    (when-not match
      (throw (ex-info "Could not find markers in template"
                      {:region region})))
    (let [[whole bol] match
          rendered (when (seq nodes) (render-nodes nodes 2 raw-body?))]
      (str/replace-first
       text whole
       (str bol begin "\n"
            (when rendered (str rendered "\n"))
            bol end)))))

(defn generate-keymap
  [template config]
  (let [{:keys [regions]} (expand-aliases config)]
    (str/replace
     (reduce (fn [text [region {:keys [nodes raw-body?]}]]
               (replace-between-markers text region nodes raw-body?))
             template
             regions)
     #"\n*\z" "\n")))

(defn load-config
  [path]
  (aero/read-config path))

(def cli-spec
  {:config {:require true :desc "Path to the EDN/Aero config"}
   :input  {:require true :desc "Path to the template .keymap"}
   :output {:desc "Output path (prints to stdout if omitted)"}})

(defn usage
  []
  (str "Usage: bb generator.clj --config <config.edn> --input <template.keymap> [--output <out.keymap>]\n\n"
       "Options:\n"
       (cli/format-opts {:spec cli-spec})))

(defn cli-error
  [{:keys [msg]}]
  (binding [*out* *err*]
    (println (str "Error: " msg "\n"))
    (println (usage)))
  (System/exit 1))

(defn write-output!
  [{:keys [config input output]}]
  (let [generated (generate-keymap (slurp input) (load-config config))]
    (if output
      (spit output generated)
      (print generated))
    generated))

(defn -main
  [& args]
  (if (some #{"--help" "-h"} args)
    (println (usage))
    (write-output! (cli/parse-opts args {:spec cli-spec
                                         :error-fn cli-error}))))

^:rct/test
(comment
  (binding->str :P) ;=> "&kp P"

  (binding->str :X) ;=> "&kp X"

  :rcf)

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
