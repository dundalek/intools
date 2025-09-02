(ns podcaster.viewer
  (:require
   [portal.ui.api :as pui]
   [portal.ui.rpc :as rpc]
   [portal.ui.inspector :as ins]
   [portal.viewer :as-alias pv]
   [podcaster.main :as-alias main]))

(defn- format-date [timestamp]
  (when timestamp
    (.toLocaleDateString (js/Date. timestamp) "en-US"
                         #js {:year "numeric" :month "short" :day "numeric"})))

(defn- format-duration [milliseconds]
  (when milliseconds
    (let [seconds (Math/floor (/ milliseconds 1000))
          minutes (Math/floor (/ seconds 60))
          hours (Math/floor (/ minutes 60))]
      (if (> hours 0)
        (str hours "h " (mod minutes 60) "m")
        (str minutes "m " (mod seconds 60) "s")))))

(defn- format-timestamp [timestamp]
  (when timestamp
    (.toLocaleDateString (js/Date. timestamp) "en-US" 
                         #js {:year "numeric" :month "short" :day "numeric"
                              :hour "numeric" :minute "2-digit"})))

(defn- episode-item [{:keys [episode kept-episodes on-keep-toggle cutoff-reached?]}]
  (let [episode-id (:feeditem episode)
        is-kept (contains? kept-episodes episode-id)]
    [:div {:class "episode-item"
           :style {:padding "8px"
                   :margin "4px 0"
                   :border "1px solid #ddd"
                   :border-radius "4px"
                   :background-color (cond
                                       (and cutoff-reached? is-kept) "#d4edda"
                                       cutoff-reached? "#f8f9fa"
                                       :else "#d4edda")}}
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "flex-start"}}
      [:div {:style {:flex-grow 1}}
       [:div {:style {:font-weight "bold" :margin-bottom "4px"}}
        (:title episode "Unknown Episode")]
       [:div {:style {:font-size "0.9em" :color "#666" :margin-bottom "4px"}}
        (str (:feed_title episode "Unknown Feed")
             (when (:author episode) (str " • " (:author episode))))]
       [:div {:style {:font-size "0.8em" :color "#999"}}
        (str (format-date (:pubdate episode))
             (when (:duration episode)
               (str " • " (format-duration (:duration episode)))))]]
      [:div {:style {:margin-left "12px"}}
       (when cutoff-reached?
         [:button {:style {:padding "6px 12px"
                           :border "1px solid #007bff"
                           :border-radius "4px"
                           :background-color (if is-kept "#007bff" "#fff")
                           :color (if is-kept "#fff" "#007bff")
                           :cursor "pointer"}
                   :on-click #(do
                                (.stopPropagation %)
                                (on-keep-toggle episode-id))}
          (if is-kept "Unkeep" "Keep")])]]]))

(defn- cutoff-marker []
  [:div {:style {:padding "12px"
                 :margin "8px 0"
                 :border "2px dashed #dc3545"
                 :border-radius "4px"
                 :background-color "#f8d7da"
                 :text-align "center"
                 :font-weight "bold"
                 :color "#721c24"}}
   "↑ Episodes above will be archived ↑"
   [:br]
   "↓ Episodes below will be kept ↓"])

(defn- stats-panel [{:keys [original-size cutoff current-archive-count kept-episodes]}]
  (let [kept-count (count kept-episodes)
        final-size (- original-size (- current-archive-count kept-count))]
    [:div {:style {:padding "16px"
                   :margin-bottom "16px"
                   :border "1px solid #007bff"
                   :border-radius "8px"
                   :background-color "#f8f9fa"}}
     [:div {:style {:display "grid"
                    :grid-template-columns "1fr 1fr 1fr"
                    :gap "16px"}}
      [:div
       [:div {:style {:font-size "0.9em" :color "#666"}} "Original Queue Size"]
       [:div {:style {:font-size "1.5em" :font-weight "bold"}} original-size]]
      [:div
       [:div {:style {:font-size "0.9em" :color "#666"}} "New Size"]
       [:div {:style {:font-size "1.5em" :font-weight "bold" :color "#28a745"}} final-size]]
      [:div
       [:div {:style {:font-size "0.9em" :color "#666"}} "Will be Archived"]
       [:div {:style {:font-size "1.5em" :font-weight "bold" :color "#dc3545"}}
        (- current-archive-count kept-count)]]]]))

