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

(defn- put-error! [ch dht error-msg when]
  (a/put! ch [::error dht {:when when :message error-msg}]))

(defn- handle-put-request
  [{::keys [impl response-ch] :as dht} key value]
  (.put impl
        (encoding/base58->buffer key)
        (encoding/clj->buffer value)
        (fn [err]
          (when err
            (put-error! response-ch dht (.-message err) ::put!)))))

(defn- handle-get-request
  [{::keys [impl response-ch] :as dht} key]
  (.get impl
        (encoding/base58->buffer key)
        #js {}
        (fn [err buf]
          (if err
            (put-error! response-ch dht (.-message err) ::get!)
            (a/put! response-ch [::get dht key (encoding/buffer->clj buf)])))))

(defn start!
  [{::keys [request-ch response-ch] :as dht}]
  (go-loop []
    (let [[req key value] (a/<! request-ch)]
      (case req
        ::get (handle-get-request dht key)
        ::put (handle-put-request dht key value)
        (put-error! response-ch dht (str "Unknown request " req) ::request)))
    (recur)))