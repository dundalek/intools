(ns intools.spotin.app.events
  (:require [re-frame.core :refer [reg-event-db reg-event-fx]]
            [intools.spotin.app.db :as db]))

(reg-event-fx :spotin/init
  (fn [_ _]
    {:db db/default-db
     :fx [[:spotin/load-cached-playlists nil]
          [:spotin/refresh-playlists nil]]}))

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
        (and (not selected-playlist) first-playlist-id)
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

(reg-event-fx :set-selected-playlist
  (fn [{db :db} [_ playlist-id]]
    {:db (assoc db :selected-playlist playlist-id)
     :spotin/load-playlist-tracks playlist-id}))

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

(reg-event-fx :run-action
  (fn [_ [_ {:keys [id arg]}]]
    {id arg}))
