#!/usr/bin/env bb

(ns scripts.implement-issues
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.lang ProcessBuilder]
           [java.nio.charset StandardCharsets]))

(def issues-dir "issues")
(def logs-dir "logs/issue-runner")

(def issue-file-pattern #"^(\d+)-(.+)\.md$")

(defn numbered-issue [path]
  (let [file-name (fs/file-name path)]
    (when-let [[_ number title] (re-matches issue-file-pattern file-name)]
      {:path path
       :file-name file-name
       :number number
       :sort-number (parse-long number)
       :title title
       :stem (str/replace file-name #"\.md$" "")})))

(defn next-issue []
  (when (fs/exists? issues-dir)
    (->> (fs/list-dir issues-dir)
         (keep numbered-issue)
         (sort-by (juxt :sort-number :file-name))
         first)))

(defn commit-message [{:keys [number title]}]
  (str "Implement issue " number ": " (str/replace title #"-" " ")))

(defn print-and-log! [log-writer s]
  (print s)
  (flush)
  (.write log-writer s)
  (.flush log-writer))

(defn title! [log-writer title]
  (print-and-log! log-writer (str "\n\n=== " title " ===\n")))

(defn run-command! [log-writer title args]
  (title! log-writer (str title "\n$ " (pr-str args)))
  (let [process (-> (ProcessBuilder. ^java.util.List args)
                    (.redirectErrorStream true)
                    (.start))
        buffer (byte-array 8192)]
    (with-open [stream (.getInputStream process)]
      (loop []
        (let [n (.read stream buffer)]
          (when (pos? n)
            (let [chunk (String. buffer 0 n StandardCharsets/UTF_8)]
              (print-and-log! log-writer chunk))
            (recur)))))
    (let [exit (.waitFor process)]
      (print-and-log! log-writer (str "\n[exit " exit "]\n"))
      exit)))

(defn require-success! [issue log-writer title args]
  (let [exit (run-command! log-writer title args)]
    (when-not (zero? exit)
      (throw (ex-info (str title " failed")
                      {:issue (:file-name issue)
                       :exit exit
                       :command args})))))

(defn run-issue! [{:keys [path file-name stem] :as issue}]
  (fs/create-dirs logs-dir)
  (let [log-path (fs/path logs-dir (str stem ".log"))]
    (with-open [log-writer (io/writer (fs/file log-path) :append true)]
      (title! log-writer (str "Issue " file-name))
      (require-success! issue log-writer "Implement issue with pi"
                        ["pi" "-p" "--mode" "json" "/skill:implement" (str "@" path)])
      (require-success! issue log-writer "Run tests"
                        ["bb" "test"])
      (require-success! issue log-writer "Delete completed issue"
                        ["rm" (str path)])
      (require-success! issue log-writer "Stage changes"
                        ["git" "add" "-A"])
      (require-success! issue log-writer "Commit changes"
                        ["git" "commit" "-m" (commit-message issue)]))))

(defn -main [& _args]
  (loop []
    (if-let [issue (next-issue)]
      (do
        (run-issue! issue)
        (recur))
      (println "No numbered issue files remain."))))

(when (= *file* (System/getProperty "babashka.file"))
  (try
    (-main)
    (catch Exception e
      (binding [*out* *err*]
        (println "Issue runner stopped:" (ex-message e))
        (when-let [data (ex-data e)]
          (println data)))
      (System/exit 1))))
