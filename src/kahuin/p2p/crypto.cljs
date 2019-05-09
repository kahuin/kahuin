(ns kahuin.p2p.crypto
  (:require [cljs.core.async :as a]
            [kahuin.p2p.encoding :as encoding]
            ["buffer" :as buffer]))

(defn <hashed
  [data]
  (let [ch (a/chan 1)
        buf (encoding/clj->buffer data)
        promise (js/crypto.subtle.digest "SHA-512" buf)]
    (.then promise
           (fn [hash-buf]
             (a/put! ch (encoding/buffer->base58 (buffer/Buffer.from hash-buf)))))
    (.finally promise #(a/close! ch))
    ch))
