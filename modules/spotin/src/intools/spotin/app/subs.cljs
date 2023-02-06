(ns intools.spotin.app.subs
  (:require
   [intools.search :as search]
   [intools.spotin.app.core :as app]
   [intools.spotin.app.query :as query]
   [intools.spotin.lib.query :refer [reg-query-sub]]
   [re-frame.core :refer [reg-sub]]))

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

(reg-sub :spotin/playlist-search-query
  (fn [db]
    (:playlist-search-query db)))

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

(reg-sub :spotin/devices-menu
  (fn [db]
    (:devices-menu db)))

(reg-sub :spotin/active-input-panel
  (fn [db]
    (:active-input-panel db)))

(reg-query-sub :spotin/player query/player)

(reg-query-sub :spotin/playlists query/playlists)

(reg-query-sub :spotin/playlist query/playlist)

(reg-query-sub :spotin/playlist-tracks query/playlist-tracks)

(reg-query-sub :spotin/artist query/artist)

(reg-query-sub :spotin/artist-albums query/artist-albums)

(reg-query-sub :spotin/artist-top-tracks query/artist-top-tracks)

(reg-query-sub :spotin/artist-related-artists query/artist-related-artists)

(reg-query-sub :spotin/devices query/devices)

(reg-query-sub :spotin/album query/album)

(reg-query-sub :spotin/album-tracks query/album-tracks)
