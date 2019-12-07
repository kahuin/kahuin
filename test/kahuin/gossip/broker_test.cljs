(ns kahuin.gossip.broker-test
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.spec.alpha :as s]
    [cljs.spec.gen.alpha :as gen]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.gossip.broker :as broker]
    [kahuin.gossip.records :as records]
    [kahuin.p2p.crypto :as crypto]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)



(deftest broker-profile
  (async done
    (go
      (let [test-gossip-id (gen/generate (s/gen ::crypto/hash))
            test-peer-id (gen/generate (s/gen ::crypto/hash))
            update-interval 300
            broker (a/<! (broker/<init! {:update-interval update-interval}))
            request-ch (::broker/request-ch broker)
            start-date (js/Date.)]
        (testing "created broker"
          (is (s/valid? ::broker/broker broker))
          (is (some? (::broker/db broker))))
        (testing "can update profile"
          (a/>! request-ch [::broker/update-profile {:nick "foo"}])
          (loop []
            (a/<! (a/timeout 100))
            (let [nick (:nick (broker/profile broker))]
              (if (empty? nick)
                (recur)
                (is (= "foo" nick))))))
        (testing "can publish gossip"
          (a/>! request-ch [::broker/publish-gossip start-date test-gossip-id])
          (loop []
            (a/<! (a/timeout 100))
            (let [pinned (:pinned (broker/profile broker))]
              (if (empty? pinned)
                (recur)
                (is (= #{test-gossip-id} pinned))))))
        (testing "does sync periodically"
          (is (< start-date (:put-at (broker/profile broker)))))
        (done)))))