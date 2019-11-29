(ns kahuin.web.views.components.common
  (:require
    [clojure.contrib.humanize :as humanize]
    [kahuin.web.routes :as routes]))

(defn spinner
  []
  [:span.h-3.w-3.mr-1.spinner])

(defn icon
  [icon-name {:keys [size] :or {size 3}}]
  [:span.icon {:class (str "icon icon-" icon-name " h-" size " w-" size)}])

(defn icon-button
  [{:keys [icon-name] :as attrs} & [label]]
  [:button.flex.items-center.p-1 (dissoc attrs :icon-name)
   label
   [:span.icon.h-3.w-3 {:class (str "icon icon-" icon-name)}]])

(defn- user-id-label
  [id]
  (let [hue (mod (hash id) 360)
        color (str "hsl(" hue ", 50%, 30%)")
        display-id (take 8 (str "@" id))]
    [:span.font-bold.text-sm {:style {:color color}} display-id]))

(defn user-label
  [{:keys [nick id]} {:keys [editable]}]
  (if editable
    [:span.flex.flex-row.w-full.items-baseline.text-sm
     [:input.flex-grow.my-1.text-black.bg-gray-100.font-sans.text-sm.rounded.font-bold
      {:type "text" :placeholder "anonymous"}]
     [user-id-label id]]
    [:a.my-1.font-bold.text-gray-600.text-sm
     {:href (when id (routes/path-for :user-profile :id id))}
     [:span (or nick "anonymous")]
     [user-id-label id]]))

(defn- gossip-header
  [{:keys [id author-profile timestamp following pinned]} {:keys [creating include-user] :or {include-user true}}]
  [:header.flex.flex-row.items-baseline.text-sm.text-gray-600.whitespace-no-wrap
   (when include-user
     [user-label author-profile])
   (when-not creating
     [:<>
      (when include-user
        (if following
          [icon-button {:icon-name "user-added"}]
          [icon-button {:icon-name "user-add"}]))
      (when timestamp
        [:span.pr-1 {:title timestamp} (humanize/datetime timestamp)])
      (when id
        (let [link (routes/path-for :gossip :id id)]
          [:a.font-bold {:href link} (take 8 link)]))
      (if pinned
        [icon-button {:icon-name "pinned" :class "ml-auto pr-0"} [:span.p-1 "Pinned"]]
        [icon-button {:icon-name "pin" :class "ml-auto pr-0"} [:span.p-1 "Pin"]])])])

(defn- gossip-contents
  [{:keys [content]} {:keys [creating]}]
  [:section.sm:mx-0.font-serif.text-justify
   (if creating
     [:<>
      [:textarea.w-full.font-serif.text-justify.bg-gray-100.rounded {:rows "10"}]
      [:div.flex.flex-row.justify-end.text-gray-600.whitespace-no-wrap.items-baseline
       [spinner]
       [:button.ml-auto.text-sm.text-brand-500.font-sans.font-bold.sm:mr-0 "Post"]]]
     content)])

(defn gossip-card
  [gossip opts]
  ^{:key (:id gossip)}
  [:article.pb-3.border-b.last:border-b-0
   [gossip-header gossip opts]
   [gossip-contents gossip opts]])

(defn gossip-list
  [gossip opts]
  [:<> (->> gossip
            (map #(gossip-card % opts))
            (into [:<>]))
   [:div.text-sm.text-gray-600 "more..."]])