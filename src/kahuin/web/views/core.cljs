(ns kahuin.web.views.core
  (:require
    [kahuin.web.routes :as routes]
    [kahuin.web.subs :as subs]
    [kahuin.web.views.components.common :as components]
    [kahuin.web.views.gossip :as gossip]
    [kahuin.web.views.news :as news]
    [kahuin.web.views.pinned :as pinned]
    [kahuin.web.views.profile :as profile]
    [kahuin.web.views.publish :as publish]
    [re-frame.core :as re-frame]))

(defn nav-item
  [icon-name label target {:keys [spinner]}]
  (let [active-panel @(re-frame/subscribe [::subs/active-panel])]
    [:a.flex.flex-row.items-baseline.m-1.sm:mx-0.sm:my-2.text-sm
     {:href (routes/path-for target)
      :class (if (= target active-panel) "text-black" "text-gray-600")}
     (if spinner [components/spinner] [components/icon icon-name])
     [:span.ml-1 label]]))

(defn root
  []
  (let [[active-panel panel-params] @(re-frame/subscribe [::subs/active-panel])]
    [:div.flex.flex-col.sm:flex-row
     [:nav.flex.flex-row.sm:flex-col.items-baseline.p-2.sm:p-3.border-b.sm:border-none
      [:a.mr-auto.text-sm.text-brand-500.font-bold.sm:pb-2 {:href "/"} "Kahuin"]
      [nav-item "news" "News" :news {:spinner false}]
      [nav-item "pin" "Pinned" :pinned]
      [nav-item "post" "Publish" :publish]
      [nav-item "user" "Profile" :profile {:active true}]]
     [:main.flex.flex-col.w-full.sm:max-w-xl.mx-auto.p-2.sm:px-0
      (case active-panel
        :pinned [pinned/panel]
        :publish [publish/panel]
        :profile [profile/own-profile-panel]
        :user-profile [profile/user-profile-panel (:id panel-params)]
        :gossip [gossip/panel (:id panel-params)]
        [news/panel])]]))
