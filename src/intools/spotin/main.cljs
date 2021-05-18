(ns intools.spotin.main
  (:require [ink :refer [Box Text]]
            [intools.hooks :as hooks]
            [intools.spotin.app.events]
            [intools.spotin.app.fx]
            [intools.spotin.app.subs]
            [intools.spotin.components.action-menu :refer [action-menu]]
            [intools.spotin.components.album-panel :refer [album-panel]]
            [intools.spotin.components.input-bar :refer [input-bar]]
            [intools.spotin.components.playlists-panel :refer [playlists-panel]]
            [intools.spotin.components.shortcuts-bar :refer [shortcuts-bar]]
            [intools.spotin.components.status-bar :refer [status-bar]]
            [intools.spotin.components.tracks-panel :refer [tracks-panel]]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [react]
            [reagent.core :as r]))

(defonce !app (atom nil))
(declare render)

(def action-separator
  {:name ""})

(def player-actions
  [{:id :play-pause
    :name "play/pause"
    :shortcut "z"
    :event [:spotin/dispatch-fx :play-pause]}
   {:id :next
    :name "next"
    :shortcut "n"
    :event [:spotin/dispatch-fx :next]}
   {:id :previous
    :name "previous"
    :shortcut "b"
    :event [:spotin/dispatch-fx :previous]}
   {:id :shuffle
    :name "shuffle"
    :event [:spotin/dispatch-fx :shuffle]}
   {:id :repeat
    :name "repeat"
    :event [:spotin/dispatch-fx :repeat]}])

(def playlist-actions
  [{:id :playlist-open
    :name "open"
    :shortcut "⏎"}
   {:id :playlist-play
    :name "play"}
   {:id :playlist-rename
    :name "rename"}
   {:id :playlist-edit-description
    :name "edit description"}
   #_{:id :playlist-make-public
      :name "make public"}
   {:id :playlist-unfollow
    :name "delete"}
   {:id :playlist-share
    :name "share"}
   action-separator
   {:id :spotin/open-random-playlist
    :name "open random"
    :shortcut "o"
    :event [:spotin/open-random-playlist]}
   {:id :spotin/refresh-playlists
    :name "refresh"
    :shortcut "r"}
   {:id :spotin/start-playlist-search
    :name "search"
    :shortcut "/"
    :event [:spotin/start-playlist-search]}])

(def playlists-actions
  [{:id :playlists-mix
    :name "mix"}])

(def track-actions
  [{:id :open-artist
    :name "open artist"}
   {:id :open-album
    :name "open album"}
   {:id :like
    :name "add to Liked Songs"}
   {:id :add-to-library
    :name "add to Library"}
   {:id :spotin/start-track-search
    :name "search"
    :shortcut "/"
    :event [:spotin/set-track-search ""]}])

(defn library-panel []
  (let [{:keys [is-focused]} (hooks/use-focus)]
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")}
     [:> Text "Panel 1"]]))

(defn confirmation-modal* [{:keys [title description focus-id on-submit on-cancel]}]
  (let [{:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus true})]
    (ink/useInput
     (fn [input ^js key]
       (when is-focused
         (cond
           (or (.-return key) (= input "y")) (when on-submit (on-submit))
           (.-escape key) (when on-cancel (on-cancel))))))
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")
             :flex-direction "column"
             :padding-x 1}
     ;; Ideally title would be over top border
     (when title [:> Box [:> Text {:color "green"} title]])
     (when description [:> Box [:> Text description]])]))

(defn confirmation-modal []
  (when-some [{:keys [on-submit on-cancel] :as opts} @(subscribe [:spotin/confirmation-modal])]
    [:f> confirmation-modal* (assoc opts
                                    :focus-id "confirmation-modal"
                                    :on-submit (fn []
                                                 (dispatch [:spotin/close-confirmation-modal])
                                                 (when on-submit (on-submit)))
                                    :on-cancel (fn []
                                                 (dispatch [:spotin/close-confirmation-modal])
                                                 (when on-cancel (on-cancel))))]))

