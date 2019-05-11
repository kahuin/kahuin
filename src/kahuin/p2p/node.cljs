(ns kahuin.p2p.node
  "Nodes are composed of a DHT and a keypair."
  (:require
    [cljs.core.async :as a :refer [go]]
    [cljs.spec.alpha :as s]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.encoding :as encoding]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.transport :as transport]
    ["libp2p" :as Node]
    ["libp2p-kad-dht" :as Kad]
    ["libp2p-mplex" :as mplex]
    ["libp2p-spdy" :as spdy]
    ["peer-info" :as PeerInfo]))

(defn- chan?
  [ch]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel ch))

(s/def ::impl some?)
(s/def ::event-ch chan?)
(s/def ::node (s/and
                ::keys/keypair
                ::dht/dht
                (s/keys :req [::impl ::event-ch])))

(s/def ::peer (s/keys :req [::keys/public]))

(defn- put-node-error! [ch node ^js/Error js-error when]
  (a/put! ch [::error node {:when when :message (.-message js-error)}]))

(defn- peer-info->id
  [peer-info]
  (.toB58String (.-id peer-info)))

(defn- peer-info->peer
  [peer-info]
  {::keys/public (peer-info->id peer-info)})

(s/fdef peer-info->peer :args (s/cat :peer-info some?) :ret ::peer)

(def ^:private default-config
  {:peerDiscovery {:autoDial true
                   :mdns {:enabled true, :interval 1000}
                   :webrtcStar {:enabled true, :interval 1000}}
   :relay {:enabled true, :hop {:enabled false, :active false}}
   :dht {:enabled true
         :kBucketSize 20
         :randomWalk {:enabled true, :interval 300e3, :timeout 10e3}}})

(defn- create-node*
  [peer-info keypair opts]
  (let [id (peer-info->id peer-info)
        transport (transport/init id)
        options {:peerInfo peer-info
                 :modules (merge (:modules transport)
                                 {:streamMuxer [mplex spdy]
                                  :dht Kad})
                 :config default-config}
        impl (Node. (clj->js options))
        node {::impl impl
              ::event-ch (a/chan (a/sliding-buffer 16))
              ::opts opts}
        dht (dht/new (.-dht impl))]
    (.add (.-multiaddrs peer-info) (::transport/address transport))
    (merge keypair dht node)))

(defn- <create-node
  [peer-id keypair opts]
  (let [ch (a/chan 1)]
    (PeerInfo/create
      peer-id
      (fn [err peer-info]
        (if err (put-node-error! ch nil err ::create-node)
                (a/put! ch (create-node* peer-info keypair opts)))
        (a/close! ch)))
    ch))

(s/fdef <create-node :args (s/cat :peer-id some? :keypair ::keys/keypair :opts map?) :ret chan?)

(defn <load
  "Creates and returns a channel that contains the node loaded from the base58-encoded or `[::error nil error-map]`."
  [base58-encoded opts]
  (go
    (let [keypair (a/<! (keys/<keypair base58-encoded))]
      (a/<! (<create-node (keys/keypair->PeerId keypair) keypair opts)))))

(s/fdef <load :args (s/cat :base58-encoded encoding/base58? :opts map?) :ret chan?)

(defn <new
  "Creates and returns a channel that contains a new node or `[::error nil error-map]`."
  [opts]
  (go (let [keypair (a/<! (keys/<new-keypair))
            base58-encoded (keys/keypair->base58 keypair)]
        (a/<! (<load base58-encoded opts)))))

(s/fdef <new :ret chan?)

(defn stop!
  "Stops the node."
  [{::keys [impl event-ch] :as node}]
  (.removeAllListeners impl)
  (.stop impl
         (fn [err]
           (when err
             (put-node-error! event-ch node err ::stop!))
           (a/close! event-ch)))
  nil)

(s/fdef stop! :args (s/cat :node ::node) :ret nil?)

(defn start!
  "Given a node, starts it.
   On start the node's ::event-ch will start getting events, starting with a
   ::start event."
  [{::keys [impl event-ch] :as node}]
  (.on impl "start" (fn [] (a/put! event-ch [::start node])))
  (.on impl "error" (fn [err] (put-node-error! event-ch node err ::impl)))
  (.on impl "peer:connect" (fn [peer-info] (a/put! event-ch [::connect node (peer-info->peer peer-info)])))
  (.on impl "peer:disconnect" (fn [peer-info] (a/put! event-ch [::disconnect node (peer-info->peer peer-info)])))
  (.start impl
          (fn [err]
            (when err
              (put-node-error! event-ch node err ::start!)
              (stop! node))))
  nil)

(s/fdef start! :args (s/cat :node ::node) :ret nil?)
