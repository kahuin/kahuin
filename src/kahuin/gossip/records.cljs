(ns kahuin.gossip.records
  (:require
    [cljs.spec.alpha :as s]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.encoding :as encoding]))

(s/def ::type #{:profile :gossip})
(s/def ::author ::keys/public)
(s/def ::timestamp string?                                  ;fixme
  )
(s/def ::contents encoding/bencodable?)
(s/def ::record (s/cat :type ::type
                       :author ::author
                       :timestamp ::timestamp
                       :contents ::contents))

(defn <hashed-matches?
  [hash-key hasheable]
  ; todo check if hash of hasheable matches hash-key
  ; r
  )

(defn- <validated-mutable
  [hash-key {::keys [type author] :as record}]
  (when (<hashed-matches? hash-key [type author])
    record))

(defn- <validated-immutable
  [hash-key record]
  (when (<hashed-matches? hash-key record)
    record))

(defmulti <validated (fn [_hash-key msg] (:type msg)))

(defmethod <validated :profile
  [hash-key record]
  (<validated-mutable hash-key record))

(defmethod <validated :gossip
  [hash-key record]
  (<validated-immutable hash-key record))

(defmulti <hashed)