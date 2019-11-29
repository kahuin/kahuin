(ns kahuin.web.views.news
  (:require
    [kahuin.web.subs :as subs]
    [kahuin.web.views.components.common :as components]
    [re-frame.core :as re-frame]))

(defn panel []
  (let [news @(re-frame/subscribe [::subs/news])]
    [components/gossip-list news]))