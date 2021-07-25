(ns intools.spotin.components.error-alert
  (:require [ink :refer [Box Text]]
            [intools.hooks :as hooks]))

(defn error-alert [{:keys [error on-dismiss]}]
  (let [{:keys [request error]} error
        {:keys [method url]} request
        focus-id "error-alert"
        {:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus true})]
    (ink/useInput
     (fn [_input ^js key]
       (when is-focused
         (cond
           (.-escape key) (on-dismiss)))))
    [:> Box {:border-style "single"
             :border-color (when is-focused "red")
             :flex-direction "column"
             :padding-x 1}
     [:> Box
      [:> Text {:wrap "truncate-end"} "API Error: " method " " url]]
     [:> Box
      (if (and (some? error) (= (-> error .-constructor .-name) "Response"))
        [:> Text
         (.-status error) " - " (.-statusText error) "\n"]
         ;; TODO resolve body for message if it exists
        [:> Text (str error)])]]))
