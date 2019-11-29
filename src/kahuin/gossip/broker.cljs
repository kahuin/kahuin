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
  [{:keys [private-key db update-interval db-key]
    :or {db-key ::profile
         update-interval 20000}
    :as opts}]
  (go
    (let [node (a/<! (if private-key (node/<load private-key opts)
                                     (node/<new opts)))
          request-ch (a/chan (a/sliding-buffer 16))
          response-ch (a/chan (a/sliding-buffer 16))
          timer-ch (<update-timer update-interval)
          db (or db (atom {db-key {}}))
          broker (assoc node ::request-ch request-ch
                             ::response-ch response-ch
                             ::timer-ch timer-ch
                             ::db db
                             ::db-key db-key)]
      broker)))

(defn- put-record-to-dht!
  [broker record]
  (go
    (let [[result & args] (a/<! (records/<to-dht-key-value broker record))]
      (println result)
      (if (= ::records/error result)
        (a/put! (::response-ch broker) [::error args])
        (do (a/put! (::dht/request-ch broker) (into [::dht/put] args))
            true)))))

(defn profile
  [{::keys [db db-key]}]
  (db-key @db))

(defn- update-profile!
  [{::keys [db db-key]} now k f & args]
  (swap! db (fn [val]
              (-> val
                  (update-in [db-key k] #(apply f % args))
                  (assoc-in [db-key :updated-at] now)))))

(defn- assoc-profile!
  [broker now k val]
  (update-profile! broker now k (constantly val)))

(defn- sync-profile!
  [{::keys [db db-key] :as broker} now]
  (let [{:keys [nick pinned]} (profile broker)
        profile-contents {:nick (or nick "")
                          :pinned (vec pinned)}]
    (go (when (a/<! (put-record-to-dht! broker {::records/type :profile
                                                ::records/author (::keys/public broker)
                                                ::records/inst now
                                                ::records/contents profile-contents}))
          (swap! db #(assoc-in % [db-key :put-at] now))))))

(defmulti handle-event (fn [_broker event] (first event)))

(defmethod handle-event ::node/start [_b _ev]
  :noop)

(defmethod handle-event ::node/connect [_b _ev]
  :noop)

(defmethod handle-event ::set-nick [broker [_ now nick]]
  (assoc-profile! broker now :nick nick))

(defmethod handle-event ::pin [broker [_ now id]]
  (update-profile! broker now :pinned conj-to-set id))

(defmethod handle-event ::unpin [broker [_ now id]]
  (update-profile! broker now :pinned disj id))

(defmethod handle-event ::follow [broker [_ now id]]
  (update-profile! broker now :following conj-to-set id))

(defmethod handle-event ::unfollow [broker [_ now id]]
  (update-profile! broker now :following disj id))

(defmethod handle-event ::gossip [broker [_ now contents]]
  (put-record-to-dht! broker {::records/type :gossip
                              ::records/author (::keys/public broker)
                              ::records/inst now
                              ::records/contents contents}))

(defmethod handle-event ::timer [broker [_ev now]]
  (let [{:keys [put-at updated-at]} (profile broker)]
    (when (< put-at updated-at)
      (sync-profile! broker now))))

(defmethod handle-event ::stop [_b _ev]
  :stop)

(defmethod handle-event :default [_b event]
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