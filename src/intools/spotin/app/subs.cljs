(ns intools.spotin.app.subs
  (:require [clojure.string :as str]
            [intools.search :as search]
            [intools.spotin.app.core :as app]
            [re-frame.core :refer [reg-sub]]))

(reg-sub :db
  (fn [db _]
    db))

(reg-sub :spotin/error
  (fn [db]
    (:error db)))

(reg-sub :spotin/current-route
  (fn [db _]
    (app/current-route db)))

(reg-sub :spotin/pending-requests
  (fn [db]
    (-> db :pending-requests pos?)))

(reg-sub :spotin/confirmation-modal
  (fn [db]
    (:confirmation-modal db)))

(reg-sub :spotin/album-by-id
  (fn [db [_ album-id]]
    (-> db :albums (get album-id))))

(reg-sub :spotin/artist-by-id
  (fn [db [_ artist-id]]
    (-> db :artists (get artist-id))))

(reg-sub :spotin/playback-status
  (fn [db]
    (:playback-status db)))

(reg-sub :spotin/playback-item-uri
  :<- [:spotin/playback-status]
  (fn [status]
    (-> status :item :uri)))

(reg-sub :spotin/playback-context-uri
  :<- [:spotin/playback-status]
  (fn [status]
    (when (:is_playing status)
      (-> status :context :uri))))

(reg-sub :spotin/playlists
  (fn [db]
    (:playlists db)))

(reg-sub :spotin/playlist-tracks
  (fn [db]
    (:playlist-tracks db)))

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

(reg-sub :spotin/track-search-query
  (fn [db]
    (:track-search-query db)))

(reg-sub :spotin/current-playlist-id
  :<- [:spotin/current-route]
  (fn [route]
    (-> route :params :playlist-id)))

(reg-sub :spotin/current-album-id
  :<- [:spotin/current-route]
  (fn [route]
    (-> route :params :album-id)))

(reg-sub :spotin/album-tracks
  (fn [db]
    (:album-tracks db)))

(reg-sub :spotin/current-playlist-tracks
  :<- [:spotin/playlist-tracks]
  :<- [:spotin/current-playlist-id]
  (fn [[playlist-tracks playlist-id]]
    (->> (get playlist-tracks playlist-id)
         (map :track))))

(reg-sub :spotin/current-album-tracks
  :<- [:spotin/album-tracks]
  :<- [:spotin/current-album-id]
  (fn [[album-tracks album-id]]
    (get album-tracks album-id)))

(defn search-tracks [tracks query]
  (->> tracks
       (search/filter-by query (fn [{:keys [name album artists]}]
                                 (->> (concat [name (:name album)]
                                              (map :name artists))
                                      (str/join " "))))))

(reg-sub :spotin/current-filtered-playlist-tracks
  :<- [:spotin/current-playlist-tracks]
  :<- [:spotin/track-search-query]
  (fn [[tracks query]]
    (search-tracks tracks query)))

(reg-sub :spotin/current-filtered-album-tracks
  :<- [:spotin/current-album-tracks]
  :<- [:spotin/track-search-query]
  (fn [[tracks query]]
    (search-tracks tracks query)))

(reg-sub :spotin/devices-menu
  (fn [db]
    (:devices-menu db)))
