(ns kahuin.p2p.dht
  (:require [kahuin.p2p.encoding :as encoding]
            [cljs.spec.alpha :as s]
            [cljs.core.async :as a :refer-macros [go-loop]]))

(defn- chan?
  [ch]
  (instance? cljs.core.async.impl.channels/ManyToManyChannel ch))

(s/def ::impl some?)
(s/def ::request-ch chan?)
(s/def ::response-ch chan?)
(s/def ::dht (s/keys :req [::impl ::request-ch ::response-ch]))

(defn- put-dht-error! [ch dht error-msg when]
  (a/put! ch [::error dht {:when when :message error-msg}]))

(defn- handle-put-request
  [{::keys [impl response-ch] :as dht} key value]
  (.put impl
        (encoding/base58->buffer key)
        (encoding/clj->bencoded-buffer value)
        (fn [err]
          (when err
            (put-dht-error! response-ch dht (.-message err) ::put!)))))

(s/fdef handle-put-request :args (s/cat :dht ::dht :key encoding/base58? :value encoding/bencodable?))

(defn- handle-get-request
  [{::keys [impl response-ch] :as dht} key]
  (.get impl
        (encoding/base58->buffer key)
        #js {}
        (fn [err buf]
          (if err
            (put-dht-error! response-ch dht (.-message err) ::get!)
            (a/put! response-ch [::get dht key (encoding/bencoded-buffer->clj buf)])))))

(s/fdef handle-get-request :args (s/cat :dht ::dht :key encoding/base58?))

(defn new
  "Creates a DHT.
  This contains ::request-ch and ::response-ch channels.
  To perform DHT requests, put either
    [::get key]
  or
    [::put key value]
  pn the ::request-ch.
  The response channel will contain [::get dht key value] or [::error dht {:when ... :message ...}]."
  [impl]
  (let [request-ch (a/chan (a/sliding-buffer 16))
        response-ch (a/chan (a/sliding-buffer 16))
        dht {::impl impl
             ::request-ch request-ch
             ::response-ch response-ch}]
    (go-loop []
      (let [[req key value] (a/<! request-ch)]
        (case req
          ::get (handle-get-request dht key)
          ::put (handle-put-request dht key value)
          (put-dht-error! response-ch dht (str "Unknown request " req) ::request)))
      (recur))
    dht))

(s/fdef new :args (s/cat :impl ::impl) :ret ::dht)