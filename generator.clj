#!/usr/bin/env bb

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

(defn replace-placeholder
  "Recursively walk a binding expression and replace :_placeholder with :MACRO_PLACEHOLDER."
  [cell]
  (cond
    (keyword? cell)
    (if (= cell :_placeholder) :MACRO_PLACEHOLDER cell)
    (vector? cell)
    (mapv replace-placeholder cell)
    :else cell))

(defn param-step?
  "Check if a cell is a param-forwarding wrapper."
  [cell]
  (and (vector? cell)
       (#{:param-1to1 :param-1to2 :param-2to1 :param-2to2} (first cell))))

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

(defn assemble-layer-bindings
  "Given a layer node with :left (half-grid) and optional :right-override,
   produce a complete :bindings grid by mirroring each left row horizontally
   and applying overrides. Validates against :keyboard geometry.
   
   :*      - sentinel meaning 'use mirrored value'
   nil row - means 'use full mirrored row'"
  [{:keys [left right-override]} {:keys [row-widths] :as keyboard}]
  (when (nil? keyboard)
    (throw (ex-info "Missing :keyboard config"
                    {:keyboard keyboard})))
  (when-not (seq row-widths)
    (throw (ex-info "Missing :keyboard :row-widths in config"
                    {:keyboard keyboard})))
  (doseq [[idx w] (map-indexed vector row-widths)]
    (when-not (integer? w)
      (throw (ex-info (str ":row-widths entry " idx " is not an integer: " w)
                      {:row-widths row-widths :index idx :value w})))
    (when (odd? w)
      (throw (ex-info (str ":row-widths entry " idx " is odd: " w)
                      {:row-widths row-widths :index idx :value w}))))
  (when-not (seq left)
    (throw (ex-info ":left is required and must contain at least one row"
                    {:left left})))
  (when-not (= (count left) (count row-widths))
    (throw (ex-info ":left row count does not match :keyboard :row-widths"
                    {:left-row-count (count left)
                     :row-widths-count (count row-widths)})))
  (doseq [[idx left-row row-width] (map vector (range) left row-widths)]
    (let [expected-half (quot row-width 2)]
      (when-not (= (count left-row) expected-half)
        (throw (ex-info (str ":left row " idx " length (" (count left-row) 
                             ") does not match half row-width (" expected-half ")")
                        {:row-idx idx
                         :left-row left-row
                         :row-width row-width
                         :expected expected-half
                         :actual (count left-row)})))))
  (when right-override
    (when-not (= (count right-override) (count left))
      (throw (ex-info ":right-override row count does not match :left"
                      {:right-override-count (count right-override)
                       :left-count (count left)})))
    (doseq [[idx override-row row-width] (map vector (range) right-override row-widths)]
      (when (some? override-row)
        (let [expected-half (quot row-width 2)]
          (when-not (= (count override-row) expected-half)
            (throw (ex-info (str ":right-override row " idx " length (" (count override-row) 
                                 ") does not match half row-width (" expected-half ")")
                            {:row-idx idx
                             :override-row override-row
                             :row-width row-width
                             :expected expected-half
                             :actual (count override-row)})))))))
  (mapv (fn [idx left-row row-width]
          (let [mirrored (vec (reverse left-row))
                override-row (when right-override (nth right-override idx))
                right-half (if (nil? override-row)
                             mirrored
                             (mapv (fn [override-cell mirrored-cell]
                                     (if (= override-cell :*)
                                       mirrored-cell
                                       override-cell))
                                   override-row
                                   mirrored))]
            (into (vec left-row) right-half)))
        (range (count left))
        left
        row-widths))

(defn resolve-left-bindings
  "Walk config regions.
   For each node in the :keymap region that has :left, assemble a full :bindings
   grid by mirroring and applying :right-override. Any existing :bindings is
   overwritten.
   For each :combo-layer node that does not declare :row-widths, inject
   :keyboard :row-widths."
  [config]
  (let [keyboard (:keyboard config)]
    (update config :regions
            (fn [regions]
              (mapv (fn [[region spec]]
                      [region (update spec :nodes
                                      (fn [nodes]
                                        (mapv (fn [node]
                                                (cond
                                                  ;; :keymap region + :left → assemble full bindings
                                                  (and (= region :keymap) (:left node))
                                                  (let [bindings (assemble-layer-bindings node keyboard)]
                                                    (-> node
                                                        (dissoc :left :right-override)
                                                        (assoc :bindings bindings)))

                                                  ;; combo-layer without :row-widths → inherit from keyboard if present
                                                  (and (= (:type node) :combo-layer) (not (:row-widths node)))
                                                  (if keyboard
                                                    (assoc node :row-widths (:row-widths keyboard))
                                                    node)

                                                  :else
                                                  node))
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
   [:press :A]    -> &macro_press &kp A
   [:release :B]  -> &macro_release &kp B
   [:tap :C]      -> &macro_tap &kp C
   [:wait 30]     -> &macro_wait_time 30
   [:tap-time 50] -> &macro_tap_time 50
   [:pause]       -> &macro_pause_for_release
   :trans/:none  -> &trans / &none (special case)"
  [cell]
  (cond
    (vector? cell)
    (let [op (first cell)]
      (case op
        (:press :release :tap)
        (str "&macro_" (name op) " " (binding->str (second cell)))
        :wait
        (str "&macro_wait_time " (second cell))
        :tap-time
        (str "&macro_tap_time " (second cell))
        :pause
        "&macro_pause_for_release"
        (:param-1to1 :param-1to2 :param-2to1 :param-2to2)
        (str "&macro_param_" (subs (name op) 6))
        (str "&" (token->str op)
             (when (seq (rest cell))
               (str " " (str/join " " (map token->str (rest cell))))))))

    (keyword? cell)
    (case cell
      :trans "&trans"
      :none "&none"
      (:param-1to1 :param-1to2 :param-2to1 :param-2to2)
      (str "&macro_param_" (subs (name cell) 6))
      (str "&kp " (name cell)))

    :else
    (str cell)))

(defn macro-binding-groups
  "Expand a macro body into a seq of <...> group contents.
   Most cells become one group. Param wrappers become two groups:
   the param control behavior, and the resolved binding with
   :_placeholder replaced by :MACRO_PLACEHOLDER."
  [body]
  (mapcat
    (fn [cell]
      (if (param-step? cell)
        (let [[param-op inner-binding] cell]
          [(str "&macro_param_" (subs (name param-op) 6))
           (binding->str (replace-placeholder inner-binding))])
        [(binding->str cell)]))
    body))

(defn render-macro
  "Render a declarative ZMK macro node.
   :name     — DT node id
   :label    — optional display name (defaults to :name)
   :type     — :macro, :macro-one-param, or :macro-two-param
   :body     — flat vector of binding expressions (compiled via binding->str)
   :wait-ms  — optional, emitted as wait-ms = <N>;
   :tap-ms   — optional, emitted as tap-ms = <N>;"
  [{:keys [name type body wait-ms tap-ms label] :as node} level]
  (let [compat-str (case type
                     :macro "zmk,behavior-macro"
                     :macro-one-param "zmk,behavior-macro-one-param"
                     :macro-two-param "zmk,behavior-macro-two-param"
                     (throw (ex-info (str "Unknown macro type: " type) {:node node})))
        binding-cells (case type
                        :macro 0
                        :macro-one-param 1
                        :macro-two-param 2)
        display-name (or label name)
        bindings-line (if (#{:macro-one-param :macro-two-param} type)
                        (let [groups (macro-binding-groups body)]
                          (str (indent (inc level)) "bindings =\n"
                               (str/join ",\n"
                                         (map #(str (indent (inc level)) "    <" % ">")
                                              groups))
                               ";"))
                        (str (indent (inc level)) "bindings = <" (str/join " " (map binding->str body)) ">;"))]
    (str/join
     "\n"
     (concat [(str (indent level) name ": " display-name " {")
              (str (indent (inc level)) "compatible = \"" compat-str "\";")
              (str (indent (inc level)) "#binding-cells = <" binding-cells ">;")
              bindings-line]
             (when wait-ms [(str (indent (inc level)) "wait-ms = <" wait-ms ">;")])
             (when tap-ms  [(str (indent (inc level)) "tap-ms = <" tap-ms ">;")])
             [(str (indent level) "};")]))))

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

(defn resolve-layer-nums
  "Resolve layer references (keywords or raw indexes) into numeric indexes.
   Keywords are resolved via layer-index-map and unknown names throw." 
  [layers layer-index-map]
  (when (seq layers)
    (map (fn [layer]
           (if (keyword? layer)
             (if-let [idx (get layer-index-map (clojure.core/name layer))]
               idx
               (throw (ex-info (str "Unknown layer name: " layer)
                               {:layer layer :available (keys layer-index-map)})))
             layer))
         layers)))

(defn render-combo-layer
  "Render a :combo-layer node into one or more ZMK combo DT nodes.
   :row-widths is required. :pattern defines relative offsets.
   :bindings uses the normal binding DSL. :layers can be keywords
   (resolved against the keymap) or raw numbers."
  [{:keys [name row-widths pattern bindings layers] :as node} level {:keys [layer-index-map]}]
  (when-not row-widths
    (throw (ex-info ":row-widths is required for :combo-layer" {:node node})))
  (let [layer-nums (resolve-layer-nums layers layer-index-map)
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
  [{:keys [type layers] :as node} level raw-body? {:keys [layer-index-map] :as opts}]
  (case type
    :combo-layer (render-combo-layer node level opts)
    (:macro :macro-one-param :macro-two-param) (render-macro node level)
    (if (:bindings node)
      (render-layer node level)
      (let [layer-nums (resolve-layer-nums layers layer-index-map)
            layer-line (when (seq layer-nums)
                         (str (indent (inc level)) "layers = <" (str/join " " layer-nums) ">;"))]
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
                 (when layer-line [layer-line])
                 [(str (indent level) "};")]))))))
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
                   resolve-left-bindings)
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
