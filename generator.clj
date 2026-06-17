(require '[babashka.deps :as deps])

(deps/add-deps '{:deps {aero/aero {:mvn/version "1.1.6"}}})

(ns generator
  (:require [aero.core :as aero]
            [babashka.cli :as cli]
            [clojure.string :as str]))

(def default-section-order
  [:combos :macros :behaviors])

(def default-keymap-markers
  {:begin "// BEGIN KEYMAP"
   :end "// END KEYMAP"})

(defn indent
  [level]
  (apply str (repeat level "    ")))

(defn render-line
  [level line]
  (if (str/blank? line)
    ""
    (str (indent level) line)))

(defn find-line-index
  [lines pred]
  (first
   (keep-indexed
    (fn [idx line]
      (when (pred line)
        idx))
    lines)))

(defn section-range
  [lines section-key]
  (let [section-name (name section-key)
        header (str section-name " {")
        start (find-line-index lines #(str/includes? % header))]
    (when-not (some? start)
      (throw (ex-info "Could not find section in template"
                      {:section section-key})))
    (loop [idx start
           balance 0]
      (let [line (nth lines idx)
            opens (count (re-seq #"\{" line))
            closes (count (re-seq #"\}" line))
            next-balance (+ balance opens (- closes))]
        (if (and (> idx start) (zero? next-balance))
          {:start start
           :end idx}
          (recur (inc idx) next-balance))))))

(defn marker-range
  [lines {:keys [begin end]}]
  (let [start (find-line-index lines #(str/includes? % begin))
        finish (find-line-index lines #(str/includes? % end))]
    (when-not (and (some? start) (some? finish))
      (throw (ex-info "Could not find keymap markers in template"
                      {:begin begin
                       :end end})))
    {:start start
     :end finish}))

(defn render-node
  [{:keys [name label body raw-body?]} level]
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
           [(str (indent level) "};")])))

(defn render-section
  [section-key {:keys [preamble nodes]}]
  (str/join
   "\n"
   (concat [(str (indent 1) (name section-key) " {")]
           (map #(render-line 2 %) preamble)
           (when (seq preamble) [""])
           (interpose "" (map #(render-node % 2) nodes))
           [(str (indent 1) "};")])))

(defn replace-lines
  [lines start end replacement]
  (vec
   (concat (subvec lines 0 start)
           (str/split-lines replacement)
           (subvec lines (inc end)))))

(defn replace-section
  [text section-key section-data]
  (let [lines (vec (str/split-lines text))
        {:keys [start end]} (section-range lines section-key)]
    (str/join
     "\n"
     (replace-lines lines start end (render-section section-key section-data)))))

(defn replace-keymap-block
  [text {:keys [begin end]} nodes]
  (let [lines (vec (str/split-lines text))
        {:keys [start end]} (marker-range lines {:begin begin :end end})
        rendered (str/join "\n" (interpose "" (map #(render-node % 2) nodes)))]
    (str/join
     "\n"
     (vec
      (concat (subvec lines 0 (inc start))
              (if (seq nodes)
                (concat (str/split-lines rendered) [(nth lines end)])
                [(nth lines end)])
              (subvec lines (inc end)))))))

(defn ensure-trailing-newline
  [text]
  (if (str/ends-with? text "\n")
    text
    (str text "\n")))

(defn normalize-config
  [config]
  (update-in config [:keymap :nodes]
             (fn [nodes]
               (mapv #(assoc % :raw-body? true) nodes))))

(defn generate-keymap
  [template config]
  (let [config (normalize-config config)
        markers (merge default-keymap-markers
                       (select-keys (:keymap config) [:begin :end]))]
    (ensure-trailing-newline
     (->> default-section-order
          (reduce (fn [text section-key]
                    (if-let [section-data (get-in config [:sections section-key])]
                      (replace-section text section-key section-data)
                      text))
                  template)
          (#(replace-keymap-block % markers (get-in config [:keymap :nodes] [])))))))

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
    (write-output! (cli/parse-opts args {:spec cli-spec}))))

(comment
  (generate-keymap (slurp "examples/1_in.keymap")
                   (load-config "examples/1.edn"))
  :rcf)

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))