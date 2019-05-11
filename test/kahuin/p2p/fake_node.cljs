(ns kahuin.p2p.fake-node
  (:require
    [cljs.core.async :as a :refer [go go-loop]]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.encoding :as encoding]))

(def fake-dht-initial-contents {})

(defn fake-node-public-key
  [i]
  (str "FAKE" i))

(defn fake-network
  []
  {:dht (atom fake-dht-initial-contents)
   :node-count (atom 0)})

(defn- handle-fake-dht-put-request
  [{::keys [network]} key value]
  (assert (encoding/base58? key))
  (assert (encoding/bencodable? value))
  (swap! (:dht network) assoc key value)
  nil)

(defn- handle-fake-dht-get-request
  [{::dht/keys [response-ch] ::keys [network] :as node} key]
  (assert (encoding/base58? key))
  (let [val (get @(:dht network) key)
        response (if val [::dht/get node key val]
                        [::dht/error node {:when ::dht/get :message "Key not found in fake DHT"}])]
    (a/put! response-ch response)))

(defn <new
  [{:keys [fake-network] :as _opts}]
  (assert fake-network "must pass a fake-network in opts")
  (a/to-chan [{::network fake-network
               ::node/impl "fake"
               ::node/event-ch (a/chan (a/sliding-buffer 16))
               ::dht/request-ch (a/chan (a/sliding-buffer 16))
               ::dht/response-ch (a/chan (a/sliding-buffer 16))
               :kahuin.p2p.keys/public (fake-node-public-key (swap! (:node-count fake-network) inc))}]))

(defn start!
  [{::node/keys [event-ch] ::dht/keys [request-ch response-ch] ::keys [network] :as node}]
  (a/put! event-ch [::node/start node])
  (doseq [i (range @(:node-count network))]
    (a/put! event-ch [::node/connect node (fake-node-public-key i)]))
  (go-loop []
    (let [[req key value] (a/<! request-ch)]
      (case req
        ::dht/get (handle-fake-dht-get-request node key)
        ::dht/put (handle-fake-dht-put-request node key value)
        (a/put! response-ch [::dht/error (str "Unknown request " req) ::request])))
    (recur))
  nil)

(defn stop!
  [_node]
  nil)