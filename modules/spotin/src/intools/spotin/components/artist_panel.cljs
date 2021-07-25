(ns intools.spotin.components.artist-panel
  (:require [clojure.string :as str]
            [ink :refer [Box Spacer Text]]
            [intools.spotin.format :refer [format-album-release-year format-duration]]
            [intools.views :refer [scroll-status use-scrollable-box use-selectable-list]]))

(defn artist-item [{:keys [name followers]} {:keys [is-selected]}]
  [:> Box
   [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
    [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
   [:> Box
    [:> Text {:bold is-selected :color (when is-selected "green")} (:total followers)]]])

(defn album-item [{:keys [name release_date total_tracks]} {:keys [is-selected]}]
  [:> Box
   [:> Box
    [:> Text (cond-> {:bold is-selected :color (when is-selected "green")}
               (not is-selected) (assoc :dim-color true))
     (format-album-release-year release_date)]]
   [:> Box {:flex-basis 0 :flex-grow 1 :padding-x 1 :justify-content "flex-start"}
    [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"}
     name]]
   [:> Box
    [:> Text {:bold is-selected :color (when is-selected "green")}
     total_tracks]]])

(defn artist-track-item [track {:keys [is-selected]}]
  (let [{:keys [name duration_ms popularity]} track]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
     [:> Box
      [:> Text {:bold is-selected :color (when is-selected "green")} popularity " "]]
     [:> Box {:width 6 :justify-content "flex-end"}
      [:> Text {:bold is-selected :color (when is-selected "green")} (format-duration duration_ms)]]]))

(defn artist-header [{:keys [name genres] :as artist}]
  (let [follower-count (-> artist :followers :total)]
    [:> Box {:flex-direction "column"
             :padding-x 1
             :padding-top 1}
     [:> Box
      [:> Text {:dim-color true} "artist "]
      [:> Text {:wrap "truncate-end"} name]
      [:> Spacer]
      [:> Text follower-count]
      [:> Text {:dim-color true} " followers"]]
     [:> Box
      [:> Text {:dim-color true} "genres "]
      [:> Text {:wrap "truncate-end"} (str/join ", " genres)]]]))

(defn artist-sub-panel [{:keys [focus-id header items item-component on-menu on-activate]}]
  (let [{:keys [box-ref is-focused selected-index displayed-selected-index displayed-items]}
        (use-scrollable-box {:focus-id focus-id
                             :items items
                             :on-activate on-activate
                             :auto-focus true})]
    (ink/useInput
     (fn [input _key]
       (when is-focused
         (case input
           "x" (when on-menu (on-menu (nth items selected-index)))
           nil))))
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :flex-basis 1
             :flex-grow 1
             :padding-x 1}
     header
     [:> Box {:flex-direction "column"
              :flex-grow 1
              :ref box-ref}
      (->> displayed-items
           (map-indexed
            (fn [idx {:keys [id] :as item}]
              (let [is-selected (and is-focused (= idx displayed-selected-index))]
                ^{:key id} [item-component item {:is-selected is-selected}]))))]
     [scroll-status selected-index items]]))
