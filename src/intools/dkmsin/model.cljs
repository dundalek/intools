(ns intools.dkmsin.model
  (:require [intools.shell :refer [sh]]
            [clojure.string :as str]))

(defrecord Module [module module-version kernel-version arch status])

(defn parse-module [line]
  (->> (re-matches #"([^,]+), ([^,]+), ([^,]+), ([^:]+): (.*)" line)
       (rest)
       (apply ->Module)))

(defn list-items []
  (->> (sh "dkms" "status")
       :out
       (str/split-lines)
       (map parse-module)))
