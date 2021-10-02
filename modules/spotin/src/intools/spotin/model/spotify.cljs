(ns intools.spotin.model.spotify
  (:require [abort-controller :refer [AbortController]]
            [clojure.string :as str]
            [node-fetch :as fetch]
            [sieppari.core :as sieppari])
  (:import (goog.Uri QueryData)))

(def client-id (.. js/process -env -SPOTIFY_CLIENT_ID))
(def client-secret (.. js/process -env -SPOTIFY_CLIENT_SECRET))
(def refresh-token (.. js/process -env -SPOTIFY_REFRESH_TOKEN))
(def redirect-uri "http://localhost:8888/callback")

(def scopes
  ["playlist-read-private"
   "playlist-read-collaborative"
   "playlist-modify-public"
   "playlist-modify-private"
   "user-read-playback-state"
   "user-modify-playback-state"
   "user-read-currently-playing"
   "user-library-read"])

(defonce ^:dynamic *access-token* nil)
(defonce ^:dynamic *before-request-callback* nil)
(defonce ^:dynamic *after-request-callback* nil)
(defonce ^:dynamic *request-error-callback* nil)

(defn uri->id [uri]
  (-> (str/split uri #":")
      (nth 2)))

(defn authorization-url []
  (str "https://accounts.spotify.com/authorize"
       "?response_type=code"
       "&scope=" (js/encodeURIComponent (str/join " " scopes))
       "&redirect_uri=" (js/encodeURIComponent redirect-uri)
       "&client_id=" (js/encodeURIComponent client-id)))

(defn encode-form-params [form-params]
  (reduce (fn [params [k v]]
            (.append params (name k) v)
            params)
          (js/URLSearchParams.)
          form-params))

(defn encode-query-params [query-params]
  (let [qd (QueryData.)]
    (doseq [[k v] query-params]
      (.set qd (name k) v))
    (.toString qd)))

(defn fetch-request [{:keys [url body form json method] :as opts}]
  (let [controller (AbortController.)
        timer-id (js/setTimeout #(.abort controller) 10000)]
    (-> (fetch url (-> (cond-> opts
                         body (assoc :body (js/JSON.stringify body))
                         (and json (not form)) (update :headers assoc :Content-Type "application/json")
                         form (assoc :body (encode-form-params form)))
                       (dissoc :json :form)
                       (assoc :signal (.-signal controller))
                       (clj->js)))
        (.finally (fn []
                    (js/clearTimeout timer-id)))
        (.then (fn [response]
                 (if (.-ok response)
                   (if (and json (not= (.-status response) 204))
                     (-> (.text response)
                         (.then (fn [text]
                                  (if-not (str/blank? text)
                                    (js/JSON.parse text)
                                    response))))
                     response)
                   (throw response)))))))

(defn tokens-from-authorization-code+ [code]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :form {:grant_type "authorization_code"
                             :code code
                             :redirect_uri redirect-uri}
                      :json true}]
    (-> (fetch-request auth-options)
        (.then (fn [^js body]
                 (js/console.log body)
                 body)))))

(defn refresh-token+ [rtoken]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :form {:grant_type "refresh_token"
                             :refresh_token rtoken}
                      :json true}]
    (-> (fetch-request auth-options)
        (.then (fn [^js body]
                 (let [token (.-access_token body)]
                   (set! *access-token* token)
                   token))))))

(defn expired-token? [^js e]
  (= (.-status e) 401))

(defn add-authorization-header [opts]
  (update opts :headers assoc :Authorization (str "Bearer " *access-token*)))

(defn re-execute-context [{:keys [queue] :as ctx}]
  (let [ctx (dissoc ctx :stack :queue)]
    (js/Promise.
     (fn [resolve reject]
       (sieppari/execute-context queue ctx resolve reject)))))

(def callbacks-interceptor
  {:enter (fn [{:keys [request] :as ctx}]
            (when *before-request-callback*
              (*before-request-callback* request))
            ctx)
   :leave (fn [{:keys [request] :as ctx}]
            (when *after-request-callback*
              (*after-request-callback* request))
            ctx)
   :error (fn [{:keys [request error] :as ctx}]
            (when *request-error-callback*
              (*request-error-callback* error request))
            (when *after-request-callback*
              (*after-request-callback* request))
            ctx)})

