(ns intools.flatpakin.model
  (:require [intools.shell :refer [sh]]
            [clojure.string :as str]))

(def installation-columns ["name" "description" "application" "version" "branch" "arch" "runtime" "origin" "installation" "ref" "active" "latest" "size" "options"])

(def sandbox-columns ["instance" "pid" "child-pid" "application" "arch" "branch" "commit" "runtime" "runtime-branch" "runtime-commit" "active" "background"])

(def history-columns ["time" "change" "ref" "application" "arch" "branch" "installation" "remote" "commit" "old-commit" "url" "user" "tool" "version"])

(def search-columns ["name" "description" "application" "version" "branch" "remotes"])

(def remote-columns ["name" "title" "url" "collection" "filter" "priority" "options" "comment" "description" "homepage" "icon"])

(defn columns-arg [columns]
  (str "--columns=" (str/join "," columns)))

(defn split-columns [s]
  (str/split s #"\t"))

(defn parse-items [columns proc]
  (->> proc
       :out
       (str/split-lines)
       (map #(->> (split-columns %)
                  (zipmap (map keyword columns))))))

(defn list-apps []
  (->> (sh "flatpak" "list" "--app" (columns-arg installation-columns))
       (parse-items installation-columns)))

(defn list-runtimes []
  (->> (sh "flatpak" "list" "--runtime" (columns-arg installation-columns))
       (parse-items installation-columns)))

(defn list-sandboxes []
  (->> (sh "flatpak" "ps" (columns-arg sandbox-columns))
       (parse-items sandbox-columns)))

(defn list-history []
  (->> (sh "flatpak" "history" (columns-arg history-columns))
       (parse-items history-columns)))

(defn list-remotes []
  (->> (sh "flatpak" "remotes" (columns-arg remote-columns))
       (parse-items remote-columns)))

(defn list-config []
  (->> (sh "flatpak" "config")
       :out
       (str/split-lines)
       (map #(str/split % #":" 2))
       (into (sorted-map))))

(defn search [query]
  (->> (sh "flatpak" "search" (columns-arg search-columns) query)
       (parse-items search-columns)))
