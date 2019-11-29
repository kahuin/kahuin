(ns kahuin.web.views.pinned
  (:require
    [kahuin.web.subs :as subs]
    [kahuin.web.views.components.common :as components]
    [re-frame.core :as re-frame]))

(defn panel []
  (let [pinned @(re-frame/subscribe [::subs/pinned])]
    [components/gossip-list pinned]))