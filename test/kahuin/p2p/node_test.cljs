(ns kahuin.p2p.node-test
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.core.async.impl.protocols :as async-protocols]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.node-test-util :as node-test-util]
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

(defn connection-test [[node1 & _rest] done]
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

          (and (= ::node/connect event) (not= (::keys/public node1) arg))
          (testing "node 1 connects to another node"
            (is (contains? arg ::keys/public))
            (done))

          :default (recur))))))

(deftest connection-test-fake
  (async done
    (node-test-util/with-test-nodes 2 #(connection-test % done))))

(deftest connection-test-real
  (async done
    (node-test-util/with-test-nodes :real 2 #(connection-test % done))))

(defn put!-get!-test [[node1 & rest] done]
  (let [get-count (atom 0)
        timeout-ch (a/timeout 30000)
        test-key "abc"
        test-value {:data "bar"}]
    (go-loop []
      (let [all-ch (concat [timeout-ch]
                           [(::node/event-ch node1)]
                           (map ::node/event-ch rest)
                           (map ::dht/response-ch rest))
            [[event node arg1 arg2] port] (a/alts! all-ch)]

        (cond
          (= timeout-ch port)
          (do (is (pos? @get-count))
              (done))

          (or (= ::node/error event) (= ::dht/error event))
          (let [msg (:message arg1)]
            (.warn js/console msg)
            (recur))

          (and (= ::node/connect event) (= node1 node))
          (do (a/>! (::dht/request-ch node1) [::dht/put test-key test-value])
              (doseq [n rest]
                (a/put! (::dht/request-ch n) [::dht/get test-key]))
              (recur))

          (= ::dht/get event)
          (do (is (= [test-key test-value] [arg1 arg2]))
              (swap! get-count inc)
              (if (= (count rest) @get-count)
                (done)
                (recur)))

          :default (recur))))))

(deftest put!-get!-test-fake
  (async done
    (node-test-util/with-test-nodes 5 #(put!-get!-test % done))))

(deftest put!-get!-test-real
  (async done
    (node-test-util/with-test-nodes :real 5 #(put!-get!-test % done))))