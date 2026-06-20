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

(defn extract-layer-indexes
  "Build a map from layer name string to its 0-based index,
   by scanning the :keymap region nodes in the config."
  [config]
  (if-let [keymap-region (some (fn [[region spec]]
                                 (when (= region :keymap) spec))
                               (:regions config))]
    (into {} (map-indexed (fn [idx node]
                            [(name (:name node)) idx])
                           (:nodes keymap-region)))
    {}))

(defn combo-positions
  "Given row-widths, a pattern of [[row-off col-off] ...], and a base [row col],
   return the absolute ZMK key-positions in pattern order, or nil if any
   offset is out of bounds."
  [row-widths pattern [base-r base-c]]
  (let [num-rows (count row-widths)
        prefix-sums (reductions + 0 row-widths)]
    (when (every? (fn [[r-off c-off]]
                    (let [r (+ base-r r-off)
                          c (+ base-c c-off)]
                      (and (>= r 0) (< r num-rows)
                           (>= c 0) (< c (nth row-widths r)))))
                  pattern)
      (map (fn [[r-off c-off]]
             (let [r (+ base-r r-off)
                   c (+ base-c c-off)]
               (+ c (nth prefix-sums r))))
           pattern))))

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

(defn render-combo-layer
  "Render a :combo-layer node into one or more ZMK combo DT nodes.
   :row-widths is required. :pattern defines relative offsets.
   :bindings uses the normal binding DSL. :layers can be keywords
   (resolved against the keymap) or raw numbers."
  [{:keys [name row-widths pattern bindings layers] :as node} level {:keys [layer-index-map]}]
  (when-not row-widths
    (throw (ex-info ":row-widths is required for :combo-layer" {:node node})))
  (let [layer-nums (when (seq layers)
                       (map (fn [layer]
                              (if (keyword? layer)
                                (if-let [idx (get layer-index-map (clojure.core/name layer))]
                                  idx
                                  (throw (ex-info (str "Unknown layer name: " layer)
                                                  {:layer layer :available (keys layer-index-map)})))
                                layer))
                             layers))
        layer-line (when (seq layer-nums)
                     (str (indent (inc level)) "layers = <" (str/join " " layer-nums) ">;"))
        combos (for [r (range (count bindings))
                     c (range (count (nth bindings r)))
                     :let [cell (get-in bindings [r c])
                           positions (combo-positions row-widths pattern [r c])]
                     :when (and positions
                                (not (#{:none :trans} cell)))]
                 (let [combo-name (str name "_" r "_" c)]
                   (str/join
                    "\n"
                    (concat [(str (indent level) combo-name " {")
                             (str (indent (inc level)) "bindings = <" (binding->str cell) ">;")
                             (str (indent (inc level)) "key-positions = <" (str/join " " positions) ">;")]
                            (when layer-line [layer-line])
                            [(str (indent level) "};")]))))]
    (str/join "\n\n" combos)))

(defn render-node
  [{:keys [type] :as node} level raw-body? opts]
  (case type
    :combo-layer (render-combo-layer node level opts)
    (if (:bindings node)
      (render-layer node level)
      (str/join
       "\n"
       (concat [(str (indent level)
                     (:name node)
                     (when (:label node)
                       (str ": " (:label node)))
                     " {")]
               (if raw-body?
                 (:body node)
                 (map #(render-line (inc level) %) (:body node)))
               [(str (indent level) "};")])))))

(defn render-nodes
  [nodes level raw-body? opts]
  (str/join "\n" (interpose "" (map #(render-node % level raw-body? opts) nodes))))

(defn replace-between-markers
  [text region nodes raw-body? opts]
  (let [begin (str "// BEGIN " (name region))
        end (str "// END " (name region))
        pattern (re-pattern (str "(?sm)^([ \\t]*)" (Pattern/quote begin)
                                 ".*?^([ \\t]*)" (Pattern/quote end)))
        match (re-find pattern text)]
    (when-not match
      (throw (ex-info "Could not find markers in template"
                      {:region region})))
    (let [[whole bol] match
          rendered (when (seq nodes) (render-nodes nodes 2 raw-body? opts))]
      (str/replace-first
       text whole
       (str bol begin "\n"
            (when rendered (str rendered "\n"))
            bol end)))))

(defn generate-keymap
  [template config]
  (let [config (expand-aliases config)
        layer-index-map (extract-layer-indexes config)
        opts {:layer-index-map layer-index-map}]
    (str/replace
     (reduce (fn [text [region {:keys [nodes raw-body?]}]]
               (replace-between-markers text region nodes raw-body? opts))
             template
             (:regions config))
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
