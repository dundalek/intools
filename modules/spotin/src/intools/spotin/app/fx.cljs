(ns intools.spotin.app.fx
  (:require
   [intools.spotin.app.mutations :as mutations]
   [intools.spotin.infrastructure.query-client :as query-client]
   [intools.spotin.infrastructure.spotify-client :as spotify-client]
   [intools.spotin.model.playlist :as playlist]
   [intools.spotin.model.spotify :as spotify]
   [re-frame.core :refer [dispatch reg-fx]]
   [react-query :as rq]))

(defn fetch-error? [err]
  (= (-> err .-constructor .-name) "Response"))

(defn error-not-found? [err]
  (and (fetch-error? err)
       (= (.-status err) 404)))

(defn with-auto-select-device+ [make-request]
  (-> (let [playback (.getQueryData (query-client/the-client) "player")]
        (if (spotify/playback-stopped? playback)
          (spotify/auto-select-device+ (spotify-client/the-client))
          ;; TODO: show device picker if auto-connect fails
          (js/Promise.resolve)))
      (.then #(spotify-client/request+ (make-request)))
      (.catch (fn [err]
                (if (error-not-found? err)
                  (-> (spotify/auto-select-device+ (spotify-client/the-client))
                      (.then #(spotify-client/request+ (make-request)))
                      (.catch (fn [_]
                                (throw err))))
                  ;; TODO open the device picker when multiple devices are listed
                  (throw err))))))

(defn with-playback-refresh+ [make-request]
  (-> (with-auto-select-device+ make-request)
      (.finally (fn []
                  (js/setTimeout #(.invalidateQueries (query-client/the-client) "player")
                                 spotify/player-update-delay)))))

(defn make-mutation-fx [get-client options]
  ;; based on onMutate
  ;; ignoring result (which is observed and returned when using the hook, but we just rely on global indicators)
  ;; delay is a hack to prevent eagerly getting client before it is bound
  (let [!observer (delay (rq/MutationObserver. (get-client) options))]
    (fn [value]
      (-> (.mutate @!observer value)
            ;; We hook into request errors globally, but reconsider if there is a better approach
          (.catch (fn [_]))))))

(defn make-optimistic-mutation-fx
  [{:keys [query-key value-path update-fn mutate-fn]}]
  (let [get-client query-client/the-client
        mutation-options #js {:mutationFn mutate-fn
                              :onMutate (fn [new-progress]
                                          (let [current (.getQueryData (get-client) query-key)
                                                optimistic (assoc-in current value-path new-progress)]
                                            (.cancelQueries (get-client) query-key)
                                            (.setQueryData (get-client) query-key optimistic)))
                              :onSettled (fn []
                                             ;; Count 1 means to only invalidate if we are the last mutation

                                            ;; this could end up wrong if mutations for different keys are triggered at the same time
                                            ;; isMutating takes a predicate function
                                            ;; could set meta on the mutation
                                           (when (= (.isMutating (get-client)) 1)
                                             (js/setTimeout (fn []
                                                              (when (= (.isMutating (get-client)) 0)
                                                                (.cancelQueries (get-client) query-key)
                                                                (.invalidateQueries (get-client) query-key)))
                                                            spotify/player-update-delay)))}
        mutate (make-mutation-fx get-client mutation-options)]
    (fn []
      (let [current (-> (.getQueryData (get-client) query-key)
                        (get-in value-path))]
        (mutate (update-fn current))))))

(defn make-optimistic-playlist-mutation-fx [get-client]
  (let [query-key "playlists"
        mutation-options #js {:mutationFn (fn [{:keys [playlist-id attr value]}]
                                            (spotify-client/request+ (spotify/playlist-change playlist-id {attr value})))
                              :onMutate (fn [{:keys [playlist-id attr value]}]
                                          (let [current (.getQueryData (get-client) query-key)
                                                optimistic (update current :items
                                                                   (partial map (fn [playlist]
                                                                                  (cond-> playlist
                                                                                    (= (:id playlist) playlist-id) (assoc attr value)))))]
                                            (.cancelQueries (get-client) query-key)
                                            (.setQueryData (get-client) query-key optimistic)
                                            current))
                              :onError (fn [_err _value original]
                                         (.setQueryData (get-client) query-key original))
                              :onSettled (fn []
                                           (.invalidateQueries (get-client) query-key))}]
    (make-mutation-fx get-client mutation-options)))

