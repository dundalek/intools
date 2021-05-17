(ns intools.search
  (:require [clojure.string :as str]))

(defn filter-by [query f coll]
  (let [query (some-> query str/trim str/lower-case)]
    (cond->> coll
      (not (str/blank? query))
      (filter (fn [item]
                (str/includes? (str/lower-case (f item)) query))))))