(defn use-playback-status [set-playback]
  (react/useEffect
   (fn []
     (let [on-interval (fn []
                         (-> (spotify/get-player+)
                             (.then (fn [body]
                                      (let [status (js->clj body :keywordize-keys true)]
                                        (set-playback status))))))
           interval-id (js/setInterval on-interval 5000)]
       (on-interval)
       #(js/clearInterval interval-id)))
   #js []))

(defn playback-status-bar []
  [status-bar {:playback @(subscribe [:spotin/playback-status])
               :pending-requests @(subscribe [:spotin/pending-requests])}])

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        {:keys [playlists actions active-input-panel playlist-search-query track-search-query]} @(subscribe [:db])
        playlists-filtered @(subscribe [:spotin/playlists-filtered])
        actions-filtered @(subscribe [:spotin/actions-filtered])
        actions-search-query @(subscribe [:spotin/actions-search-query])
        current-route @(subscribe [:spotin/current-route])
        tracks-filtered @(subscribe [:spotin/tracks-filtered])
        playback-item-uri @(subscribe [:spotin/playback-item-uri])
        playback-context-uri @(subscribe [:spotin/playback-context-uri])
        confirmation-modal-open? (some? @(subscribe [:spotin/confirmation-modal]))
        focused-component-id (cond
                               confirmation-modal-open? "confirmation-modal"
                               actions "action-menu"
                               active-input-panel "input-bar")
        force-focus (contains? #{"action-menu" "input-bar" "confirmation-modal"} focused-component-id)
        {:keys [active-focus-id focus-next focus-previous]} (hooks/use-focus-manager {:focus-id focused-component-id
                                                                                      :force force-focus})]
    ; (use-playback-status #(dispatch [:spotin/set-playback-status %]))
    (ink/useInput
     (fn [input _key]
       (when-not (or (and (= active-focus-id "action-menu") actions-search-query)
                     (and (= active-focus-id "playlists-panel") playlist-search-query)
                     (and (= active-focus-id "tracks-panel") track-search-query))
         (case input
           "q" (do (.exit app)
                   (.exit js/process))
           "u" (dispatch [:spotin/router-back])
            ; "x" (dispatch (if actions
                            ; [:close-action-menu]
                            ; [:open-action-menu player-actions]))
           (when-some [{:keys [event shortcut] :as action} (->> (case active-focus-id
                                                                  "action-menu" actions
                                                                  "playlists-panel" (concat playlist-actions player-actions)
                                                                  "tracks-panel" (concat track-actions player-actions)
                                                                  player-actions)
                                                                (some (fn [{:keys [shortcut] :as action}]
                                                                        (when (= shortcut input) action))))]
             (let [actions-focused? (= active-focus-id "action-menu")
                   search-action? (and actions-focused? (= shortcut "/"))]
               (when (and actions-focused? (not search-action?))
                 (dispatch [:close-action-menu]))
               (cond
                 ;; override / to search in action menu - this is a bit hacky without event propagation
                 search-action? (dispatch [:spotin/set-actions-search ""])
                 event (dispatch event)
                 :else (dispatch [:run-action action]))))))))
    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
     [confirmation-modal]
     (case (:type active-input-panel)
       :playlist-rename
       (let [{:keys [id name]} (:arg active-input-panel)]
         [:f> input-bar {:focus-id "input-bar"
                         :label (str "Rename playlist '" name "':")
                         :default-value name
                         :on-submit (fn [value]
                                      ;; TODO invalidate current playlist via rf fx
                                      (-> (spotify/playlist-rename+ id value)
                                          (.then #(dispatch [:spotin/refresh-playlists])))
                                      (dispatch [:close-input-panel]))
                         :on-cancel #(dispatch [:close-input-panel])}])
       :playlist-edit-description
       (let [{:keys [id name description]} (:arg active-input-panel)]
         [:f> input-bar {:focus-id "input-bar"
                         :label (str "Edit description for playlist '" name "':")
                         :default-value description
                         :on-submit (fn [value]
                                     ;; TODO invalidate current playlist via rf fx
                                      (-> (spotify/playlist-change-description+ id value)
                                          (.then #(dispatch [:spotin/refresh-playlists])))
                                      (dispatch [:close-input-panel]))
                         :on-cancel #(dispatch [:close-input-panel])}])

       nil)
     [:> Box {:flex-grow 1}
      (when actions
        [:f> action-menu
         {:actions actions-filtered
          :is-searching (some? actions-search-query)
          :on-search-change #(dispatch [:spotin/set-actions-search %])
          :on-search-cancel #(dispatch [:spotin/set-actions-search nil])
          :on-activate (fn [{:keys [id arg event] :as action}]
                         (dispatch [:close-action-menu])
                         (if event
                           (dispatch event)
                           (case id
                             :playlist-open (dispatch [:set-selected-playlist (:id arg)])
                             :playlist-rename (dispatch [:playlist-rename arg])
                             :playlist-edit-description (dispatch [:playlist-edit-description arg])
                             :playlist-unfollow (dispatch [:spotin/open-confirmation-modal
                                                           {:title "Delete playlist"
                                                            :description (str "Are you sure you want to delete playlist '" (:name arg) "'?")
                                                            :on-submit #(dispatch [:playlist-unfollow (:id arg)])}])
                             :open-album (dispatch [:spotin/open-album (-> arg :track :album :id)])
                             (dispatch [:run-action action]))))
          :on-cancel #(dispatch [:close-action-menu])}])
      [:> Box {:width "20%"
               :flex-direction "column"}
       #_[:f> library-panel]
       [:f> playlists-panel {:focus-id "playlists-panel"
                             :selected-playlist-id (-> current-route :params :playlist-id)
                             :playlists playlists-filtered
                             :is-searching (some? playlist-search-query)
                             :playback-context-uri playback-context-uri
                             :on-search-change #(dispatch [:spotin/set-playlist-search %])
                             :on-search-cancel #(dispatch [:spotin/clear-playlist-search])
                             :on-menu (fn [playlist playlist-ids]
                                        (let [playlist-actions (map #(assoc % :arg playlist) playlist-actions)
                                              selected-playlists (map #(get playlists %) playlist-ids)
                                              playlists-actions (when (seq playlist-ids)
                                                                  (map #(assoc % :arg selected-playlists) playlists-actions))
                                              actions (concat playlist-actions
                                                              playlists-actions
                                                              [action-separator]
                                                              player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [{:keys [id]}]
                                            (dispatch [:set-selected-playlist id])
                                            ;; Try to focus the tracks panel after playlist selected, it is a bit brittle
                                            (focus-next))}]]
      (case (:name current-route)
        :playlist
        (let [{:keys [playlist-id]} (:params current-route)]
          ^{:key playlist-id}
          [:f> tracks-panel {:focus-id "tracks-panel"
                             :playlist (get playlists playlist-id)
                             :tracks tracks-filtered
                             :is-searching (some? track-search-query)
                             :playback-item-uri playback-item-uri
                             :on-search-change #(dispatch [:spotin/set-track-search %])
                             :on-search-cancel #(dispatch [:spotin/set-track-search nil])
                             :on-menu (fn [item]
                                        (let [track-actions (map #(assoc % :arg item) track-actions)
                                              actions (concat track-actions [action-separator] player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [item]
                                            (let [playlist (get playlists playlist-id)]
                                              (spotify/player-play+
                                               {:context_uri (:uri playlist)
                                                :offset {:uri (-> item :track :uri)}})))}])
                                                     ;;:uris [(:uri track)]})))}]]
        :album
        (let [{:keys [album-id]} (:params current-route)]
          [:f> album-panel {:album @(subscribe [:spotin/album-by-id album-id])}])
        nil)]
     [playback-status-bar]
     [shortcuts-bar {:actions player-actions}]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (set! spotify/*before-request-callback* #(dispatch [:spotin/request-started]))
  (set! spotify/*after-request-callback* #(dispatch [:spotin/request-finished]))
  (rf/dispatch-sync [:spotin/init])
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app])))

