(ns kahuin.p2p.encoding
  (:require
    [cljs.spec.alpha :as s]
    [cljs.spec.gen.alpha :as gen]
    [clojure.string :as string]
    [clojure.test.check.generators :as tcheck-gen]
    ["bencode-js" :as bencode]
    ["buffer" :as buffer]
    ["bs58" :as bs58]))

(defn buffer?
  [buf]
  (instance? buffer/Buffer buf))

(def ^:private base58-charset (set "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"))

(defn base58?
  [s]
  (and (string? s)
       (every? base58-charset s)))

(def ^:private base-58-char-gen (gen/elements base58-charset))

(defn base-58-gen
  ([]
   (gen/fmap clojure.string/join (gen/vector base-58-char-gen)))
  ([l]
   (gen/fmap clojure.string/join (gen/vector base-58-char-gen l))))

(s/def ::buffer buffer?)
(s/def ::base58 (s/with-gen base58? base-58-gen))

(s/def ::bencodeable-scalar
  (s/or :bencode-string string?
        :bencode-integer int?))

(def ^:private gen-valid-keyword (gen/fmap keyword tcheck-gen/symbol-name-or-namespace))

(s/def ::bencodeable
  (s/or :bencode-scalar ::bencodeable-scalar
        :bencode-list (s/with-gen (s/and sequential? (s/coll-of ::bencodeable))
                                  (constantly (gen/vector-distinct (s/gen ::bencodeable-scalar))))
        :bencode-map (s/with-gen (s/map-of simple-keyword? ::bencodeable)
                                 (constantly (gen/map gen-valid-keyword (s/gen ::bencodeable-scalar))))))

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

(defn clj->bencoded-buffer
  "Encodes clojure data into buffer"
  [c]
  (->> c
       (bencode)
       (.encode encoder)
       (buffer/Buffer.from)))

(s/fdef clj->bencoded-buffer :args (s/cat :c any?) :ret ::buffer)

(defn bencoded-buffer->clj
  "Decodes buffer into clojure data"
  [buf]
  (->> buf
       (.decode decoder)
       (bdecode)))

(s/fdef bencoded-buffer->clj :args (s/cat :buf ::buffer) :ret any?)

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