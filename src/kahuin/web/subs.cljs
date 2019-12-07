(ns kahuin.web.subs
  (:require
    [kahuin.gossip.re-frame :as gossip]
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::active-panel
  (fn [db _] (:active-panel db)))

(re-frame/reg-sub
  ::user-profile
  :<- [::gossip/profiles]
  (fn [profiles [_ id]]
    (assoc (get profiles id) :id id)))

(re-frame/reg-sub
  ::my-profile
  :<- [::gossip/profiles]
  :<- [::gossip/id]
  (fn [[profiles id] _]
    (assoc (get profiles id) :id id)))

(re-frame/reg-sub
  ::following-ids
  :<- [::my-profile]
  (fn [profile _]
    (set (:following profile))))

(re-frame/reg-sub
  ::pinned-keys
  :<- [::my-profile]
  (fn [profile _]
    (set (:pinned profile))))

(defn gossip-display
  [gossip-raw profiles-raw following-ids pinned-keys]
  (->> gossip-raw
       (map (fn [[key gossip]]
              [key (let [author-id (:author gossip)
                         author-profile {:nick (:nick (profiles-raw author-id))
                                         :id author-id}]
                     (assoc gossip :id key
                                   :author-profile author-profile
                                   :following (contains? following-ids author-id)
                                   :pinned (contains? pinned-keys key)))]))
       (into {})))

(re-frame/reg-sub
  ::gossip-display
  :<- [::gossip/gossip]
  :<- [::gossip/profiles]
  :<- [::following-ids]
  :<- [::pinned-keys]
  (fn [[gossip-raw profiles-raw following-ids pinned-keys] _]
    (gossip-display gossip-raw profiles-raw following-ids pinned-keys)))

(re-frame/reg-sub
  ::news
  :<- [::gossip-display]
  (fn [gossip-display _]
    (->> (vals gossip-display)
         (filter :following)
         (sort-by (comp - :timestamp)))))

(re-frame/reg-sub
  ::pinned
  :<- [::gossip-display]
  (fn [gossip-display _]
    (->> (vals gossip-display)
         (filter :pinned)
         (sort-by (comp - :timestamp)))))

(re-frame/reg-sub
  ::gossip-by-author
  :<- [::gossip-display]
  (fn [gossip-display [_ author-id]]
    (filter #(= author-id (:author %)) (vals gossip-display))))

(re-frame/reg-sub
  ::gossip-by-id
  :<- [::gossip-display]
  (fn [gossip-display [_ id]]
    (get gossip-display id)))
