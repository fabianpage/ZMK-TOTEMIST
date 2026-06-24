(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {aero/aero {:mvn/version "1.1.6"}}})

(ns generator
  (:require [aero.core :as aero]
            [babashka.cli :as cli]
            [clojure.string :as str]
            [clojure.walk :as walk])
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
  "Recursively walk the full config map and expand alias keywords at every level.
   Previously this only expanded inside :bindings vectors of layer nodes."
  [config]
  (if-let [aliases (not-empty (:aliases config))]
    (walk/postwalk
      (fn [x]
        (resolve-alias aliases x))
      config)
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

(defn make-empty-grid
  "Create a grid of the given dimensions filled with empty-cell."
  [row-widths empty-cell]
  (mapv (fn [w] (vec (repeat w empty-cell))) row-widths))

(defn assemble-placements
  "Resolve placements into a flat bindings grid.

   placements  - vector of {:tile <keyword> :pos [col row]}
   row-widths  - vector of row widths for the target grid
   tiles       - map of {<keyword> {:bindings <grid>}}
   opts        - optional map:
                :empty  - fill value for empty cells (default :trans)
                :clip?  - if true, silently skip out-of-bounds writes;
                          if false (default), throw"
  [placements row-widths tiles {:keys [empty clip?] :or {empty :trans}}]
  (let [num-rows (count row-widths)]
    ;; Validate all placements reference existing tiles
    (doseq [[idx {:keys [tile]}] (map-indexed vector placements)]
      (when-not (get tiles tile)
        (throw (ex-info (str "Unknown tile: " tile)
                        {:tile tile
                         :placement-idx idx
                         :available (keys tiles)}))))

    ;; Build the grid by pasting each placement in order
    (reduce
     (fn [current-grid {:keys [tile pos] :as placement}]
       (let [tile-bindings (get-in tiles [tile :bindings])
             [start-col start-row] pos]
         (when-not (and (vector? pos) (= 2 (count pos)))
           (throw (ex-info ":pos must be a vector of [col row]"
                           {:placement placement})))
         (reduce
          (fn [g [row-idx col-idx cell]]
            (let [target-row (+ start-row row-idx)
                  target-col (+ start-col col-idx)]
              (cond
                ;; Row out of bounds
                (or (< target-row 0) (>= target-row num-rows))
                (if clip?
                  g
                  (throw (ex-info "Tile placement out of bounds: row outside grid"
                                  {:tile tile
                                   :pos pos
                                   :target-row target-row
                                   :num-rows num-rows})))

                ;; Col out of bounds for this row
                (or (< target-col 0) (>= target-col (nth row-widths target-row)))
                (if clip?
                  g
                  (throw (ex-info "Tile placement out of bounds: col exceeds row width"
                                  {:tile tile
                                   :pos pos
                                   :target-col target-col
                                   :row-width (nth row-widths target-row)
                                   :target-row target-row})))

                ;; In bounds: place the cell (last placement wins)
                :else (assoc-in g [target-row target-col] cell))))
          current-grid
          (for [row-idx (range (count tile-bindings))
                col-idx (range (count (nth tile-bindings row-idx)))
                :let [cell (get-in tile-bindings [row-idx col-idx])]]
            [row-idx col-idx cell]))))
     (make-empty-grid row-widths empty)
     placements)))

(defn- resolve-placements-node
  "If node has :placements but no :bindings, assemble a flat :bindings grid.
   Otherwise return node unchanged."
  [tiles node]
  (if (and (:placements node) (not (:bindings node)))
    (do
      (when-not (:row-widths node)
        (throw (ex-info ":row-widths is required when using :placements"
                        {:node node})))
      (let [row-widths (:row-widths node)
            empty-cell (or (:empty node) :trans)
            clip? (boolean (:clip? node))
            assembled (assemble-placements (:placements node) row-widths tiles
                                             {:empty empty-cell :clip? clip?})]
        (-> node
            (dissoc :placements :empty :clip?)
            (assoc :bindings assembled))))
    node))

(defn resolve-placements
  "Walk config regions and resolve :placements → :bindings for any node
   that has :placements but not :bindings. This is a pure preprocessing
   step so render-layer and render-combo-layer require zero changes."
  [config]
  (let [tiles (:tiles config)]
    (update config :regions
            (fn [regions]
              (mapv (fn [[region spec]]
                      [region (update spec :nodes
                                      (fn [nodes]
                                        (mapv (partial resolve-placements-node tiles)
                                              nodes)))])
                    regions)))))

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
  (let [config (-> config
                   expand-aliases
                   resolve-placements)
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