(defn- cutoff-controls [{:keys [original-size current-archive-count on-adjust on-save]}]
  [:div {:style {:display "flex"
                 :justify-content "space-between"
                 :align-items "center"}}
   [:div {:style {:text-align "center" :margin-bottom "8px"}}
    [:div {:style {:margin-bottom "8px" :font-weight "bold"}}
     "Queue Target: " (- original-size current-archive-count)]
    [:div {:style {:margin-bottom "8px" :font-weight "bold"}}
     " Episodes to review: " current-archive-count]]
   [:div {:style {:text-align "center"}}
    [:button {:style {:padding "6px 12px"
                      :margin "0 4px"
                      :border "1px solid #6c757d"
                      :border-radius "4px"
                      :background-color "#fff"
                      :cursor "pointer"}
              :on-click #(do (.stopPropagation %)
                             (on-adjust -10))}
     "Review 10 fewer"]
    [:button {:style {:padding "6px 12px"
                      :margin "0 4px"
                      :border "1px solid #6c757d"
                      :border-radius "4px"
                      :background-color "#fff"
                      :cursor "pointer"}
              :on-click #(do (.stopPropagation %)
                             (on-adjust 10))}
     "Review 10 more"]]
   [:button {:style {:padding "12px 24px"
                     :border "none"
                     :border-radius "8px"
                     :background-color "#dc3545"
                     :color "#fff"
                     :font-size "1.1em"
                     :font-weight "bold"
                     :cursor "pointer"}
             :on-click #(do (.stopPropagation %)
                            (on-save))}
    "Archive Episodes!"]])

(defn- queue-list [{:keys [all-episodes cutoff kept-episodes current-archive-count on-keep-toggle]}]
  (let [reversed-episodes (reverse all-episodes)
        archive-count-from-bottom current-archive-count]
    [:div {:style {:margin "16px 0"
                   :max-height "60vh"
                   :overflow-y "auto"
                   :border "1px solid #ddd"
                   :border-radius "8px"
                   :padding "8px"}}
     (when (seq reversed-episodes)
       (for [[idx episode] (map-indexed vector reversed-episodes)]
         (let [is-archived? (< idx archive-count-from-bottom)
               show-cutoff-after? (= idx (dec archive-count-from-bottom))]
           [:div {:key (:feeditem episode)}
            [episode-item {:episode episode
                           :kept-episodes kept-episodes
                           :on-keep-toggle on-keep-toggle
                           :cutoff-reached? is-archived?}]
            (when show-cutoff-after?
              [cutoff-marker])])))]))

(defn- details-panel [{:keys [original-size cutoff current-archive-count kept-episodes on-adjust on-save]}]
  [:div
   [stats-panel {:original-size original-size
                 :cutoff cutoff
                 :current-archive-count current-archive-count
                 :kept-episodes kept-episodes}]

   [cutoff-controls {:current-archive-count current-archive-count
                     :original-size original-size
                     :on-adjust on-adjust
                     :on-save on-save}]])

