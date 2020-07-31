(ns intools.flatpakin.main
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [selectable-list]]
            [intools.flatpakin.model :as model]))

(defonce !app (atom nil))
(declare render)

(def tabs
  [{:name "Apps"
    :shortcut "1"
    :route ::app-list}
   {:name "Runtimes"
    :shortcut "2"
    :route ::runtime-list}
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

;; Permissions


; (def all (concat apps runtimes))


; name,application,version,branch,installation


(defn app []
  (let [route-name nil]
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
       ::module-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items (group-modules modules)
                              :item-component module-row
                              :on-activate (fn [module]
                                             (dispatch [:select-module module]))}]])]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (render))

(defn ^:dev/after-load reload! []
  (render))
