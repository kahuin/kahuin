(ns kahuin.p2p.encoding
  (:require
    [cljs.spec.alpha :as s]
    [clojure.string :as string]
    ["bencode-js" :as bencode]
    ["buffer" :as buffer]
    ["bs58" :as bs58]))

(defn buffer?
  [buf]
  (instance? buffer/Buffer buf))

(defn base58?
  [s]
  (and (string? s)
       (every? (set "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz") s)))

(s/def ::buffer buffer?)
(s/def ::base58 base58?)

(s/def ::bencodeable
  (s/or :bencode-string string?
        :bencode-integer int?
        :bencode-list (s/and sequential?
                             (s/coll-of ::bencodeable))
        :bencode-map (s/map-of (s/and keyword? (comp nil? namespace))
                               ::bencodeable)))

(defn bencodable?
  [c]
  (s/valid? ::bencodeable c))

(defn bencode
  "Bencodes clojure data into string"
  [c]
  (-> c
      (clj->js)
      (bencode/encode)))

(s/fdef bencode :args (s/cat :c ::bencodeable) :ret string?)

(defn bdecode
  "Decodes bencoded string into clojure data"
  [s]
  (-> s
      (bencode/decode)
      (js->clj :keywordize-keys true)))

(s/fdef bdecode :args (s/cat :s string?) :ret ::bencodeable)

(def ^:private encoder (js/TextEncoder.))
(def ^:private decoder (js/TextDecoder.))

(defn clj->buffer
  "Encodes clojure data into buffer"
  [c]
  (->> c
       (bencode)
       (.encode encoder)
       (buffer/Buffer.from)))

(s/fdef clj->buffer :args (s/cat :c any?) :ret ::buffer)

(defn buffer->clj
  "Decodes buffer into clojure data"
  [buf]
  (->> buf
       (.decode decoder)
       (bdecode)))

(s/fdef buffer->clj :args (s/cat :buf ::buffer) :ret any?)

(defn buffer->base58
  [buf]
  (-> buf
      (bs58/encode)
      (string/reverse)))

(s/fdef buffer->base58 :args (s/cat :buf ::buffer) :ret ::base58)

(defn base58->buffer
  [b58]
  (-> b58
      (string/reverse)
      (bs58/decode)))

(s/fdef base58->buffer :args (s/cat :b58 ::base58) :ret ::buffer)