(defn- navigation-bar [{:keys [current-screen on-switch-screen]}]
  [:div {:style {:display "flex"
                 :background-color "#f8f9fa"
                 :border-bottom "2px solid #dee2e6"
                 :margin-bottom "16px"}}
   [:button {:style {:padding "12px 24px"
                     :border "none"
                     :background-color (if (= current-screen :queue) "#007bff" "transparent")
                     :color (if (= current-screen :queue) "#fff" "#007bff")
                     :font-weight "bold"
                     :cursor "pointer"
                     :border-bottom (if (= current-screen :queue) "3px solid #007bff" "3px solid transparent")}
             :on-click #(on-switch-screen :queue)}
    "Queue Management"]
   [:button {:style {:padding "12px 24px" 
                     :border "none"
                     :background-color (if (= current-screen :archived) "#007bff" "transparent")
                     :color (if (= current-screen :archived) "#fff" "#007bff")
                     :font-weight "bold"
                     :cursor "pointer"
                     :border-bottom (if (= current-screen :archived) "3px solid #007bff" "3px solid transparent")}
             :on-click #(on-switch-screen :archived)}
    "Archived Episodes"]
   [:button {:style {:padding "12px 24px" 
                     :border "none"
                     :background-color (if (= current-screen :bumped) "#007bff" "transparent")
                     :color (if (= current-screen :bumped) "#fff" "#007bff")
                     :font-weight "bold"
                     :cursor "pointer"
                     :border-bottom (if (= current-screen :bumped) "3px solid #007bff" "3px solid transparent")}
             :on-click #(on-switch-screen :bumped)}
    "Bumped Episodes"]])

(defn- archived-episode-item [{:keys [episode]}]
  [:div {:class "archived-episode-item"
         :style {:padding "12px"
                 :margin "4px 0"
                 :border "1px solid #ddd"
                 :border-radius "4px"
                 :background-color "#fff"}}
   [:div {:style {:display "flex"
                  :justify-content "space-between"
                  :align-items "flex-start"}}
    [:div {:style {:flex-grow 1}}
     [:div {:style {:font-weight "bold" :margin-bottom "4px"}}
      (:title episode "Unknown Episode")]
     [:div {:style {:font-size "0.9em" :color "#666" :margin-bottom "4px"}}
      (str (:feed_title episode "Unknown Feed")
           (when (:author episode) (str " • " (:author episode))))]
     [:div {:style {:font-size "0.8em" :color "#999" :margin-bottom "4px"}}
      (str "Published: " (format-date (:pubdate episode))
           (when (:duration episode)
             (str " • " (format-duration (:duration episode)))))]
     [:div {:style {:font-size "0.8em" :color "#6c757d"}}
      (str "Archived: " (format-timestamp (:archived_at episode))
           " • Reason: " (:reason episode "unknown"))]]]])

(defn- archived-screen [{:keys [archived-episodes]}]
  [:div {:style {:margin "16px 0"}}
   [:div {:style {:margin-bottom "16px"}}
    [:h3 {:style {:margin "0 0 8px 0" :color "#007bff"}}
     (str "Archived Episodes (" (count archived-episodes) ")")]
    (if (empty? archived-episodes)
      [:div {:style {:padding "24px"
                     :text-align "center"
                     :color "#6c757d"
                     :font-style "italic"}}
       "No episodes have been archived yet."]
      [:div {:style {:max-height "70vh"
                     :overflow-y "auto"
                     :border "1px solid #ddd"
                     :border-radius "8px"
                     :padding "8px"}}
       (for [episode archived-episodes]
         [:div {:key (str (:feeditem_id episode) "-" (:archived_at episode))}
          [archived-episode-item {:episode episode}]])])]])

(defn- bumped-episode-item [{:keys [episode]}]
  [:div {:class "bumped-episode-item"
         :style {:padding "12px"
                 :margin "4px 0"
                 :border "1px solid #ddd"
                 :border-radius "4px"
                 :background-color "#fff"}}
   [:div {:style {:display "flex"
                  :justify-content "space-between"
                  :align-items "flex-start"}}
    [:div {:style {:flex-grow 1}}
     [:div {:style {:font-weight "bold" :margin-bottom "4px"}}
      (:title episode "Unknown Episode")]
     [:div {:style {:font-size "0.9em" :color "#666" :margin-bottom "4px"}}
      (str (:feed_title episode "Unknown Feed")
           (when (:author episode) (str " • " (:author episode))))]
     [:div {:style {:font-size "0.8em" :color "#999" :margin-bottom "4px"}}
      (str "Published: " (format-date (:pubdate episode))
           (when (:duration episode)
             (str " • " (format-duration (:duration episode)))))]
     [:div {:style {:font-size "0.8em" :color "#007bff"}}
      (str "Bumped: " (format-timestamp (:bumped_at episode))
           " • Position: " (:queue_position_before episode) " → " (:queue_position_after episode))]]]])

(defn- bumped-screen [{:keys [bumped-episodes]}]
  [:div {:style {:margin "16px 0"}}
   [:div {:style {:margin-bottom "16px"}}
    [:h3 {:style {:margin "0 0 8px 0" :color "#007bff"}}
     (str "Bumped Episodes (" (count bumped-episodes) ")")]
    (if (empty? bumped-episodes)
      [:div {:style {:padding "24px"
                     :text-align "center"
                     :color "#6c757d"
                     :font-style "italic"}}
       "No episodes have been bumped yet."]
      [:div {:style {:max-height "70vh"
                     :overflow-y "auto"
                     :border "1px solid #ddd"
                     :border-radius "8px"
                     :padding "8px"}}
       (for [episode bumped-episodes]
         [:div {:key (str (:feeditem_id episode) "-" (:bumped_at episode))}
          [bumped-episode-item {:episode episode}]])])]])

(defn- main-viewer [state]
  (let [{:keys [queue cutoff episodes-to-prune kept-episodes original-size current-archive-count current-screen archived-episodes bumped-episodes]} state
        kept-episodes-set (set kept-episodes)

        handle-keep-toggle (fn [episode-id]
                             (if (contains? kept-episodes-set episode-id)
                               (rpc/call `main/dispatch [::main/unkeep-episode {:episode-id episode-id}])
                               (rpc/call `main/dispatch [::main/keep-episode {:episode-id episode-id}])))

        handle-adjust (fn [delta]
                        (let [new-archive-count (max 0 (+ current-archive-count delta))
                              new-cutoff (- original-size new-archive-count)]
                          (rpc/call `main/dispatch [::main/adjust-cutoff {:cutoff new-cutoff}])))

        handle-save (fn []
                      (rpc/call `main/dispatch [::main/save-changes {}]))

        handle-switch-screen (fn [screen]
                               (rpc/call `main/dispatch [::main/switch-screen {:screen screen}]))]

    [:div {:style {:height "80vh" :padding "16px"}}
     [ins/inspector
      {::pv/default ::navigation-bar}
      {:current-screen current-screen
       :on-switch-screen handle-switch-screen}]

     (case current-screen
       :queue
       [:div
        [ins/inspector
         {::pv/default ::details-panel}
         {:original-size original-size
          :cutoff cutoff
          :current-archive-count current-archive-count
          :kept-episodes kept-episodes-set
          :on-adjust handle-adjust
          :on-save handle-save}]

        [ins/inspector
         {::pv/default ::queue-list}
         {:all-episodes queue
          :cutoff cutoff
          :kept-episodes kept-episodes-set
          :current-archive-count current-archive-count
          :on-keep-toggle handle-keep-toggle}]]

       :archived
       [ins/inspector
        {::pv/default ::archived-screen}
        {:archived-episodes archived-episodes}]

       :bumped
       [ins/inspector
        {::pv/default ::bumped-screen}
        {:bumped-episodes bumped-episodes}]

       [:div "Unknown screen"])]))

(pui/register-viewer!
 {:name ::details-panel
  :predicate (fn [value]
               (and (map? value)
                    (:original-size value)
                    (:current-archive-count value)
                    (:on-adjust value)
                    (:on-save value)))
  :component details-panel
  :doc "Podcaster details panel with stats and controls"})

(pui/register-viewer!
 {:name ::queue-list
  :predicate (fn [value]
               (and (map? value)
                    (:all-episodes value)
                    (:kept-episodes value)
                    (:current-archive-count value)))
  :component queue-list
  :doc "Podcaster queue list viewer"})

(pui/register-viewer!
 {:name ::navigation-bar
  :predicate (fn [value]
               (and (map? value)
                    (:current-screen value)
                    (:on-switch-screen value)))
  :component navigation-bar
  :doc "Navigation bar for switching screens"})

(pui/register-viewer!
 {:name ::archived-screen
  :predicate (fn [value]
               (and (map? value)
                    (:archived-episodes value)))
  :component archived-screen
  :doc "Archived episodes list viewer"})

(pui/register-viewer!
 {:name ::bumped-screen
  :predicate (fn [value]
               (and (map? value)
                    (:bumped-episodes value)))
  :component bumped-screen
  :doc "Bumped episodes list viewer"})

(pui/register-viewer!
 {:name ::main
  :predicate (fn [value]
               (and (map? value)
                    (:queue value)
                    (:cutoff value)
                    (:current-screen value)))
  :component main-viewer
  :doc "Podcaster queue management interface"})
