#!/usr/bin/env bb

(ns scripts.implement-issues
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io OutputStream]
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

(defn print-and-log! [log-stream s]
  (let [bytes (.getBytes s StandardCharsets/UTF_8)]
    (.write System/out bytes)
    (.flush System/out)
    (.write log-stream bytes)
    (.flush log-stream)))

(defn title! [log-stream title]
  (print-and-log! log-stream (str "\n\n=== " title " ===\n")))

(defn tee-output-stream [log-stream]
  (proxy [OutputStream] []
    (write
      ([b]
       (.write System/out b)
       (.write log-stream b)
       (.flush System/out)
       (.flush log-stream))
      ([bytes off len]
       (.write System/out bytes off len)
       (.write log-stream bytes off len)
       (.flush System/out)
       (.flush log-stream)))
    (flush []
      (.flush System/out)
      (.flush log-stream))))

(defn run-command! [log-stream title args]
  (title! log-stream (str title "\n$ " (pr-str args)))
  (let [{:keys [exit]} (shell {:continue true
                               :err :out
                               :out (tee-output-stream log-stream)
                               :cmd args})]
    (print-and-log! log-stream (str "\n[exit " exit "]\n"))
    exit))

(defn require-success! [issue log-stream title args]
  (let [exit (run-command! log-stream title args)]
    (when-not (zero? exit)
      (throw (ex-info (str title " failed")
                      {:issue (:file-name issue)
                       :exit exit
                       :command args})))))

(defn run-issue! [{:keys [path file-name stem] :as issue}]
  (fs/create-dirs logs-dir)
  (let [log-path (fs/path logs-dir (str stem ".log"))]
    (with-open [log-stream (io/output-stream (fs/file log-path) :append true)]
      (title! log-stream (str "Issue " file-name))
      (require-success! issue log-stream "Implement issue with pi"
                        ["pi" "-p" "/skill:implement" (str "@" path)])
      (require-success! issue log-stream "Run tests"
                        ["bb" "test"])
      (require-success! issue log-stream "Delete completed issue"
                        ["rm" (str path)])
      (require-success! issue log-stream "Stage changes"
                        ["git" "add" "-A"])
      (require-success! issue log-stream "Commit changes"
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
