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
    :event [:run-action :play-pause]}
   {:id :next
    :name "next"
    :shortcut "n"
    :event [:run-action :next]}
   {:id :previous
    :name "previous"
    :shortcut "b"
    :event [:run-action :previous]}
   {:id :shuffle
    :name "shuffle"
    :event [:run-action :shuffle]}
   {:id :repeat
    :name "repeat"
    :event [:run-action :repeat]}])

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
    :shortcut "g"
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
    :name "add to Library"}])

(defn library-panel []
  (let [{:keys [is-focused]} (hooks/use-focus)]
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")}
     [:> Text "Panel 1"]]))

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        {:keys [playlists playlist-tracks actions active-input-panel playlist-search-query]} @(subscribe [:db])
        playlists-filtered @(subscribe [:spotin/playlists-filtered])
        actions-filtered @(subscribe [:spotin/actions-filtered])
        actions-search-query @(subscribe [:spotin/actions-search-query])
        current-route @(subscribe [:spotin/current-route])
        focused-component-id (cond
                               actions-search-query "action-menu-search-bar"
                               playlist-search-query "playlist-search-bar"
                               actions "action-menu"
                               active-input-panel "input-bar")
        {:keys [active-focus-id focus-next focus-previous]} (hooks/use-focus-manager {:focus-id focused-component-id})]
    (ink/useInput
     (fn [input _key]
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
               :else (dispatch [:run-action action])))))))
    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
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
          :is-searching (boolean actions-search-query)
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
                             :playlist-unfollow (dispatch [:playlist-unfollow (:id arg)])
                             :open-album (dispatch [:spotin/open-album (-> arg :track :album :id)])
                             (dispatch [:run-action action]))))
          :on-cancel #(dispatch [:close-action-menu])}])
      [:> Box {:width "20%"
               :flex-direction "column"}
       #_[:f> library-panel]
       (when playlist-search-query
         [:f> input-bar {:focus-id "playlist-search-bar"
                         :label "Search playlists:"
                         :on-change #(dispatch [:spotin/set-playlist-search %])
                         :on-cancel (fn []
                                      (focus-next)
                                      (dispatch [:spotin/clear-playlist-search]))}])
       [:f> playlists-panel {:focus-id "playlists-panel"
                             :selected-playlist-id (-> current-route :params :playlist-id)
                             :playlists playlists-filtered
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
          [:f> tracks-panel {:playlist (get playlists playlist-id)
                             :tracks (get playlist-tracks playlist-id)
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
     #_[:f> status-bar]
     [shortcuts-bar {:actions player-actions}]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (rf/dispatch-sync [:spotin/init])
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app]))
  #_(render))

