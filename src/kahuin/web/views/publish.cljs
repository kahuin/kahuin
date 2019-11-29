(ns kahuin.web.views.publish
  (:require
    [kahuin.web.subs :as subs]
    [kahuin.web.views.components.common :as components]
    [re-frame.core :as re-frame]))

(defn panel []
  (let [profile @(re-frame/subscribe [::subs/my-profile])]
   [components/gossip-card
    {:author-profile profile}
    {:creating true}]))