(def refresh-interceptor
  {:enter (fn [ctx]
            (let [ctx (assoc ctx :refresh-ctx ctx)]
              (if *access-token*
                ctx
                (-> (refresh-token+ refresh-token)
                    (.then (fn [] ctx))))))
   :error (fn [{:keys [error refresh-ctx stack] :as ctx}]
            (if-not (expired-token? error)
              ctx
              ;; Naive implementation: we might get multiple refreshes for concurrent requests.
              (-> (refresh-token+ refresh-token)
                  (.then #(re-execute-context refresh-ctx))
                  (.then #(assoc % :stack stack)))))})

(def authorize-interceptor
  {:enter (fn [ctx]
            (update ctx :request add-authorization-header))})

(def request-interceptors
  [callbacks-interceptor
   refresh-interceptor
   authorize-interceptor
   fetch-request])

(defn request-with-auto-refresh+ [opts]
  (js/Promise.
   (fn [resolve reject]
     (sieppari/execute request-interceptors opts resolve reject))))

(defn request
  ([url] (request url nil))
  ([url {:keys [method query-params body]}]
   (cond-> {:method (str/upper-case (name method))
            :url (if (seq query-params)
                   (str url "?" (encode-query-params query-params))
                   url)
            :json true}
     (map? body) (assoc :body (clj->js body)))))

(defn get-request
  ([url] (get-request url nil))
  ([url opts]
   (request url (assoc opts :method :get))))

(defn put+
  ([url] (put+ url nil))
  ([url opts]
   (request-with-auto-refresh+ (request url (assoc opts :method :put)))))

(defn post+
  ([url] (post+ url nil))
  ([url opts]
   (request-with-auto-refresh+ (request url (assoc opts :method :post)))))

(defn get+ [url]
  (request-with-auto-refresh+ (get-request url)))

(defn get-clj+
  ([url] (get-clj+ url nil))
  ([url opts]
   (-> (request-with-auto-refresh+ (get-request url opts))
       (.then (fn [body] (js->clj body :keywordize-keys true))))))

(defn delete-request [url]
  {:method "DELETE"
   :url url
   :json true})

(defn delete+ [url]
  (request-with-auto-refresh+ (delete-request url)))

(defn get-playlists []
  (get-request "https://api.spotify.com/v1/me/playlists"))

(defn paginated-get+ [initial-url]
  (let [!items (atom nil)]
    (letfn [(fetch-page+ [url]
              (-> (get-clj+ url)
                  (.then (fn [{:keys [items next]}]
                           (swap! !items concat items)
                           (if next
                             (fetch-page+ next)
                             {:items @!items})))))]
      (fetch-page+ initial-url))))

(defn get-playlist+ [playlist-id]
  (get-clj+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))))

(defn get-all-playlists+ []
  (paginated-get+ "https://api.spotify.com/v1/me/playlists?limit=50"))

(defn get-playlist-tracks+ [playlist-id]
  ;; TODO use paginated-get+
  (get-clj+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/tracks")
            {:query-params {:limit 100}}))

(defn playlist-change+ [playlist-id body]
  (put+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))
        {:body body}))

(defn playlist-unfollow+ [playlist-id]
  (delete+ (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/followers")))

(defn get-album+ [album-id]
  (get-clj+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id))))

(defn get-album-tracks+ [album-id]
  ;; TODO use paginated-get+
  (get-clj+ (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id) "/tracks")
            {:query-params {:limit 50}}))

(defn get-artist+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id))))

(defn get-artist-albums+ [artist-id]
  ;; TODO use paginated-get+
  ;; TODO include appears_on,compilation album groups later, needs thinking about how to fit them in the UI
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/albums")
            {:query-params {:limit 50 :include_groups "album,single"}}))

(defn get-artist-top-tracks+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/top-tracks")
            {:query-params {:market "from_token"}}))

(defn get-artist-related-artists+ [artist-id]
  (get-clj+ (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/related-artists")))

(defn get-player+ []
  (get-clj+ "https://api.spotify.com/v1/me/player"))

(defn get-player-devices+ []
  (get-clj+ "https://api.spotify.com/v1/me/player/devices"))

(defn create-playlist+ [user-id {:keys [_name _public _collaborative _description] :as body}]
  (post+ (str "https://api.spotify.com/v1/users/" user-id "/playlists")
         {:body (merge {:public false :collaborative false} body)}))

(defn player-transfer+ [device-id]
  (put+ "https://api.spotify.com/v1/me/player"
        {:body {:device_ids [device-id]}}))

(defn player-volume+ [volume-percent]
  (put+ (str "https://api.spotify.com/v1/me/player/volume")
        {:query-params {:volume_percent volume-percent}}))

(defn player-seek+ [position-ms]
  (put+ (str "https://api.spotify.com/v1/me/player/seek")
        {:query-params {:position_ms position-ms}}))

(defn player-play+
  ([] (player-play+ nil))
  ([body]
   (put+ "https://api.spotify.com/v1/me/player/play"
         {:body body})))

(defn player-pause+ []
  (put+ "https://api.spotify.com/v1/me/player/pause"))

(defn player-play-pause+ [play?]
  (if play?
    (player-play+)
    (player-pause+)))

(defn player-shuffle+ [state]
  (put+ (str "https://api.spotify.com/v1/me/player/shuffle")
        {:query-params {:state (if state "true" "false")}}))

(defn player-toggle-shuffle+ []
  (-> (get-player+)
      (.then (fn [{:keys [shuffle_state]}]
               (player-shuffle+ (not shuffle_state))))))

;; repeat_state
(def repeat-state-transition
  {"context" "track"
   "track" "off"
   "off" "context"})

(defn player-repeat+ [state]
  (put+ (str "https://api.spotify.com/v1/me/player/repeat")
        {:query-params {:state state}}))

(defn player-toggle-repeat+ []
  (-> (get-player+)
      (.then (fn [{:keys [repeat_state]}]
               (player-repeat+ (repeat-state-transition repeat_state))))))

(defn player-next+ []
  (post+ "https://api.spotify.com/v1/me/player/next"))

(defn player-previous+ []
  (post+ "https://api.spotify.com/v1/me/player/previous"))

(defn user-id+ []
  (-> (get+ "https://api.spotify.com/v1/me")
      (.then (fn [^js body]
               (.-id body)))))

(defn auto-select-device+ []
  (-> (get-player-devices+)
      (.then (fn [{:keys [devices]}]
               (if (= (count devices) 1)
                 (let [device-id (-> devices first :id)]
                   (player-transfer+ device-id))
                 (throw (js/Error. "No active player device detected.")))))))

(defn playback-stopped? [playback]
  (and (not (:is_playing playback))
       (not (-> playback :item :name))))
