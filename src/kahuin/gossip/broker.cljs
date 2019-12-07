(ns kahuin.gossip.broker
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [kahuin.gossip.records :as records]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]
    [cljs.spec.alpha :as s]))

(defn- chan?
  [c]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel c))

(defn- conj-to-set
  "Like conj but ensure result is a set, so we can disj"
  [coll & xs]
  (set (apply conj coll xs)))

(s/def ::request-ch chan?)
(s/def ::response-ch chan?)
(s/def ::broker (s/and ::node/node
                       (s/keys :req [::request-ch ::response-ch])))

(defn- <update-timer
  [millis]
  (let [ch (a/chan (a/dropping-buffer 1))]
    (js/setInterval #(a/put! ch [::timer (js/Date.)]) millis)
    ch))

(defn- <create-broker
  [{:keys [private-key db update-interval]
    :or {update-interval 20000}
    :as opts}]
  (go
    (let [node-opts (dissoc opts :db :update-interval :private-key)
          node (a/<! (if private-key (node/<load private-key node-opts)
                                     (node/<new node-opts)))
          request-ch (a/chan (a/sliding-buffer 16))
          response-ch (a/chan (a/sliding-buffer 16))
          timer-ch (<update-timer update-interval)
          db (or db (atom {}))
          broker (assoc node ::request-ch request-ch
                             ::response-ch response-ch
                             ::timer-ch timer-ch
                             ::db db)]
      broker)))

(defn- put-record-to-dht!
  [broker record]
  (go
    (.info js/console "Putting record to dht" record)
    (let [[result & args] (a/<! (records/<to-dht-key-value broker record))]
      (if (= ::records/error result)
        (a/put! (::response-ch broker) [::error args])
        (do (a/put! (::dht/request-ch broker) (into [::dht/put] args))
            true)))))

(defn profile
  [{::keys [db]}]
  (:profile @db))

(defn- sync-profile!
  [{::keys [db] :as broker} now]
  (let [{:keys [nick pinned]} (profile broker)
        profile-contents {:nick (or nick "")
                          :pinned (vec pinned)}]
    (go (when (a/<! (put-record-to-dht! broker {::records/type :profile
                                                ::records/author (::keys/public broker)
                                                ::records/inst now
                                                ::records/contents profile-contents}))
          (swap! db #(assoc-in % [::profile :put-at] now))))))

(defmulti handle-event (fn [_broker event] (first event)))

(defmethod handle-event ::node/start [_broker _ev]
  :noop)

(defmethod handle-event ::node/connect [_broker _ev]
  :noop)

(defmethod handle-event ::update-profile [{::keys [db]} [_ now profile]]
  (swap! db
         (fn [db]
           (update db :profile #(merge % profile {:updated-at now})))))

(defmethod handle-event ::publish-gossip [broker [_ now contents]]
  (put-record-to-dht! broker {::records/type :gossip
                              ::records/author (::keys/public broker)
                              ::records/inst now
                              ::records/contents contents}))

(defmethod handle-event ::timer [broker [_ev now]]
  (let [{:keys [put-at updated-at]} (profile broker)]
    (when (< put-at updated-at)
      (sync-profile! broker now))))

(defmethod handle-event ::stop [_broker _ev]
  :stop)

(defmethod handle-event :default [_broker event]
  (.warn js/console "Unknown event, stopping broker" event)
  :stop)

(defn <init!
  [opts]
  (go
    (let [broker (a/<! (<create-broker opts))
          {request-ch ::request-ch
           timer-ch ::timer-ch
           node-event-ch ::node/event-ch
           dht-response-ch ::dht/response-ch} broker
          in-chs [request-ch node-event-ch dht-response-ch timer-ch]]
      (node/start! broker)
      (go-loop []
        (let [[event _in-ch] (a/alts! in-chs)]
          (if (= :stop (handle-event broker event))
            (a/close! (::response-ch broker))
            (recur))))
      broker)))