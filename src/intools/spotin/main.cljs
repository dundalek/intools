(ns intools.spotin.main
  (:require [reagent.core :as r]
            [react]
            [ink :refer [Box Text Spacer]]
            [intools.views :refer [uncontrolled-text-input use-selectable-list]]
            [intools.spotin.model :as model]
            [intools.hooks :as hooks]
            [clojure.string :as str]))

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

(defn interleave-all
  "Like interleave, but exhausts all colls instead of stoping on the shortest one."
  [& colls]
  (lazy-seq
    (let [ss (keep seq colls)]
      (when (seq ss)
        (concat (map first ss)
                (apply interleave-all (map rest ss)))))))

(defn generate-mixed-playlist [playlists]
  (->> playlists
       (map shuffle)
       (apply interleave-all)
       (take 50)
       (shuffle)))

(defn create-mixed-playlist+ [playlists]
  (-> (map model/get-playlist-tracks+ (map :id playlists))
      (js/Promise.all)
      (.then (fn [bodies]
               (let [track-uris (->> bodies
                                     (map (fn [body]
                                            (->> (-> body (js->clj :keywordize-keys true) :items))))
                                     (generate-mixed-playlist)
                                     (map #(get-in % [:track :uri])))]
                  (.then (model/user-id+)
                    (fn [user-id]
                      (.then (model/create-playlist+ user-id {:name (str "Generated-" (+ 100 (rand-int 900)))
                                                              :description (str "Generated from: " (str/join ", " (map :name playlists)))})
                        (fn [^js body]
                          (let [playlist-id (.-id body)]
                            (model/authorized-post+ (str "https://api.spotify.com/v1/playlists/" playlist-id "/tracks")
                                                    {:uris track-uris})))))))))
      (.then #(js/console.log %) #(js/console.log %))))

(defn dispatch-action! [{:keys [id arg]}]
  (case id
    "play-pause" (model/player-play-pause+)
    "next" (model/player-next+)
    "previous" (model/player-previous+)
    "shuffle" (model/player-toggle-shuffle+)
    "repeat" (model/player-toggle-repeat+)
    "playlist-play" (model/player-play+ {:context_uri (:uri arg)})pinephone
    "playlist-share" (js/console.log "Playlist URI:" (:uri arg))
    "playlists-mix" (create-mixed-playlist+ arg)))

(defn init-state []
  {:route {:name ::apps}
   :playlists {}
   :playlist-order []
   :playlist-tracks {}
   :selected-playlist nil
   :action-menu nil
   :input-panel nil})

(defmulti reducer (fn [_state [event-name]] event-name))

(defmethod reducer :navigate [state [_ route]]
  (assoc state :route route))

(defmethod reducer :set-playlists [state [_ playlists]]
  (assoc state
         :playlist-order (map :id playlists)
         :playlists (->> playlists
                         (reduce (fn [m {:keys [id] :as item}]
                                    (assoc m id item))
                                 {}))))

(defmethod reducer :set-playlist-tracks [state [_ playlist-id tracks]]
  (assoc-in state [:playlist-tracks playlist-id] tracks))

(defmethod reducer :set-selected-playlist [state [_ playlist-id]]
  (assoc state :selected-playlist playlist-id))

(defmethod reducer :open-action-menu [state [_ menu]]
  (assoc state :actions menu))

(defmethod reducer :close-action-menu [state _]
  (assoc state :actions nil))

(defmethod reducer :open-input-panel [state [_ data]]
  (assoc state :active-input-panel data))

(defmethod reducer :close-input-panel [state _]
  (assoc state :active-input-panel nil))

(defmethod reducer :playlist-rename [state [_ arg]]
  (assoc state :active-input-panel {:type :playlist-rename
                                    :arg arg}))

(defmethod reducer :playlist-edit-description [state [_ arg]]
  (assoc state :active-input-panel {:type :playlist-edit-description
                                    :arg arg}))

;; useReducer does not work with multimethods
(defn reducer-fn [state event]
  (reducer state event))

(defn format-duration [ms]
  (let [sec (Math/floor (/ ms 1000))
        seconds (mod sec 60)
        minutes (quot sec 60)]
    (str minutes ":" (when (< seconds 10) "0") seconds)))

(defn library-panel []
  (let [focused? (.-isFocused (ink/useFocus))]
    [:> Box {:border-style "single"
             :border-color (when focused? "green")}
        [:> Text "Panel 1"]]))

(defn playlist-item [{:keys [name]} {:keys [is-selected is-active]}]
  [:> Text {:bold is-selected
            :color (cond
                     is-active "yellow"
                     is-selected "green")
            :wrap "truncate-end"}
   name])

(defn playlists-panel [{:keys [playlists on-activate on-menu]}]
  (let [[selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)
                          value (op selected id)]
                      (set-selected value)))

        {:keys [selected-index is-focused]}
        (use-selectable-list {:items playlists
                              :on-activate #(on-activate %)
                              :on-toggle on-toggle
                              :auto-focus true})

        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (- (or (:height viewport) (count playlists)) 2) ; -2 to compensate for borders
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-items (->> playlists (drop offset) (take viewport-height))]
    (ink/useInput
      (fn [input _key]
        (when is-focused
          (case input
            "x" (when on-menu (on-menu (nth playlists selected-index) selected))
            nil))))
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :flex-grow 1
             :ref box-ref}
      (->> displayed-items
          (map-indexed
           (fn [idx {:keys [id] :as item}]
             ^{:key idx}
             [playlist-item item {:is-selected (= idx (- selected-index offset))
                                  :is-active (contains? selected id)}])))]))

(defn track-item [{:keys [track]} {:keys [is-selected]}]
  (let [{:keys [name duration_ms album artists]} track]
    [:> Box
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} name]]
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} (str/join ", " (map :name artists))]]
      [:> Box {:flex-basis 0 :flex-grow 1 :padding-right 1 :justify-content "flex-start"}
       [:> Text {:bold is-selected :color (when is-selected "green") :wrap "truncate-end"} (:name album)]]
      [:> Box {:width 6 :justify-content "flex-end"}
       [:> Text {:bold is-selected :color (when is-selected "green")} (format-duration duration_ms)]]]))

