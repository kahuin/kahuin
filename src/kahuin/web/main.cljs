(ns kahuin.web.main
  (:require
    [kahuin.web.views.core :as views]
    [reagent.core :as reagent]))

(defn ^:export render []
  (reagent/render [views/root] (js/document.getElementById "app")))

(defn ^:export init []
  (render))
