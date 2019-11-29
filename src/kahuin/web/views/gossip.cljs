(ns kahuin.web.views.gossip
  (:require
    [kahuin.web.subs :as subs]
    [kahuin.web.views.components.common :as components]
    [re-frame.core :as re-frame]))

(defn panel
  [id]
  (let [gossip @(re-frame/subscribe [::subs/gossip-by-id id])]
    [components/gossip-card gossip]))