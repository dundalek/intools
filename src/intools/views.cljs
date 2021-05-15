(ns intools.views
  (:require [react]
            [ink :refer [Box Text]]
            [ink-text-input :refer [default] :rename {default TextInput}]
            [intools.hooks :as hooks]))
            ; [ink-select-input :refer [default] :rename {default SelectInput}]))

(defn use-selectable-list [{:keys [focus-id items on-activate on-toggle on-cancel on-input auto-focus]}]
  (let [[selected-index set-selected-index] (react/useState 0)
        {:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus auto-focus})]
    (ink/useInput
     (fn [input ^js key]
       (when is-focused
         (cond
           (.-upArrow key) (set-selected-index (Math/max 0 (dec selected-index)))
           (.-downArrow key) (set-selected-index (Math/min (dec (count items)) (inc selected-index)))
           (.-return key) (when on-activate
                            (on-activate (nth items selected-index)))
           (.-escape key) (when on-cancel
                            (on-cancel))
           (= input " ") (when on-toggle
                           (on-toggle (nth items selected-index)))
           :else (when on-input
                   (on-input input key))))))
    {:selected-index selected-index
     :is-focused is-focused}))

(defn selectable-list [{:keys [items item-component] :as props}]
  (let [{:keys [selected-index]} (use-selectable-list (dissoc props :item-component))]
    [:> Box {:flex-direction "column"}
     (->> items
          (map-indexed
           (fn [idx {:keys [key] :as item}]
             ^{:key (or key idx)}
             [item-component item {:is-selected (= idx selected-index)}])))]))

(defn action-bar [actions]
  (->> actions
       (map (fn [{:keys [name shortcut shortcut-label]}]
              [:<>
               [:> Text name]
               [:> Text {:color "blue"}
                (str " [" (or shortcut-label shortcut) "]")]]))
       (interpose [:> Text "  "])
       (into [:> Box {:border-style "round"}])))

;; ink's UncontrolledTextInput doesn't support default value
;; also adding the on-cancel callback, limitation: only one widget should be rendered at a time
(defn uncontrolled-text-input [{:keys [default-value on-cancel] :as props}]
  (ink/useInput
   (fn [_input ^js key]
     (when (and on-cancel (.-escape key))
       (on-cancel))))
  (let [[value set-value] (react/useState (or default-value ""))]
    [:> TextInput (-> props
                      (dissoc :default-value :on-cancel)
                      (assoc :value value
                             :on-change set-value))]))
