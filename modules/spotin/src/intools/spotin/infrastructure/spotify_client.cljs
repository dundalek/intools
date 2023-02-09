(ns intools.spotin.infrastructure.spotify-client
  (:require
   [intools.spotin.infrastructure.fetch :as fetch]
   [intools.spotin.model.spotify :as spotify]
   [sieppari.core :as sieppari]))

(defn make-client [{:keys [client-id client-secret refresh-token]}]
  (let [!access-token (atom nil)
        request-interceptors [spotify/callbacks-interceptor
                              (spotify/make-refresh-interceptor
                               {:!access-token !access-token
                                :client-opts {:client-id client-id
                                              :client-secret client-secret
                                              :refresh-token refresh-token}})
                              spotify/js->clj-response-interceptor
                              spotify/parse-json-response-interceptor
                              (spotify/make-authorize-interceptor {:get-access-token (fn [] @!access-token)})
                              (spotify/make-timeout-signal-interceptor 10000)
                              fetch/request->fetch+]
        request+ (fn request+ [opts]
                   (js/Promise.
                    (fn [resolve reject]
                      (sieppari/execute request-interceptors opts resolve reject))))]
    {:request+ request+}))

(def ^:dynamic *client* nil)

(defn the-client []
  *client*)

(defn request+ [opts]
  (let [{:keys [request+]} (the-client)]
    (request+ opts)))
