(ns kahuin.p2p.crypto
  (:require [cljs.core.async :as a]
            [kahuin.p2p.encoding :as encoding]
            ["buffer" :as buffer]
            [cljs.spec.alpha :as s]))

(s/def ::hash (s/with-gen (s/and ::encoding/base58 #(= (count %) 44))
                          #(encoding/base-58-gen 44)))

(defn <hashed
  [data]
  (let [ch (a/chan 1)
        buf (encoding/clj->bencoded-buffer data)
        promise (js/crypto.subtle.digest "SHA-256" buf)]
    (.then promise
           (fn [hash-buf]
             (a/put! ch (encoding/buffer->base58 (buffer/Buffer.from hash-buf)))))
    (.finally promise #(a/close! ch))
    ch))
