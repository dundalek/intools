(ns intools.spotin.main
  (:require [clojure.string :as str]
            [ink :refer [Box Spacer Text]]
            [intools.hooks :as hooks]
            [intools.spotin.app.events]
            [intools.spotin.app.fx]
            [intools.spotin.app.subs]
            [intools.spotin.components.action-menu :refer [action-menu]]
            [intools.spotin.components.input-bar :refer [input-bar]]
            [intools.spotin.components.playlists-panel :refer [playlists-panel]]
            [intools.spotin.components.shortcuts-bar :refer [shortcuts-bar]]
            [intools.spotin.components.status-bar :refer [status-bar]]
            [intools.spotin.components.tracks-panel :refer [album-header album-track-item playlist-header playlist-track-item tracks-panel]]
            [intools.spotin.format :refer [format-album-release-year format-duration]]
            [intools.spotin.model.spotify :as spotify]
            [intools.views :refer [scroll-status use-scrollable-box use-selectable-list]]
            [re-frame.core :as rf :refer [dispatch subscribe]]
            [react]
            [reagent.core :as r]))

(defonce !app (atom nil))
(declare render)

(def sidepanel-width "22%")

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
    :event [:spotin/dispatch-fx :repeat]}
   {:id :spotin/open-currently-playing
    :name "currently playing"
    :shortcut "."
    :event [:spotin/open-currently-playing]}
   {:id :spotin/player-volume-up
    :name "volume up 10%"
    :shortcut "+"
    :event [:spotin/player-volume-up]}
   {:id :spotin/player-volume-down
    :name "volume down 10%"
    :shortcut "-"
    :event [:spotin/player-volume-down]}
   {:id :spotin/player-seek-forward
    :name "seek forward 10s"
    :shortcut ">"
    :event [:spotin/player-seek-forward]}
   {:id :spotin/player-seek-backward
    :name "seek back 10s"
    :shortcut "<"
    :event [:spotin/player-seek-backward]}
   {:id :spotin/devices
    :name "devices"
    :shortcut "e"
    :event [:spotin/open-devices-menu]}])

(def excluded-from-shortcuts-bar?
  #{:spotin/player-volume-up
    :spotin/player-volume-down
    :spotin/player-seek-forward
    :spotin/player-seek-backward
    :spotin/open-currently-playing})

(def shortcuts-bar-actions
  (concat [{:shortcut "x"
            :name "menu"}]
          [{:shortcut "/"
            :name "search"}]
          (->> player-actions
               (remove (comp excluded-from-shortcuts-bar? :id))
               (filter :shortcut))
          [{:shortcut "q"
            :name "quit"}]))

(def playlist-actions
  [{:id :playlist-play
    :name "play"}
   {:id :playlist-rename
    :name "rename"}
   {:id :playlist-edit-description
    :name "edit description"}
   {:id :playlist-unfollow
    :name "delete"}
   ;; or private
   ;;{:id :playlist-make-public
   ;; :name "TBD make public"}
   ;;;; Copy Spotify URI / Copy embed code
   ;;{:id :playlist-share
   ;; :name "TBD share"}
   {:id :playlist-open
    :name "open"
    :shortcut "⏎"
    :event [:set-selected-playlist]}
   ;;{:id :playlist-create
   ;; :name "TBD create playlist"}
   ;;{:id :folder-create
   ;; :name "TBD create folder"}
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
  [{:name "open artist"
    :event [:spotin/open-track-artist]}
   {:name "open album"
    :event [:spotin/open-track-album]}
   ;; - TBD Go to song radio
   ;;{:name "TBD add to queue"}
   ;;{:id :like
   ;; :name "TBD add to Liked Songs"}
   ;;{:id :add-to-library
   ;; :name "TBD add to playlist"}
   ;;{:name "TBD share"}
     ;;{:name "TBD remove from this playlist"}
   {:name "play"
    :shortcut "⏎"
    :event [:spotin/dispatch-fx :track-play]}])

(def tracks-actions
  (conj track-actions
        {:id :spotin/start-track-search
         :name "search"
         :shortcut "/"
         :event [:spotin/set-track-search ""]}))

(def album-actions
  [{:name "play"
    :event [:spotin/dispatch-fx :album-play]}
   {:name "open"
    :shortcut "⏎"
    :event [:spotin/open-album]}])
   ; {:name "TBD go to album radio"}
   ; {:name "TBD Save to Your Library"}
   ; {:name "TBD Add to playlist"}
   ; {:name "TBD Share"}])

(def artist-tracks-actions
  (into [{:name "play artist"
          :event [:spotin/dispatch-fx :artist-context-play]}]
        ;; dropping the `open artist` actions, it is not useful since we are already on artist's screen
        (drop 1 tracks-actions)))

