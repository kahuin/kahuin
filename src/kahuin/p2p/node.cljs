(ns kahuin.p2p.node
  (:require
    [cljs.core.async :as a :refer [go]]
    [cljs.spec.alpha :as s]
    [kahuin.p2p.encoding :as encoding]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.transport :as transport]
    ["libp2p" :as Node]
    ["libp2p-kad-dht" :as Kad]
    ["peer-info" :as PeerInfo]
    ["peer-id" :as PeerId]))

(defn- chan?
  [ch]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel ch))

(s/def ::impl #(instance? Node %))
(s/def ::ch chan?)
(s/def ::node (s/keys :req [::impl ::ch ::keys/private ::keys/public]))

(s/def ::event (s/cat :event-type keyword? :node ::node :args (s/* some?)))

(defn- put-error! [ch node ^js/Error js-error when]
  (a/put! ch [::error node {:when when :message (.-message js-error)}]))

(defn- peer-info->id
  [peer-info]
  (.toB58String (.-id peer-info)))

(defn- create-node*
  [peer-info]
  (let [id (peer-info->id peer-info)
        transport (transport/init id)
        options {:peerInfo peer-info
                 :modules (merge (:modules transport) {:dht Kad})
                 :config (merge (:config transport) {:dht {:enabled true}})}
        node (Node. (clj->js options))]
    (.add (.-multiaddrs peer-info) (::transport/address transport))
    {::impl node
     ::ch (a/chan (a/sliding-buffer 16))}))

(defn- <create-node
  [peer-id keypair]
  (let [ch (a/chan 1)]
    (PeerInfo/create
      peer-id
      (fn [err peer-info]
        (if err (put-error! ch nil err ::create-node)
                (a/put! ch (merge keypair (create-node* peer-info))))))
    ch))

(s/fdef <create-node :args (s/cat :peer-id #(instance? PeerId %) :keypair ::keys/keypair) :ret chan?)

(defn <load
  "Creates and returns a channel that contains the node loaded from the base58-encoded or `[::error nil error-map]`."
  [base58-encoded]
  (go
    (let [keypair (a/<! (keys/<keypair base58-encoded))
          private-key (::keys/private keypair)
          public-key (.-public private-key)
          node (a/<! (<create-node (PeerId. (.-bytes public-key) private-key public-key) keypair))]
      node)))

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
  [{::keys [impl ch] :as node}]
  (.removeAllListeners impl)
  (.stop impl
         (fn [err]
           (when err
             (put-error! ch node err ::stop!))
           (a/close! ch)))
  nil)

(s/fdef stop! :args (s/cat :node ::node))

(defn start!
  "Given a node, starts it.
   On start the node's ::ch will start getting events, starting with a
   ::start event."
  [{::keys [impl ch] :as node}]
  (.on impl "start" (fn [] (a/put! ch [::start node])))
  (.on impl "error" (fn [err] (put-error! ch node err ::impl)))
  (.on impl "connection:start" (fn [peer-info] (a/put! ch [::connect node (peer-info->id peer-info)])))
  (.on impl "connection:end" (fn [peer-info] (a/put! ch [::disconnect node (peer-info->id peer-info)])))
  (.start impl
          (fn [err]
            (when err
              (put-error! ch node err ::start!)
              (stop! node))))
  nil)

(s/fdef start! :args (s/cat :node ::node) :ret nil?)

(defn put!
  ""
  [{::keys [impl ch] :as node} key value]
  (.put (.-dht impl)
        (encoding/base58->buffer key)
        (encoding/clj->buffer value)
        (fn [err]
          (if err
            (put-error! ch node err ::put!)
            (a/put! ch [::dht:put node key value]))))
  nil)

(s/fdef put! :args (s/cat :node ::node :key encoding/base58? :value any?) :ret nil?)

(defn get!
  ""
  [{::keys [impl ch] :as node} key]
  (.get (.-dht impl)
        (encoding/base58->buffer key)
        {}
        (fn [err buf]
          (if err
            (put-error! ch node err ::get!)
            (a/put! ch [::dht:get node key buf]))))
  nil)

(s/fdef get! :args (s/cat :node ::node :key encoding/base58?) :ret nil?)