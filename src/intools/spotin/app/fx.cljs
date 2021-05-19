(ns intools.spotin.app.fx
  (:require [intools.spotin.model.playlist :as playlist]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :refer [dispatch reg-fx]]))

(reg-fx :spotin/fetch-playback-status
  (fn [request-id]
    (-> (spotify/get-player+)
        (.then (fn [body]
                 (let [status (js->clj body :keywordize-keys true)]
                   (dispatch [:spotin/set-playback-status status request-id]))))
        (.catch (fn [e]
                  (dispatch [:spotin/clear-playback-request-id request-id])
                  (throw e))))))

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

(reg-fx :spotin/player-transfer
  (fn [device-id]
    (spotify/player-transfer+ device-id)))

(reg-fx :spotin/player-volume
  (fn [{:keys [volume-percent request-id]}]
    (-> (spotify/player-volume+ volume-percent)
        (.finally (fn []
                    ;; There does not seem to be Read-your-writes consistency,
                    ;; delay for a second before trying to fetch status update
                    (js/setTimeout #(dispatch [:spotin/update-playback-status request-id])
                                   1000))))))

(reg-fx :spotin/player-seek
  (fn [{:keys [progress request-id]}]
    (-> (spotify/player-seek+ progress)
        (.finally (fn []
                    ;; There does not seem to be Read-your-writes consistency,
                    ;; delay for a second before trying to fetch status update
                    (js/setTimeout #(dispatch [:spotin/update-playback-status request-id])
                                   1000))))))

(reg-fx :playlist-share
  (fn [arg] (js/console.log "Playlist URI:" (:uri arg))))

(reg-fx :playlists-mix
  (fn [arg]
    (-> (playlist/create-mixed-playlist+ arg)
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

(reg-fx :playlist-unfollow
  (fn [playlist-id]
    (-> (spotify/playlist-unfollow+ playlist-id)
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

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
        (.then (fn [{:keys [items]}]
                 (dispatch [:set-playlist-tracks playlist-id items]))))))

(reg-fx :spotin/load-album
  (fn [album-id]
    (-> (spotify/get-album+ album-id)
        (.then (fn [body]
                 (dispatch [:spotin/set-album album-id
                            (js->clj body :keywordize-keys true)]))))
    (-> (spotify/get-album-tracks+ album-id)
        (.then (fn [{:keys [items]}]
                 (dispatch [:spotin/set-album-tracks album-id items]))))))
