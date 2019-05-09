(ns kahuin.p2p.node-test
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.core.async.impl.protocols :as async-protocols]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.fake-node :as fake-node]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(deftest <new-test
  (async done
    (go
      (testing "create a new node"
        (let [result (a/<! (node/<new {}))]
          (is (some? result))
          (done))))))

(def test-base-58-encoded-node
  "uBHufbzMbExodFuodWi7tFkNXYhzCzgBhmUCs2KKcd9M1FZX4")

(deftest <load-test
  (async done
    (go
      (testing "load node from base58 encoded string"
        (let [result (a/<! (node/<load test-base-58-encoded-node {}))]
          (is (= "rxptixh3Nt8eHSi7wmbKgRgqd35tnZLpLJ8ZUKQRtAL32" (:kahuin.p2p.keys/public result)))
          (done))))))

(deftest start!-stop!-test
  (async done
    (go
      (let [node (a/<! (node/<load test-base-58-encoded-node {}))]
        (testing "start node"
          (node/start! node)
          (is (= [::node/start node] (a/<! (::node/event-ch node)))))
        (testing "stop node"
          (node/stop! node)
          (is (= (async-protocols/closed? (::node/event-ch node)))))
        (done)))))

(def test-base-58-encoded-node-1
  "4XZF1ZEuqGeZXPdjf6QhjyLtVyTz4uAA6a61uemyFnkLFfaZf")

(def test-base-58-encoded-node-2
  "4XZF1Uo42B4SqecMdCTU6PNt727pWMJiSNzsVxpkgSPs9KYCh")

(defn with-test-nodes
  [n f]
  (go (let [use-fake true
            fake-network (when use-fake (fake-node/fake-network))
            nodes (->> (repeatedly n #((if use-fake fake-node/<new node/<new) {:fake-network fake-network}))
                       (a/merge)
                       (a/into [])
                       (a/<!))]
        (dorun (map (if use-fake fake-node/start! node/start!) nodes))
        (println "started" n "test nodes")
        (println (map :kahuin.p2p.keys/public nodes))
        (with-redefs [node/put! (if use-fake fake-node/put! node/put!)
                      node/get! (if use-fake fake-node/get! node/get!)]
          (a/<! (f nodes)))
        (println "stopping" n "test nodes")
        (dorun (map fake-node/stop! nodes)))))

(deftest connection-test
  (async done
    (with-test-nodes 2
      (fn [[node1 & _rest]]
        (let [timeout-ch (a/timeout 30000)]
          (go-loop []
            (let [[[event _node arg] port] (a/alts! [timeout-ch (::node/event-ch node1)])]
              (cond
                (= timeout-ch port)
                (do (is (not :timed-out))
                    (done))

                (= event ::node/error)
                (do (is (not arg))
                    (recur))

                (and (= ::node/connect event) (not= (:kahuin.p2p.keys/public node1) arg))
                (testing "node 1 connects to another node"
                  (is true)
                  (done))

                :default (recur)))))))))

(deftest put!-get!-test
  (async done
    (with-test-nodes 5
      (fn [[node1 & rest]]
        (let [get-count (atom 0)
              timeout-ch (a/timeout 30000)]
          (go-loop []
            (let [[[event node arg1 arg2] port] (a/alts! (concat [timeout-ch (::node/event-ch node1)]
                                                                 (map ::node/event-ch rest)
                                                                 (map ::node/dht-ch rest)))]
              (cond
                (= timeout-ch port)
                (do (is (pos? @get-count))
                    (done))

                (= ::node/error event)
                (let [msg (:message arg1)]
                  (when-not (= "Failed to lookup key! No peers from routing table!")
                    (.warn js/console msg))
                  (recur))

                (and (= ::node/connect event) (= node1 node))
                (do (node/put! node1 "abc" {:data "bar"})
                    (recur))

                (and (= ::node/connect event) (not= node1 node))
                (do (node/get! node "abc")
                    (recur))

                (= ::node/dht:get event)
                (do (is (= ["abc" {:data "bar"}] [arg1 arg2]))
                    (swap! get-count inc)
                    (if (= (count rest) @get-count)
                      (done)
                      (recur)))

                :default (recur)))))))))