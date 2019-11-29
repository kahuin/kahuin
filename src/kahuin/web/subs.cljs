(ns kahuin.web.subs
  (:require
    [re-frame.core :as re-frame]))

(re-frame/reg-sub
  ::active-panel
  (fn [db _] (:active-panel db)))

(defn profile-by-user-id
  [db user-id]
  (assoc (get-in db [:profiles user-id]) :id user-id))

(re-frame/reg-sub
  ::profiles-raw
  (fn [db _]
    (:profiles db)))

(re-frame/reg-sub
  ::user-profile
  (fn [db [_ user-id]]
    (profile-by-user-id db user-id)))

(re-frame/reg-sub
  ::my-profile
  (fn [db _]
    (let [user-id (-> db :credentials :public-key)]
      (profile-by-user-id db user-id))))

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

(re-frame/reg-sub
  ::gossip-raw
  (fn [db _]
    (:gossip db)))

(defn gossip-display
  [gossip-raw following-ids pinned-keys profiles-raw]
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
  :<- [::gossip-raw]
  :<- [::following-ids]
  :<- [::pinned-keys]
  :<- [::profiles-raw]
  (fn [[gossip-raw following-ids pinned-keys profiles-raw] _]
    (gossip-display gossip-raw following-ids pinned-keys profiles-raw)))

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
