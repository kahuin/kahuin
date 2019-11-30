(ns kahuin.p2p.node.test-util
  (:require
    [cljs.core.async :as a :refer-macros [go go-loop]]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.dht :as dht]
    [kahuin.p2p.keys :as keys]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.node.fake-node :as fake-node]))

(defn- <new-test-nodes
  [fake-contents n]
  (go (let [use-real (= :real fake-contents)
            fake-network (when-not use-real (fake-node/fake-network fake-contents))]
        (->> (repeatedly n (fn []
                             (if use-real (node/<new {})
                                          (fake-node/<new {:fake-network fake-network}))))
             (a/merge)
             (a/into [])
             (a/<!)))))

(defn with-test-nodes
  "Create n test fake test nodes and call f with the collection of nodes as argument.
  fake-contents is the initial contents of the fake DHT.

  If fake-contents is :real then actually uses real node implementation."
  ([n f]
   (with-test-nodes {} n f))
  ([fake-contents n f]
   (assert (or (map? fake-contents) (= :real fake-contents)))
   (go (let [use-real (= :real fake-contents)
             nodes (a/<! (<new-test-nodes fake-contents n))]
         (dorun (map (if use-real node/start! fake-node/start!) nodes))
         (println "started" n "test nodes" (map :kahuin.p2p.keys/public nodes))
         (a/<! (f nodes))
         (dorun (map (if use-real node/stop! fake-node/stop!) nodes))))))

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