(ns intools.dkmsin.main
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [selectable-list action-bar]]
            [intools.shell :refer [sh]]
            ; [intools.hooks :refer [use-fullscreen]]
            [intools.dkmsin.actions :as actions]
            [intools.dkmsin.model :as model]
            [ink-text-input :refer [UncontrolledTextInput]]))
            ; [ink-select-input :refer [default] :rename {default SelectInput}]))

(defonce !app (atom nil))
(declare render)

(def tabs
  [{:name "Modules"
    :shortcut "1"
    :route ::module-list}
   {:name "Kernels"
    :shortcut "2"
    :route ::kernel-list}
   {:name "All"
    :shortcut "3"
    :route ::instance-list}])

(def global-actions
  [{:name "Select"
    :shortcut-label "enter"}
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

(def selectable-list-actions
  [{:name "Select"
    :shortcut-label "enter"}
   {:name "Nav"
    :shortcut-label "↑↓"}
   {:name "Back"
    :shortcut-label "esc"}
   {:name "Quit"
    :shortcut "ctrl+c"}])

(def actions
  [{:id "build"
    :name "Build"
    :shortcut "b"}
   {:id "install"
    :name "Install"
    :shortcut "i"}
   {:id "uninstall"
    :name "Uninstall"
    :shortcut "u"}
   {:id "remove"
    :name "Remove"
    :shortcut "d"}
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

(defn init-state []
  {:navigation-stack (list {:name ::module-list})
   :modules (model/list-items)})

(defmulti reducer (fn [_state [event-name]] event-name))

; (defmethod reducer :next-tab [state [_]]
;   (update state :tab-index (fn [index] (mod (inc index) (count tabs)))))

(defmethod reducer :navigate [state [_ route]]
  (update state :navigation-stack conj route))

(defmethod reducer :navigate-replace [state [_ route]]
  (assoc state :navigation-stack (list route)))

(defmethod reducer :navigate-back [state]
  (let [next-stack (-> state :navigation-stack pop)]
    (cond-> state
      (seq next-stack) (assoc :navigation-stack next-stack))))

(defmethod reducer :select-module [state [_ {:keys [module-name instances]}]]
  (update state :navigation-stack conj {:name ::module-actions
                                        :params {:module-name module-name
                                                 :instances instances}}))

;; useReducer does not work with multimethods
(defn reducer-fn [state event]
  (reducer state event))

(defn instance-row [{:keys [module module-version kernel-version arch status]} {:keys [is-selected]}]
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

(defn distinct-vals [f coll]
  (->> coll (map f) (distinct) (sort)))

(defn group-modules [modules]
  (->> modules
       (group-by :module)
       (map (fn [[module items]]
              {:module module
               :module-versions (reverse (distinct-vals :module-version items))
               :kernel-versions (reverse (distinct-vals :kernel-version items))
               :arch (distinct-vals :arch items)
               :statuses (distinct-vals :status items)}))
       (sort-by :module)))

(defn module-row [{:keys [module module-versions kernel-versions archs statuses]} {:keys [is-selected]}]
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
              {:kernel-version kernel-version
               :modules (distinct-vals :module items)
               :archs (distinct-vals :arch items)
               :statuses (distinct-vals :status items)}))
       (sort-by :kernel-version #(compare %2 %1))))

(defn kernel-row [{:keys [kernel-version modules archs statuses]} {:keys [is-selected]}]
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

(defn run-sh [& args]
  (let [^js/InkInstance app @!app]
    (.clear app)
    (.unmount app))
  (apply println (cons "\nRunning command:\n " args))
  (apply sh args)
  (render))

(defn run-fx! [[fx arg]]
  (case fx
    :sh (apply run-sh arg)))

(defn run-module-command [{:keys [id] :as _action} instances]
  (assert (->> instances (map :module) (distinct) (count) (= 1)))
  (assert (->> instances (map :module-version) (distinct) (count) (= 1)))
  (let [{:keys [module module-version]} (first instances)]
    (apply run-sh (concat ["sudo" "dkms" id
                           "-m" (str module "/" module-version)]
                          (mapcat (fn [{:keys [kernel-version arch]}]
                                    ["-k" (str kernel-version "/" arch)])
                                  instances)))))

(defn action-row [{:keys [name shortcut]} {:keys [is-selected]}]
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

(defn app []
  ; (use-fullscreen)
  (let [[{:keys [navigation-stack modules]} dispatch] (react/useReducer reducer-fn nil init-state)
        route (peek navigation-stack)
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
         (dispatch [:navigate-replace {:name route}])
         (case input
           "m" (dispatch [:navigate {:name ::module-add}])
           "i" (run-fx! (actions/autoinstall))
           nil))))
    [:> Box {:flex-direction "column"}
     [:> Box {:border-style "round"}
      (->> tabs
           (map (fn [{:keys [name shortcut route]}]
                  (let [is-selected (some #(= (:name %) route) navigation-stack)]
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
                              :on-activate (fn [{:keys [module]}]
                                             (dispatch [:select-module {:module-name module
                                                                        :instances (->> modules
                                                                                        (filter #(= module (:module %))))}]))}]]

       ::kernel-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items (group-kernels modules)
                              :item-component kernel-row
                              :on-activate (fn [{:keys [kernel-version]}]
                                             (dispatch [:navigate {:name ::kernel-module-list
                                                                   :params {:instances (->> modules
                                                                                            (filter #(= kernel-version (:kernel-version %))))}}]))}]]

       ::instance-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items modules
                              :item-component instance-row
                              :on-activate (fn [{:keys [module] :as selected}]
                                             (dispatch [:select-module {:module-name module
                                                                        :instances [selected]}]))}]]

       ::module-add
       [:> Box
        [:> Box {:margin-right 1}
         [:> Text "Module path:"]]
        [:> UncontrolledTextInput {:on-submit #(run-fx! (actions/add %))}]]

       ::module-actions
       (let [{:keys [module-name instances]} (:params route)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}
          [:> Box
           [:> Text "Select action for: "]
           [:> Text {:color "green"} module-name]]
          [action-menu {:on-activate (fn [action]
                                       (cond
                                         (= (:id action) "export")
                                         (dispatch [:navigate {:name ::module-export-actions
                                                               :params (:params route)}])

                                         (< 1 (count (distinct-vals :module-version instances)))
                                         (dispatch [:navigate {:name ::module-version-list
                                                               :params {:action action
                                                                        :module-name module-name
                                                                        :instances instances}}])

                                         (< 1 (count instances))
                                         (dispatch [:navigate {:name ::kernel-version-list
                                                               :params {:action action
                                                                        :module-name module-name
                                                                        :module-version (-> instances first :module-version)
                                                                        :instances instances}}])

                                         :else
                                         (run-module-command action instances)))
                        :on-cancel #(dispatch [:navigate-back])}]])

       ::module-version-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:> Text "TODO: Select module version"]]

       ::kernel-version-list
       (let [{:keys [action module-name module-version instances]} (:params route)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}
          [:> Box
           [:> Text "Select kernel to "]
           [:> Text {:color "red"} (:id action)]
           [:> Text " "]
           [:> Text {:color "green"} module-name]
           [:> Text " "]
           [:> Text module-version]]
          [:f> selectable-list
           {:items (cons ::all-kernels instances)
            :item-component (fn [{:keys [kernel-version arch status] :as item} {:keys [is-selected]}]
                              (if (= item ::all-kernels)
                                [:> Box
                                 [:> Text {:wrap "truncate-end" :bold is-selected} (str "All kernels (" (count instances) ")")]]
                                [:> Box
                                 [:> Text {:color "green" :wrap "truncate-end" :bold is-selected} kernel-version]
                                 [:> Text " "]
                                 [:> ink/Spacer]
                                 [:> Text {:wrap "truncate-end" :bold is-selected} arch]
                                 [:> Text " "]
                                 [:> ink/Spacer]
                                 [:> Text {:color "red" :wrap "truncate-end" :bold is-selected} status]]))
            :on-cancel #(dispatch [:navigate-back])
            :on-activate (fn [item]
                           (run-module-command action (if (= item ::all-kernels)
                                                        instances
                                                        [item])))}]])

       ::kernel-module-list
       [:> Box {:margin-x 1
                :flex-direction "column"}
        [:f> selectable-list {:items (group-modules (-> route :params :instances))
                              :item-component module-row
                              :on-activate (fn [{:keys [module]}]
                                             (dispatch [:select-module {:module-name module
                                                                        :instances (->> route :params :instances
                                                                                        (filter #(= module (:module %))))}]))}]]

       ::module-export-actions
       [:> Box {:margin-x 1}
        [:f> selectable-list {:items export-actions
                              :item-component
                              (fn [{:keys [id]} {:keys [is-selected]}]
                                [:> Box
                                 [:> Text {:color (when is-selected "blue") :bold is-selected} id]])
                              :on-activate (fn [{:keys [id]}]
                                             ;; TODO
                                             #_(run-sh "sudo" "dkms" id))
                              :on-cancel #(dispatch [:navigate-back])}]]

       nil)

     [action-bar
      (case route-name
        (::module-actions ::kernel-version-list) selectable-list-actions

        global-actions)]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  ; (swap! !app assoc :modules modules)
  (render))

(defn ^:dev/after-load reload! []
  (render))

(comment

  (shadow/repl :dkmsin)

  (group-kernels (model/list-items)))
