(ns kahuin.p2p.encoding
  (:require
    [cljs.spec.alpha :as s]
    [clojure.edn :as edn]
    ["buffer" :as buffer]
    ["multihashes" :as multihashes]))

(defn buffer?
  [buf]
  (instance? buffer/Buffer buf))

(defn base58?
  [s]
  (and (string? s)
       (every? (set "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz") s)))

(s/def ::buffer buffer?)
(s/def ::base58 base58?)

(def ^:private encoder (js/TextEncoder.))
(def ^:private decoder (js/TextDecoder.))

(defn clj->buffer
  "Encodes clojure data into buffer"
  [c]
  (->> (prn-str c)
       (.encode encoder)
       (.from buffer/Buffer)))

(s/fdef clj->buffer :args (s/cat :c any?) :ret ::buffer)

(defn buffer->clj
  "Decodes buffer into clojure data"
  [buf]
  (-> (.decode decoder buf)
      (edn/read-string)))

(s/fdef buffer->clj :args (s/cat :buf ::buffer) :ret any?)

(defn buffer->base58
  [buf]
  (multihashes/toB58String buf))

(s/fdef buffer->base58 :args (s/cat :buf ::buffer) :ret ::base58)

(defn base58->buffer
  [b58]
  (multihashes/fromB58String b58))

(s/fdef base58->buffer :args (s/cat :b58 ::base58) :ret ::buffer)