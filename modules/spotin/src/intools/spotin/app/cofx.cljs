(ns intools.spotin.app.cofx
  (:require
   [intools.spotin.infrastructure.query-client :as query-client]
   [re-frame.core :refer [reg-cofx]]))

(reg-cofx
  :query-data
  (fn [coeffects query-key]
    (assoc coeffects :query-data (.getQueryData (query-client/the-client) query-key))))
