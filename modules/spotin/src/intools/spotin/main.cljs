(ns intools.spotin.main
  (:require
   [clojure.string :as str]
   [goog.object :as gobj]
   [ink :refer [Box]]
   [intools.hooks :as hooks]
   [intools.spotin.actions :as actions]
   [intools.spotin.app.cofx]
   [intools.spotin.app.events]
   [intools.spotin.app.fx]
   [intools.spotin.app.subs]
   [intools.spotin.containers :as containers]
   [intools.spotin.infrastructure.query-client :as query-client]
   [intools.spotin.infrastructure.terminal-title :as terminal-title]
   [intools.spotin.model.spotify :as spotify]
   [re-frame.core :as rf :refer [dispatch subscribe]]
   [react]
   [reagent.core :as r]))

(defonce !app (atom nil))

(def sidepanel-width "22%")

(defn app-title []
  (let [{:keys [item]} (:data @(subscribe [:spotin/player]))
        title (str (when item
                     (str (:name item)
                          "  ·  "
                          (->> item :artists
                               (map :name)
                               (str/join ", "))
                          "  ·  "))
                   "spotin")]
    (terminal-title/use-terminal-title title)
    nil))

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        actions @(subscribe [:spotin/actions])
        playlist-search-query @(subscribe [:spotin/playlist-search-query])
        active-input-panel @(subscribe [:spotin/active-input-panel])
        actions-search-query @(subscribe [:spotin/actions-search-query])
        track-search-query @(subscribe [:spotin/track-search-query])
        current-route @(subscribe [:spotin/current-route])
        confirmation-modal-open? (some? @(subscribe [:spotin/confirmation-modal]))
        devices-menu-open? @(subscribe [:spotin/devices-menu])
        focused-component-id (cond
                               @(subscribe [:spotin/error]) "error-alert"
                               confirmation-modal-open? "confirmation-modal"
                               (or actions devices-menu-open?) "action-menu"
                               active-input-panel "input-bar")
        force-focus (not= focused-component-id "error-alert")
        {:keys [active-focus-id focus-next focus-previous]} (hooks/use-focus-manager {:focus-id focused-component-id
                                                                                      :force force-focus})
        dispatch-action (fn [{:keys [event arg] :as _action}]
                          (assert (vector? event))
                          (dispatch (conj event arg)))]

    (ink/useInput
     (fn [input key]
       ;; only handle globally when no input is focused, this is a bit hacky since ink's focus system is a bit limited
       (when-not (or (= active-focus-id "input-bar")
                     (and (= active-focus-id "action-menu") actions-search-query)
                     (and (= active-focus-id "playlists-panel") playlist-search-query)
                     (and (= active-focus-id "tracks-panel") track-search-query))
         ;; when no menu/input component has focus we can make escape key to go to previous view
         (when (and (or (not focused-component-id)
                        (not= focused-component-id active-focus-id))
                    (.-escape key))
           (dispatch [:spotin/router-back]))
         (case input
           "q" (do (.exit app)
                   (.exit js/process))
           (when-some [{:keys [shortcut] :as action} (->> (case active-focus-id
                                                            "action-menu" actions
                                                            "playlists-panel" (concat actions/playlist-actions actions/player-actions)
                                                            "tracks-panel" (concat actions/tracks-actions actions/player-actions)
                                                            actions/player-actions)
                                                          (some (fn [{:keys [shortcut] :as action}]
                                                                  (when (= shortcut input) action))))]
             (let [actions-focused? (= active-focus-id "action-menu")
                   search-action? (and actions-focused? (= shortcut "/"))]
               (when (and actions-focused? (not search-action?))
                 (dispatch [:close-action-menu]))
               ;; override / to search in action menu - this is a bit hacky without event propagation
               (if search-action?
                 (dispatch [:spotin/set-actions-search ""])
                 (dispatch-action action))))))))

    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
     [:f> app-title]
     [containers/error-alert]
     [containers/confirmation-modal]
     (case (:type active-input-panel)
       :playlist-rename
       [:f> containers/playlist-rename-input-panel (:arg active-input-panel)]
       :playlist-edit-description
       [:f> containers/playlist-edit-description-input-panel (:arg active-input-panel)]
       nil)
     [:> Box {:flex-grow 1}
      (when actions
        [containers/action-menu-panel {:on-activate dispatch-action}])
      (if devices-menu-open?
        [:f> containers/devices-menu {:width sidepanel-width}]
        [:> Box {:width sidepanel-width
                 :flex-direction "column"}
         #_[:f> library-panel]
         [:f> containers/playlists-panel]])
      (case (:name current-route)
        :playlist [:f> containers/playlist-tracks-panel (-> current-route :params :playlist-id)]
        :album [:f> containers/album-tracks-panel (-> current-route :params :album-id)]
        :artist [:f> containers/artist-panel (-> current-route :params :artist-id)]
        nil)]
     [:f> containers/playback-status-bar]
     [containers/shortcuts-bar]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (when-some [missing-credentials (->> ["SPOTIFY_CLIENT_ID" "SPOTIFY_CLIENT_SECRET" "SPOTIFY_REFRESH_TOKEN"]
                                       (filter #(str/blank? (-> js/process .-env (gobj/get %))))
                                       (seq))]
    (doseq [x missing-credentials]
      (println "Missing" x))
    (println "\nPlease refer to the README for configuration instructions.")
    (.exit js/process))

  (set! spotify/*before-request-callback* #(dispatch [:spotin/request-started]))
  (set! spotify/*after-request-callback* #(dispatch [:spotin/request-finished]))
  (set! spotify/*request-error-callback* (fn [error request]
                                           (dispatch [:spotin/request-failed error request])))
  (rf/dispatch-sync [:spotin/init])
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app])))
