(ns kahuin.web.views.profile
  (:require
    [kahuin.web.views.components.common :as components]
    [kahuin.web.subs :as subs]
    [re-frame.core :as re-frame]))

(defn profile-panel
  [{:keys [id] :as profile} opts]
  (let [gossip @(re-frame/subscribe [::subs/gossip-by-author id])]
    [:<>
     [:form.flex.sm:px-0.w-full
      [components/user-label profile opts]]
     [:h2.py-2.font-bold.text-gray-600 "Publications"]
     [components/gossip-list gossip {:include-user false}]]))

(defn own-profile-panel
  []
  (let [profile @(re-frame/subscribe [::subs/my-profile])]
    [profile-panel profile {:editable true}]))

(defn user-profile-panel
  [id]
  (let [profile @(re-frame/subscribe [::subs/user-profile id])]
    [profile-panel profile]))