(ns kahuin.p2p.node-test
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.core.async.impl.protocols :as async-protocols]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.node :as node]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(deftest <new-test
  (async done
    (go
      (testing "create a new node"
        (let [result (a/<! (node/<new))]
          (is (some? result))
          (done))))))

(def test-base-58-encoded-node
  "4XZF1M9dcKK2sCUmhBgzCzhYXNkFt7iWdouFdoxEbMzbfuHBu")

(deftest <load-test
  (async done
    (go
      (testing "load node from base58 encoded string"
        (let [result (a/<! (node/<load test-base-58-encoded-node))]
          (is (= "GZsJqUscK3AeyFzCo5UphzHQe6UjxKX6vfSKe6ebP9pmbkaZTp" (:kahuin.p2p.keys/public result)))
          (done))))))

(deftest start!-stop!-test
  (async done
    (go
      (let [node (a/<! (node/<load test-base-58-encoded-node))]
        (testing "start node"
          (node/start! node)
          (is (= [::node/start node] (a/<! (::node/ch node)))))
        (testing "stop node"
          (node/stop! node)
          (is (= (async-protocols/closed? (::node/ch node)))))
        (done)))))

(def test-base-58-encoded-node-1
  "4XZF1ZEuqGeZXPdjf6QhjyLtVyTz4uAA6a61uemyFnkLFfaZf")

(def test-base-58-encoded-node-2
  "4XZF1Uo42B4SqecMdCTU6PNt727pWMJiSNzsVxpkgSPs9KYCh")

(defn with-2-test-nodes
  [f]
  (go (let [node1 (a/<! (node/<new))
            node2 (a/<! (node/<new))]
        (node/start! node1)
        (node/start! node2)
        (a/<! (f [node1 node2]))
        (node/stop! node1)
        (node/stop! node2))))

(deftest start!-test
  (async done
    (with-2-test-nodes
      (fn [[node1 _node2]]
        (go-loop []
          (let [[event node & _args] (a/<! (::node/ch node1))]
            (if (= ::node/start event)
              (testing "node 1 started"
                (is (= node1 node))
                (done))
              (recur))))))))

(deftest discovery-test
  (async done
    (with-2-test-nodes
      (fn [[node1 node2]]
        (go-loop []
          (let [[event node & args] (a/<! (::node/ch node1))]
            (if (and (= ::node/peer:discovery event)
                     (= (:kahuin.p2p.keys/public node2)
                        (-> args first ::node/peer-id)))
              (testing "node 1 discovers node 2"
                (is (= node1 node))
                (done))
              (recur))))))))

(deftest put!-test
  (async done
    (with-2-test-nodes
      (fn [[node1 _node2]]
        (node/put! node1 "abc" :bar)
        (go-loop []
          (let [[event node & args] (a/<! (::node/ch node1))]
            (if (= ::node/dht:put event)
              (testing "node1 put"
                (is (= node1 node))
                (is (= ["abc" :bar] args))
                (done))
              (recur))))))))

(deftest put!-get!-test
  (async done
    (with-2-test-nodes
      (fn [[node1 node2]]
        (node/put! node1 "abc" :bar)
        (go-loop []
          (let [[event node & args] (a/<! (::node/ch node2))]
            (println event args)
            ;(.log js/console (.-_dht (::node/impl node)))
            (case event
              ::node/peer:discovery
              (do (when (= node node2)
                    (node/get! node2 "abc"))
                  (recur))
              ::node/dht:put
              (testing "node2 get"
                (is (= node2 node))
                (is (= ["abc" :bar] args))
                (done))
              (recur))))))))