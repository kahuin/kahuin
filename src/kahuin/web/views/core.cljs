(ns kahuin.web.views.core
  (:require
    [kahuin.web.events :as events]
    [reagent.core :as reagent]))

(defn handle-kahuin-swipe
  [action]
  ;(when action (js/alert action))
  )

(defn kahuin
  []
  (let [pinned (reagent/atom false)]
    (fn []
      [:section.kahuin
       [:div.kahuin-header
        [:button.kahuin-header:user (take 6 "75d11c5524394774894dd17dd1367ab3")]
        [:button.kahuin-header:details
         [:span "yesterday at 20:04"]
         [:span "86f8aa4cc6"]
         [:span "6 replies"]]]
       [:div.kahuin-contents
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."]
       [:div.kahuin-footer
        [:button.kahuin-footer:hide-button]
        [:button.kahuin-footer:pin-button
         {:class (when @pinned "pinned")
          :on-click #(swap! pinned not)}]
        [:button.kahuin-footer:reply-button]]])))

(defn root
  []
  [:div
   [:div.root-header
    [:h1.root-header:title "Kahuin"]
    [:div.root-menu
     [:a.root-menu:link.news.active {:href "#news"} "News"]
     [:a.root-menu:link.pinned {:href "#pinned"} "Pinned"]
     [:span.root-menu.spacer]
     [:a.root-menu:link.profile {:href "#profile"} "Profile"]]
    ]
   [:div.kahuin-list
    [kahuin]
    [kahuin]
    [:button.kahuin-more-button [:i "..."] "More"]]])
