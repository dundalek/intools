(ns file-browser
  (:require
    [portal.ui.api :as pui]
    [portal.ui.rpc :as rpc]
    [portal.ui.inspector :as ins]
    [server :as-alias server]))

(defn format-file-size [size]
  (cond
    (nil? size) ""
    (< size 1024) (str size " B")
    (< size (* 1024 1024)) (str (Math/round (/ size 1024)) " KB")
    (< size (* 1024 1024 1024)) (str (Math/round (/ size 1024 1024)) " MB")
    :else (str (Math/round (/ size 1024 1024 1024)) " GB")))

(defn format-date [date]
  (when date
    (.toLocaleDateString (js/Date. date))))

(defn file-row [{:keys [file selected-file]}]
  (let [is-selected (= (:path file) (:path selected-file))
        is-directory (:is-directory file)]
    [:tr
     {:style {:background-color (when is-selected "#e3f2fd")
              :cursor "pointer"}
      :on-click (fn [ev]
                  (.stopPropagation ev)
                  (rpc/call `server/dispatch {:type :select-file :file file}))
      :on-double-click (fn [ev]
                         (.stopPropagation ev)
                         (when is-directory
                           (rpc/call `server/dispatch {:type :navigate-to :path (:path file)})))}
     [:td {:style {:padding "8px"}}
      (if is-directory "📁" "📄")]
     [:td {:style {:padding "8px" :font-family "monospace"}}
      (:name file)]
     [:td {:style {:padding "8px" :text-align "right"}}
      (format-file-size (:size file))]
     [:td {:style {:padding "8px"}}
      (format-date (:last-modified file))]]))

(defn file-browser-component [state]
  (let [{:keys [current-path files selected-file]} state]
    [:div {:style {:height "80vh" :display "flex" :flex-direction "column"}}
     [:div {:style {:padding "16px" :border-bottom "1px solid #ccc"}}
      [:div {:style {:display "flex" :align-items "center" :gap "8px"}}
       [:button
        {:on-click (fn [ev]
                     (.stopPropagation ev)
                     (rpc/call `server/dispatch {:type :navigate-up}))
         :style {:padding "4px 8px"}}
        "↑ Up"]
       [:span {:style {:font-family "monospace" :font-size "14px"}}
        current-path]]]
     
     [:div {:style {:flex 1 :overflow "auto"}}
      [:table {:style {:width "100%" :border-collapse "collapse"}}
       [:thead
        [:tr {:style {:background-color "#f5f5f5"}}
         [:th {:style {:padding "8px" :text-align "left" :width "40px"}} ""]
         [:th {:style {:padding "8px" :text-align "left"}} "Name"]
         [:th {:style {:padding "8px" :text-align "right" :width "100px"}} "Size"]
         [:th {:style {:padding "8px" :text-align "left" :width "120px"}} "Modified"]]]
       [:tbody
        (for [file files]
          ^{:key (:path file)}
          [file-row {:file file :selected-file selected-file}])]]]])) 

(pui/register-viewer!
 {:name ::file-browser-viewer
  :predicate map?
  :component file-browser-component
  :doc "File browser portlet"})