(ns intools.spotin.app.mutations
  (:require
   [intools.spotin.infrastructure.spotify-client :as spotify-client]
   [intools.spotin.model.spotify :as spotify]))

(def volume-path [:device :volume_percent])

(defn update-volume-up [value]
  (Math/min 100 (+ value 10)))

(defn update-volume-down [value]
  (Math/max 0 (- value 10)))

(def play-pause
  {:query-key "player"
   :value-path [:is_playing]
   :mutate-fn #(spotify-client/request+ (spotify/player-play-pause %))
   :update-fn not})

(def seek-forward
  {:query-key "player"
   :value-path [:progress_ms]
   :mutate-fn #(spotify-client/request+ (spotify/player-seek %))
   :update-fn #(+ % 10000)})

(def seek-backward
  {:query-key "player"
   :value-path [:progress_ms]
   :mutate-fn #(spotify-client/request+ (spotify/player-seek %))
   :update-fn #(-> % (- 10000) (Math/max 0))})

(def volume-up
  {:query-key "player"
   :value-path volume-path
   :mutate-fn #(spotify-client/request+ (spotify/player-volume %))
   :update-fn update-volume-up})

(def volume-down
  {:query-key "player"
   :value-path volume-path
   :mutate-fn #(spotify-client/request+ (spotify/player-volume %))
   :update-fn update-volume-down})

(def toggle-shuffle
  {:query-key "player"
   :value-path [:shuffle_state]
   :mutate-fn #(spotify-client/request+ (spotify/player-shuffle %))
   :update-fn not})

(def toggle-repeat
  {:query-key "player"
   :value-path [:repeat_state]
   :mutate-fn #(spotify-client/request+ (spotify/player-repeat %))
   :update-fn spotify/repeat-state-transition})
