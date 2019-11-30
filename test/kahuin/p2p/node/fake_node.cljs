(ns kahuin.p2p.node.fake-node
  "Fake implementation of kahuin.p2p.node that uses a local atom to store the pseudo-DHT
   To use, first create a fake-network to pass as :fake-network option to <new
   Up to 16 fake nodes can be created per network.

   See kahuin.p2p.node ns for more details."
  (:require
    [cljs.core.async :as a :refer [go go-loop]]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.encoding :as encoding]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]
    [cljs.spec.alpha :as s]))

(def ^:private fake-private-keys
  ["Eu1Lx1XED942nmhhUFiw3TGpJZaJRw5dqZyAQ399qxoQ1FZX4"
   "EYqniMvs3Y8QNYEndbB6u2WGL8aqjxhQHC1t5fp33c4W1FZX4"
   "5HioW7MWh57nLszgFLZuRwGLkq1gUJHKF23mdZqK7WzW1FZX4"
   "U5a29VjDwUpbr2uhE3zHG2qNBfd1YL5hERc3nRGamjBJ1FZX4"
   "SUdVQsHt4Qz6oJeR1rqTZkjiSuo2ZzUzf3azWH7iGDxR1FZX4"
   "bYuh1MjUhvCdWwTayVTh2Scayfja1eeHuD12zK4po8CT1FZX4"
   "sPjaRkrpgA13hHJin4bKRQ44TAbmH4EfSMBLYj1iT9fL1FZX4"
   "43ZRPrUHUJT5LVBqu2wAyHxDM4xdafTcYd6jX99VwDfH1FZX4"
   "MTPyWxYvDGU6jhf2jtZ6b8FXAZGHjbaDkgmvsyRpyKrT1FZX4"
   "3MQJTs3zCazu2E8tpzm3BHtaqH4HQKJ848SDAbqVvRUR1FZX4"
   "pDNJyLLpsgYxzRjxfJtuP6Ah2ya6p6ejEQZDvwwUtHsL1FZX4"
   "f3F9rsgowytT5uqu2fHwoLcsR4VwqtjqMwh2AHDathgN1FZX4"
   "HsyXYAfEAWdLm8s4KH2HUzcNQLzUGvkpmcdMYnNqVU2Q1FZX4"
   "kF775eJLcDkSNebiaWbLfYSDKNiyDjKp38se15hg6iSM1FZX4"
   "vmwmgJdkpU8JpnrHnRsiLiUW4ux2zSjmE6eqdPYjf6PP1FZX4"
   "ACXUfX4gjCZodN6ioG5GaUwGLWmZU3NdM4PkoPw2Sd2H1FZX4"])

(defn fake-network
  "Creates a fake network to be used with <new
  Each network supports up to 16 nodes"
  [fake-dht-initial-contents]
  {:dht (atom fake-dht-initial-contents)
   :node-count (atom 0)
   :node-keys (atom #{})})

(defn- handle-fake-dht-put-request
  [{::dht/keys [impl]} key value]
  (swap! (:dht impl) assoc key value)
  nil)

(s/fdef handle-fake-dht-put-request :args (s/cat :dht ::dht/dht :key encoding/base58? :value encoding/bencodable?))

(defn- handle-fake-dht-get-request
  [{::dht/keys [response-ch impl] :as node} key]
  (let [dht-contents @(:dht impl)
        val (get dht-contents key)
        response (if val [::dht/get node key val]
                         [::dht/error
                          node
                          {:when ::dht/get
                           :message (str "Key not found\nFake DHT contents are " dht-contents)}])]
    (a/put! response-ch response)))

(s/fdef handle-fake-dht-get-request :args (s/cat :dht ::dht/dht :key encoding/base58?))

(defn <new
  "Creates a fake node that emulates the behaviour of kahuin.p2p.node
  A :fake-network must be passed in opts."
  [{:keys [fake-network] :as _opts}]
  (assert fake-network "must pass a fake-network in opts")
  (let [ch (a/chan 1)
        index (swap! (:node-count fake-network) inc)
        event-ch (a/chan (a/sliding-buffer 16))
        request-ch (a/chan (a/sliding-buffer 16))
        response-ch (a/chan (a/sliding-buffer 16))
        dht {::dht/impl fake-network
             ::dht/request-ch request-ch
             ::dht/response-ch response-ch}
        node {::node/impl fake-network
              ::node/event-ch event-ch}]
    (go
      (let [keypair (a/<! (keys/<keypair (nth fake-private-keys (dec index))))
            node (merge keypair
                        dht
                        node)]
        (swap! (:node-keys fake-network) conj (::keys/public keypair))
        (a/>! ch node))
      (a/close! ch)
      (go-loop []
        (let [[req key value] (a/<! request-ch)]
          (case req
            ::dht/get (handle-fake-dht-get-request dht key)
            ::dht/put (handle-fake-dht-put-request dht key value)
            (a/put! response-ch [::dht/error (str "Unknown request " req) ::request])))
        (recur)))
    ch))

(s/fdef <new :ret #(instance? cljs.core.async.impl.channels/ManyToManyChannel %))

(defn start!
  "Starts the fake node, sending ::node/connect events to all nodes in the network.
  Caveat: you should first create all the nodes then start them."
  [{::node/keys [event-ch impl] :as node}]
  (a/put! event-ch [::node/start node])
  (doseq [key @(:node-keys impl)]
    (if (not= (::keys/public node) key)
      (a/put! event-ch [::node/connect node {::keys/public key}])))
  nil)

(s/fdef start! :args (s/cat :node ::node/node) :ret nil?)

(defn stop!
  [_node]
  nil)

(s/fdef stop! :args (s/cat :node ::node/node) :ret nil?)