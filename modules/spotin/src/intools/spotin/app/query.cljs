(ns intools.spotin.app.query
  (:require ["react-query/lib/core/focusManager" :refer [focusManager]]
            ["react-query/lib/core/onlineManager" :refer [onlineManager]]
            ["react-query/lib/core/utils" :as utils]
            [intools.spotin.model.spotify :as spotify]
            [react-query :refer [useMutation useQuery]]
            [react]))

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

(defn use-optimistic-mutation [{:keys [query-key value-path mutate-fn update-fn]}]
  (let [mutation (useMutation
                  mutate-fn
                  #js{:onMutate (fn [new-progress]
                                  (let [current (.getQueryData @!query-client query-key)
                                        optimistic (assoc-in current value-path new-progress)]
                                    (.cancelQueries @!query-client query-key)
                                    (.setQueryData @!query-client query-key optimistic)))
                      :onSettled (fn []
                                    ;; Count 1 means to only invalidate if we are the last mutation
                                   (when (= (.isMutating @!query-client) 1)
                                      ;; Playback API does not seem to have Read-your-writes consistency,
                                      ;; delay for a second before trying to fetch status update
                                     (js/setTimeout (fn []
                                                      (when (= (.isMutating @!query-client) 0)
                                                        (.cancelQueries @!query-client query-key)
                                                        (.invalidateQueries @!query-client query-key)))
                                                    1000)))})
        mutate (.-mutate mutation)
        mutate-update (react/useCallback
                       (fn []
                         (let [current (-> (.getQueryData @!query-client query-key)
                                           (get-in value-path))]
                           (mutate (update-fn current))))
                       #js [mutate])]
    (set! (.-mutate mutation) mutate-update)
    mutation))

(defn use-optimistic-playlist-mutation [{:keys [playlist-id attr]}]
  (let [query-key "playlists"]
    (useMutation
     #(spotify/playlist-change+ playlist-id {attr %})
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

(defn use-player []
  (useQuery "player" spotify/get-player+ #js {:refetchInterval 5000}))

(defn use-playlists []
  (useQuery "playlists" spotify/get-all-playlists+))

(defn use-playlist [playlist-id]
  (useQuery #js ["playlists" playlist-id] #(spotify/get-playlist+ playlist-id)))

(defn use-playlist-tracks [playlist-id]
  (useQuery #js ["playlist-tracks" playlist-id] #(spotify/get-playlist-tracks+ playlist-id)))

(defn use-artist [artist-id]
  (useQuery #js ["artists" artist-id] #(spotify/get-artist+ artist-id)))

(defn use-artist-albums [artist-id]
  (useQuery #js ["artist-albums" artist-id] #(spotify/get-artist-albums+ artist-id)))

(defn use-artist-top-tracks [artist-id]
  (useQuery #js ["artist-top-tracks" artist-id] #(spotify/get-artist-top-tracks+ artist-id)))

(defn use-artist-related-artists [artist-id]
  (useQuery #js ["artist-related-artists" artist-id] #(spotify/get-artist-related-artists+ artist-id)))

(defn use-devices []
  (useQuery "devices" spotify/get-player-devices+))

(defn use-albums [album-id]
  (useQuery #js ["albums" album-id] #(spotify/get-album+ album-id)))

(defn use-album-tracks [album-id]
  (useQuery #js ["album-tracks" album-id] #(spotify/get-album-tracks+ album-id)))
