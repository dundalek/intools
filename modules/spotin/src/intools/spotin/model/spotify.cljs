(ns intools.spotin.model.spotify
  (:refer-clojure :exclude [get])
  (:require [abort-controller :refer [AbortController]]
            [clojure.core :as clojure]
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

;; Playback API does not seem to have Read-your-writes consistency,
;; wait 2 seconds before trying to fetch status update.
(def player-update-delay 2000)

(defn uri->id [uri]
  (-> (str/split uri #":")
      (nth 2)))

(defn authorization-url []
  (str "https://accounts.spotify.com/authorize"
       "?response_type=code"
       "&scope=" (js/encodeURIComponent (str/join " " scopes))
       "&redirect_uri=" (js/encodeURIComponent redirect-uri)
       "&client_id=" (js/encodeURIComponent client-id)))

(def content-types
  {:form "application/x-www-form-urlencoded"
   :json "application/json"})

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

(defn request->url [{:keys [url query-params]}]
  (if (seq query-params)
    (str url "?" (encode-query-params query-params))
    url))

(defn request->fetch-options [{:keys [method url headers query-params body content-type accept signal]}]
  (cond-> {:method (str/upper-case (name method))
           :headers headers}

    (and (= content-type :json) (map? body))
    (-> (assoc :body (js/JSON.stringify (clj->js body)))
        (update :headers assoc :Content-Type (clojure/get content-types content-type)))

    (and (= content-type :form) (map? body))
    (-> (assoc :body (encode-form-params body))
        (update :headers assoc :Content-Type (clojure/get content-types content-type)))

    accept (update :headers assoc :Accept (clojure/get content-types accept))
    signal (assoc :signal signal)
    :always clj->js))

(defn parse-json-response [response]
  (if (.-ok response)
    (if (not= (.-status response) 204)
      (-> (.text response)
          (.then (fn [text]
                   (if-not (str/blank? text)
                     (js/JSON.parse text)
                     response))))
      response)
    (throw response)))

(def parse-json-response-interceptor
  {:leave (fn [ctx]
            (-> (js/Promise.resolve (:response ctx))
                (.then parse-json-response)
                (.then #(assoc ctx :response %))))})

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

(defn add-authorization-header [opts]
  (update opts :headers assoc :Authorization (str "Bearer " *access-token*)))

(def authorize-interceptor
  {:enter (fn [ctx]
            (update ctx :request add-authorization-header))})

(def js->clj-response-interceptor
  {:leave (fn [ctx]
            (update ctx :response js->clj :keywordize-keys true))})

(def timer-key ::timer-id)

(defn make-timeout-signal-interceptor [timeout-ms]
  {:enter (fn [ctx]
            (let [controller (AbortController.)
                  timer-id (js/setTimeout #(.abort controller) timeout-ms)]
              (-> ctx (assoc timer-key timer-id)
                  (assoc-in [:request :signal] (.-signal controller)))))
   :leave (fn [ctx]
            (js/clearTimeout (timer-key ctx))
            ctx)
   :error (fn [ctx]
            (js/clearTimeout (timer-key ctx))
            ctx)})

(defn expired-token? [^js e]
  (= (.-status e) 401))

(defn request->fetch+ [request]
  (fetch (request->url request)
         (request->fetch-options request)))

(def basic-request-interceptors
  [parse-json-response-interceptor
   (make-timeout-signal-interceptor 10000)
   request->fetch+])

(defn basic-request+ [opts]
  (js/Promise.
   (fn [resolve reject]
     (sieppari/execute basic-request-interceptors opts resolve reject))))

(defn tokens-from-authorization-code+ [code]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :content-type :form
                      :accept :json
                      :body {:grant_type "authorization_code"
                             :code code
                             :redirect_uri redirect-uri}}]
    (-> (basic-request+ auth-options)
        (.then (fn [^js body]
                 (js/console.log body)
                 body)))))

(defn refresh-token+ [rtoken]
  (let [auth-options {:method "POST"
                      :url "https://accounts.spotify.com/api/token"
                      :headers {:Authorization (str "Basic "
                                                    (-> (js/Buffer.from (str client-id ":" client-secret))
                                                        (.toString "base64")))}
                      :content-type :form
                      :accept :json
                      :body {:grant_type "refresh_token"
                             :refresh_token rtoken}}]
    (-> (basic-request+ auth-options)
        (.then (fn [^js body]
                 (let [token (.-access_token body)]
                   (set! *access-token* token)
                   token))))))

(defn re-execute-context [{:keys [queue] :as ctx}]
  (let [ctx (dissoc ctx :stack :queue)]
    (js/Promise.
     (fn [resolve reject]
       (sieppari/execute-context queue ctx resolve reject)))))

(def refresh-interceptor
  {:enter (fn [ctx]
            (let [ctx (assoc ctx ::refresh-ctx ctx)]
              (if *access-token*
                ctx
                (-> (refresh-token+ refresh-token)
                    (.then (fn [] ctx))))))
   :error (fn [{:keys [error stack] :as ctx}]
            (if-not (expired-token? error)
              ctx
              ;; Naive implementation: we might get multiple refreshes for concurrent requests.
              (-> (refresh-token+ refresh-token)
                  (.then #(re-execute-context (::refresh-ctx ctx)))
                  (.then #(assoc % :stack stack)))))})

(def request-interceptors
  [callbacks-interceptor
   refresh-interceptor
   js->clj-response-interceptor
   parse-json-response-interceptor
   authorize-interceptor
   (make-timeout-signal-interceptor 10000)
   request->fetch+])

(defn request+ [opts]
  (js/Promise.
   (fn [resolve reject]
     (sieppari/execute request-interceptors opts resolve reject))))

(defn get [url & [opts]]
  (assoc opts :method :get :url url :content-type :json :accept :json))

(defn put [url & [opts]]
  (assoc opts :method :put :url url :content-type :json :accept :json))

(defn post [url & [opts]]
  (assoc opts :method :post :url url :content-type :json :accept :json))

(defn delete [url & [opts]]
  (assoc opts :method :delete :url url :content-type :json :accept :json))

(defn paginated-get+ [initial-url]
  (let [!items (atom nil)]
    (letfn [(fetch-page+ [url]
              (-> (request+ (get url))
                  (.then (fn [{:keys [items next]}]
                           (swap! !items concat items)
                           (if next
                             (fetch-page+ next)
                             {:items @!items})))))]
      (fetch-page+ initial-url))))

(defn get-playlist [playlist-id]
  (get (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))))

(defn get-all-playlists+ []
  (paginated-get+ "https://api.spotify.com/v1/me/playlists?limit=50"))

(defn get-playlist-tracks+ [playlist-id]
  ;; TODO use paginated-get+
  (request+ (get (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/tracks")
                 {:query-params {:limit 100}})))

(defn playlist-change [playlist-id body]
  (put (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id))
       {:body body}))

(defn playlist-unfollow [playlist-id]
  (delete (str "https://api.spotify.com/v1/playlists/" (js/encodeURIComponent playlist-id) "/followers")))

(defn get-album [album-id]
  (get (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id))))

(defn get-album-tracks+ [album-id]
  ;; TODO use paginated-get+
  (request+ (get (str "https://api.spotify.com/v1/albums/" (js/encodeURIComponent album-id) "/tracks")
                 {:query-params {:limit 50}})))

(defn get-artist [artist-id]
  (get (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id))))

(defn get-artist-albums+ [artist-id]
  ;; TODO use paginated-get+
  ;; TODO include appears_on,compilation album groups later, needs thinking about how to fit them in the UI
  (request+ (get (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/albums")
                 {:query-params {:limit 50 :include_groups "album,single"}})))

(defn get-artist-top-tracks [artist-id]
  (get (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/top-tracks")
       {:query-params {:market "from_token"}}))

(defn get-artist-related-artists [artist-id]
  (get (str "https://api.spotify.com/v1/artists/" (js/encodeURIComponent artist-id) "/related-artists")))

(defn get-player []
  (get "https://api.spotify.com/v1/me/player"))

(defn get-player-devices []
  (get "https://api.spotify.com/v1/me/player/devices"))

(defn create-playlist [user-id {:keys [_name _public _collaborative _description] :as body}]
  (post (str "https://api.spotify.com/v1/users/" (js/encodeURIComponent user-id) "/playlists")
        {:body (merge {:public false :collaborative false} body)}))

(defn player-transfer [device-id]
  (put "https://api.spotify.com/v1/me/player"
       {:body {:device_ids [device-id]}}))

(defn player-queue [uri]
  (post "https://api.spotify.com/v1/me/player/queue"
        {:query-params {:uri uri}}))

(defn player-volume [volume-percent]
  (put "https://api.spotify.com/v1/me/player/volume"
       {:query-params {:volume_percent volume-percent}}))

(defn player-seek [position-ms]
  (put "https://api.spotify.com/v1/me/player/seek"
       {:query-params {:position_ms position-ms}}))

(defn player-play
  ([] (player-play nil))
  ([body]
   (put "https://api.spotify.com/v1/me/player/play"
        {:body body})))

(defn player-pause []
  (put "https://api.spotify.com/v1/me/player/pause"))

(defn player-play-pause [play?]
  (if play?
    (player-play)
    (player-pause)))

(defn player-shuffle [state]
  (put "https://api.spotify.com/v1/me/player/shuffle"
       {:query-params {:state (if state "true" "false")}}))

;; repeat_state
(def repeat-state-transition
  {"context" "track"
   "track" "off"
   "off" "context"})

(defn player-repeat [state]
  (put "https://api.spotify.com/v1/me/player/repeat"
       {:query-params {:state state}}))

(defn player-next []
  (post "https://api.spotify.com/v1/me/player/next"))

(defn player-previous []
  (post "https://api.spotify.com/v1/me/player/previous"))

(defn current-user []
  (get "https://api.spotify.com/v1/me"))

(defn auto-select-device+ []
  (-> (request+ (get-player-devices))
      (.then (fn [{:keys [devices]}]
               (if (= (count devices) 1)
                 (let [device-id (-> devices first :id)]
                   (request+ (player-transfer device-id)))
                 (throw (js/Error. "No active player device detected.")))))))

(defn playback-stopped? [playback]
  (and (not (:is_playing playback))
       (not (-> playback :item :name))))
