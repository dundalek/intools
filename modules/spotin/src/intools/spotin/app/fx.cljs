(ns intools.spotin.app.fx
  (:require [intools.spotin.app.query :refer [!query-client]]
            [intools.spotin.model.playlist :as playlist]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :refer [dispatch reg-fx]]))

(defn fetch-error? [err]
  (= (-> err .-constructor .-name) "Response"))

(defn error-not-found? [err]
  (and (fetch-error? err)
       (= (.-status err) 404)))

(defn with-playback-refresh+ [f]
  (-> (let [playback (.getQueryData @!query-client "player")]
        (if (spotify/playback-stopped? playback)
          (spotify/auto-select-device+)
          ;; TODO: show device picker if auto-connect fails
          (js/Promise.resolve)))
      (.then #(f))
      (.catch (fn [err]
                (if (error-not-found? err)
                  (-> (spotify/auto-select-device+)
                      (.then #(f))
                      (.catch (fn [_]
                                (throw err))))
                  ;; TODO open the device picker when multiple devices are listed
                  (throw err))))
      (.finally #(.invalidateQueries @!query-client "player"))))

(reg-fx :next
  (fn [_] (with-playback-refresh+ spotify/player-next+)))

(reg-fx :previous
  (fn [_] (with-playback-refresh+ spotify/player-previous+)))

(reg-fx :playlist-play
  (fn [arg] (with-playback-refresh+ #(spotify/player-play+ {:context_uri (:uri arg)}))))

(reg-fx :album-play
  (fn [{:keys [item]}] (with-playback-refresh+ #(spotify/player-play+ {:context_uri (:uri item)}))))

(reg-fx :artist-play
  (fn [{:keys [item]}] (with-playback-refresh+ #(spotify/player-play+ {:context_uri (:uri item)}))))

(reg-fx :artist-context-play
  (fn [{:keys [context]}] (with-playback-refresh+ #(spotify/player-play+ {:context_uri (:uri context)}))))

(reg-fx :track-play
  (fn [{:keys [item items context]}]
    (if (= (:type context) "artist")
      ;; playing top tracks from artist context is not allowed,
      ;; so we add the track uris directly
      (let [position (->> items
                          (keep-indexed #(when (= (:id item) (:id %2)) %1))
                          (first))
            opts {:uris (into [] (map :uri items))
                  :offset {:position position}}]
        (with-playback-refresh+ #(spotify/player-play+ opts)))
      (with-playback-refresh+ #(spotify/player-play+ {:context_uri (:uri context)
                                                      :offset {:uri (:uri item)}})))))

(reg-fx :spotin/player-transfer
  (fn [device-id]
    (with-playback-refresh+ #(spotify/player-transfer+ device-id))))

(reg-fx :playlist-share
  (fn [arg] (js/console.log "Playlist URI:" (:uri arg))))

(reg-fx :playlists-mix
  (fn [playlist-ids]
    (-> (playlist/create-mixed-playlist+ playlist-ids)
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

(reg-fx :playlist-unfollow
  (fn [playlist-id]
    (-> (spotify/request+ (spotify/playlist-unfollow playlist-id))
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

(reg-fx :spotin/invalidate-query
  (fn [query-key]
    (.invalidateQueries @!query-client query-key)))
