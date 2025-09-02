#!/usr/bin/env bb
;; File browser portlet server
;; -*- clojure -*-
;; vim: set filetype=clojure:
#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns server
  (:require
   [babashka.deps :as deps]
   [babashka.fs :as fs]
   [clojure.java.io :as io]))

(deps/add-deps '{:deps {djblue/portal {:mvn/version "0.59.1"}}})

(require '[portal.api :as p]
         '[portal.viewer :as pv]
         '[portal.runtime :as prt])

(defn list-directory [path]
  (let [path-obj (fs/path path)
        files (try
                (fs/list-dir path-obj)
                (catch Exception _
                  []))]
    (->> files
         (map (fn [file]
                {:name (str (fs/file-name file))
                 :path (str file)
                 :is-directory (fs/directory? file)
                 :size (when (fs/regular-file? file)
                         (fs/size file))
                 :last-modified (fs/last-modified-time file)}))
         (sort-by (juxt #(not (:is-directory %)) :name)))))

(def initial-state
  {:current-path (str (fs/cwd))
   :files []
   :selected-file nil})

(defonce !app-state
  (atom (-> initial-state
            (assoc :files (list-directory (:current-path initial-state)))
            (pv/default :file-browser/file-browser-viewer))))

(defn navigate-to [path]
  (let [resolved-path (str (fs/canonicalize path))
        files (list-directory resolved-path)]
    (swap! !app-state assoc
           :current-path resolved-path
           :files files
           :selected-file nil)))

(defn navigate-up []
  (let [current-path (:current-path @!app-state)
        parent-path (str (fs/parent current-path))]
    (when parent-path
      (navigate-to parent-path))))

(defn dispatch [message]
  (case (:type message)
    :navigate-to (navigate-to (:path message))
    :navigate-up (navigate-up)
    :select-file (swap! !app-state assoc :selected-file (:file message))
    nil))

(defn -main [& _args]
  (prt/register! #'dispatch)
  (p/open {:value !app-state
           :on-load #(p/eval-str (slurp (str (fs/path (fs/parent *file*) "src/file_browser.cljs"))))})
  @(promise))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
