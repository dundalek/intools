(ns intools.spotin.infrastructure.fetch
  (:require
   [clojure.string :as str]
   [clojure.core :as clojure])
   ;; can't upgrade node-fetch from 2.x, since 3.x is ESM only which shadow-cljs does not like
   ;; native fetch should be available in node 18+
   ; [node-fetch :as fetch]
  (:import (goog.Uri QueryData)))

(def fetch
  #_fetch/default
  js/fetch)

(def content-types
  {:form "application/x-www-form-urlencoded"
   :json "application/json"})

(defn encode-form-params [form-params]
  (reduce (fn [params [k v]]
            (.append params (name k) v)
            params)
          (js/URLSearchParams.)
          form-params))

(defn encode-query-params [query-params]
  (let [qd (QueryData.)]
    (doseq [[k v] query-params]
      (.set qd (name k) v))
    (.toString qd)))

(defn request->url [{:keys [url query-params]}]
  (if (seq query-params)
    (str url "?" (encode-query-params query-params))
    url))

(defn request->fetch-options [{:keys [method url headers query-params body content-type accept signal]}]
  (cond-> {:method (str/upper-case (name method))
           :headers headers}

    (and (= content-type :json) (map? body))
    (-> (assoc :body (js/JSON.stringify (clj->js body)))
        (update :headers assoc :Content-Type (clojure/get content-types content-type)))

    (and (= content-type :form) (map? body))
    (-> (assoc :body (encode-form-params body))
        (update :headers assoc :Content-Type (clojure/get content-types content-type)))

    accept (update :headers assoc :Accept (clojure/get content-types accept))
    signal (assoc :signal signal)
    :always clj->js))

(defn request->fetch+ [request]
  (fetch (request->url request)
         (request->fetch-options request)))
