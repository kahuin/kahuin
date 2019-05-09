(ns kahuin.p2p.fake-node
  (:require
    [cljs.core.async :as a :refer [go]]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.encoding :as encoding]))

(def fake-dht-initial-contents {})

(defn fake-node-public-key
  [i]
  (str "fake-node-" i))

(defn fake-network
  []
  {:dht (atom fake-dht-initial-contents)
   :node-count (atom 0)})

(defn <new
  [{:keys [fake-network] :as _opts}]
  (assert fake-network "must pass a fake-network in opts")
  (a/to-chan [{::fake-network fake-network
               ::node/impl "fake"
               ::node/event-ch (a/chan (a/sliding-buffer 16))
               ::node/dht-ch (a/chan (a/sliding-buffer 16))
               :kahuin.p2p.keys/public (fake-node-public-key (swap! (:node-count fake-network) inc))}]))

(defn load!
  [_base58-encoded opts]
  (<new opts))

(defn start!
  [{::node/keys [event-ch] ::keys [fake-network] :as node}]
  (a/put! event-ch [::node/start node])
  (doseq [i (range @(:node-count fake-network))]
    (a/put! event-ch [::node/connect node (fake-node-public-key i)]))
  nil)

(defn stop!
  [_node]
  nil)

(defn put!
  [{::keys [fake-network]} key value]
  (assert (encoding/base58? key))
  (assert (encoding/bencodable? value))
  (swap! (:dht fake-network) assoc key value)
  nil)

(defn get!
  [{::node/keys [event-ch dht-ch] ::keys [fake-network] :as node} key]
  (assert (encoding/base58? key))
  (let [val (get @(:dht fake-network) key)]
    (if val
      (a/put! dht-ch [::node/dht:get node key val])
      (a/put! event-ch [::node/error node {:when ::node/get! :message "Key not found in fake DHT"}]))))