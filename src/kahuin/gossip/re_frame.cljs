(ns kahuin.gossip.re-frame
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [kahuin.gossip.broker :as broker]
    [re-frame.core :as rf]))

(rf/reg-cofx :now
  (fn [cofx]
    (assoc cofx :now (js/Date.))))

(def with-now
  [(rf/inject-cofx :now)])

(def ^:private profile-key ::broker/profile)
(def ^:private broker-key ::broker/broker)

(defn init! [opts]
  (go
    (let [broker (a/<! (broker/<init! (merge {:db re-frame.db/app-db, :db-key profile-key} opts)))]
      ((when (:sync opts) rf/dispatch-sync rf/dispatch)
        [::broker-initialized broker]))))

(rf/reg-event-db
  ::initialized
  (fn [db [_ broker]]
    (assoc db broker-key broker)))

(defn put-event!
  "Sends an event as request to the broker registered in db"
  [{:keys [db now]} event]
  (let [[re-frame-event-key & event-values] event
        broker-event-key (keyword 'kahuin.gossip.broker (name re-frame-event-key))]
    (->> (concat [broker-event-key now] event-values)
         (a/put! (-> db broker-key ::broker/request-ch)))))

(rf/reg-event-fx ::set-nick with-now put-event!)
(rf/reg-event-fx ::pin with-now put-event!)
(rf/reg-event-fx ::unpin with-now put-event!)
(rf/reg-event-fx ::follow with-now put-event!)
(rf/reg-event-fx ::unfollow with-now put-event!)
(rf/reg-event-fx ::gossip with-now put-event!)

(defn get-from-profile
  [db [key & _]]
  (let [non-ns-key (keyword (name key))]
    (-> db profile-key non-ns-key)))

(rf/reg-sub ::nick get-from-profile)
(rf/reg-sub ::pinned get-from-profile)
(rf/reg-sub ::following get-from-profile)
(rf/reg-sub ::gossip get-from-profile)