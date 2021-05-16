(ns intools.spotin.components.input-bar
  (:require [intools.views :refer [uncontrolled-text-input]]
            [intools.hooks :as hooks]
            [ink :refer [Box Text]]))

(defn input-bar [{:keys [focus-id label default-value on-submit on-cancel]}]
  (let [{:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus true})]
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")
             :flex-direction "column"}
     (when label
       [:> Box
        [:> Text label]])
     [:> Box
      [:f> uncontrolled-text-input {:on-submit on-submit
                                    :on-cancel on-cancel
                                    :focus is-focused
                                    :default-value default-value}]]]))
