(ns intools.spotin.app.fx
  (:require [re-frame.core :refer [reg-fx]]
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

