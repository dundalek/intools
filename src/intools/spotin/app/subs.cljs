(ns intools.spotin.app.subs
  (:require [re-frame.core :refer [reg-sub]]
            [clojure.string :as str]))

(reg-sub :db
  (fn [db _]
    db))

(reg-sub :spotin/current-route
  (fn [db _]
    (-> db :routes peek)))

(reg-sub :spotin/album-by-id
  (fn [db [_ album-id]]
    (-> db :albums (get album-id))))

(reg-sub :spotin/playlists
  (fn [db]
    (:playlists db)))

(reg-sub :spotin/playlist-order
  (fn [db]
    (:playlist-order db)))

(reg-sub :spotin/playlist-search-query
  (fn [db]
    (:playlist-search-query db)))

(reg-sub :spotin/playlists-filtered
  :<- [:spotin/playlists]
  :<- [:spotin/playlist-order]
  :<- [:spotin/playlist-search-query]
  (fn [[playlists playlist-order playlist-search-query]]
    (let [query (some-> playlist-search-query str/trim str/lower-case)
          playlists-coll (map #(get playlists %) playlist-order)]
      (cond->> playlists-coll
        (not (str/blank? query))
        (filter (fn [{:keys [name]}]
                  (str/includes? (str/lower-case name) playlist-search-query)))))))
