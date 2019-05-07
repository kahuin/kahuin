(ns kahuin.p2p.keys
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [cljs.spec.alpha :as s]
    [kahuin.p2p.encoding :as encoding]
    [goog.object :as gobj]
    ["buffer" :as buffer]
    ["libp2p-crypto" :as crypto]
    ["libp2p-crypto-secp256k1" :as crypto-secp256k1]
    ["peer-id" :as PeerId]))

(defn- chan?
  [ch]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel ch))

(s/def ::public encoding/base58?)
(s/def ::private any?)
(s/def ::keypair (s/keys :req [::public ::private]))

(s/def ::message (s/keys :req-unq [:data]))

(s/def ::signature encoding/base58?)
(s/def ::signed (s/keys :req [::signature]
                        :req-unq [:data]))

(s/def ::valid boolean?)
(s/def ::validated (s/keys :req [::signature ::valid]
                           :req-unq [:data]))

(defn- put-error! [ch err when]
  (a/put! ch [::error {:when when :error err}]))

(defn- private-key->keypair
  [private-key]
  {::public (encoding/buffer->base58 (.. private-key -public -bytes))
   ::private private-key})

(s/fdef private-key->keypair :args (s/cat :private-key ::private) :ret ::keypair)

(defn <new-keypair
  ""
  []
  (let [ch (a/chan 1)]
    (crypto/keys.generateKeyPair
      "secp256k1"
      25
      (fn [err private-key]
        (if err
          (put-error! ch err ::generate-keypair)
          (a/put! ch (private-key->keypair private-key)))
        (a/close! ch)))
    ch))

(s/fdef <new-keypair :ret chan?)

(defn <keypair
  ""
  [base58-encoded]
  (let [ch (a/chan 1)]
    (crypto/keys.unmarshalPrivateKey
      (encoding/base58->buffer base58-encoded)
      (fn [err private-key]
        (if err (put-error! ch err ::load-keypair)
                (a/put! ch (private-key->keypair private-key)))
        (a/close! ch)))
    ch))

(s/fdef <keypair :args (s/cat :base58-encoded encoding/base58?) :ret chan?)

(defn keypair->base58
  [{::keys [private] :as _keypair}]
  (encoding/buffer->base58 (.-bytes private)))

(s/fdef keypair->base58
        :args (s/cat :keypair ::keypair)
        :ret encoding/base58?)

(defn- message->signed
  [msg sig-buf]
  (assoc msg ::signature (encoding/buffer->base58 sig-buf)))

(s/fdef message->signed
        :args (s/cat :msg ::message :sig-buf encoding/buffer?)
        :ret ::signed)

(defn <signed
  ""
  [{::keys [private]} {:keys [data] :as msg}]
  (let [ch (a/chan 1)
        on-sign (fn [err sig-buf]
                  (if err (put-error! ch err ::sign)
                          (a/put! ch (message->signed msg sig-buf)))
                  (a/close! ch))]
    (.sign private (encoding/clj->buffer data) on-sign)
    ch))

(s/fdef <signed
        :args (s/cat :keypair (s/keys :req [::private]), :msg ::message)
        :ret chan?)

(defn- signed->validated
  [signed valid]
  (assoc signed ::valid valid))

(s/fdef signed->validated
        :args (s/cat :signed ::signed :valid ::valid)
        :ret ::validated)

(defn <validated
  ""
  [{::keys [public]} {:keys [data] ::keys [signature] :as signed}]
  (let [ch (a/chan 1)
        public-key (crypto/keys.unmarshalPublicKey (encoding/base58->buffer public))
        signature-buffer (encoding/base58->buffer signature)]
    (.verify public-key
             (encoding/clj->buffer data)
             signature-buffer
             (fn [err valid]
               (if err (put-error! ch err ::validate)
                       (a/put! ch (signed->validated signed valid)))
               (a/close! ch)))
    ch))

(s/fdef <validated
        :args (s/cat :keypair (s/keys :req [::public]), :msg ::signed)
        :ret chan?)

(defn- keypair->PeerId
  [keypair]
  (let [private-key (::private keypair)
        public-key (.-public private-key)]
    (PeerId. (.-bytes public-key) private-key public-key)))

;; Some monkey-patching

(gobj/set
  PeerId
  "createFromPubKey"
  (fn [pub-key-str-or-buf cb]
    (let [buf (if (string? pub-key-str-or-buf)
                (buffer/Buffer.from pub-key-str-or-buf "base64")
                pub-key-str-or-buf)
          public-key (crypto/keys.unmarshalPublicKey buf)]
      (cb nil (PeerId. buf nil public-key)))))