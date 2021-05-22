(ns intools.hooks
  (:require ["ink/build/components/FocusContext" :refer [default] :rename {default FocusContext}]
            [ink]
            [react]))

(defn use-interval [f delay]
  (react/useEffect
   (fn []
     (let [interval-id (js/setInterval f delay)]
       (f)
       #(js/clearInterval interval-id)))
   #js []))

(def enterAltScreenCommand "\u001b[?1049h")
(def leaveAltScreenCommand "\u001b[?1049l")

(defn use-fullscreen []
  (let [write (-> (ink/useStdout) .-write)]
    (react/useEffect
     (fn []
       (write enterAltScreenCommand)
       #(write leaveAltScreenCommand))
     #js [])))

(defn get-window-size []
  {:cols (.-stdout.columns js/process)
   :rows (.-stdout.rows js/process)})

(defn use-window-size []
  (let [[size set-size] (react/useState (get-window-size))]
    (react/useEffect
     (fn []
       (let [on-resize #(set-size (get-window-size))]
         (.on (.-stdout js/process) "resize" on-resize)
         #(.off (.-stdout js/process) "resize" on-resize)))
     #js [])
    size))

#_(defn use-ref-size [box-ref]
    (let [window-size (use-window-size)
          [viewport set-viewport] (react/useState nil)]
      (react/useEffect
       (fn []
         (set-viewport (js->clj (ink/measureElement (.-current box-ref))
                                :keywordize-keys true))
         js/undefined)
       #js [window-size])
      viewport))

(defn use-ref-size [box-ref]
  ;; window size value is ignored, hook is used just to re-measure when it changes
  (let [_ (use-window-size)
        [viewport set-viewport] (react/useState nil)]
    (react/useEffect
     (fn []
       (when (.-current box-ref)
         (let [measured (js->clj (ink/measureElement (.-current box-ref))
                                 :keywordize-keys true)]
           (when (not= viewport measured)
             (set-viewport measured))))
       js/undefined))
    viewport))

(defn use-scrollable-offset [{:keys [selected-index height]}]
  (let [[offset set-offset] (react/useState 0)]
    (react/useEffect
     (fn []
       (when height
         (set-offset
          (Math/min
           selected-index
           (Math/max offset
                     (- (inc selected-index)
                        height)))))
       js/undefined)
     #js [selected-index height])
    offset))

(defn use-focus
  ([] (use-focus {}))
  ([{:keys [is-active auto-focus id]
     :or {is-active true
          auto-focus false
          id nil}}]
   (let [stdin (ink/useStdin)
         context (react/useContext FocusContext)
         id (react/useMemo #(or id
                                (-> (Math/random) .toString (.slice 2 7)))
                           #js [id])]

     (react/useEffect
      (fn []
        (.add context id #js{:autoFocus auto-focus})
        (fn []
          (.remove context id)))
      #js [id auto-focus])

     (react/useEffect
      (fn []
        (if is-active
          (.activate context id)
          (.deactivate context id))
        js/undefined)
      #js [id is-active])

     (react/useEffect
      (fn []
        (if (or (not (.-isRawModeSupported stdin))
                (not is-active))
          js/undefined
          (do (.setRawMode stdin true)
              (fn []
                (.setRawMode stdin false)))))
      #js [is-active])

     {:is-focused (and (boolean id) (= (.-activeId context) id))})))

(defn use-focus-manager [{:keys [focus-id force]}]
  (let [context (react/useContext FocusContext)
        active-id (.-activeId context)
        [requested-id set-requested-id] (react/useState nil)]
    (react/useEffect
     (fn []
       (set-requested-id focus-id)
       js/undefined)
     #js [focus-id force])
    (react/useEffect
     (fn []
       (cond
         ;; This could get into infinite loop if the focusable does not exist
         ;; Perhaps it would be good to implement cycle detection
         requested-id (if (= active-id requested-id)
                        (when-not force
                          (set-requested-id nil))
                        (.focusNext context))
         ;; Workaround to always have some component focused, e.g. after closing a menu
         (not active-id) (.focusNext context))
       js/undefined)
     #js [requested-id active-id force])
    {:active-focus-id active-id
     :enable-focus (.-enableFocus context)
     :disable-focus (.-disableFocus context)
     :focus-next (.-focusNext context)
     :focus-previous (.-focusPrevious context)}))
