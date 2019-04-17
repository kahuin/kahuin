(ns kahuin.p2p.encoding-test
  (:require
    [cljs.test :refer [deftest testing is]]
    [kahuin.p2p.encoding :as encoding]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(deftest clj->buffer->clj-test
  (is (= nil (encoding/buffer->clj (encoding/clj->buffer nil))))
  (is (= 1 (encoding/buffer->clj (encoding/clj->buffer 1))))
  (is (= "foo" (encoding/buffer->clj (encoding/clj->buffer "foo"))))
  (is (= [1 2 3] (encoding/buffer->clj (encoding/clj->buffer [1 2 3]))))
  (is (= {} (encoding/buffer->clj (encoding/clj->buffer {}))))
  (is (= {:x 1} (encoding/buffer->clj (encoding/clj->buffer {:x 1})))))

(def base58-chars "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

(deftest base58->buffer->base58
  (doall
    (for [b58 [""
               "j"
               "123foo"
               base58-chars
               (reduce str (shuffle base58-chars))
               (reduce str (repeat 10 base58-chars))]]
      (is (= b58 (-> (encoding/base58->buffer b58)
                     (encoding/buffer->base58)))))))