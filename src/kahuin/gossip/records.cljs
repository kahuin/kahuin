(ns kahuin.gossip.records
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [cljs.spec.alpha :as s]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.encoding :as encoding]
    [kahuin.p2p.crypto :as crypto]))



(s/def ::dht-key ::crypto/hash)

(s/def ::type #{:profile :gossip})
(s/def ::author ::keys/public)
(s/def ::inst inst?)

(s/def ::nick (s/and string? #(< (count %) 60)))
(s/def ::pinned (s/coll-of ::dht-key))
(s/def ::profile-contents (s/keys :req-un [::nick ::pinned]))
(s/def ::gossip-contents (s/and string? #(< 1 (count %) 1000)))

(s/def ::contents (s/or :profile-contents ::profile-contents
                        :gossip-contents ::gossip-contents))

(s/def ::record (s/keys :req [::type ::author ::inst ::contents]))

(s/def ::signed-message ::keys/signed)

(s/def ::timestamp pos-int?)

(s/def ::data (s/or :profile (s/tuple #{"profile"} ::author ::timestamp ::profile-contents)
                    :gossip (s/tuple #{"gossip"} ::author ::timestamp ::gossip-contents)))

(s/def ::dht-value (s/tuple ::data ::keys/signature))


(defn- inst->timestamp
  [inst]
  (-> inst
      (js/Date.parse)
      (/ 1000)))

(defn- timestamp->inst
  [timestamp]
  (-> (* 1000 timestamp)
      (js/Date.)))

(defn- record->bencodable
  [{::keys [type author inst contents] :as _record}]
  [(name type) author (inst->timestamp inst) contents])

(s/fdef record->bencodable
        :args (s/cat :record ::record)
        :ret ::encoding/bencodeable)

(defn- signed-message->dht-value
  [{::keys/keys [signature] :keys [data] :as _signed-message}]
  [data signature])

(s/fdef signed-message->dht-value
        :args (s/cat :signed-message ::signed-message)
        :ret ::dht-value)

(defn- dht-value->signed-message
  [[data signature]]
  {::keys/signature signature
   :data data})

(defn- bencodable->data
  [[[type-name author timestamp contents] _signature]]
  {::type (keyword type-name)
   ::author author
   ::inst (timestamp->inst timestamp)
   ::contents contents})

(s/fdef dht-value->signed-message
        :args (s/cat :dht-value ::dht-value)
        :ret ::signed-message)

(defn- type-valid?
  [type contents]
  (case type
    :profile (s/valid? ::profile-contents contents)
    :gossip (s/valid? ::gossip-contents contents)))

(defn- <hashed-mutable
  [{::keys [type author _inst _contents] :as _record}]
  (crypto/<hashed [(name type) author]))

(s/fdef <hashed-mutable :args (s/cat :record ::record))

(defn- <hashed-immutable
  [record]
  (crypto/<hashed (record->bencodable record)))

(s/fdef <hashed-immutable :args (s/cat :record ::record))

(defn <dht-key
  [{::keys [type] :as record}]
  (go
    (case type
      :profile (a/<! (<hashed-mutable record))
      :gossip (a/<! (<hashed-immutable record))
      [::error "Invalid type"])))

(s/fdef <dht-key :args (s/cat :record ::record))

(defn <to-dht-key-value
  [keypair record]
  (go
    (try
      (let [dht-key (a/<! (<dht-key record))
            signed-msg (a/<! (keys/<signed keypair {:data (record->bencodable record)}))]
        [::dht-key-value dht-key (signed-message->dht-value signed-msg)])
      (catch js/Error e
             [::error e]))))

(s/fdef <to-dht-key-value :args (s/cat :key (s/keys :req [::keys/public]) :record ::record))

(defn <from-dht-key-value
  "Takes a DHT key-value pair, checks that it represents a valid record and verifies it's signature against
  the claimed author key.
  Returns a channel that contains [::record record] or [::error err]"
  [dht-key dht-value]
  (go
    (let [signed-message (dht-value->signed-message dht-value)
          {::keys [type _inst author contents] :as record} (bencodable->data dht-value)
          key {::keys/public author}
          verified-message (a/<! (keys/<verified key signed-message))]
      (cond (not (type-valid? type contents)) [::error "Invalid record type or contents" record]
            (not (::keys/veridic verified-message)) [::error "Bad record signature"]
            (not= dht-key (a/<! (<dht-key record))) [::error "Invalid record hash" dht-key]
            :default [::record (assoc record ::key key)]))))

(s/fdef <from-dht-key-value :args (s/cat :dht-key ::dht-key
                                         :dht-value ::dht-value))