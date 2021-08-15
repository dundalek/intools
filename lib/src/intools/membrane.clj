(ns intools.membrane
  (:require [membrane.ui :as ui]
            [membrane.lanterna :refer [rectangle]]))

(defn bordered-box [title body]
  (let [body (ui/padding 1 1
                         body)
        [w h] (ui/bounds body)
        title-width (-> title ui/bounds first)
        w (max w (+ 2 title-width))]
    [(rectangle (inc w) (inc h))
     (ui/translate 2 0 title)
     body]))

(defn vertical-layout
  [& elems]
  (let [elems (seq elems)
        first-elem (first elems)
        offset-y (+ (ui/height first-elem)
                    (ui/origin-y first-elem))]
    (when elems
      (loop [elems (next elems)
             offset-y offset-y
             group-elems [first-elem]]
        (if elems
          (let [elem (first elems)
                dy (+ (ui/height elem)
                      (ui/origin-y elem))]
            (recur
             (next elems)
             (+ offset-y dy)
             (conj group-elems
                   (ui/translate 0 offset-y
                                 elem))))
          group-elems)))))

(defn selectable-list [{:keys [state update! items item-component]}]
  (let [{:keys [selected]} state]
    (ui/on :key-press (fn [key]
                        (case key
                          :down (update! update :selected #(Math/min (dec (count items))
                                                                     (inc %)))
                          :up (update! update :selected #(Math/max 0 (dec %)))
                          nil))
           (apply vertical-layout
                  (for [[idx item] (map-indexed list items)]
                    (item-component item {:is-selected (= selected idx)
                                          #_(str [selected idx (= selected idx)])}))))))
