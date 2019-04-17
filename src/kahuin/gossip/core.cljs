(ns kahuin.gossip.core
  (:require
    [cljs.core.async :as a]
    [kahuin.p2p.node :as node]))


(defn init!
  []
  (node/start! (a/<! (node/<new))))

