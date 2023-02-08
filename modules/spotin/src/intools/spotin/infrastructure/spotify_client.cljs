(ns intools.spotin.infrastructure.spotify-client
  (:require
   [intools.spotin.model.spotify :refer [request-interceptors]]
   [sieppari.core :as sieppari]))

(defn request+ [opts]
  (js/Promise.
   (fn [resolve reject]
     (sieppari/execute request-interceptors opts resolve reject))))

(def client
  {:request+ request+})
