(ns intools.spotin.app.cofx
  (:require [intools.spotin.app.query :refer [!query-client]]
            [re-frame.core :refer [reg-cofx]]))

(reg-cofx
  :query-data
  (fn [coeffects query-key]
    (assoc coeffects :query-data (.getQueryData @!query-client query-key))))
