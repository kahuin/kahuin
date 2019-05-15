(ns kahuin.p2p.keys
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [cljs.spec.alpha :as s]
    [cljs.spec.gen.alpha :as gen]
    [kahuin.p2p.encoding :as encoding]
    [goog.object :as gobj]
    ["buffer" :as buffer]
    ["libp2p-crypto" :as crypto]
    ["libp2p-crypto-secp256k1" :as crypto-secp256k1]
    ["peer-id" :as PeerId]))

(defn- chan?
  [ch]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel ch))

(s/def ::data ::encoding/bencodeable)

(def ^:private public-key-gen
  (gen/elements ["MQ29yX3mKYZ74XjwhxMzxScMGZw7ko9W61bFjbQzHkeg"
                 "EyACut6PhLuAyJc2Rs29jHbm6v7pMbaMGqx81D9GPi2v"
                 "WxcYNDv2tYcsL5uQWsnZnmEK41igMjNg5MaQzr4wKUee"
                 "mbDb6r3F5FkA8LgHAPtNU189MgDXraiyyBBwvMxeHUv22"
                 "wxFRJbXWjycffjfVBkwxWcEcfr96C8KAApj11Hmh7jdr"
                 "7RLfC7H3mYEwbJNu4JaSVugXcoNwhag6Vekwvb7hkjT22"
                 "3v8NKKdEL9gbznHALLrCiLv9XqCtYxm2gNqV9oPEMxbx"
                 "XNYE1oHL81tKsV7ThnNwJshEB3NKCjJWjDnnojQNub5p"
                 "kGQJPi8iaLVjJNsUhxTwcyjpafxiAKpQR4ECyFoFSf5z"
                 "wYvGQBddfB1HdHGWYtkZ5EkfP9aQZAt4wN9TPRWcGEGp"
                 "Y6d5JNx8s8uWV8S1ukoeB4eBjcGcMjpedUMNaz8ryAzg"
                 "k4pFdYVwcYu3XHHUr95Aqu3dFP1vLfHQ3TiRBxhFezhw"
                 "uG6zsrwg8tAhuiEBoytycw91qiUFfaFfFEeDY7tBGcs32"
                 "ckcWAiy6Mw2hgnJi7C4XoUvh43i751Eaau3ykDzmBBGf"
                 "imKBbn4EjYtaEwJ4katPhrLzxioXmYowiA9q6JgxCkzz"
                 "nrPPYNe3Vq9NRs6NhdmBDod5y2qKBSiD5bvqjv2jLtQq"
                 "JinE1nxbSJsVnvniV8afuKDxiougHTwzvLbXtk14LYXy"
                 "7fhp6rgp2QiMZhxAqzjFhnLGJ5viCCmRue52cvsnAgEm"
                 "FQVQRosUpDc2AU1ZSh6PPA6KCaMj8Z6FLLqX3VfSRxu22"
                 "KkgKg2N9ptX9bwk3jr7PhBv8Ge4SK1Xpk3pxaCF6Ztiw"
                 "hTRQUDrSDFFxctxTXEntEcfXnYavHj6eT8tNV8VNT39e"
                 "tBYeGFm4XybsF6SNUZDQHbHPVgQjJ3fLEQvMYTGTrG7d"
                 "aNaaY37T8T4eQ1mpoentaSgMSdX4aQyKHTHtNF64CvAv"
                 "cwT4hg671h3YqJhMQRCpWv9kwfFmSgazaX4Q7YYD3zo62"
                 "KTSyZ62xHe5X9mwkmMiVjvSkGDTL3AP6e1AT8Af8NND52"
                 "fVWvmTD1N9U9Ub7cK4BwDgpL5JSrrit7xKJaexKX8Vam"
                 "FBm5B5J6emjsTUss8YNc2tm1ED1F2FKQVT1nxH4zue4v"]))

(s/def ::public (s/with-gen (s/and ::encoding/base58 #(<= 44 (count %) 45))
                            (constantly public-key-gen)))
(s/def ::private any?)
(s/def ::keypair (s/keys :req [::public ::private]))

(s/def ::message (s/keys :req-un [::data]))

(s/def ::signature (s/with-gen (s/and ::encoding/base58 #(<= 96 (count %) 97))
                               #(encoding/base-58-gen 97)))
(s/def ::signed (s/keys :req [::signature]
                        :req-un [::data]))

(s/def ::veridic boolean?)
(s/def ::verified (s/keys :req [::signature ::veridic]
                          :req-un [::data]))

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
    (.sign private (encoding/clj->bencoded-buffer data) on-sign)
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
             (encoding/clj->bencoded-buffer data)
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