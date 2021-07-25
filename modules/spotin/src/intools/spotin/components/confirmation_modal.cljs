(ns intools.spotin.components.confirmation-modal
  (:require [ink :refer [Box Text]]
            [intools.hooks :as hooks]))

(defn confirmation-modal [{:keys [title description focus-id on-submit on-cancel]}]
  (let [{:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus true})]
    (ink/useInput
     (fn [input ^js key]
       (when is-focused
         (cond
           (or (.-return key) (= input "y")) (when on-submit (on-submit))
           (.-escape key) (when on-cancel (on-cancel))))))
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")
             :flex-direction "column"
             :padding-x 1}
     ;; Ideally title would be over top border
     (when title [:> Box [:> Text {:color "green"} title]])
     (when description [:> Box [:> Text description]])]))
