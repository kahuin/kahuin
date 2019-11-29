(ns kahuin.web.main
  (:require
    [kahuin.web.events :as events]
    [kahuin.web.routes :as routes]
    [kahuin.web.views.core :as views]
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]))

(defn ^:export render []
  (reagent/render [views/root] (js/document.getElementById "app")))

(defn ^:export init []
  (routes/init)
  (re-frame/dispatch [::events/initialise-demo-db])
  (render))
