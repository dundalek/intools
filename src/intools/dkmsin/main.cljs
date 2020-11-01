(ns intools.dkmsin.main
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.views :refer [selectable-list action-bar uncontrolled-text-input]]
            [intools.shell :refer [sh]]
            ; [intools.hooks :refer [use-fullscreen]]
            [intools.dkmsin.actions :as actions]
            [intools.dkmsin.model :as model]))
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

(def command-input-actions
  [{:name "Run"
    :shortcut-label "enter"}
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
  (->>
   [{:id "mkdriverdisk"
     :shortcut "1"
     :args "mkdriverdisk [-d distro] [-r release] [--media mediatype] [-k kernel/arch] [module/version]"}
    {:id "mktarball"
     :shortcut "2"
     :args "mktarball [module/module-version] [-k kernel/arch] [--archive /path/to/tarball.tar] [--source-only] [--binaries-only]"}
    {:id "mkrpm"
     :shortcut "3"
     :args "mkrpm [module/module-version] [-k kernel/arch] [--source-only] [--binaries-only]"}
    {:id "mkdeb"
     :shortcut "4"}
    {:id "mkbmdeb"
     :shortcut "5"}
    {:id "mkdsc"
     :shortcut "6"}
    {:id "mkkmp"
     :shortcut "7"
     :args "mkkmp [module/module-version] [--spec specfile]"}]
   (mapv (fn [{:keys [id] :as item}]
           (assoc item :name id)))))

(def action-add
  {:id "add"
   :args "add [module/module-version] [/path/to/source-tree] [/path/to/tarball.tar]"})

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

(defn with-terminal-output [f]
  (let [^js/InkInstance app @!app]
    (.clear app)
    (.unmount app))
  (f)
  (render))

(defn run-sh [& args]
  (apply println (cons "\nRunning command:\n " args))
  (apply sh args))

(defn run-fx! [[fx arg]]
  (case fx
    :sh (with-terminal-output
          #(apply run-sh arg))))

(defn command-params [{:keys [command module module-version kernel-version arch]}]
  ["sudo" "dkms" command (str module "/" module-version) "-k" (str kernel-version "/" arch)])

(defn run-module-command [{:keys [id] :as _action} instances]
  (with-terminal-output
    #(doseq [instance instances]
       (apply run-sh (command-params (assoc instance :command id))))))

(defn action-row [{:keys [name shortcut]} {:keys [is-selected]}]
  [:> Box
   [:> Text {:color (when is-selected "blue") :bold is-selected} (str "[" shortcut "] ")]
   [:> Text {:color (when is-selected "blue") :bold is-selected} name]])

(defn action-menu [{:keys [actions on-activate on-cancel]}]
  ; (let [is-focused (.-isFocused (ink/useFocus))])
  [:<>
   ; [:> Box {:border-style "round"}]
   [:> Box {:margin-x 1}
    [:f> selectable-list {:items actions
                          :item-component action-row
                          :on-activate on-activate
                          :on-cancel on-cancel
                          :on-input (fn [input _key]
                                      (some->> actions
                                               (some (fn [{:keys [shortcut] :as action}]
                                                       (when (= shortcut input)
                                                         action)))
                                               (on-activate)))}]]])

(defn app []
  ; (use-fullscreen)
  (let [[{:keys [navigation-stack modules]} dispatch] (react/useReducer reducer-fn nil init-state)
        route (peek navigation-stack)
        route-name (:name route)
        focus-manager (ink/useFocusManager)
        handle-action-selected (fn [{:keys [action module-name instances]}]
                                 (cond
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

                                   (:args action)
                                   (dispatch [:navigate {:name ::command-input
                                                         :params {:command-text (str/join " " (command-params (assoc (first instances)
                                                                                                                     :command (:id action))))
                                                                  :args (:args action)}}])

                                   :else
                                   (run-module-command action instances)))]
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
           "m" (dispatch [:navigate {:name ::command-input
                                     :params {:command-text "sudo dkms add"
                                              :args (:args action-add)}}])
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

       ::command-input
       (let [{:keys [command-text args]} (:params route)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}

          [:> Text "Edit your command and press [enter] to run:"]
          [:> Box
           [:> Text "$ "]
           [:f> uncontrolled-text-input {:on-submit (fn [s]
                                                      (with-terminal-output
                                                        #(apply run-sh (-> s (str/trim) (str/split #"\s+")))))
                                         :on-cancel #(dispatch [:navigate-back])
                                         :default-value (str command-text " ")}]]
          [:> Box {:margin-top 1}
           [:> Text "Available options:"]]
          [:> Box {:margin-left 2}
           [:> Text args]]])

       ::module-actions
       (let [{:keys [module-name instances]} (:params route)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}
          [:> Box
           [:> Text "Select action for: "]
           [:> Text {:color "green"} module-name]]
          [action-menu {:actions actions
                        :on-activate (fn [action]
                                       (if (= (:id action) "export")
                                         (dispatch [:navigate {:name ::module-export-actions
                                                               :params (:params route)}])
                                         (handle-action-selected {:action action
                                                                  :module-name module-name
                                                                  :instances instances})))
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
       (let [{:keys [module-name instances]} (:params route)]
         [:> Box {:margin-x 1
                  :flex-direction "column"}
          [:> Box
           [:> Text "Select action for: "]
           [:> Text {:color "green"} module-name]]
          [action-menu {:actions export-actions
                        :on-activate (fn [action]
                                       (handle-action-selected {:action action
                                                                :module-name module-name
                                                                :instances instances}))
                        :on-cancel #(dispatch [:navigate-back])}]])

       nil)

     [action-bar
      (case route-name
        (::module-actions ::kernel-version-list) selectable-list-actions
        ::command-input command-input-actions

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
