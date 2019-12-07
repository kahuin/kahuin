(ns kahuin.gossip.re-frame
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [kahuin.gossip.broker :as broker]
    [re-frame.core :as re-frame]))

(re-frame/reg-cofx :now
  (fn [cofx]
    (assoc cofx :now (js/Date.))))

(def with-now
  [(re-frame/inject-cofx :now)])

(re-frame/reg-event-db
  ::initialized
  (fn [db [_ broker]]
    (assoc db ::broker/broker broker)))

(defn init!
  ([]
   (init! {}))
  ([{:keys [handler] :as opts :or {handler ::initialized}}]
   (go
     (let [broker (a/<! (broker/<init! (merge {:db (atom {})} opts)))]
       (re-frame/dispatch [handler broker])))))

(defn put-event!
  "Sends an event as request to the broker registered in db"
  [{:keys [db now]} event]
  (let [[re-frame-event-key & event-values] event
        broker-event-key (keyword 'kahuin.gossip.broker (name re-frame-event-key))]
    (->> (concat [broker-event-key now] event-values)
         (a/put! (-> db ::broker/broker ::broker/request-ch)))))

(re-frame/reg-event-fx
  ::update-profile with-now put-event!)
(re-frame/reg-event-fx ::publish-gossip with-now put-event!)

(re-frame/reg-sub
  ::id
  (fn [db _]
    (-> db ::broker/broker :kahuin.p2p.keys/public)))

(defn broker-db
  [db]
  (some-> db ::broker/broker ::broker/db deref))

(re-frame/reg-sub
  ::profiles
  (fn [db _]
    (:profiles (broker-db db))))

(re-frame/reg-sub
  ::gossip
  (fn [db _]
    (:gossip (broker-db db))))