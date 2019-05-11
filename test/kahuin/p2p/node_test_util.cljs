(ns kahuin.p2p.node-test-util
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [kahuin.p2p.node :as node]
    [kahuin.p2p.fake-node :as fake-node]))

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