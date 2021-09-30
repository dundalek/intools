(ns intools.spotin.main
  (:require [clojure.string :as str]
            [goog.object :as gobj]
            [ink :refer [Box]]
            [intools.hooks :as hooks]
            [intools.spotin.actions :refer [player-actions playlist-actions tracks-actions]]
            [intools.spotin.app.cofx]
            [intools.spotin.app.events]
            [intools.spotin.app.fx]
            [intools.spotin.app.query :as query :refer [!query-client]]
            [intools.spotin.app.subs]
            [intools.spotin.components.shortcuts-bar :refer [shortcuts-bar]]
            [intools.spotin.containers :as containers]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [react]
            [react-query :refer [QueryClient QueryClientProvider]]
            [reagent.core :as r]))

(defonce !app (atom nil))
(declare render)

(def sidepanel-width "22%")

(def included-in-shortcuts-bar?
  #{:play-pause
    :next
    :previous
    :spotin/devices})

(def shortcuts-bar-actions
  (concat [{:shortcut "x"
            :name "menu"}]
          [{:shortcut "/"
            :name "search"}]
          (->> player-actions
               (filter (fn [{:keys [shortcut id]}]
                         (and (some? shortcut)
                              (included-in-shortcuts-bar? id)))))
          [{:shortcut "q"
            :name "quit"}]))

(defn use-play-pause-mutate []
  (let [play-pause-mutation (query/use-optimistic-mutation {:query-key "player"
                                                            :value-path [:is_playing]
                                                            :mutate-fn spotify/player-play-pause+
                                                            :update-fn not})]
    (react/useCallback (fn []
                         (let [playback (.getQueryData @!query-client "player")]
                           (if (spotify/playback-stopped? playback)
                             (-> (spotify/auto-select-device+)
                                 ;; TODO: show device picker if auto-connect fails
                                 (.then #(spotify/player-play+)))
                             (.mutate play-pause-mutation)))))))

(def volume-path [:device :volume_percent])

(defn volume-up [value]
  (Math/min 100 (+ value 10)))

(defn volume-down [value]
  (Math/max 0 (- value 10)))

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
        seek-forward-mutation (query/use-optimistic-mutation {:query-key "player"
                                                              :value-path [:progress_ms]
                                                              :mutate-fn spotify/player-seek+
                                                              :update-fn #(+ % 10000)})
        seek-backward-mutation (query/use-optimistic-mutation {:query-key "player"
                                                               :value-path [:progress_ms]
                                                               :mutate-fn spotify/player-seek+
                                                               :update-fn #(-> % (- 10000) (Math/max 0))})
        volume-up-mutation (query/use-optimistic-mutation {:query-key "player"
                                                           :value-path volume-path
                                                           :mutate-fn spotify/player-volume+
                                                           :update-fn volume-up})
        volume-down-mutation (query/use-optimistic-mutation {:query-key "player"
                                                             :value-path volume-path
                                                             :mutate-fn spotify/player-volume+
                                                             :update-fn volume-down})
        shuffle-mutation (query/use-optimistic-mutation {:query-key "player"
                                                         :value-path [:shuffle_state]
                                                         :mutate-fn spotify/player-shuffle+
                                                         :update-fn not})
        repeat-mutation (query/use-optimistic-mutation {:query-key "player"
                                                        :value-path [:repeat_state]
                                                        :mutate-fn spotify/player-repeat+
                                                        :update-fn spotify/repeat-state-transition})
        play-pause-mutate (use-play-pause-mutate)
        dispatch-action (fn [{:keys [id event arg] :as action}]
                          (if event
                            (dispatch (conj event arg))
                            (case id
                              :playlist-rename (dispatch [:playlist-rename arg])
                              :playlist-edit-description (dispatch [:playlist-edit-description arg])
                              :playlist-unfollow (dispatch [:spotin/open-confirmation-modal
                                                            {:title "Delete playlist"
                                                             :description (str "Are you sure you want to delete playlist '" (:name arg) "'?")
                                                             :on-submit #(dispatch [:playlist-unfollow (:id arg)])}])
                              :spotin/player-seek-forward (.mutate seek-forward-mutation)
                              :spotin/player-seek-backward (.mutate seek-backward-mutation)
                              :spotin/player-volume-up (.mutate volume-up-mutation)
                              :spotin/player-volume-down (.mutate volume-down-mutation)
                              :shuffle (.mutate shuffle-mutation)
                              :repeat (.mutate repeat-mutation)
                              :play-pause (play-pause-mutate)
                              (dispatch [:run-action action]))))]

    (ink/useInput
     (fn [input key]
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
                                                            "playlists-panel" (concat playlist-actions player-actions)
                                                            "tracks-panel" (concat tracks-actions player-actions)
                                                            player-actions)
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
     [containers/error-alert]
     [containers/confirmation-modal]
     (case (:type active-input-panel)
       :playlist-rename
       [containers/playlist-rename-input-panel (:arg active-input-panel)]
       :playlist-edit-description
       [containers/playlist-edit-description-input-panel (:arg active-input-panel)]
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
     [shortcuts-bar {:actions shortcuts-bar-actions}]]))

(defn app-wrapper []
  [:> QueryClientProvider {:client @!query-client}
   [:f> app]])

(defn render []
  (reset! !app (ink/render (r/as-element [app-wrapper]))))

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
  (reset! !query-client (QueryClient.))
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [app-wrapper])))
