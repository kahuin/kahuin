(ns kahuin.p2p.node
  (:require
    [cljs.core.async :as a :refer [go]]
    [cljs.spec.alpha :as s]
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

(s/def ::impl #(instance? Node %))
(s/def ::event-ch chan?)
(s/def ::dht-ch chan?)
(s/def ::node (s/keys :req [::impl ::event-ch ::dht-ch ::keys/private ::keys/public]))

(s/def ::event (s/cat :event-type keyword? :node ::node :args (s/* some?)))

(defn- put-error! [ch node ^js/Error js-error when]
  (a/put! ch [::error node {:when when :message (.-message js-error)}]))

(defn- peer-info->id
  [peer-info]
  (.toB58String (.-id peer-info)))

(def ^:private default-config
  {:peerDiscovery {:autoDial true
                   :mdns {:enabled true, :interval 1000}
                   :webrtcStar {:enabled true, :interval 1000}}
   :relay {:enabled true, :hop {:enabled false, :active false}}
   :dht {:enabled true
         :kBucketSize 20
         :randomWalk {:enabled true, :interval 300e3, :timeout 10e3}}})

(defn- create-node*
  [peer-info]
  (let [id (peer-info->id peer-info)
        transport (transport/init id)
        options {:peerInfo peer-info
                 :modules (merge (:modules transport)
                                 {:streamMuxer [mplex spdy]
                                  :dht Kad})
                 :config default-config}
        node (Node. (clj->js options))]
    (.add (.-multiaddrs peer-info) (::transport/address transport))
    {::impl node
     ::event-ch (a/chan (a/sliding-buffer 16))
     ::dht-ch (a/chan (a/sliding-buffer 16))}))

(defn- <create-node
  [peer-id keypair]
  (let [ch (a/chan 1)]
    (PeerInfo/create
      peer-id
      (fn [err peer-info]
        (if err (put-error! ch nil err ::create-node)
                (a/put! ch (merge keypair (create-node* peer-info))))
        (a/close! ch)))
    ch))

(s/fdef <create-node :args (s/cat :peer-id some? :keypair ::keys/keypair) :ret chan?)

(defn <load
  "Creates and returns a channel that contains the node loaded from the base58-encoded or `[::error nil error-map]`."
  [base58-encoded]
  (go
    (let [keypair (a/<! (keys/<keypair base58-encoded))]
      (a/<! (<create-node (keys/keypair->PeerId keypair) keypair)))))

(s/fdef <load :args (s/cat :base58-encoded encoding/base58?) :ret chan?)

(defn <new
  "Creates and returns a channel that contains a new node or `[::error nil error-map]`."
  []
  (go (let [keypair (a/<! (keys/<new-keypair))
            base58-encoded (keys/keypair->base58 keypair)]
        (a/<! (<load base58-encoded)))))

(s/fdef <new :ret chan?)

(defn stop!
  ""
  [{::keys [impl event-ch] :as node}]
  (.removeAllListeners impl)
  (.stop impl
         (fn [err]
           (when err
             (put-error! event-ch node err ::stop!))
           (a/close! event-ch)))
  nil)

(s/fdef stop! :args (s/cat :node ::node))

(defn start!
  "Given a node, starts it.
   On start the node's ::ch will start getting events, starting with a
   ::start event."
  [{::keys [impl event-ch] :as node}]
  (.on impl "start" (fn [] (a/put! event-ch [::start node])))
  (.on impl "error" (fn [err] (put-error! event-ch node err ::impl)))
  (.on impl "peer:connect" (fn [peer-info] (a/put! event-ch [::connect node (peer-info->id peer-info)])))
  (.on impl "peer:disconnect" (fn [peer-info] (a/put! event-ch [::disconnect node (peer-info->id peer-info)])))
  (.start impl
          (fn [err]
            (when err
              (put-error! event-ch node err ::start!)
              (stop! node))))
  nil)

(s/fdef start! :args (s/cat :node ::node) :ret nil?)

(defn put!
  ""
  [{::keys [impl event-ch] :as node} key value]
  (.put (.-dht impl)
        (encoding/base58->buffer key)
        (encoding/clj->buffer value)
        (fn [err]
          (when err
            (put-error! event-ch node err ::put!))))
  nil)

(s/fdef put! :args (s/cat :node ::node :key encoding/base58? :value any?) :ret nil?)

(defn get!
  ""
  [{::keys [impl event-ch dht-ch] :as node} key]
  (.get (.-dht impl)
        (encoding/base58->buffer key)
        #js {}
        (fn [err buf]
          (if err
            (put-error! event-ch node err ::get!)
            (a/put! dht-ch [::dht:get node key (encoding/buffer->clj buf)]))))
  nil)

(s/fdef get! :args (s/cat :node ::node :key encoding/base58?) :ret nil?)