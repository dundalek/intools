(ns lshwin.viewer
  (:require
   [portal.ui.api :as pui]
   [portal.ui.inspector :as ins]
   [portal.viewer :as-alias pv]
   [reagent.core :as r]
   [lshwin.core :as core]))

(defn- toggle-switch [checked? on-change label]
  [:label {:style {:display "inline-flex"
                   :align-items "center"
                   :cursor "pointer"
                   :font-size "0.9em"
                   :color "#666"}}
   [:input {:type "checkbox"
            :checked checked?
            :on-change #(on-change (.. % -target -checked))
            :style {:margin-right "6px"}}]
   label])

(defn hardware-item? [value]
  (and (map? value)
       (:id value)
       (:class value)
       (not (::disable-item-viewer (meta value)))))

(defn- hardware-item-component [item]
  (let [{:keys [id class description product children]} item
        title (str class ": "
                   (or description product "Unknown Hardware")
                   (when id (str " (" id ")")))
        has-children? (seq children)]
    [:div {:style {:border "1px solid #ddd"
                   :border-radius "8px"
                   :padding "12px"
                   :margin "8px 0"
                   :background-color "#f9f9f9"}}

     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "flex-start"
                    :margin-bottom "8px"}}
      [:div {:style {:flex-grow 1}}
       [:div {:style {:font-weight "bold"
                      :font-size "1.1em"
                      :color "#333"
                      :margin-bottom "4px"}}
        title]]

      [:div {:style {:margin-left "12px"
                     :font-size "0.8em"
                     :color "#999"}}
       (when has-children?
         (str (count children) " child" (when (> (count children) 1) "ren")))]]

     [:div {:style {:margin-top "12px"}}
      [ins/inspector
       {}
       (-> item
           (dissoc :children)
           (vary-meta assoc ::disable-item-viewer true))]]

     (when has-children?
       [:div {:style {:margin-top "12px"
                      :padding-top "8px"}}
        [:div {:style {:font-weight "bold"
                       :margin-bottom "8px"
                       :color "#555"}}
         "Children:"]
        [ins/inspector
         {::pv/default :portal.ui.inspector/coll}
         (mapv #(with-meta % {::pv/default ::hardware-item}) children)]])]))

(defn root? [value]
  (and (map? value)
       (hardware-item? (:data value))))

(defn- root-component [{:keys [data]}]
  (r/with-let [expand-bridges-state (r/atom true)
               show-flat-list-state (r/atom false)]
    (let [expand-bridges? @expand-bridges-state
          show-flat-list? @show-flat-list-state
          processed-data (cond-> data
                           expand-bridges? core/expand-bridge-children)]
      [:div {:style {:padding "12px"
                     :border "1px solid #ccc"
                     :border-radius "8px"
                     :margin "8px 0"
                     :background-color "#fafafa"}}
       [:div {:style {:margin-bottom "12px"
                      :padding-bottom "8px"
                      :border-bottom "1px solid #eee"
                      :display "flex"
                      :justify-content "space-between"
                      :align-items "center"}}
        [:h3 {:style {:margin 0 :color "#333"}} "Hardware Information"]
        [:div {:style {:display "flex"
                       :gap "16px"
                       :align-items "center"}}
         [toggle-switch expand-bridges? #(reset! expand-bridges-state %) "Expand bridges"]
         [toggle-switch show-flat-list? #(reset! show-flat-list-state %) "Show flat list"]]]

       (if show-flat-list?
         [ins/inspector
          {::pv/default :portal.ui.inspector/coll}
          (->> processed-data
               core/flatten-children
               (mapv #(with-meta % {::pv/default ::hardware-item})))]
         [ins/inspector
          {::pv/default ::hardware-item}
          (with-meta processed-data {::pv/default ::hardware-item})])])))

(pui/register-viewer!
 {:name ::hardware-item
  :predicate hardware-item?
  :component hardware-item-component
  :doc "Hardware item viewer for lshw output"})

(pui/register-viewer!
 {:name ::root
  :predicate root?
  :component root-component
  :doc "Root hardware viewer with bridge expansion toggle"})
