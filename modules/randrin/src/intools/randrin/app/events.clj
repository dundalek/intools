(ns intools.randrin.app.events
  (:require
   [intools.randrin.app.db :as db]
   [intools.randrin.model :as model]
   [re-frame.core :refer [reg-event-db reg-event-fx]]
   [hyperfiddle.rcf :refer [tests]]))

(reg-event-fx :randrin/init
  (fn [_ _]
    ;; TODO: use cofx for xrandr data
    {:db (-> db/default-db
             (assoc :screen (-> (model/list-screens) first)))}))

(reg-event-db :update
  (fn [db [_ & args]]
    (apply update db args)))

(defn db->focusables [db]
  (-> db :focus-manager :focusables))

(defn db->focused-id [db]
  (-> db :focus-manager :active-id))

(defn next-focused-id [focusables active-id]
  (or (and active-id
           (->> focusables
                (drop-while #(not= % active-id))
                next
                first))
      (first focusables)))

(defn prev-focused-id [focusables active-id]
  (next-focused-id (reverse focusables) active-id))

(defn set-focused-id [db active-id]
  (assoc-in db [:focus-manager :active-id] active-id))

(reg-event-db :randrin/focus-next
  (fn [db _]
    (let [id (next-focused-id (db->focusables db) (db->focused-id db))]
      (set-focused-id db id))))

(reg-event-db :randrin/focus-prev
  (fn [db _]
    (let [id (prev-focused-id (db->focusables db) (db->focused-id db))]
      (set-focused-id db id))))

(tests
 (hyperfiddle.rcf/enable!)

 (next-focused-id [:a :b :c] nil) := :a
 (next-focused-id [:a :b :c] :a) := :b
 (next-focused-id [:a :b :c] :b) := :c
 (next-focused-id [:a :b :c] :c) := :a
 (next-focused-id [:a :b :c] :d) := :a
 (next-focused-id [] :a) := nil

 (prev-focused-id [:a :b :c] nil) := :c
 (prev-focused-id [:a :b :c] :a) := :c
 (prev-focused-id [:a :b :c] :b) := :a
 (prev-focused-id [:a :b :c] :c) := :b
 (prev-focused-id [:a :b :c] :d) := :c
 (prev-focused-id [] :a) := nil)
