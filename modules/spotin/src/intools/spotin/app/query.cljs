(ns intools.spotin.app.query
  (:require
   [intools.spotin.infrastructure.query-client :as query-client]
   [intools.spotin.infrastructure.spotify-client :as spotify-client]
   [intools.spotin.model.spotify :as spotify]
   [react]
   [react-query :as rq]))

(defn make-mutation-fx [query-client options]
  ;; based on onMutate
  ;; ignoring result (which is observed and returned when using the hook, but we just rely on global indicators)
  (let [observer (rq/MutationObserver. query-client options)]
    (fn [value]
      (-> (.mutate observer value)
            ;; We hook into request errors globally, but reconsider if there is a better approach
          (.catch (fn [_]))))))

(defn make-optimistic-mutation-fx
  ([options] (make-optimistic-mutation-fx (query-client/the-client) options))
  ([query-client {:keys [query-key value-path update-fn mutate-fn]}]
   (let [mutation-options #js {:mutationFn mutate-fn
                               :onMutate (fn [new-progress]
                                           (let [current (.getQueryData query-client query-key)
                                                 optimistic (assoc-in current value-path new-progress)]
                                             (.cancelQueries query-client query-key)
                                             (.setQueryData query-client query-key optimistic)))
                               :onSettled (fn []
                                              ;; Count 1 means to only invalidate if we are the last mutation

                                             ;; this could end up wrong if mutations for different keys are triggered at the same time
                                             ;; isMutating takes a predicate function
                                             ;; could set meta on the mutation
                                            (when (= (.isMutating query-client) 1)
                                              (js/setTimeout (fn []
                                                               (when (= (.isMutating query-client) 0)
                                                                 (.cancelQueries query-client query-key)
                                                                 (.invalidateQueries query-client query-key)))
                                                             spotify/player-update-delay)))}
         mutate (make-mutation-fx query-client mutation-options)]
     (fn []
       (let [current (-> (.getQueryData query-client query-key)
                         (get-in value-path))]
         (mutate (update-fn current)))))))

(defn make-optimistic-playlist-mutation-fx [query-client]
  (let [query-key "playlists"
        mutation-options #js {:mutationFn (fn [{:keys [playlist-id attr value]}]
                                            (spotify-client/request+ (spotify/playlist-change playlist-id {attr value})))
                              :onMutate (fn [{:keys [playlist-id attr value]}]
                                          (let [current (.getQueryData query-client query-key)
                                                optimistic (update current :items
                                                                   (partial map (fn [playlist]
                                                                                  (cond-> playlist
                                                                                    (= (:id playlist) playlist-id) (assoc attr value)))))]
                                            (.cancelQueries query-client query-key)
                                            (.setQueryData query-client query-key optimistic)
                                            current))
                              :onError (fn [_err _value original]
                                         (.setQueryData query-client query-key original))
                              :onSettled (fn []
                                           (.invalidateQueries query-client query-key))}]
    (make-mutation-fx query-client mutation-options)))

(defn player []
  #js {:queryKey #js ["player"]
       :queryFn #(spotify-client/request+ (spotify/get-player))
       :refetchInterval 5000})

(defn playlists []
  #js {:queryKey #js ["playlists"]
       :queryFn #(spotify/get-all-playlists+ spotify-client/client)})

(defn playlist [playlist-id]
  #js {:queryKey #js ["playlists" playlist-id]
       :queryFn #(spotify-client/request+ (spotify/get-playlist playlist-id))})

(defn playlist-tracks [playlist-id]
  #js {:queryKey #js ["playlist-tracks" playlist-id]
       :queryFn #(spotify/get-playlist-tracks+ spotify-client/client playlist-id)})

(defn artist [artist-id]
  #js {:queryKey #js ["artists" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist artist-id))})

(defn artist-albums [artist-id]
  #js {:queryKey #js ["artist-albums" artist-id]
       :queryFn #(spotify/get-artist-albums+ spotify-client/client artist-id)})

(defn artist-top-tracks [artist-id]
  #js {:queryKey #js ["artist-top-tracks" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist-top-tracks artist-id))})

(defn artist-related-artists [artist-id]
  #js {:queryKey #js ["artist-related-artists" artist-id]
       :queryFn #(spotify-client/request+ (spotify/get-artist-related-artists artist-id))})

(defn devices []
  #js {:queryKey #js ["devices"]
       :queryFn #(spotify-client/request+ (spotify/get-player-devices))})

(defn album [album-id]
  #js {:queryKey #js ["albums" album-id]
       :queryFn #(spotify-client/request+ (spotify/get-album album-id))})

(defn album-tracks [album-id]
  #js {:queryKey #js ["album-tracks" album-id]
       :queryFn #(spotify/get-album-tracks+ spotify-client/client album-id)})