(defn playlist-header [{:keys [playlist]}]
  (let [{:keys [name description owner tracks]} playlist]
    [:> Box {:flex-direction "column"
             :margin-bottom 1}
      [:> Box
          [:> Text {:dim-color true} "playlist    "]
          [:> Text name]
          [:> Spacer]
          [:> Text {:dim-color true} "by "]
          [:> Text (:display_name owner)]
          [:> Spacer]
          [:> Text (:total tracks) " songs" #_", 3hr 42 min"]]
      (when-not (str/blank? description)
        [:> Box
          [:> Text {:dim-color true} "description "]
          [:> Text description]])]))

(defn tracks-panel [{:keys [playlist tracks on-activate on-menu]}]
  (let [[selected set-selected] (react/useState #{})
        on-toggle (fn [{:keys [id]}]
                    (let [op (if (contains? selected id) disj conj)]
                      (set-selected (op selected id))))
        {:keys [selected-index is-focused]} (use-selectable-list {:items tracks
                                                                  :on-toggle on-toggle
                                                                  :on-activate on-activate
                                                                  :auto-focus true})
        box-ref (react/useRef)
        viewport (hooks/use-ref-size box-ref)
        viewport-height (- (or (:height viewport) (count tracks))
                           4
                           (if (str/blank? (:description playlist)) 0 1))
        offset (hooks/use-scrollable-offset {:selected-index selected-index
                                             :height viewport-height})
        displayed-tracks (->> tracks (drop offset) (take viewport-height))]
    (ink/useInput
      (fn [input _key]
        (when is-focused
          (case input
            "x" (when on-menu (on-menu))
            nil))))
    [:> Box {:flex-direction "column"
             :flex-grow 1
             :border-style "single"
             :border-color (when is-focused "green")
             :padding-x 1
             :ref box-ref}
      #_[:> Box [:> Text selected-index " of " (count tracks) " offset=" offset " viewport=" viewport-height]]
      (when playlist [playlist-header {:playlist playlist}])
      (->> displayed-tracks
           (map-indexed
            (fn [idx {:keys [id] :as item}]
              ^{:key idx}
              [track-item item {:is-selected (= idx (- selected-index offset))
                                :is-active (contains? selected id)}])))]))

(defn action-item [{:keys [shortcut name]} {:keys [is-selected]}]
  [:> Text {:bold is-selected
            :color (when is-selected "green")
            :wrap "truncate-end"}
      (or shortcut " ") " " name])

(defn action-menu [{:keys [actions on-activate on-cancel]}]
  (let [{:keys [selected-index is-focused]}
        (use-selectable-list {:items actions
                              :on-activate on-activate
                              :on-cancel on-cancel
                              :auto-focus true})]
    [:> Box {:flex-direction "column"
             :border-style "single"
             :border-color (when is-focused "green")
             :width 20}
      (->> actions
          (map-indexed
           (fn [idx item]
             ^{:key idx}
             [action-item item {:is-selected (= idx selected-index)}])))]))

(defn input-panel [{:keys [label default-value on-submit on-cancel]}]
  (let [is-focused (.-isFocused (ink/useFocus #js{:autoFocus true}))]
    [:> Box {:border-style "single"
             :border-color (when is-focused "green")
             :flex-direction "column"}
      (when label
        [:> Box
         [:> Text label]])
      [:> Box
        [:f> uncontrolled-text-input {:on-submit on-submit
                                      :on-cancel on-cancel
                                      :focus is-focused
                                      :default-value default-value}]]]))

#_(cond-> status
    (:is_playing status)
    (update :progress_ms
           #(Math/min
              (+ % (* tick 1000))
              (-> status :item :duration_ms))))

(defn status-bar []
  (let [[playback set-playback] (react/useState nil)
        {:keys [is_playing progress_ms shuffle_state repeat_state device item]} playback
        {:keys [duration_ms album artists] item-name :name} item]
    (react/useEffect
      (fn []
        (let [on-interval (fn []
                            (-> (model/get-player+)
                                (.then (fn [body]
                                         (let [status (js->clj body :keywordize-keys true)]
                                           (set-playback status))))))
              interval-id (js/setInterval on-interval 5000)]
          (on-interval)
          #(js/clearInterval interval-id)))
      #js [])
    [:> Box {:border-style "single"
             :flex-direction "column"
             :padding-x 1}
      (if is_playing
        [:> Box {:justify-content "center"}
          [:> Text {:dim-color true} "playing "]
          [:> Text item-name]
          [:> Text {:dim-color true} " by "]
          [:> Text (str/join ", " (map :name artists))]
          #_[:> Text {:dim-color true} " from "]
          #_[:> Text (:name album)]]
        [:> Box {:justify-content "center"}
          [:> Text {:dim-color true} "stopped"]])
      [:> Box {:margin-top 1}
        [:> Text (:name device)]
        [:> Box {:flex-grow 1}]
        [:> Text {:dim-color true} "Volume "]
        [:> Text (:volume_percent device) "%"]
        [:> Box {:flex-grow 1}]
        [:> Text (format-duration progress_ms)]
        [:> Text {:dim-color true} " / "]
        [:> Text (format-duration duration_ms)]
        [:> Box {:flex-grow 1}]
        [:> Text {:dim-color (not shuffle_state)}
            "Shuffle " (if shuffle_state "on" "off")]
        [:> Box {:flex-grow 1}]
        [:> Text {:dim-color (= repeat_state "off")}
            "Repeat " repeat_state]]]))

(defn shortcut-bar []
  [:> Box
    [:> Text
     "x: menu, "
     (->> player-actions
          (filter :shortcut)
          (map (fn [{:keys [shortcut name]}]
                 (str shortcut ": " name)))
          (str/join ", "))
     ", q: quit"]])

(defn app []
  #_(hooks/use-fullscreen)
  (let [app (ink/useApp)
        size (hooks/use-window-size)
        focus-manager (ink/useFocusManager)

        [{:keys [playlist-order playlists selected-playlist playlist-tracks actions active-input-panel]} dispatch]
        (react/useReducer reducer-fn nil init-state)]
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
    (react/useEffect
      (fn []
        ; (.focusNext focus-manager)
        (-> (model/get-all-playlists+)
            (.then (fn [{:keys [items]}]
                     (dispatch [:set-playlists items])
                     (when-let [id (-> items first :id)]
                       (dispatch [:set-selected-playlist id])
                       (-> (model/get-playlist-tracks+ id)
                           (.then (fn [body]
                                     (dispatch [:set-playlist-tracks id (-> body (js->clj :keywordize-keys true) :items)]))))))))

        js/undefined)
      #js [])
    [:> Box {:width (:cols size)
             :height (dec (:rows size))
             :flex-direction "column"}
     (case (:type active-input-panel)
       :playlist-rename
       (let [{:keys [id name]} (:arg active-input-panel)]
         [:f> input-panel {:label (str "Rename playlist '" name "':")
                           :default-value name
                           :on-submit (fn [value]
                                        ;; TODO invalidate current values
                                        (model/playlist-rename+ id value)
                                        (dispatch [:close-input-panel]))
                           :on-cancel #(dispatch [:close-input-panel])}])
       :playlist-edit-description
       (let [{:keys [id name description]} (:arg active-input-panel)]
        [:f> input-panel {:label (str "Edit description for playlist '" name "':")
                          :default-value description
                          :on-submit (fn [value]
                                       ;; TODO invalidate current values
                                       (model/playlist-change-description+ id value)
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
                                            (-> (model/get-playlist-tracks+ id)
                                                (.then (fn [body]
                                                          (dispatch [:set-playlist-tracks id (-> body (js->clj :keywordize-keys true) :items)])))))}]]

      [:f> tracks-panel {:playlist (get playlists selected-playlist)
                         :tracks (get playlist-tracks selected-playlist)
                         :on-menu #(dispatch [:open-action-menu (concat track-actions [action-separator] player-actions)])
                         :on-activate (fn [item]
                                        (let [playlist (get playlists selected-playlist)]
                                          (model/player-play+
                                           {:context_uri (:uri playlist)
                                            :offset {:uri (-> item :track :uri)}})))}]]
                                                 ;;:uris [(:uri track)]})))}]]
     #_[:f> status-bar]
     [shortcut-bar]]))




(defn render []
  (reset! !app (ink/render (r/as-element [:f> app]))))

(defn -main []
  (render))

(defn ^:dev/after-load reload! []
  (.rerender ^js/InkInstance @!app (r/as-element [:f> app]))
  #_(render))

