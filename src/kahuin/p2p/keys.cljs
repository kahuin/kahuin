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

(s/def ::veridic boolean?)
(s/def ::verified (s/keys :req [::signature ::veridic]
                          :req-unq [:data]))

(defn- put-error! [ch err when]
  (a/put! ch [::error {:when when :error err}]))

(def ^:private public-key-header
  (js/Uint8Array. [8 2 18 33]))

(defn- short->long-public-key-buffer
  "Adds public-key-header to buffer"
  [buf]
  (let [offset (.-length public-key-header)
        size (+ offset (.-length buf))
        key-buf (buffer/Buffer. size)]
    (.set key-buf public-key-header)
    (.set key-buf buf offset)
    key-buf))

(defn- long->short-public-key-buffer
  "Chops off the first 4 bytes of buf, and checks them against the key-header. Returns nil if the header is bad."
  [buf]
  (let [offset (.-length public-key-header)]
    (when (= (.toString public-key-header) (.toString (js/Uint8Array. (.slice buf 0 offset))))
      (.slice buf offset))))

(defn- private-key->short-public-key-buffer
  [private-key]
  (long->short-public-key-buffer (.. private-key -public -bytes)))

(defn- long-public-key-buffer->public-key
  [buf]
  (crypto/keys.unmarshalPublicKey buf))

(s/fdef long-public-key-buffer->public-key :args (s/cat :buf encoding/buffer?))

(defn- private-key->keypair
  [private-key]
  {::public (-> (private-key->short-public-key-buffer private-key)
                (encoding/buffer->base58))
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

(defn- signed->verified
  [signed veridic]
  (assoc signed ::veridic veridic))

(s/fdef signed->verified
        :args (s/cat :signed ::signed :veridic ::veridic)
        :ret ::verified)

(defn <verified
  ""
  [{::keys [public]} {:keys [data] ::keys [signature] :as signed}]
  (let [ch (a/chan 1)
        public-key (-> (encoding/base58->buffer public)
                       (short->long-public-key-buffer)
                       (long-public-key-buffer->public-key))
        signature-buffer (encoding/base58->buffer signature)]
    (.verify public-key
             (encoding/clj->buffer data)
             signature-buffer
             (fn [err veridic]
               (if err (put-error! ch err ::verification)
                       (a/put! ch (signed->verified signed veridic)))
               (a/close! ch)))
    ch))

(s/fdef <verified
        :args (s/cat :keypair (s/keys :req [::public]), :msg ::signed)
        :ret chan?)

(defn- keypair->PeerId
  [keypair]
  (let [private-key (::private keypair)
        long-public-key (.-public private-key)]
    (PeerId. (private-key->short-public-key-buffer private-key) private-key long-public-key)))

;; Some monkey-patching

(gobj/set
  PeerId
  "createFromPubKey"
  (fn [buf cb]
    (cb nil (PeerId. (long->short-public-key-buffer buf) nil (long-public-key-buffer->public-key buf)))))