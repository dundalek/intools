(ns intools.spotin.containers
  (:require [clojure.string :as str]
            [ink :refer [Box Text]]
            [intools.search :as search]
            [intools.spotin.actions :refer [action-separator album-actions artist-actions artist-tracks-actions player-actions playlist-actions playlists-actions tracks-actions]]
            [intools.spotin.app.query :as query]
            [intools.spotin.components.action-menu :as action-menu]
            [intools.spotin.components.artist-panel :as artist-panel]
            [intools.spotin.components.confirmation-modal :as confirmation-modal]
            [intools.spotin.components.device-menu :as device-menu]
            [intools.spotin.components.error-alert :as error-alert]
            [intools.spotin.components.input-bar :as input-bar]
            [intools.spotin.components.playlists-panel :as playlists-panel]
            [intools.spotin.components.status-bar :as status-bar]
            [intools.spotin.components.tracks-panel :refer [album-header album-track-item playlist-header playlist-track-item tracks-panel]]
            [intools.spotin.model.spotify :as spotify]
            [re-frame.core :refer [dispatch subscribe]]
            [react]
            [react-query :refer [useQuery]]))

(defn playlist-rename-input-panel [{:keys [id name]}]
  [:f> input-bar/input-bar
   {:focus-id "input-bar"
    :label (str "Rename playlist '" name "':")
    :default-value name
    :on-submit (fn [value]
                 ;; TODO improve invalidation
                 (-> (spotify/playlist-rename+ id value)
                     (.then #(dispatch [:spotin/refresh-playlists])))
                 (dispatch [:close-input-panel]))
    :on-cancel #(dispatch [:close-input-panel])}])

(defn playlist-edit-description-input-panel [{:keys [id name description]}]
  [:f> input-bar/input-bar
   {:focus-id "input-bar"
    :label (str "Edit description for playlist '" name "':")
    :default-value description
    :on-submit (fn [value]
                 ;; TODO improve invalidation
                 (-> (spotify/playlist-change-description+ id value)
                     (.then #(dispatch [:spotin/refresh-playlists])))
                 (dispatch [:close-input-panel]))
    :on-cancel #(dispatch [:close-input-panel])}])

(defn playlists-panel []
  (let [player-query (query/use-player)
        playback-context-uri (let [status (.-data player-query)]
                               (when (:is_playing status)
                                 (-> status :context :uri)))
        playlist-search-query @(subscribe [:spotin/playlist-search-query])
        current-route @(subscribe [:spotin/current-route])]
    [:f> playlists-panel/playlists-panel
     {:focus-id "playlists-panel"
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
                                       player-actions)]
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
  (let [query (useQuery "devices" spotify/get-player-devices+)
        actions (->> query .-data :devices
                     (map #(select-keys % [:id :name :type :is_active])))]
    [:f> action-menu/action-menu
     {:actions actions
      :item-component device-menu/device-item
      :width width
      :on-cancel #(dispatch [:spotin/close-devices-menu])
      :on-activate (fn [{:keys [id]}]
                     (dispatch [:spotin/player-transfer id]))}]))

(defn main-tracks-panel [{:keys [header context-item tracks-filtered track-item-component]}]
  (let [player-query (query/use-player)
        playback-item-uri (-> player-query .-data :item :uri)
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

(defn search-tracks [query tracks]
  (->> tracks
       (search/filter-by query (fn [{:keys [name album artists]}]
                                 (->> (concat [name (:name album)]
                                              (map :name artists))
                                      (str/join " "))))))

(defn playlist-tracks-panel [playlist-id]
  (let [search-query @(subscribe [:spotin/track-search-query])
        query (useQuery #js ["playlists" playlist-id] #(spotify/get-playlist+ playlist-id))
        context-item (.-data query)
        tracks-query (useQuery #js ["playlist-tracks" playlist-id] #(spotify/get-playlist-tracks+ playlist-id))
        all-tracks (-> tracks-query .-data :items)
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
        query (useQuery #js ["albums" album-id] #(spotify/get-album+ album-id))
        context-item (.-data query)
        tracks-query (useQuery #js ["album-tracks" album-id] #(spotify/get-album-tracks+ album-id))
        all-tracks (-> tracks-query .-data :items)
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
  (let [top-tracks-query (useQuery #js ["artist-top-tracks" artist-id] #(spotify/get-artist-top-tracks+ artist-id))
        top-tracks (-> top-tracks-query .-data :tracks)]
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
  (let [related-artists-query (useQuery #js ["artist-related-artists" artist-id] #(spotify/get-artist-related-artists+ artist-id))
        related-artists (-> related-artists-query .-data :artists)]
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
                   (dispatch [:spotin/open-album {:item item
                                                  :context artist}]))}])

(defn artist-panel [artist-id]
  (let [artist-query (useQuery #js ["artists" artist-id] #(spotify/get-artist+ artist-id))
        artist (.-data artist-query)
        albums-query (useQuery #js ["artist-albums" artist-id] #(spotify/get-artist-albums+ artist-id))
        albums (-> albums-query .-data :items)
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
  (let [query (query/use-player)
        playback (.-data query)]
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
