(ns intools.spotin.infrastructure.spotify-client
  (:require
   [intools.spotin.infrastructure.fetch :as fetch]
   [intools.spotin.infrastructure.system :as system]
   [intools.spotin.model.spotify :as spotify]
   [sieppari.core :as sieppari]))

(defn make-client [{:keys [client-id client-secret refresh-token
                           before-request-callback after-request-callback request-error-callback]}]
  (let [!access-token (atom nil)
        request-interceptors [(spotify/make-callbacks-interceptor
                                {:before-request-callback before-request-callback
                                 :after-request-callback after-request-callback
                                 :request-error-callback request-error-callback})
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

(defn the-client []
  (:spotify-client system/*system*))

(defn request+ [opts]
  (let [{:keys [request+]} (the-client)]
    (request+ opts)))
