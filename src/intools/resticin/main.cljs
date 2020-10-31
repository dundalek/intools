(ns intools.resticin.main
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [action-bar selectable-list]]
            [intools.resticin.model :as model])
  (:import (goog.i18n DateTimeFormat)))

(defonce !app (atom nil))
(declare render)

(def date-format (DateTimeFormat. "y-MM-dd HH:mm:ss"))

(def tabs
  [{:name "Snapshots"
    :shortcut "1"
    :route ::snapshots}
   {:name "Files"
    :shortcut "2"
    :route ::files}
   ;; should keys be top-level or under settings?
   {:name "Keys"
    :shortcut "3"
    :route ::keys}
   ;; Health, Options, Setting, Misc
   {:name "Health"
    :shortcut "4"
    :route ::health}
   {:name "Objects"
    :shortcut "5"
    :route ::objects}])
   ;;   objects - list + cat
   ;; [blobs|packs|index|snapshots|keys|locks] [flags]


(def global-actions
  [{:id :backup
    :name "Backup"
    :shortcut "b"}
   {:id :mount
    :name "Mount"
    :shortcut "m"}
     ; mount         Mount the repository}
   {:id :forget
    :name "Forget"
    :shortcut "d"}
   {:id :version
    :name "Version"
    :shortcut "v"}])

(def snapshot-actions
  [{:name "Restore"}
   {:name "Files"} ;; do find under list with /
   {:name "Diff"}
   {:name "Tags"}
   {:name "Forget"}
   {:name "Stats"}])

(def health-actions
  [{:name "Stats"}
   {:name "Cache"}
   {:name "Cache Cleanup"}
   {:name "Check Errors"}
   {:name "Prune"}
   {:name "Unlock"}
   {:name "Unlock All"}
   {:name "Migrate"}
   {:name "Rebuild Index"}])

(defn init-state []
  {:route {:name ::snapshots}

   :items (model/list-snapshots)})
(defmulti reducer (fn [_state [event-name]] event-name))

(defmethod reducer :navigate [state [_ route]]
  (-> (case (:name route)
        ::snapshots (assoc state :items (model/list-snapshots))
        ::keys (assoc state :items (model/list-keys))
        state)
      (assoc :route route)))

(defmethod reducer :select-snapshot [state [_ item]]
  (assoc state :route {:name ::snapshot-actions
                       :params {:snapshot item}}))

;; useReducer does not work with multimethods
(defn reducer-fn [state event]
  (reducer state event))

(derive ::snapshot-actions ::snapshots)

(defn snapshot-row [{:keys [short-id time hostname tags paths is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} short-id]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (.format date-format time)]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} hostname]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (str/join ", " tags)]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (str/join ", " paths)]])

(defn key-row [{:keys [id user-name host-name created current is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} (if current "*" " ")]
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} id]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} user-name]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} host-name]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (.format date-format created)]])

(defn app []
  (let [[{:keys [route items]} dispatch] (react/useReducer reducer-fn nil init-state)
        route-name (:name route)]
        ; focus-manager (ink/useFocusManager)]
    ;; Switch focus next to switch focus from the global key handling to the module list
    ; (react/useEffect
    ;  (fn []
    ;    (.focusNext focus-manager)
    ;    js/undefined)
    ;  #js [])
    (ink/useInput
     (fn [input _key]
       (if-let [route (some (fn [{:keys [shortcut route]}]
                              (when (= shortcut input)
                                route))
                            tabs)]
         (dispatch [:navigate {:name route}])
         (when-let [id (some (fn [{:keys [shortcut id]}]
                               (when (= shortcut input)
                                 id))
                             global-actions)]
           (case id
             :backup (dispatch [:navigate {:name ::backup}])
             :forget (dispatch [:navigate {:name ::forget}])
             nil)))))
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
       ::snapshots
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items items
                              :item-component snapshot-row
                              :on-activate #(dispatch [:select-snapshot %])}]]
       ::snapshot-actions
       [:> Box {:margin-x 1}
        [:f> selectable-list {:items snapshot-actions
                              :item-component
                              (fn [{:keys [name is-selected]}]
                                [:> Box
                                 [:> Text {:color (when is-selected "blue") :bold is-selected} name]])
                              :on-activate (fn [])
                              :on-cancel #(dispatch [:navigate {:name ::snapshots}])}]]
       ::keys
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items items
                              :item-component key-row
                              :on-activate #(dispatch [:select-key %])}]]

       ::health
       [:> Box {:margin-x 1}
        [:f> selectable-list {:items health-actions
                              :item-component
                              (fn [{:keys [name is-selected]}]
                                [:> Box
                                 [:> Text {:color (when is-selected "blue") :bold is-selected} name]])
                              :on-activate (fn [])
                              :on-cancel (fn [])}]]

       ::backup
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:> Text "TODO Backup screen"]]
           ; list of files --files-from
           ; files from file
           ;
           ; -- exclude
           ;
           ; --host
           ; -- tag
           ;
           ; by default could prefil with files from last snap shot]
       ::forget
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:> Text "TODO Forget screen"]]

       ;;       + automatically call prune
       nil)
     [action-bar global-actions]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (render))

(defn ^:dev/after-load reload! []
  (render))
