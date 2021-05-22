(ns intools.resticin.model
  (:require [clojure.string :as str]
            [intools.shell :refer [sh]]))

(defn list-snapshots []
  (->> (-> (sh "restic" "snapshots" "--json")
           :out
           (js/JSON.parse)
           (js->clj :keywordize-keys true))
       (map (fn [{:keys [short_id] :as item}]
              (-> item
                  (dissoc :short_id)
                  (assoc :short-id short_id)
                  (update :time #(js/Date. %)))))))

(defn list-keys []
  (->> (-> (sh "restic" "key" "list" "--json")
           :out
           (js/JSON.parse)
           (js->clj :keywordize-keys true))
       (map (fn [{:keys [userName hostName] :as item}]
              (-> item
                  (dissoc :userName :hostName)
                  (assoc :user-name userName
                         :host-name hostName)
                  (update :created #(js/Date. %)))))))

(defn snapshots->tags [snapshots]
  (->> snapshots
       (mapcat :tags)
       (distinct)))

(defn list-tags []
  (->> (list-snapshots)
       (snapshots->tags)))

(defn list-files
  ([snapshot-id] (list-files snapshot-id "/"))
  ([snapshot-id path]
   (->> (sh "restic" "ls" "--json" snapshot-id path)
        :out
        (str/split-lines)
        (map (fn [line]
               (let [item (-> line
                              (js/JSON.parse)
                              (js->clj :keywordize-keys true))]
                 (-> item
                     (dissoc :struct_type :short_id)
                     (assoc :struct-type (:struct_type item))
                     (assoc :short-id (:short_id item))
                     (update :time #(js/Date. %))))))
        ;; Dropping the first item which is the snapshot
        (rest))))
