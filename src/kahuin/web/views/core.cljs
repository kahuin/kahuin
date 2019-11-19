(ns kahuin.web.views.core
  (:require
    [kahuin.web.events :as events]
    [reagent.core :as reagent]))

(defn kahuin-header
  [kahuin {:keys [creating]}]
  (if creating
    [:header.flex.flex-row.text-sm.text-gray-500.whitespace-no-wrap
     [:input.flex-grow.text-black.bg-gray-100.font-sans.text-sm.rounded.font-bold
      {:type "text" :placeholder "anonymous"}]
     [:div.mx-1.text-green-500.font-bold "@68add762"]]
    [:header.flex.flex-row.text-sm.text-gray-500.whitespace-no-wrap
     [:a.m-1.mr-0.sm:ml-0.font-bold
      [:span "Jane Doe"]
      [:span.text-green-500 (take 8 "@75d11c5524394774894dd17dd1367ab3")]]
     [:button.m-1 "Follow"]
     [:a.m-1.font-bold (take 8 ">774894dd17dd1367ab3")]
     [:button.m-1 "Quote"]
     [:button.m-1.sm:ml-auto.sm:mr-0 "Pin"]]))

(defn kahuin-contents
  [contents {:keys [creating]}]
  (if creating
    [:div
     [:textarea.w-full.my-2.font-serif.text-justify.bg-gray-100.rounded {:rows "10"}]
     [:div.flex.flex-row.justify-end.text-gray-500.whitespace-no-wrap.items-baseline
      [:span.spinner.h-3.w-3]
      [:button.ml-auto.text-sm.text-pink-500.font-bold.sm:mr-0 "Publish"]]]
    [:section.m-1.my-2.sm:mx-0.font-serif.text-justify
     contents]))

(defn kahuin
  [opts]
  [:article.py-2.border-b
   [kahuin-header {} opts]
   [kahuin-contents "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum." opts]])

(defn nav-item
  [label link {:keys [active spinner]}]
  [:a.flex.flex-row.items-baseline.m1.sm:mt-2.px-1.text-sm
   {:href  link
    :class (if active "text-black" "text-gray-500")}
   [:span label]
   (when spinner [:span.h-3.w-3.ml-1.spinner])])

(defn root
  []
  [:div.flex.flex-col.sm:flex-row
   [:nav.flex.flex-row.sm:flex-col.items-baseline.py-2.sm:px-3.border-b.sm:border-none
    [:a.m-1.mr-auto.text-sm.text-pink-500.font-bold "Kahuin"]
    [nav-item "News" "" {:spinner true}]
    [nav-item "Pinned" ""]
    [nav-item "Publish" ""]
    [nav-item "Profile" "" {:active true}]]
   [:main.flex.flex-col.max-w-xl.mx-auto
    [kahuin]
    [kahuin {:creating true}]]])
