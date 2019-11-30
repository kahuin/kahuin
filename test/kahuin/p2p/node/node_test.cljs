(ns kahuin.p2p.node.node-test
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.core.async.impl.protocols :as async-protocols]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.node.test-util :as node-test-util]
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
          (is (= "rxptixh3Nt8eHSi7wmbKgRgqd35tnZLpLJ8ZUKQRtAL32" (::keys/public result)))
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

(deftest connection-test-fake
  (async done
    (node-test-util/with-test-nodes 2 #(node-test-util/connection-test % done))))

(deftest put!-get!-test-fake
  (async done
    (node-test-util/with-test-nodes 5 #(node-test-util/put!-get!-test % done))))