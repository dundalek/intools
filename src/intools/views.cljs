(ns intools.views
  (:require [react]
            [ink :refer [Box]]))
            ; [ink-select-input :refer [default] :rename {default SelectInput}]))

(defn selectable-list [{:keys [items item-component on-activate on-cancel]}]
  (let [[selected-index set-selected-index] (react/useState 0)
        is-focused (.-isFocused (ink/useFocus #js{:autoFocus true}))]
    (ink/useInput
     (fn [_input key]
       (when is-focused
         (cond
           (.-upArrow key) (set-selected-index (Math/max 0 (dec selected-index)))
           (.-downArrow key) (set-selected-index (Math/min (dec (count items)) (inc selected-index)))
           (.-return key) (when on-activate
                            (on-activate (nth items selected-index)))
           (.-escape key) (when on-cancel
                            (on-cancel))))))
    [:> Box {:flex-direction "column"}
     (->> items
          (map-indexed
           (fn [idx {:keys [key] :as item}]
             ^{:key (or key idx)}
             [item-component (assoc item :is-selected (= idx selected-index))])))]))
