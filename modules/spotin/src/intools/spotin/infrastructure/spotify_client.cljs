(ns intools.spotin.infrastructure.spotify-client
  (:require
   [intools.spotin.infrastructure.fetch :as fetch]
   [intools.spotin.model.spotify :as spotify]
   [sieppari.core :as sieppari]))

(def client-id (.. js/process -env -SPOTIFY_CLIENT_ID))
(def client-secret (.. js/process -env -SPOTIFY_CLIENT_SECRET))
(def refresh-token (.. js/process -env -SPOTIFY_REFRESH_TOKEN))
(defonce !access-token (atom nil))

(def request-interceptors
  [spotify/callbacks-interceptor
   (spotify/make-refresh-interceptor
    {:!access-token !access-token
     :client-opts {:client-id client-id
                   :client-secret client-secret
                   :refresh-token refresh-token}})
   spotify/js->clj-response-interceptor
   spotify/parse-json-response-interceptor
   (spotify/make-authorize-interceptor {:get-access-token (fn [] @!access-token)})
   (spotify/make-timeout-signal-interceptor 10000)
   fetch/request->fetch+])

(defn request+ [opts]
  (js/Promise.
   (fn [resolve reject]
     (sieppari/execute request-interceptors opts resolve reject))))

(def client
  {:request+ request+})
