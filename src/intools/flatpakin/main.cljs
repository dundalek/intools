(ns intools.flatpakin.main
  (:require [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [selectable-list]]
            [intools.flatpakin.model :as model]))

(defonce !app (atom nil))
(declare render)

(def tabs
  [{:name "Apps"
    :shortcut "1"
    :route ::apps}
   {:name "Runtimes"
    :shortcut "2"
    :route ::runtimes}
   {:name "Config"
    :shortcut "3"
    :route ::config}
   {:name "Repositories"
    :shortcut "4"
    :route ::repositories}
   {:name "History"
    :shortcut "5"
    :route ::history}])
   ; {:name "Sandboxes"
   ;  :shortcut "3"
   ;  :route ::runtime-list}
   ; {:name "All"
   ;  :shortcut "3"
   ;  :route ::all-list}])

;; second level filter - app, runtime, running

;; jak udelat search? nejaka global install akce?

;; nebo / - search and concat installed apps?
  ;; search nedava installed status, ale to pujde joinout is installed apps
  ;; search returns also runtimes, can have multiple versions

;; Permissions


; (def all (concat apps runtimes))


(derive ::app-actions ::apps)

(defn init-state []
  {:route {:name ::apps}
   :items (model/list-apps)})

(defmulti reducer (fn [_state [event-name]] event-name))

(defmethod reducer :navigate [state [_ route]]
  (assoc state :route route))

(defmethod reducer :select-app [state [_ item]]
  (assoc state :route {:name ::app-actions
                       :params {:app item}}))

;; useReducer does not work with multimethods
(defn reducer-fn [state event]
  (reducer state event))

(defn app-row [{:keys [name application version branch installation is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} name]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "blue" :wrap "truncate-end" :bold is-selected} application]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} version]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} branch]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} installation]])

(defn app []
  (let [[{:keys [route items]} dispatch] (react/useReducer reducer-fn nil init-state)
        route-name (:name route)
        focus-manager (ink/useFocusManager)]
    ;; Switch focus next to switch focus from the global key handling to the module list
    (react/useEffect
     (fn []
       (.focusNext focus-manager)
       js/undefined)
     #js [])
    (ink/useInput
     (fn [input _key]
       (when-let [route (some (fn [{:keys [shortcut route]}]
                                (when (= shortcut input)
                                  route))
                              tabs)]
         (dispatch [:navigate {:name route}]))))
    [:> Box {:flex-direction "column"}
     [:> Box {:border-style "round"}
      (->> tabs
           (map (fn [{:keys [name shortcut route]}]
                  (let [is-selected (isa? route-name route)]
                    [:<>
                     [:> Text {:color (when is-selected "yellow")} name]
                     [:> Text {:color "blue"} (str " [" shortcut "]")]])))
           (interpose [:> Text "  |  "])
           (into [:<>]))]
     (case route-name
       ::apps
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items items
                              :item-component app-row
                              :on-activate #(dispatch [:select-app %])}]]
       nil)]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (render))

(defn ^:dev/after-load reload! []
  (render))
