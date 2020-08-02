(ns intools.dkmsin.main
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [selectable-list action-bar]]
            ; [intools.hooks :refer [use-fullscreen]]
            [intools.dkmsin.model :as model]
            [ink-text-input :refer [UncontrolledTextInput]]))
            ; [ink-select-input :refer [default] :rename {default SelectInput}]))

(defonce !app (atom nil))
(declare render)

(def spawn (.-spawn (js/require "child_process")))

(def tabs
  [{:name "Modules"
    :shortcut "1"
    :route ::module-list}
   {:name "Kernels"
    :shortcut "2"
    :route ::kernel-list}
   {:name "All instances"
    :shortcut "3"
    :route ::instance-list}])

(def global-actions
  [{:name "Select"
    :shortcut-label "enter][→"}
   ; {:name "Search"
   ;  :shortcut "/"}
   {:name "Add module"
    :shortcut "m"}
   {:name "Autoinstall"
    :shortcut "i"}
   {:name "Nav"
    :shortcut-label "↑↓"}
   {:name "Quit"
    :shortcut "ctrl+c"}])
   ;; Help

(def actions
  [{:id "install"
    :name "Install"
    :shortcut "i"}
   {:id "remove"
    :name "Remove"
    :shortcut "d"}
   {:id "build"
    :name "Build"
    :shortcut "b"}
   {:id "uninstall"
    :name "Uninstall"
    :shortcut "u"}
   {:id "export"
    :name "Export..."
    :shortcut "e"}])
   ;; Show source directory or open shell in it?

(def export-actions
  [{:id "mkdriverdisk"}
   {:id "mktarball"}
   {:id "mkrpm"}
   {:id "mkdev"}
   {:id "mkbmdeb"}
   {:id "mkdsc"}
   {:id "mkkmp"}])

;; Routes


(derive ::module-actions ::module-list)
(derive ::module-export-actions ::module-list)
(derive ::module-add ::module-list)
;
; ::kernel-list
;
; ::instance-list
; ::instance-actions
; ::instance-export-actions


(defn init-state []
  {:route {:name ::module-list}
   :modules (model/list-items)})

(defmulti reducer (fn [_state [event-name]] event-name))

; (defmethod reducer :next-tab [state [_]]
;   (update state :tab-index (fn [index] (mod (inc index) (count tabs)))))

(defmethod reducer :navigate [state [_ route]]
  (assoc state :route route))

(defmethod reducer :select-module [state [_ module]]
  (assoc state :route {:name ::module-actions
                       :params {:module module}}))

;; useReducer does not work with multimethods
(defn reducer-fn [state event]
  (reducer state event))

(defn instance-row [{:keys [module module-version kernel-version arch status is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} module]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "blue" :wrap "truncate-end" :bold is-selected} module-version]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} kernel-version]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} arch]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "red" :wrap "truncate-end" :bold is-selected} status]])

(defn group-modules [modules]
  (->> modules
       (group-by :module)
       (map (fn [[module versions]]
              (let [module-versions (->> versions (map :module-version) (distinct) (sort #(compare %2 %1)))
                    kernel-versions (->> versions (map :kernel-version) (distinct) (sort #(compare %2 %1)))
                    archs (->> versions (map :arch) (distinct))
                    statuses (->> versions (map :status) (distinct))]
                {:module module
                 :module-versions module-versions
                 :kernel-versions kernel-versions
                 :arch archs
                 :statuses statuses})))
       (sort-by :module)))

(defn module-row [{:keys [module module-versions kernel-versions archs statuses is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} module]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "blue" :wrap "truncate-end" :bold is-selected} (first module-versions)]
   (when (> (count module-versions) 1)
     [:> Text {:color "blue" :wrap "truncate-end" :bold is-selected} (str " (" (count module-versions) ")")])
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (first kernel-versions)]
   (when (> (count kernel-versions) 1)
     [:> Text {:wrap "truncate-end" :bold is-selected} (str " (" (count kernel-versions) ")")])
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (str/join ", " archs)]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "red" :wrap "truncate-end" :bold is-selected} (str/join ", " statuses)]])

