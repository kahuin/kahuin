(ns kahuin.web.routes
  (:require
    [bidi.bidi :as bidi]
    [kahuin.web.events :as events]
    [pushy.core :as pushy]
    [re-frame.core :as re-frame]))

(def state (atom {}))

(def routes
  ["/" {"" :news
        "profile" :profile
        "pinned" :pinned
        "publish" :publish
        "<" {[:id] :gossip}
        "@" {[:id] :user-profile}}])

(defn- match [path]
  (bidi/match-route routes path))

(def path-for (partial bidi/path-for routes))

(defn- dispatch-route [matched-route]
  (re-frame/dispatch [::events/set-active-panel [(:handler matched-route) (:route-params matched-route)]]))

(defn init []
  (pushy/start! (pushy/pushy dispatch-route match)))