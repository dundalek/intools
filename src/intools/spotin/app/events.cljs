(ns intools.spotin.app.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]
            [intools.spotin.app.db :as db]))

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

(reg-event-db :spotin/router-navigate
  (fn [db [_ route]]
    (router-navigate db route)))

(reg-event-db :spotin/router-back
  (fn [db _]
    (router-back db)))

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
    (assoc db :actions nil)))

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
