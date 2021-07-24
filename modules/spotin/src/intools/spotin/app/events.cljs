(ns intools.spotin.app.events
  (:require [intools.spotin.app.core :as app]
            [intools.spotin.app.db :as db]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :refer [inject-cofx reg-event-db reg-event-fx]]))

(reg-event-fx :spotin/init
  (fn [_ _]
    {:db db/default-db}))

(reg-event-db :spotin/request-started
  (fn [db]
    (update db :pending-requests inc)))

(reg-event-db :spotin/request-finished
  (fn [db]
    (update db :pending-requests dec)))

(reg-event-db :spotin/request-failed
  (fn [db [_ error request]]
    (assoc db :error {:error error
                      :request request})))

(reg-event-db :spotin/clear-error
  (fn [db]
    (assoc db :error nil)))

(reg-event-db :spotin/router-navigate
  (fn [db [_ route]]
    (app/router-navigate db route)))

(reg-event-db :spotin/router-back
  (fn [db _]
    (app/router-back db)))

(reg-event-fx :spotin/refresh-playlists
  (fn [_ _]
    {:spotin/invalidate-query "playlists"}))

(defn select-playlist-fx [db playlist-id]
  {:db (app/router-navigate db {:name :playlist
                                :params {:playlist-id playlist-id}})})

(reg-event-fx :select-playlist
  (fn [{db :db} [_ {:keys [id]}]]
    (select-playlist-fx db id)))

(defn open-album-fx [db album-id]
  {:db (app/router-navigate db {:name :album
                                :params {:album-id album-id}})})

(reg-event-fx :spotin/open-track-album
  (fn [{db :db} [_ {:keys [item]}]]
    (open-album-fx db (-> item :album :id))))

(reg-event-fx :spotin/open-album
  (fn [{db :db} [_ {:keys [item]}]]
    (open-album-fx db (:id item))))

(defn open-artist-fx [db artist-id]
  {:db (app/router-navigate db {:name :artist
                                :params {:artist-id artist-id}})})

(reg-event-fx :spotin/open-track-artist
  (fn [{db :db} [_ {:keys [item]}]]
    ;; TODO multiple artists, perhaps show a menu
    (open-artist-fx db (-> item :artists first :id))))

(reg-event-fx :spotin/open-artist
  (fn [{db :db} [_ {:keys [item]}]]
    (open-artist-fx db (:id item))))

(reg-event-fx :spotin/open-currently-playing
  [(inject-cofx :query-data "player")]
  (fn [{:keys [db query-data]}]
    ;; TODO show error alert for no context or unexpected context type
    (if-some [{:keys [type uri]} (-> query-data :context)]
      (let [id (spotify/uri->id uri)]
        (case type
          "playlist" (select-playlist-fx db id)
          "album" (open-album-fx db id)
          "artist" (open-artist-fx db id)))
      ;; no context specified, try show song's album
      (when-some [album-id (-> query-data :item :album :id)]
        (open-album-fx db album-id)))))

(reg-event-db :open-action-menu
  (fn [db [_ menu]]
    (assoc db :actions menu)))

(reg-event-db :close-action-menu
  (fn [db _]
    (assoc db
           :actions nil
           :actions-search-query nil)))

(reg-event-db :open-input-panel
  (fn [db [_ data]]
    (assoc db :active-input-panel data)))

(reg-event-db :close-input-panel
  (fn [db _]
    (assoc db :active-input-panel nil)))

(reg-event-db :playlist-rename
  (fn [db [_ arg]]
    (assoc db :active-input-panel {:type :playlist-rename
                                   :arg arg})))

(reg-event-db :playlist-edit-description
  (fn [db [_ arg]]
    (assoc db :active-input-panel {:type :playlist-edit-description
                                   :arg arg})))

(reg-event-fx :playlist-unfollow
  (fn [_ [_ playlist-id]]
    {:playlist-unfollow playlist-id}))

(reg-event-fx :run-action
  (fn [_ [_ {:keys [id arg]}]]
    {id arg}))

(reg-event-fx :spotin/dispatch-fx
  (fn [_ [_ id arg]]
    {id arg}))

(reg-event-fx :spotin/open-random-playlist
  [(inject-cofx :query-data "playlists")]
  (fn [{:keys [db query-data]} _]
    (let [playlist-id (-> query-data :items rand-nth :id)]
      (select-playlist-fx db playlist-id))))

(defn set-playlist-search [db query]
  (assoc db :playlist-search-query query))

(reg-event-db :spotin/set-playlist-search
  (fn [db [_ query]]
    (set-playlist-search db query)))

(reg-event-db :spotin/start-playlist-search
  (fn [db _]
    (set-playlist-search db "")))

(reg-event-db :spotin/clear-playlist-search
  (fn [db _]
    (set-playlist-search db nil)))

(reg-event-db :spotin/set-actions-search
  (fn [db [_ query]]
    (assoc db :actions-search-query query)))

(reg-event-db :spotin/set-track-search
  (fn [db [_ query]]
    (assoc db :track-search-query query)))

(reg-event-db :spotin/open-confirmation-modal
  (fn [db [_ opts]]
    (assoc db :confirmation-modal opts)))

(reg-event-db :spotin/close-confirmation-modal
  (fn [db]
    (assoc db :confirmation-modal nil)))

(reg-event-db :spotin/open-devices-menu
  (fn [db]
    (assoc db :devices-menu true)))

(reg-event-db :spotin/close-devices-menu
  (fn [db]
    (assoc db :devices-menu false)))

(reg-event-fx :spotin/player-transfer
  (fn [{db :db} [_ device-id]]
    {:db (assoc db :devices-menu false)
     :spotin/player-transfer device-id}))
