(ns intools.spotin.main
  (:require [reagent.core :as r]
            [react]
            [ink :refer [Box Text]]
            [intools.spotin.model.spotify :as spotify]
            [intools.spotin.model.playlist :as playlist]
            [intools.hooks :as hooks]
            [intools.spotin.components.action-menu :refer [action-menu]]
            [intools.spotin.components.input-bar :refer [input-bar]]
            [intools.spotin.components.playlists-panel :refer [playlists-panel]]
            [intools.spotin.components.shortcuts-bar :refer [shortcuts-bar]]
            [intools.spotin.components.status-bar :refer [status-bar]]
            [intools.spotin.components.tracks-panel :refer [tracks-panel]]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [intools.spotin.app.events]
            [intools.spotin.app.subs]))

(defonce !app (atom nil))
(declare render)

(def player-actions
  [{:id "play-pause"
    :name "play/pause"
    :shortcut "z"}
   {:id "next"
    :name "next"
    :shortcut "n"}
   {:id "previous"
    :name "previous"
    :shortcut "b"}
   {:id "shuffle"
    :name "shuffle"}
   {:id "repeat"
    :name "repeat"}])

(def playlist-actions
  [{:id "playlist-play"
    :name "play"}
   {:id "playlist-rename"
    :name "rename"}
   {:id "playlist-edit-description"
    :name "edit description"}
   {:id "playlist-make-public"
    :name "make public"}
   {:id "playlist-delete"
    :name "delete"}
   {:id "playlist-share"
    :name "share"}])

(def playlists-actions
  [{:id "playlists-mix"
    :name "mix"}])

(def track-actions
  [{:id "like"
    :name "Add to Liked Songs"}
   {:id "add-to-library"
    :name "Add to Library"}
   {:id "open-artist"
    :name "Open artist"}
   {:id "open-album"
    :name "Open album"}])

(def action-separator
  {:name ""})

(defn dispatch-action! [{:keys [id arg]}]
  (case id
    "play-pause" (spotify/player-play-pause+)
    "next" (spotify/player-next+)
    "previous" (spotify/player-previous+)
    "shuffle" (spotify/player-toggle-shuffle+)
    "repeat" (spotify/player-toggle-repeat+)
    "playlist-play" (spotify/player-play+ {:context_uri (:uri arg)})
    "playlist-share" (js/console.log "Playlist URI:" (:uri arg))
    "playlists-mix" (playlist/create-mixed-playlist+ arg)))

(defn library-panel []
  (let [focused? (.-isFocused (ink/useFocus))]
    [:> Box {:border-style "single"
             :border-color (when focused? "green")}
        [:> Text "Panel 1"]]))

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        focus-manager (ink/useFocusManager)
        {:keys [playlist-order playlists selected-playlist playlist-tracks actions active-input-panel]} @(subscribe [:db])]
    (ink/useInput
      (fn [input _key]
        (case input
          "q" (do (.exit app)
                  (.exit js/process))
          ; "x" (dispatch (if actions
                          ; [:close-action-menu]
                          ; [:open-action-menu player-actions]))
          (some->> player-actions
                   (some (fn [{:keys [shortcut] :as action}]
                           (when (= shortcut input) action)))
                   (dispatch-action!)))))
    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
     (case (:type active-input-panel)
       :playlist-rename
       (let [{:keys [id name]} (:arg active-input-panel)]
         [:f> input-bar {:label (str "Rename playlist '" name "':")
                         :default-value name
                         :on-submit (fn [value]
                                      ;; TODO invalidate current values
                                      (spotify/playlist-rename+ id value)
                                      (dispatch [:close-input-panel]))
                         :on-cancel #(dispatch [:close-input-panel])}])
       :playlist-edit-description
       (let [{:keys [id name description]} (:arg active-input-panel)]
        [:f> input-bar {:label (str "Edit description for playlist '" name "':")
                        :default-value description
                        :on-submit (fn [value]
                                     ;; TODO invalidate current values
                                     (spotify/playlist-change-description+ id value)
                                     (dispatch [:close-input-panel]))
                        :on-cancel #(dispatch [:close-input-panel])}])

       nil)
     [:> Box {:flex-grow 1}
      (when actions
        [:f> action-menu {:actions actions
                          :on-activate (fn [{:keys [id arg] :as action}]
                                         (dispatch [:close-action-menu])
                                         (case id
                                           "playlist-rename" (dispatch [:playlist-rename arg])
                                           "playlist-edit-description" (dispatch [:playlist-edit-description arg])
                                           (dispatch-action! action)))
                          :on-cancel #(dispatch [:close-action-menu])}])
      [:> Box {:width "20%"
               :flex-direction "column"}
        #_[:f> library-panel]
        [:f> playlists-panel {:playlists (map #(get playlists %) playlist-order)
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
                                            (-> (spotify/get-playlist-tracks+ id)
                                                (.then (fn [body]
                                                          (dispatch [:set-playlist-tracks id (-> body (js->clj :keywordize-keys true) :items)])))))}]]

      [:f> tracks-panel {:playlist (get playlists selected-playlist)
                         :tracks (get playlist-tracks selected-playlist)
                         :on-menu #(dispatch [:open-action-menu (concat track-actions [action-separator] player-actions)])
                         :on-activate (fn [item]
                                        (let [playlist (get playlists selected-playlist)]
                                          (spotify/player-play+
                                           {:context_uri (:uri playlist)
                                            :offset {:uri (-> item :track :uri)}})))}]]
                                                 ;;:uris [(:uri track)]})))}]]
     [:f> status-bar]
     [shortcuts-bar {:actions player-actions}]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (rf/dispatch-sync [:initialize-db])
  (-> (spotify/get-all-playlists+)
      (.then (fn [{:keys [items]}]
               (dispatch [:set-playlists items])
               (when-let [id (-> items first :id)]
                 (dispatch [:set-selected-playlist id])
                 (-> (spotify/get-playlist-tracks+ id)
                     (.then (fn [body]
                               (dispatch [:set-playlist-tracks id (-> body (js->clj :keywordize-keys true) :items)]))))))))
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app]))
  #_(render))

