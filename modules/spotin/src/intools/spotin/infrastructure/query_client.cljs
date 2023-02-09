(ns intools.spotin.infrastructure.query-client
  (:require
   ["react-query" :as rq]
   ["react-query/lib/core/focusManager" :refer [focusManager]]
   ["react-query/lib/core/onlineManager" :refer [onlineManager]]
   ["react-query/lib/core/utils" :as utils]
   [intools.spotin.infrastructure.system :as system]))

(defn- subscribe-noop []
  (fn []))

;; `refetchInterval` is disabled when running on server (node), monkey-patch it to make it work.
;; But then we need to also monkey-patch the managers because they depend on browser-only APIs.
(set! (.-isServer utils) false)
(set! (.-subscribe focusManager) subscribe-noop)
(set! (.-subscribe onlineManager) subscribe-noop)

(defn make-client []
  (rq/QueryClient.))

(defn the-client []
  (:query-client system/*system*))
