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
  (-> (js/Promise.resolve)
      (.then #(f))
      (.catch (fn [err]
                (if (error-not-found? err)
                  (-> (spotify/get-player-devices+)
                      (.then (fn [{:keys [devices]}]
                               (if (= (count devices) 1)
                                 (let [device-id (-> devices first :id)]
                                   (-> (spotify/player-transfer+ device-id)
                                       (.then #(f))))
                                 ;; TODO open the device picker when multiple devices are listed
                                 (throw err)))))
                  (throw err))))
      (.finally #(dispatch [:spotin/refresh-playback-status]))))

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
  (fn [_] (with-playback-refresh+ spotify/player-play-pause+)))

(reg-fx :next
  (fn [_] (with-playback-refresh+ spotify/player-next+)))

(reg-fx :previous
  (fn [_] (with-playback-refresh+ spotify/player-previous+)))

(reg-fx :shuffle
  (fn [_] (with-playback-refresh+ spotify/player-toggle-shuffle+)))

(reg-fx :repeat
  (fn [_] (with-playback-refresh+ spotify/player-toggle-repeat+)))

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

(reg-fx :spotin/invalidate-query
  (fn [query-key]
    (.invalidateQueries @!query-client query-key)))

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

(reg-fx :spotin/load-artist
  (fn [artist-id]
    (-> (spotify/get-artist+ artist-id)
        (.then (fn [body]
                 (dispatch [:spotin/set-artist artist-id
                            (js->clj body :keywordize-keys true)]))))
    (-> (spotify/get-artist-albums+ artist-id)
        (.then (fn [{:keys [items]}]
                 (dispatch [:spotin/set-artist-albums artist-id items]))))
    (-> (spotify/get-artist-top-tracks+ artist-id)
        (.then (fn [{:keys [tracks]}]
                 (dispatch [:spotin/set-artist-top-tracks artist-id tracks]))))
    (-> (spotify/get-artist-related-artists+ artist-id)
        (.then (fn [{:keys [artists]}]
                 (dispatch [:spotin/set-artist-related-artists artist-id artists]))))))
