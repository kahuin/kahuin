(ns kahuin.web.subs-test
  (:require
    [cljs.test :refer [deftest testing is async]]
    [kahuin.web.subs :as subs]))

(def test-db
  {:credentials {:private-key "uBHufbzMbExodFuodWi7tFkNXYhzCzgBhmUCs2KKcd9M1FZX4"
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
             :content "Lorem ipsum"}}})

(deftest gossip-list-test
  (is (= {"Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU"
          {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
           :timestamp #inst "2019-11-27"
           :content "Lorem ipsum"
           :id "Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU"
           :author-profile {:nick "Bob", :id "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"}
           :following true
           :pinned true}
          "fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"
          {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
           :timestamp #inst "2019-11-28"
           :content "Lorem ipsum"
           :id "fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"
           :author-profile {:nick "Bob", :id "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"}
           :following true
           :pinned false}
          "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"
          {:author "M9kNFWqepnxB11C4UysHRWaiPv8PEwxymuCv3vsLYFJx"
           :timestamp #inst "2019-11-29"
           :content "Lorem ipsum"
           :id "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"
           :author-profile {:nick nil, :id "M9kNFWqepnxB11C4UysHRWaiPv8PEwxymuCv3vsLYFJx"}
           :following false
           :pinned true}}
         (subs/gossip-display (-> test-db :gossip)
                              (-> test-db :profiles vals first :following)
                              (-> test-db :profiles vals first :pinned)
                              (-> test-db :profiles))))
  (is (= {"Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU"
          {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
           :timestamp #inst "2019-11-27"
           :content "Lorem ipsum"
           :id "Ssc3tKXiBcjDyRP1cCiuvmJt3UaQ3daqzxjKvZ2kpceU"
           :author-profile {:nick "Bob", :id "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"}
           :following false
           :pinned false}
          "fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"
          {:author "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"
           :timestamp #inst "2019-11-28"
           :content "Lorem ipsum"
           :id "fUx9SLXZi4p4X8fM3UjakFhynPpGbdL4cWbE2V51hE8N"
           :author-profile {:nick "Bob", :id "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg"}
           :following false
           :pinned true}
          "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"
          {:author "M9kNFWqepnxB11C4UysHRWaiPv8PEwxymuCv3vsLYFJx"
           :timestamp #inst "2019-11-29"
           :content "Lorem ipsum"
           :id "gDzbNfYsV47GsFd1CD4No6z8VH7RzCbeYUC9cGAX1x1n"
           :author-profile {:nick nil, :id "M9kNFWqepnxB11C4UysHRWaiPv8PEwxymuCv3vsLYFJx"}
           :following false
           :pinned false}}
         (subs/gossip-display (-> test-db :gossip)
                              (-> test-db :profiles vals second :following)
                              (-> test-db :profiles vals second :pinned)
                              (-> test-db :profiles)))))


