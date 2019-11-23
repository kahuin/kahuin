(ns kahuin.web.views.core
  (:require
    [kahuin.web.events :as events]
    [reagent.core :as reagent]))

(defn spinner
  []
  [:span.h-3.w-3.mr-1.spinner])

(defn icon
  [icon-name {:keys [size] :or {size 3}}]
  [:span.icon {:class (str "icon icon-" icon-name " h-" size " w-" size)}])

(defn icon-button
  [{:keys [icon-name] :as attrs} & [label]]
  [:button.flex.items-center.p-1 attrs
   label
   [:span.icon.h-3.w-3 {:class (str "icon icon-" icon-name)}]])

(def colors ["red" "orange" "yellow" "green" "teal" "blue" "indigo" "purple"])

(defn user-label
  [name id]
  [:a.my-1.font-bold
   [:span name]
   (let [color (get colors (mod (hash id) (count colors)))]
     [:span {:class (str "text-" color "-600")} (take 8 (str "@" id))])])

(defn kahuin-header
  [kahuin {:keys [creating]}]
  [:header.flex.flex-row.items-baseline.text-sm.text-gray-500.whitespace-no-wrap
   (if creating
     [:<>
      [:input.flex-grow.my-1.text-black.bg-gray-100.font-sans.text-sm.rounded.font-bold
       {:type "text" :placeholder "anonymous"}]
      [:div.mx-1.text-green-500.font-bold "@68add762"]]
     [:<>
      [user-label "Jane Doe" "7ba5d11c547748947ab3"]
      (if false
        [icon-button {:icon-name "user-added"}]
        [icon-button {:icon-name "user-add"}])
      [:a.font-bold (take 8 ">774894dd17dd1367ab3")]
      (if false
        [icon-button {:icon-name "pinned" :class "ml-auto pr-0"} [:span.p-1 "Pinned"]]
        [icon-button {:icon-name "pin" :class "ml-auto pr-0"} [:span.p-1 "Pin"]])])])

(defn kahuin-contents
  [contents {:keys [creating]}]
  [:section.sm:mx-0.font-serif.text-justify
   (if creating
     [:<>
      [:textarea.w-full.font-serif.text-justify.bg-gray-100.rounded {:rows "10"}]
      [:div.flex.flex-row.justify-end.text-gray-500.whitespace-no-wrap.items-baseline
       [spinner]
       [:button.ml-auto.text-sm.text-pink-500.font-sans.font-bold.sm:mr-0 "Post"]]]
     contents)])

(defn kahuin
  [opts]
  [:article.p-2.sm:px-0.border-b
   [kahuin-header {} opts]
   [kahuin-contents "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum." opts]])

(defn nav-item
  [icon-name label link {:keys [active spinner]}]
  [:a.flex.flex-row.items-baseline.m-1.sm:mx-0.sm:my-2.text-sm
   {:href  link
    :class (if active "text-black" "text-gray-500")}
   (if spinner [spinner] [icon icon-name])
   [:span.ml-1 label]])

(defn root
  []
  [:div.flex.flex-col.sm:flex-row
   [:nav.flex.flex-row.sm:flex-col.items-baseline.p-2.sm:p-4.border-b.sm:border-none
    [:a.mr-auto.text-sm.text-pink-500.font-bold.pb-2 "Kahuin"]
    [nav-item "news" "News" "" {:spinner false}]
    [nav-item "pin" "Pinned" ""]
    [nav-item "post" "Post" ""]
    [nav-item "user" "Profile" "" {:active true}]]
   [:main.flex.flex-col.sm:max-w-xl.mx-auto
    [kahuin]
    [kahuin {:creating true}]]])
