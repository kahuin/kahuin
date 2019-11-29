(ns kahuin.web.events
  (:require
    [kahuin.gossip.broker]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-db
  ::initialise-demo-db
  (fn [_ _] {:credentials {:private-key "uBHufbzMbExodFuodWi7tFkNXYhzCzgBhmUCs2KKcd9M1FZX4"
                           :public-key "rxptixh3Nt8eHSi7wmbKgRgqd35tnZLpLJ8ZUKQRtAL32"}
             :profiles {"rxptixh3Nt8eHSi7wmbKgRgqd35tnZLpLJ8ZUKQRtAL32"
                        {:nick "Alice"
                         :pinned #{"Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU" "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"}
                         :following #{"xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"}}
                        "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
                        {:nick "Bob"
                         :pinned #{"fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"}
                         :following #{}}
                        "3D5zcC6mdAs8DXNfZu8zDgBAeo7uBacQdA7JfdVzsxro"
                        {:nick "Carol"
                         :pinned #{}
                         :following #{}}}
             :gossip {"Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU"
                      {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
                       :timestamp #inst "2019-11-27"
                       :content "Lorem ipsum"}
                      "fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"
                      {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
                       :timestamp #inst "2019-11-28"
                       :content "Lorem ipsum"}
                      "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"
                      {:author "M9kNFWqepnxB11C4UysHRWaiPv8PEwxymuCv3vsLYFJx"
                       :timestamp #inst "2019-11-29"
                       :content "Lorem ipsum"}}}))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))