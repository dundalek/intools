(ns intools.spotin.app.query
  (:require ["react-query/lib/core/focusManager" :refer [focusManager]]
            ["react-query/lib/core/onlineManager" :refer [onlineManager]]
            ["react-query/lib/core/utils" :as utils]
            [intools.spotin.model.spotify :as spotify]
            [react-query :refer [useQuery]]))

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

(defn use-player []
  (useQuery "player" spotify/get-player+ #js {:refetchInterval 5000}))
