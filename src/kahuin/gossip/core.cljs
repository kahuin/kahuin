(ns kahuin.gossip.core
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [kahuin.p2p.crypto :as crypto]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]))


(defn init!
  [node]
  (let [ch (a/chan (a/sliding-buffer 16))
        profile (atom {})]
    (go
      (node/start! node)
      (loop []
        (let [[event & args] (a/<! (a/alts! [(<update-timer 1000) (::node/event-ch node) (::dht/response-ch node)]))]
          (println event)
          (case event
            ::node/error
            ; TODO
            (recur)

            ::dht/error
            ; TODO
            (recur)

            ::dht/get
            (let [[node key value] args]
              (a/>! ch (a/<! (handle-get node key value)))
              (recur))

            nil
            :done                                           ; break loop

            :default (recur)))))
    (merge
      node
      {::profile profile
       ::ch ch})))