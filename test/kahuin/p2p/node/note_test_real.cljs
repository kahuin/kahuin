(ns kahuin.p2p.node.note-test-real
  (:require
    [cljs.test :refer [deftest testing is async]]
    [kahuin.p2p.node.test-util :as node-test-util]))

(deftest connection-test-real
  (async done
    (node-test-util/with-test-nodes :real 2 #(node-test-util/connection-test % done))))

(deftest put!-get!-test-real
  (async done
    (node-test-util/with-test-nodes :real 10 #(node-test-util/put!-get!-test % done))))