(def artist-actions
  [{:name "play artist"
    :event [:spotin/dispatch-fx :artist-play]}
   {:name "open"
    :shortcut "⏎"
    :event [:spotin/open-artist]}])
   ;;{:name "TBD Follow"}])
   ;;{:name "TBD Go to artist radio"}
   ;;{:name "TBD Share"}])

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

(defn playback-status-bar []
  [:f> status-bar {:playback @(subscribe [:spotin/playback-status])
                   :pending-requests @(subscribe [:spotin/pending-requests])}])

(defn device-item [{:keys [name type is_active]} {:keys [is-selected]}]
  [:> Box
   [:> Text {:bold is-selected
             :color (when is-selected "green")
             :wrap "truncate-end"}
    (if is_active "* " "  ")
    name " " type]])

(defn devices-menu []
  (let [[devices set-devices] (react/useState [])
        actions (->> devices
                     (map (fn [{:keys [id] :as device}]
                            (-> device
                                (select-keys [:id :name :type :is_active])))))]
    (react/useEffect
     (fn []
       (-> (spotify/get-player-devices+)
           (.then #(set-devices (:devices %))))
       js/undefined)
     #js [])
    [:f> action-menu {:actions actions
                      :item-component device-item
                      :width sidepanel-width
                      :on-cancel #(dispatch [:spotin/close-devices-menu])
                      :on-activate (fn [{:keys [id]}]
                                     (dispatch [:spotin/player-transfer id]))}]))

(defn error-alert* [{:keys [request error]}]
  (let [{:keys [method url]} request
        focus-id "error-alert"
        {:keys [is-focused]} (hooks/use-focus {:id focus-id
                                               :auto-focus true})]
    (ink/useInput
     (fn [_input ^js key]
       (when is-focused
         (cond
           (.-escape key) (dispatch [:spotin/clear-error])))))
    [:> Box {:border-style "single"
             :border-color (when is-focused "red")
             :flex-direction "column"
             :padding-x 1}
     [:> Box
      [:> Text {:wrap "truncate-end"} "API Error: " method " " url]]
     [:> Box
      (if (and (instance? js/Error error) (= (.-name error) "StatusCodeError"))
        (let [error (-> error .-error .-error)]
          [:> Text
           (.-status error) " - " (.-reason error) "\n"
           (.-message error)])
        [:> Text (str error)])]]))

(defn error-alert []
  (when-some [err @(subscribe [:spotin/error])]
    [:f> error-alert* err]))

(defn main-tracks-panel [{:keys [header context-item track-item-component]}]
  (let [playback-item-uri @(subscribe [:spotin/playback-item-uri])
        tracks-filtered @(subscribe [:spotin/tracks-filtered])
        track-search-query @(subscribe [:spotin/track-search-query])]
    [:f> tracks-panel {:focus-id "tracks-panel"
                       :header header
                       :tracks tracks-filtered
                       :track-item-component track-item-component
                       :is-searching (some? track-search-query)
                       :playback-item-uri playback-item-uri
                       :on-search-change #(dispatch [:spotin/set-track-search %])
                       :on-search-cancel #(dispatch [:spotin/set-track-search nil])
                       :on-menu (fn [item]
                                  (let [tracks-actions (map #(assoc % :arg {:item item
                                                                            :context context-item})
                                                            tracks-actions)
                                        actions (concat tracks-actions [action-separator] player-actions)]
                                    (dispatch [:open-action-menu actions])))
                       :on-activate (fn [item]
                                      (dispatch [:spotin/dispatch-fx :track-play
                                                 {:context context-item
                                                  :item item}]))}]))

(defn artist-item [{:keys [name followers]} {:keys [is-selected]}]
  [:> Box
   [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
    [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
   [:> Box
    [:> Text {:bold is-selected :color (when is-selected "green")} (:total followers)]]])

(defn album-item [{:keys [name release_date total_tracks]} {:keys [is-selected]}]
  [:> Box
   [:> Box
    [:> Text (cond-> {:bold is-selected :color (when is-selected "green")}
               (not is-selected) (assoc :dim-color true))
     (format-album-release-year release_date)]]
   [:> Box {:flex-basis 0 :flex-grow 1 :padding-x 1 :justify-content "flex-start"}
    [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"}
     name]]
   [:> Box
    [:> Text {:bold is-selected :color (when is-selected "green")}
     total_tracks]]])

(defn artist-track-item [track {:keys [is-selected]}]
  (let [{:keys [name duration_ms popularity]} track]
    [:> Box
     [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
      [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
     [:> Box
      [:> Text {:bold is-selected :color (when is-selected "green")} popularity " "]]
     [:> Box {:width 6 :justify-content "flex-end"}
      [:> Text {:bold is-selected :color (when is-selected "green")} (format-duration duration_ms)]]]))

(defn artist-header [{:keys [name genres] :as artist}]
  (let [follower-count (-> artist :followers :total)]
    [:> Box {:flex-direction "column"
             :padding-x 1
             :padding-top 1}
     [:> Box
      [:> Text {:dim-color true} "artist "]
      [:> Text {:wrap "truncate-end"} name]
      [:> Spacer]
      [:> Text follower-count]
      [:> Text {:dim-color true} " followers"]]
     [:> Box
      [:> Text {:dim-color true} "genres "]
      [:> Text {:wrap "truncate-end"} (str/join ", " genres)]]]))

(defn artist-sub-panel [{:keys [focus-id header items item-component on-menu on-activate]}]
  (let [{:keys [box-ref is-focused selected-index displayed-selected-index displayed-items]}
        (use-scrollable-box {:focus-id focus-id
                             :items items
                             :on-menu on-menu
                             :on-activate on-activate
                             :auto-focus true})]
    (ink/useInput
     (fn [input _key]
       (when is-focused
         (case input
           "x" (when on-menu (on-menu (nth items selected-index)))
           nil))))
    [:> Box {:flex-direction "column"
             :width "50%"
             :border-style "single"
             :border-color (when is-focused "green")
             :flew-grow 1
             :padding-x 1}
     header
     [:> Box {:flex-direction "column"
              :flew-grow 1
              :ref box-ref}
      (->> displayed-items
           (map-indexed
            (fn [idx {:keys [id] :as item}]
              (let [is-selected (and is-focused (= idx displayed-selected-index))]
                ^{:key id} [item-component item {:is-selected is-selected}]))))]
     [scroll-status selected-index items]]))

(defn dedupe-releases [albums]
  ;; When there are multiple releases of the same album, Spotify seems to show the earliest.
  ;; Since the albums are by default sorted by latest first, we pick the last one in each group.
  (->> albums
       (group-by :name)
       (map (comp last val))
       (sort-by :release_date #(compare %2 %1))))

(defn artist-panel [{:keys [artist]}]
  (let [{:keys [artist albums top-tracks related-artists]} artist
        groups (group-by :album_group albums)]
    [:> Box {:flex-direction "column"
             :flex-grow 1}
     [artist-header artist]
     [:> Box {:height "50%"}
      [:f> artist-sub-panel {:focus-id "artist-top-tracks"
                             :header [:> Box {:height 1 :justify-content "space-between"}
                                      [:> Text {:dim-color true} "Top Tracks"]
                                      [:> Text {:dim-color true} "popularity time"]]
                             :items top-tracks
                             :item-component artist-track-item
                             :on-menu (fn [item]
                                        (let [tracks-actions (map #(assoc % :arg {:item item
                                                                                  :items top-tracks
                                                                                  :context artist})
                                                                  artist-tracks-actions)
                                              actions (concat tracks-actions [action-separator] player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [item]
                                            (dispatch [:spotin/dispatch-fx :track-play {:item item
                                                                                        :items top-tracks
                                                                                        :context artist}]))}]
      [:f> artist-sub-panel {:focus-id "artist-singles"
                             :header [:> Box {:height 1 :justify-content "space-between"}
                                      [:> Text {:dim-color true} "Singles and EPs"]
                                      [:> Text {:dim-color true} "songs"]]
                             :items (dedupe-releases (get groups "single"))
                             :item-component album-item
                             :on-menu (fn [item]
                                        (let [album-actions (map #(assoc % :arg {:item item
                                                                                 :context artist})
                                                                 album-actions)
                                              actions (concat album-actions [action-separator] player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [item]
                                            (dispatch [:spotin/open-album {:item item
                                                                           :context artist}]))}]]
     [:> Box {:height "50%"}
      [:f> artist-sub-panel {:focus-id "artist-albums"
                             :header [:> Box {:height 1 :justify-content "space-between"}
                                      [:> Text {:dim-color true} "Albums"]
                                      [:> Text {:dim-color true} "songs"]]
                             :items (dedupe-releases (get groups "album"))
                             :item-component album-item
                             :on-menu (fn [item]
                                        (let [album-actions (map #(assoc % :arg {:item item
                                                                                 :context artist})
                                                                 album-actions)
                                              actions (concat album-actions [action-separator] player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [item]
                                            (dispatch [:spotin/open-album {:item item
                                                                           :context artist}]))}]
      [:f> artist-sub-panel {:focus-id "artist-related-artists"
                             :header [:> Box {:height 1 :justify-content "space-between"}
                                      [:> Text {:dim-color true} "Related Artists"]
                                      [:> Text {:dim-color true} "followers"]]
                             :items related-artists
                             :item-component artist-item
                             :on-menu (fn [item]
                                        (let [artist-actions (map #(assoc % :arg {:item item
                                                                                  :context artist})
                                                                  artist-actions)
                                              actions (concat artist-actions [action-separator] player-actions)]
                                          (dispatch [:open-action-menu actions])))
                             :on-activate (fn [item]
                                            (dispatch [:spotin/open-artist {:item item
                                                                            :context artist}]))}]]]))

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        {:keys [playlists actions active-input-panel playlist-search-query]} @(subscribe [:db])
        playlists-filtered @(subscribe [:spotin/playlists-filtered])
        actions-filtered @(subscribe [:spotin/actions-filtered])
        actions-search-query @(subscribe [:spotin/actions-search-query])
        track-search-query @(subscribe [:spotin/track-search-query])
        current-route @(subscribe [:spotin/current-route])
        tracks-filtered @(subscribe [:spotin/tracks-filtered])
        playback-context-uri @(subscribe [:spotin/playback-context-uri])
        confirmation-modal-open? (some? @(subscribe [:spotin/confirmation-modal]))
        devices-menu-open? @(subscribe [:spotin/devices-menu])
        focused-component-id (cond
                               @(subscribe [:spotin/error]) "error-alert"
                               confirmation-modal-open? "confirmation-modal"
                               (or actions devices-menu-open?) "action-menu"
                               active-input-panel "input-bar")
        force-focus (not= focused-component-id "error-alert")
        {:keys [active-focus-id focus-next focus-previous]} (hooks/use-focus-manager {:focus-id focused-component-id
                                                                                      :force force-focus})]
    (hooks/use-interval
     #(dispatch [:spotin/refresh-playback-status])
     5000)

    (ink/useInput
     (fn [input key]
       (when-not (or (and (= active-focus-id "action-menu") actions-search-query)
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
           (when-some [{:keys [event shortcut arg] :as action} (->> (case active-focus-id
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
               (cond
                 ;; override / to search in action menu - this is a bit hacky without event propagation
                 search-action? (dispatch [:spotin/set-actions-search ""])
                 event (dispatch (conj event arg))
                 :else (dispatch [:run-action action]))))))))
    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
     [error-alert]
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
      (when devices-menu-open?
        [:f> devices-menu])
      (when actions
        [:f> action-menu
         {:actions actions-filtered
          :is-searching (some? actions-search-query)
          :width 21
          :on-search-change #(dispatch [:spotin/set-actions-search %])
          :on-search-cancel #(dispatch [:spotin/set-actions-search nil])
          :on-activate (fn [{:keys [id arg event] :as action}]
                         (dispatch [:close-action-menu])
                         (if event
                           (dispatch (conj event arg))
                           (case id
                             :playlist-rename (dispatch [:playlist-rename arg])
                             :playlist-edit-description (dispatch [:playlist-edit-description arg])
                             :playlist-unfollow (dispatch [:spotin/open-confirmation-modal
                                                           {:title "Delete playlist"
                                                            :description (str "Are you sure you want to delete playlist '" (:name arg) "'?")
                                                            :on-submit #(dispatch [:playlist-unfollow (:id arg)])}])
                             (dispatch [:run-action action]))))
          :on-cancel #(dispatch [:close-action-menu])}])
      (when-not devices-menu-open?
        [:> Box {:width sidepanel-width
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
                               :on-activate (fn [playlist]
                                              (dispatch [:set-selected-playlist playlist]))}]])
                                              ;; Try to focus the tracks panel after playlist selected, it is a bit brittle
                                              ;;(focus-next))}]])
      (case (:name current-route)
        :playlist
        (let [context-id (-> current-route :params :playlist-id)
              context-item (get playlists context-id)]
          ^{:key context-id} [main-tracks-panel {:track-item-component playlist-track-item
                                                 :context-item context-item
                                                 :header [playlist-header {:playlist context-item
                                                                           :tracks tracks-filtered}]}])
        :album
        (let [context-id (-> current-route :params :album-id)
              context-item @(subscribe [:spotin/album-by-id context-id])]
          ^{:key context-id} [main-tracks-panel {:track-item-component album-track-item
                                                 :context-item context-item
                                                 :header [album-header {:album context-item}]}])

        :artist
        [artist-panel {:artist @(subscribe [:spotin/artist-by-id (-> current-route :params :artist-id)])}]

        nil)]
     [playback-status-bar]
     [shortcuts-bar {:actions shortcuts-bar-actions}]]))

(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (set! spotify/*before-request-callback* #(dispatch [:spotin/request-started]))
  (set! spotify/*after-request-callback* #(dispatch [:spotin/request-finished]))
  (set! spotify/*request-error-callback* (fn [error request]
                                           (dispatch [:spotin/request-failed error request])))
  (rf/dispatch-sync [:spotin/init])
  (render))

(defn ^:dev/after-load reload! []
  (rf/clear-subscription-cache!)
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app])))
