(ns intools.spotin.app.fx
  (:require [re-frame.core :refer [dispatch reg-fx]]
            [intools.spotin.model.spotify :as spotify]
            [intools.spotin.model.playlist :as playlist]))

(reg-fx :play-pause
  (fn [_] (spotify/player-play-pause+)))

(reg-fx :next
  (fn [_] (spotify/player-next+)))

(reg-fx :previous
  (fn [_] (spotify/player-previous+)))

(reg-fx :shuffle
  (fn [_] (spotify/player-toggle-shuffle+)))

(reg-fx :repeat
  (fn [_] (spotify/player-toggle-repeat+)))

(reg-fx :playlist-play
  (fn [arg] (spotify/player-play+ {:context_uri (:uri arg)})))

(reg-fx :playlist-share
  (fn [arg] (js/console.log "Playlist URI:" (:uri arg))))

(reg-fx :playlists-mix
  (fn [arg] (playlist/create-mixed-playlist+ arg)))

(reg-fx :spotin/load-cached-playlists
  (fn [_]
    (-> (spotify/load-cached-edn+ "get-all-playlists+")
        (.then (fn [{:keys [items]}]
                 (dispatch [:set-cached-playlists items]))
               (fn [_ignore])))))

(reg-fx :spotin/refresh-playlists
    (fn [_]
      (-> (spotify/cache-edn+ "get-all-playlists+" spotify/get-all-playlists+)
          (.then (fn [{:keys [items]}]
                   (dispatch [:set-playlists items]))))))

(reg-fx :spotin/load-playlist-tracks
  (fn [playlist-id]
    (-> (spotify/get-playlist-tracks+ playlist-id)
        (.then (fn [body]
                  (dispatch [:set-playlist-tracks playlist-id (-> body (js->clj :keywordize-keys true) :items)]))))))
