(ns intools.spotin.app.events
  (:require
   [intools.spotin.actions :as actions]
   [intools.spotin.app.core :as app]
   [intools.spotin.app.db :as db]
   [intools.spotin.model.spotify :as spotify]
   [re-frame.core :refer [dispatch inject-cofx reg-event-db reg-event-fx]]))

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

(reg-event-fx :spotin/activate-album
  (fn [{db :db} [_ {:keys [item]}]]
    (if (= 1 (:total_tracks item))
      {:album-play {:item item}}
      (open-album-fx db (:id item)))))

(defn open-artist-fx [db artist-id]
  {:db (app/router-navigate db {:name :artist
                                :params {:artist-id artist-id}})})

(reg-event-fx :spotin/open-track-artist
  (fn [{db :db} [_ {:keys [item]}]]
    ;; TODO multiple artists, perhaps show a menu
    (open-artist-fx db (-> item :artists first :id))))

(reg-event-fx :spotin/queue-track
  (fn [_ [_ {:keys [item]}]]
    {:spotin/queue-track (-> item :uri)}))

(reg-event-fx :spotin/open-artist
  (fn [{db :db} [_ {:keys [item]}]]
    (open-artist-fx db (:id item))))

(reg-event-fx :spotin/player-previous
  [(inject-cofx :query-data "player")]
  (fn [{:keys [query-data]}]
    ;; When pressing back we seek to beginning of the current track.
    ;; Only after pressing back again while close to the beginning of the track
    ;; will go to previous track. Spotify seems to consider ~2s as close to
    ;; the beggining, let's use 8s as a safety margin due to possible delays.
    ;; TODO: do optimistic seek, should also help with lowering the safety margin
    (if (< (:progress_ms query-data) 8000)
      {:previous nil}
      {:spotin/player-seek 0})))

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
    (app/open-action-menu db menu)))

(reg-event-db :close-action-menu
  (fn [db _]
    (app/close-action-menu db)))

(reg-event-db :spotin/track-panel-menu-opened
  (fn [db [_ arg]]
    (let [tracks-actions (map #(assoc % :arg arg)
                              actions/tracks-actions)
          actions (concat tracks-actions
                          [actions/action-separator]
                          actions/player-actions)]
      (app/open-action-menu db actions))))

(reg-event-db :close-input-panel
  (fn [db _]
    (app/close-input-panel db)))

(reg-event-db :playlist-rename
  (fn [db [_ arg]]
    (app/open-input-panel db {:type :playlist-rename
                              :arg arg})))

(reg-event-db :playlist-edit-description
  (fn [db [_ arg]]
    (app/open-input-panel db {:type :playlist-edit-description
                              :arg arg})))

(reg-event-fx :spotin/playlist-change-submitted
  (fn [{db :db} [_ params]]
    {:db (app/close-input-panel db)
     :spotin/update-playlist-attribute params}))

(reg-event-db :spotin/playlist-unfollow-selected
  (fn [db [_ arg]]
    (app/open-confirmation-modal
     db
     {:title "Delete playlist"
      :description (str "Are you sure you want to delete playlist '" (:name arg) "'?")
      :on-submit #(dispatch [:spotin/playlist-unfollow-confirmed (:id arg)])})))

(reg-event-fx :spotin/playlist-unfollow-confirmed
  (fn [_ [_ playlist-id]]
    {:playlist-unfollow playlist-id}))

;; Just for prototyping, introduce dedicated events later
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

(reg-event-db :spotin/close-confirmation-modal
  (fn [db _]
    (app/close-confirmation-modal db)))

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

(reg-event-db :spotin/open-dev-stories
  (fn [db _]
    (app/router-navigate db {:name :stories})))