(defn group-kernels [modules]
  (->> modules
       (group-by :kernel-version)
       (map (fn [[kernel-version items]]
              (let [modules (->> items (map :modules) (distinct) (sort))
                    archs (->> items (map :arch) (distinct))
                    statuses (->> items (map :status) (distinct))]
                {:kernel-version kernel-version
                 :modules modules
                 :archs archs
                 :statuses statuses})))
       (sort-by :kernel-version #(compare %2 %1))))

(defn kernel-row [{:keys [kernel-version modules archs statuses is-selected]}]
  [:> Box
   [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} kernel-version]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (str (count modules) " module(s)")]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:wrap "truncate-end" :bold is-selected} (str/join ", " archs)]
   [:> Text " "]
   [:> ink/Spacer]
   [:> Text {:color "red" :wrap "truncate-end" :bold is-selected} (str/join ", " statuses)]])

(defn run-sh [cmd & args]
  (.clear @!app)
  (.unmount @!app)
  (js/console.log "\nRunning...\n")
  (println cmd args)
  (render)
  #_(doto (spawn cmd (to-array args)
                 #js {:stdio #js ["ignore" "inherit" "inherit"]})
      (.on "close" (fn [code]
                     (js/console.log "\ndone" code)
                     (render)))
      (.on "error" (fn [err]
                     (js/console.log "error" err)))))

(defn run-command [{:keys [id] :as _action} {:keys [module module-versions]}]
  (run-sh "sudo" "dkms" id module "-v" (first module-versions)))
    ;; what if more moudule versions?
    ;; or --all


(defn action-row [{:keys [name shortcut is-selected]}]
  [:> Box
   [:> Text {:color (when is-selected "blue") :bold is-selected} (str "[" shortcut "] ")]
   [:> Text {:color (when is-selected "blue") :bold is-selected} name]])

(defn action-menu [{:keys [on-activate on-cancel]}]
  ; (let [is-focused (.-isFocused (ink/useFocus))])
  [:<>
   ; [:> Box {:border-style "round"}]
   [:> Box {:margin-x 1}
    [:f> selectable-list {:items actions
                          :item-component action-row
                          :on-activate on-activate
                          :on-cancel on-cancel}]]])

(defn demo []
  ; (use-fullscreen)
  (let [[{:keys [route modules]} dispatch] (react/useReducer reducer-fn nil init-state)
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
       (if-let [route (some (fn [{:keys [shortcut route]}]
                              (when (= shortcut input)
                                route))
                            tabs)]
         (dispatch [:navigate {:name route}])
         (case input
           "m" (dispatch [:navigate {:name ::module-add}])
           "i" (run-sh "sudo" "dkms" "autoinstall")
           nil))))
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
                                             (dispatch [:select-module module]))}]]
       ::kernel-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items (group-kernels modules)
                              :item-component kernel-row}]]
       ::instance-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items modules
                              :item-component instance-row}]]
       ::module-actions
       (let [selected-module (-> route :params :module)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}
          [:> Text (:module selected-module)]
          [action-menu {:on-activate (fn [action]
                                       (if (= (:id action) "export")
                                         (dispatch [:navigate {:name ::module-export-actions
                                                               :params {:module selected-module}}])
                                         (run-command action selected-module)))
                        :on-cancel (fn []
                                     (dispatch [:navigate {:name ::module-list}]))}]])
       ::module-add
       [:> Box
        [:> Box {:margin-right 1}
         [:> Text "Module path:"]]
        [:> UncontrolledTextInput {:on-submit (fn [value]
                                                (run-sh "sudo" "dkms" "add" value))}]]

       ::module-export-actions
       [:> Box {:margin-x 1}
        [:f> selectable-list {:items export-actions
                              :item-component
                              (fn [{:keys [id is-selected]}]
                                [:> Box
                                 [:> Text {:color (when is-selected "blue") :bold is-selected} id]])
                              :on-activate (fn [{:keys [id]}]
                                             (run-sh "sudo" "dkms" id))
                              :on-cancel #(dispatch [:navigate {:name ::module-list}])}]]
       nil)

     [action-bar global-actions]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> demo]))))

(defn -main []
  ; (swap! !app assoc :modules modules)
  (render))

(defn ^:dev/after-load reload! []
  (render))