(reg-fx :next
  (fn [_] (with-playback-refresh+ spotify/player-next)))

(reg-fx :previous
  (fn [_] (with-playback-refresh+ spotify/player-previous)))

(reg-fx :playlist-play
  (fn [arg] (with-playback-refresh+ #(spotify/player-play {:context_uri (:uri arg)}))))

(reg-fx :album-play
  (fn [{:keys [item]}] (with-playback-refresh+ #(spotify/player-play {:context_uri (:uri item)}))))

(reg-fx :artist-play
  (fn [{:keys [item]}] (with-playback-refresh+ #(spotify/player-play {:context_uri (:uri item)}))))

(reg-fx :artist-context-play
  (fn [{:keys [context]}] (with-playback-refresh+ #(spotify/player-play {:context_uri (:uri context)}))))

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
        (with-playback-refresh+ #(spotify/player-play opts)))
      (with-playback-refresh+ #(spotify/player-play {:context_uri (:uri context)
                                                     :offset {:uri (:uri item)}})))))

(reg-fx :spotin/queue-track
  (fn [uri]
    (with-auto-select-device+ #(spotify/player-queue uri))))

(reg-fx :spotin/player-seek
  (fn [position-ms]
    (with-auto-select-device+ #(spotify/player-seek position-ms))))

(reg-fx :spotin/player-transfer
  (fn [device-id]
    (with-playback-refresh+ #(spotify/player-transfer device-id))))

(reg-fx :playlist-share
  (fn [arg] (js/console.log "Playlist URI:" (:uri arg))))

(reg-fx :playlists-mix
  (fn [playlist-ids]
    (-> (playlist/create-mixed-playlist+ (spotify-client/the-client) playlist-ids)
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

(reg-fx :playlist-unfollow
  (fn [playlist-id]
    (-> (spotify-client/request+ (spotify/playlist-unfollow playlist-id))
        ;; TODO: maybe only refresh the single playlist
        (.then #(dispatch [:spotin/refresh-playlists])))))

(reg-fx :spotin/invalidate-query
  (fn [query-key]
    (.invalidateQueries (query-client/the-client) query-key)))

(reg-fx :spotin/player-seek-forward
  (make-optimistic-mutation-fx mutations/seek-forward))

(reg-fx :spotin/player-seek-backward
  (make-optimistic-mutation-fx mutations/seek-backward))

(reg-fx :spotin/player-volume-up
  (make-optimistic-mutation-fx mutations/volume-up))

(reg-fx :spotin/player-volume-down
  (make-optimistic-mutation-fx mutations/volume-down))

(reg-fx :spotin/player-toggle-shuffle
  (make-optimistic-mutation-fx mutations/toggle-shuffle))

(reg-fx :spotin/player-toggle-repeat
  (make-optimistic-mutation-fx mutations/toggle-repeat))

(reg-fx :spotin/play-pause
  (let [mutate (make-optimistic-mutation-fx mutations/play-pause)]
    (fn [_]
      (let [playback (.getQueryData (query-client/the-client) "player")]
        ;; If playback is in stopped state then paly/pause does nothing.
        ;; Therefore we try to select available device and triger play directly.
        (if (spotify/playback-stopped? playback)
          (-> (spotify/auto-select-device+ (spotify-client/the-client))
              ;; TODO: show device picker if auto-connect fails
              (.then #(spotify-client/request+ (spotify/player-play))))
          (mutate))))))

(reg-fx :spotin/update-playlist-attribute
  (make-optimistic-playlist-mutation-fx query-client/the-client))
