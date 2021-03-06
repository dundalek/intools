(ns intools.views
  (:require [ink :refer [Box Spacer Text]]
            [ink-text-input :refer [default] :rename {default TextInput}]
            [intools.hooks :as hooks]
            [react]))
            ; [ink-select-input :refer [default] :rename {default SelectInput}]))

(defn use-selectable-list-input [{:keys [is-focused items selected-index on-select
                                         on-activate on-toggle on-cancel on-input]}]
  (ink/useInput
   (fn [input ^js key]
     (when is-focused
       (cond
         (.-upArrow key) (on-select (Math/max 0 (dec selected-index)))
         (.-downArrow key) (on-select (Math/min (dec (count items)) (inc selected-index)))
         (.-return key) (when on-activate
                          (on-activate (nth items selected-index)))
         (.-escape key) (when on-cancel
                          (on-cancel))
         (= input " ") (when on-toggle
                         (on-toggle (nth items selected-index)))
         :else (when on-input
                 (on-input input key))))))

  ;; reset current index if number of items changes
  ;; add an option to disable this if it will not be desirable in some situation
  (react/useEffect
   (fn []
     (on-select 0)
     js/undefined)
   #js [(count items)]))

(defn use-selectable-list-controlled [{:keys [focus-id auto-focus selected-index] :as opts}]
  (let [{:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus auto-focus})]
    (use-selectable-list-input (-> opts
                                   (dissoc :focus-id :auto-focus)
                                   (assoc :is-focused is-focused)))
    {:selected-index selected-index
     :is-focused is-focused}))

(defn use-selectable-list [opts]
  (let [[selected-index on-select] (react/useState 0)]
    (use-selectable-list-controlled (assoc opts
                                           :selected-index selected-index
                                           :on-select on-select))))

(defn use-scrollable-box [{:keys [items] :as opts}]
  (let [[selected-index on-select] (react/useState 0)
        {:keys [is-focused]} (use-selectable-list-controlled
                              (assoc opts
                                     :selected-index selected-index
                                     :on-select on-select))
        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (or (:height viewport) 0)
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-selected-index (- selected-index offset)
        displayed-items (->> items (drop offset) (take viewport-height))]
    {:box-ref box-ref
     :is-focused is-focused
     :selected-index selected-index
     :select on-select
     :displayed-selected-index displayed-selected-index
     :displayed-items displayed-items}))

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
;; also adding the on-cancel callback
(defn uncontrolled-text-input [{:keys [focus default-value on-change on-cancel] :as props}]
  (ink/useInput
   (fn [_input ^js key]
     (when (and on-cancel (not (false? focus)) (.-escape key))
       (on-cancel))))
  (let [[value set-value] (react/useState (or default-value ""))
        on-change (fn [s]
                    (set-value s)
                    (when on-change (on-change s)))]
    [:> TextInput (-> props
                      (dissoc :default-value :on-cancel)
                      (assoc :value value
                             :on-change on-change))]))

(defn scroll-status [index items]
  ;; It would be nice to render this over the bottom border
  [:> Box {:height 1}
   [:> Spacer]
   [:> Text {:dim-color true}
    (when (pos? (count items))
      [:> Text (inc index) " of "])
    (count items)]])
