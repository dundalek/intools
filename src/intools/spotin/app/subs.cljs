(ns intools.spotin.app.subs
  (:require [intools.search :as search]
            [re-frame.core :refer [reg-sub]]))

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
    (search/filter-by playlist-search-query :name (map #(get playlists %) playlist-order))))

(reg-sub :spotin/actions
  (fn [db]
    (:actions db)))

(reg-sub :spotin/actions-search-query
  (fn [db]
    (:actions-search-query db)))

(reg-sub :spotin/actions-filtered
  :<- [:spotin/actions]
  :<- [:spotin/actions-search-query]
  (fn [[actions query]]
    (search/filter-by query :name actions)))
