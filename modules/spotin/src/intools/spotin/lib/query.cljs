(ns intools.spotin.lib.query
  (:require
   ["react-query" :as rq]
   [cljs-bean.core :as bean]
   [intools.spotin.app.query :refer [!query-client]]
   [re-frame.core :as rf]
   [reagent.core :as r]
   [reagent.ratom :as ra]))

(defn make-query-reaction [options]
  ;; based on useBaseQuery
  ;; onError, onSuccess, onSettled are not wrapped in batchCalls
  ;; no suspense
  ;; changing options will likely result in a new instance instead of updating existing one
  ;; no notifyOnChangeProps tracked support
  (let [query-client @!query-client
        defaulted-options (.defaultQueryObserverOptions query-client options)
        _ (set! (.-optimisticResults defaulted-options) true)
        observer (rq/QueryObserver. query-client defaulted-options)
        get-result #(.getOptimisticResult observer defaulted-options)
        !result (r/atom (get-result))
        unsubscribe (.subscribe observer
                                (.batchCalls rq/notifyManager
                                             (fn [] (reset! !result (get-result)))))
        _ (.updateResult observer)]
    (ra/make-reaction
     (fn []
       ;; maybe normalize with :prop->key
       ;; https://github.com/mfikes/cljs-bean/blob/master/doc/key-mapping.md
       (bean/bean @!result))
     :on-dispose (fn []
                   (unsubscribe)))))

(defn reg-query-sub [kw make-query]
  (rf/reg-sub kw
    (fn [[_ & args]]
      (make-query-reaction (apply make-query args)))
    identity))
