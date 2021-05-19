(ns intools.spotin.app.events
  (:require [intools.spotin.app.db :as db]
            [re-frame.core :refer [reg-event-db reg-event-fx]]))

(defn router-navigate [db route]
  (update db :routes conj route))

(defn router-back [db]
  (cond-> db
    (seq (:routes db)) (update :routes pop)))

(reg-event-fx :spotin/init
  (fn [_ _]
    {:db db/default-db
     :fx [[:spotin/load-cached-playlists nil]
          [:spotin/refresh-playlists nil]]}))

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
    (router-navigate db route)))

(reg-event-db :spotin/router-back
  (fn [db _]
    (router-back db)))

(defn- expected-request? [{:keys [playback-request-id] :as _db} request-id]
  (or (not playback-request-id)
      (= playback-request-id request-id)))

(reg-event-fx :spotin/refresh-playback-status
  (fn [{db :db}]
    (when (expected-request? db nil)
      {:spotin/fetch-playback-status nil})))

(reg-event-fx :spotin/update-playback-status
  (fn [{db :db} [_ request-id]]
    (when (expected-request? db request-id)
      {:spotin/fetch-playback-status request-id})))

(reg-event-db :spotin/clear-playback-request-id
  (fn [db [_ request-id]]
    (when (expected-request? db request-id)
      (assoc db :playback-request-id nil))))

(reg-event-db :spotin/set-playback-status
  (fn [db [_ status request-id]]
    (when (expected-request? db request-id)
      (assoc db
             :playback-request-id nil
             :playback-status status))))

(reg-event-fx :spotin/refresh-playlists
  (fn [_ _]
    {:spotin/refresh-playlists nil}))

(defn set-playlists [db playlists]
  (let [{:keys [selected-playlist]} db
        first-playlist-id (-> playlists first :id)
        new-db (assoc db
                      :playlist-order (map :id playlists)
                      :playlists (->> playlists
                                      (reduce (fn [m {:keys [id] :as item}]
                                                (assoc m id item))
                                              {})))]
    (cond-> {:db new-db}
      #_#_(and (not selected-playlist) first-playlist-id)
        (assoc :dispatch [:set-selected-playlist first-playlist-id]))))

(reg-event-fx :set-playlists
  (fn [{db :db} [_ playlists]]
    (set-playlists db playlists)))

(reg-event-fx :set-cached-playlists
  (fn [{db :db} [_ playlists]]
    (when (empty? (:playlists db))
      (set-playlists db playlists))))

(reg-event-db :set-playlist-tracks
  (fn [db [_ playlist-id tracks]]
    (assoc-in db [:playlist-tracks playlist-id] tracks)))

(defn select-playlist-fx [{db :db} playlist-id]
  {:db (router-navigate db {:name :playlist
                            :params {:playlist-id playlist-id}})
   :spotin/load-playlist-tracks playlist-id})

(reg-event-fx :set-selected-playlist
  (fn [cofx [_ playlist-id]]
    (select-playlist-fx cofx playlist-id)))

(reg-event-fx :spotin/open-album
  (fn [{db :db} [_ album-id]]
    {:db (router-navigate db {:name :album
                              :params {:album-id album-id}})
     :spotin/load-album album-id}))

(reg-event-db :spotin/set-album
  (fn [db [_ album-id album]]
    (assoc-in db [:albums album-id] album)))

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
  (fn [{db :db :as cofx} _]
    (let [playlist-id (rand-nth (:playlist-order db))]
      (select-playlist-fx cofx playlist-id))))

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

(def volume-path [:playback-status :device :volume_percent])

(defn volume-up [value]
  (Math/min 100 (+ value 10)))

(defn volume-down [value]
  (Math/max 0 (- value 10)))

(defonce !request-counter (atom 0))
(defn update-volume-fx [db update-fn]
  ;; TODO it would be cleaner to use co-effect for counter
  (let [request-id (swap! !request-counter inc)
        old-volume (get-in db volume-path)
        new-volume (update-fn old-volume)]
    {:db (-> db
             (assoc-in volume-path new-volume)
             (assoc :playback-request-id request-id))
     :spotin/player-volume {:volume-percent new-volume
                            :request-id request-id}}))

(reg-event-fx :spotin/player-volume-up
  (fn [{db :db}]
    (update-volume-fx db volume-up)))

(reg-event-fx :spotin/player-volume-down
  (fn [{db :db}]
    (update-volume-fx db volume-down)))

(def progress-path [:playback-status :progress_ms])

(defn seek-fx [db update-fn]
  ;; TODO it would be cleaner to use co-effect for counter
  (let [request-id (swap! !request-counter inc)
        old-progress (get-in db progress-path)
        new-progress (update-fn old-progress)]
    {:db (-> db
             (assoc-in progress-path new-progress)
             (assoc :playback-request-id request-id))
     :spotin/player-seek {:progress new-progress
                          :request-id request-id}}))

(reg-event-fx :spotin/player-seek-forward
  (fn [{db :db}]
    (seek-fx db #(+ % 10000))))

(reg-event-fx :spotin/player-seek-backward
  (fn [{db :db}]
    (seek-fx db #(-> % (- 10000) (Math/max 0)))))
