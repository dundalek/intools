(ns intools.spotin.app.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub :db
  (fn [db _]
    db))
