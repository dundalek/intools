(ns intools.spotin.containers
  (:require
   [clojure.string :as str]
   [ink :refer [Box Text]]
   [intools.hooks :as hooks]
   [intools.search :as search]
   [intools.spotin.actions :as actions :refer [action-separator album-actions
                                               artist-actions
                                               artist-tracks-actions player-actions playlist-actions playlists-actions]]
   [intools.spotin.components.action-menu :as action-menu]
   [intools.spotin.components.artist-panel :as artist-panel]
   [intools.spotin.components.confirmation-modal :as confirmation-modal]
   [intools.spotin.components.device-menu :as device-menu]
   [intools.spotin.components.error-alert :as error-alert]
   [intools.spotin.components.input-bar :as input-bar]
   [intools.spotin.components.playlists-panel :as playlists-panel]
   [intools.spotin.components.shortcuts-bar :as shortcuts-bar]
   [intools.spotin.components.status-bar :as status-bar]
   [intools.spotin.components.tracks-panel :refer [album-header
                                                   album-track-item playlist-header
                                                   playlist-track-item tracks-panel]]
   [intools.views :refer [uncontrolled-text-input]]
   [re-frame.core :refer [dispatch subscribe]]
   [react]))

(defn playlist-rename-input-panel [{:keys [id name]}]
  [:f> input-bar/input-bar
   {:focus-id "input-bar"
    :label (str "Rename playlist '" name "':")
    :default-value name
    :on-submit (fn [value]
                 (dispatch [:spotin/playlist-change-submitted {:playlist-id id
                                                               :attr :name
                                                               :value value}]))

    :on-cancel #(dispatch [:close-input-panel])}])

(defn playlist-edit-description-input-panel [{:keys [id name description]}]
  [:f> input-bar/input-bar
   {:focus-id "input-bar"
    :label (str "Edit description for playlist '" name "':")
    :default-value description
    :on-submit (fn [value]
                 (dispatch [:spotin/playlist-change-submitted {:playlist-id id
                                                               :attr :description
                                                               :value value}]))
    :on-cancel #(dispatch [:close-input-panel])}])

(defn playlists-panel []
  (let [player (:data @(subscribe [:spotin/player]))
        playback-context-uri (when (:is_playing player)
                               (-> player :context :uri))
        playlist-search-query @(subscribe [:spotin/playlist-search-query])
        current-route @(subscribe [:spotin/current-route])
        playlists (:data @(subscribe [:spotin/playlists]))]
    [:f> playlists-panel/playlists-panel
     {:focus-id "playlists-panel"
      :playlists playlists
      :selected-playlist-id (-> current-route :params :playlist-id)
      :search-query playlist-search-query
      :playback-context-uri playback-context-uri
      :on-search-change #(dispatch [:spotin/set-playlist-search %])
      :on-search-cancel #(dispatch [:spotin/clear-playlist-search])
      :on-menu (fn [playlist playlist-ids]
                 (let [playlist-actions (map #(assoc % :arg playlist) playlist-actions)
                       playlists-actions (when (seq playlist-ids)
                                           (map #(assoc % :arg playlist-ids) playlists-actions))
                       actions (concat playlist-actions
                                       playlists-actions
                                       [action-separator]
                                       player-actions
                                       [action-separator]
                                       actions/global-actions)]
                   (dispatch [:open-action-menu actions])))
      :on-activate (fn [playlist]
                     (let [{:keys [name params]} current-route]
                       (if (and (= name :playlist)
                                (= (:playlist-id params) (:id playlist)))
                         (dispatch [:spotin/dispatch-fx :playlist-play playlist])
                         (dispatch [:select-playlist playlist]))))}]))

(defn action-menu-panel [{:keys [on-activate]}]
  (let [actions-filtered @(subscribe [:spotin/actions-filtered])
        actions-search-query @(subscribe [:spotin/actions-search-query])]
    [:f> action-menu/action-menu
     {:actions actions-filtered
      :is-searching (some? actions-search-query)
      :width 21
      :on-search-change #(dispatch [:spotin/set-actions-search %])
      :on-search-cancel #(dispatch [:spotin/set-actions-search nil])
      :on-activate (fn [action]
                     (dispatch [:close-action-menu])
                     (on-activate action))
      :on-cancel #(dispatch [:close-action-menu])}]))

(defn devices-menu [{:keys [width]}]
  (let [actions (->> @(subscribe [:spotin/devices])
                     :data
                     :devices
                     (map #(select-keys % [:id :name :type :is_active])))]
    [:f> action-menu/action-menu
     {:actions actions
      :item-component device-menu/device-item
      :header [:> Box {:padding-x 2}
               [:> Text {:dim-color true} "Devices"]]
      :width width
      :on-cancel #(dispatch [:spotin/close-devices-menu])
      :on-activate (fn [{:keys [id]}]
                     (dispatch [:spotin/player-transfer id]))}]))

(defn tracks-search-box [{:keys [is-focused]}]
  (when @(subscribe [:spotin/track-search-query])
    [:> Box {:height 2}
     [:> Text "Search tracks: "]
     [:f> uncontrolled-text-input {:focus is-focused
                                   :on-change #(dispatch [:spotin/set-track-search %])
                                   :on-cancel #(dispatch [:spotin/set-track-search nil])}]]))

(defn main-tracks-panel [{:keys [header context-item tracks-filtered track-item-component]}]
  (let [player (:data @(subscribe [:spotin/player]))
        playback-item-uri (-> player :item :uri)
        focus-id "tracks-panel"
        is-searchbox-focused (hooks/use-is-focused focus-id)]
    [:f> tracks-panel {:focus-id focus-id
                       :header [:> Box {:flex-direction "column"}
                                header
                                [tracks-search-box {:is-focused is-searchbox-focused}]]
                       :tracks tracks-filtered
                       :track-item-component track-item-component
                       :playback-item-uri playback-item-uri
                       :on-menu (fn [item]
                                  (dispatch [:spotin/track-panel-menu-opened
                                             {:context context-item
                                              :item item}]))
                       :on-activate (fn [item]
                                      (dispatch [:spotin/dispatch-fx :track-play
                                                 {:context context-item
                                                  :item item}]))}]))

(defn search-tracks [query tracks]
  (->> tracks
       (search/filter-by query (fn [{:keys [name album artists]}]
                                 (->> (concat [name (:name album)]
                                              (map :name artists))
                                      (str/join " "))))))

(defn playlist-tracks-panel [playlist-id]
  (let [search-query @(subscribe [:spotin/track-search-query])
        context-item (:data @(subscribe [:spotin/playlist playlist-id]))
        all-tracks (-> @(subscribe [:spotin/playlist-tracks playlist-id])
                       :data :items)
        tracks-filtered (react/useMemo
                         #(->> all-tracks
                               (map :track)
                               (search-tracks search-query))
                         #js [all-tracks search-query])]
    [:f> main-tracks-panel {:track-item-component playlist-track-item
                            :context-item context-item
                            :tracks-filtered tracks-filtered
                            :header [playlist-header {:playlist context-item
                                                      :tracks tracks-filtered}]}]))

(defn album-tracks-panel [album-id]
  (let [search-query @(subscribe [:spotin/track-search-query])
        context-item (:data @(subscribe [:spotin/album album-id]))
        all-tracks (-> @(subscribe [:spotin/album-tracks album-id])
                       :data
                       :items)
        tracks-filtered (react/useMemo
                         #(->> all-tracks
                               (search-tracks search-query))
                         #js [all-tracks search-query])]
    [:f> main-tracks-panel {:track-item-component album-track-item
                            :context-item context-item
                            :tracks-filtered tracks-filtered
                            :header [album-header {:album context-item}]}]))

(defn dedupe-releases [albums]
  ;; When there are multiple releases of the same album, Spotify seems to show the earliest.
  ;; Since the albums are by default sorted by latest first, we pick the last one in each group.
  (->> albums
       (group-by :name)
       (map (comp last val))
       (sort-by :release_date #(compare %2 %1))))

(defn artist-top-track-sub-panel [{:keys [artist-id artist]}]
  (let [top-tracks (-> @(subscribe [:spotin/artist-top-tracks artist-id])
                       :data
                       :tracks)]
    [:f> artist-panel/artist-sub-panel
     {:focus-id "artist-top-tracks"
      :header [:> Box {:height 1 :justify-content "space-between"}
               [:> Text {:dim-color true} "Top Tracks"]
               [:> Text {:dim-color true} "popularity time"]]
      :items top-tracks
      :item-component artist-panel/artist-track-item
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
                                                                 :context artist}]))}]))

(defn artist-related-artists-sub-panel [{:keys [artist-id artist]}]
  (let [related-artists (-> @(subscribe [:spotin/artist-related-artists artist-id])
                            :data
                            :artists)]
    [:f> artist-panel/artist-sub-panel
     {:focus-id "artist-related-artists"
      :header [:> Box {:height 1 :justify-content "space-between"}
               [:> Text {:dim-color true} "Related Artists"]
               [:> Text {:dim-color true} "followers"]]
      :items related-artists
      :item-component artist-panel/artist-item
      :on-menu (fn [item]
                 (let [artist-actions (map #(assoc % :arg {:item item
                                                           :context artist})
                                           artist-actions)
                       actions (concat artist-actions [action-separator] player-actions)]
                   (dispatch [:open-action-menu actions])))
      :on-activate (fn [item]
                     (dispatch [:spotin/open-artist {:item item
                                                     :context artist}]))}]))

(defn artist-releases-panel [{:keys [artist items focus-id header]}]
  [:f> artist-panel/artist-sub-panel
   {:focus-id focus-id
    :header header
    :items items
    :item-component artist-panel/album-item
    :on-menu (fn [item]
               (let [album-actions (map #(assoc % :arg {:item item
                                                        :context artist})
                                        album-actions)
                     actions (concat album-actions [action-separator] player-actions)]
                 (dispatch [:open-action-menu actions])))
    :on-activate (fn [item]
                   (dispatch [:spotin/activate-album {:item item
                                                      :context artist}]))}])

(defn artist-panel [artist-id]
  (let [artist (:data @(subscribe [:spotin/artist artist-id]))
        albums (-> @(subscribe [:spotin/artist-albums artist-id])
                   :data
                   :items)
        groups (group-by :album_group albums)]
    [:> Box {:flex-direction "column"
             :flex-grow 1}
     [artist-panel/artist-header artist]
     [:> Box {:flex-basis 1
              :flex-grow 1}
      [:f> artist-top-track-sub-panel {:artist-id artist-id
                                       :artist artist}]
      [artist-releases-panel {:focus-id "artist-singles"
                              :artist artist
                              :items (dedupe-releases (get groups "single"))
                              :header [:> Box {:height 1 :justify-content "space-between"}
                                       [:> Text {:dim-color true} "Singles and EPs"]
                                       [:> Text {:dim-color true} "songs"]]}]]
     [:> Box {:flex-basis 1
              :flex-grow 1}
      [artist-releases-panel {:focus-id "artist-albums"
                              :artist artist
                              :items (dedupe-releases (get groups "album"))
                              :header [:> Box {:height 1 :justify-content "space-between"}
                                       [:> Text {:dim-color true} "Albums"]
                                       [:> Text {:dim-color true} "songs"]]}]
      [:f> artist-related-artists-sub-panel {:artist-id artist-id
                                             :artist artist}]]]))

(defn playback-status-bar []
  (let [playback (:data @(subscribe [:spotin/player]))]
    [:f> status-bar/status-bar {:playback playback
                                :pending-requests @(subscribe [:spotin/pending-requests])}]))

(defn confirmation-modal []
  (when-some [{:keys [on-submit on-cancel] :as opts} @(subscribe [:spotin/confirmation-modal])]
    [:f> confirmation-modal/confirmation-modal
     (assoc opts
            :focus-id "confirmation-modal"
            :on-submit (fn []
                         (dispatch [:spotin/close-confirmation-modal])
                         (when on-submit (on-submit)))
            :on-cancel (fn []
                         (dispatch [:spotin/close-confirmation-modal])
                         (when on-cancel (on-cancel))))]))

(defn error-alert []
  (when-some [error @(subscribe [:spotin/error])]
    [:f> error-alert/error-alert {:error error
                                  :on-dismiss #(dispatch [:spotin/clear-error])}]))

(defn shortcuts-bar []
  [shortcuts-bar/shortcuts-bar {:actions actions/shortcuts-bar-actions}])
