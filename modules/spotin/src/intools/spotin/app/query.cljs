(ns intools.spotin.app.query
  (:require
   ["react-query/lib/core/focusManager" :refer [focusManager]]
   ["react-query/lib/core/onlineManager" :refer [onlineManager]]
   ["react-query/lib/core/utils" :as utils]
   [intools.spotin.infrastructure.spotify-client :as spotify-client]
   [intools.spotin.model.spotify :as spotify]
   [react]
   [react-query :as rq :refer [useMutation]]))

(defn- subscribe-noop []
  (fn []))

(defonce !query-client
  (do
    ;; `refetchInterval` is disabled when running on server (node), monkey-patch it to make it work.
    ;; But then we need to also monkey-patch the managers because they depend on browser-only APIs.
    (set! (.-isServer utils) false)
    (set! (.-subscribe focusManager) subscribe-noop)
    (set! (.-subscribe onlineManager) subscribe-noop)

    (atom nil)))

(defn make-optimistic-mutation-options [{:keys [query-key value-path mutate-fn]}]
   #js {:mutationFn mutate-fn
        :onMutate (fn [new-progress]
                    (let [current (.getQueryData @!query-client query-key)
                          optimistic (assoc-in current value-path new-progress)]
                      (.cancelQueries @!query-client query-key)
                      (.setQueryData @!query-client query-key optimistic)))
        :onSettled (fn []
                      ;; Count 1 means to only invalidate if we are the last mutation

                     ;; this could end up wrong if mutations for different keys are triggered at the same time
                     ;; isMutating takes a predicate function
                     ;; could set meta on the mutation
                     (when (= (.isMutating @!query-client) 1)
                       (js/setTimeout (fn []
                                        (when (= (.isMutating @!query-client) 0)
                                          (.cancelQueries @!query-client query-key)
                                          (.invalidateQueries @!query-client query-key)))
                                      spotify/player-update-delay)))})

(defn use-optimistic-mutation [{:keys [query-key value-path update-fn _mutate-fn] :as options}]
  (let [mutation (useMutation (make-optimistic-mutation-options options))
        mutate (.-mutate mutation)
        mutate-update (react/useCallback
                       (fn []
                         (let [current (-> (.getQueryData @!query-client query-key)
                                           (get-in value-path))]
                           (mutate (update-fn current))))
                       #js [mutate])]
    (set! (.-mutate mutation) mutate-update)
    mutation))

(defn make-optimistic-mutation-fx [{:keys [query-key value-path update-fn _mutate-fn] :as options}]
  ;; based on onMutate
  ;; ignoring result (which is observed and returned when using the hook, but we just rely on global indicators)
  ;; create observer lazily so that query-client is initialized at that point
  (let [!observer (delay (rq/MutationObserver. @!query-client
                                               (make-optimistic-mutation-options options)))]
    (fn mutate []
      (let [current (-> (.getQueryData @!query-client query-key)
                        (get-in value-path))
            variables (update-fn current)]
        (-> (.mutate @!observer variables)
            (.catch (fn [_])))))))

(defn use-optimistic-playlist-mutation [{:keys [playlist-id attr]}]
  (let [query-key "playlists"]
    (useMutation
     #(spotify-client/request+ (spotify/playlist-change playlist-id {attr %}))
     #js{:onMutate (fn [value]
                     (let [current (.getQueryData @!query-client query-key)
                           optimistic (update current :items
                                              (partial map (fn [playlist]
                                                             (cond-> playlist
                                                               (= (:id playlist) playlist-id) (assoc attr value)))))]
                       (.cancelQueries @!query-client query-key)
                       (.setQueryData @!query-client query-key optimistic)
                       current))
         :onError (fn [_err _value original]
                    (.setQueryData @!query-client query-key original))
         :onSettled (fn []
                      ;; Count 1 means to only invalidate if we are the last mutation
                      (when (= (.isMutating @!query-client) 1)
                        (.invalidateQueries @!query-client query-key)))})))

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
