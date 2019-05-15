(ns kahuin.p2p.encoding-test
  (:require
    [cljs.spec.alpha :as s]
    [cljs.spec.gen.alpha :as gen]
    [cljs.test :refer [deftest testing is]]
    [kahuin.p2p.encoding :as encoding]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(deftest bencode-roundtrip
  (testing "can bencode integers"
    (is (= 1 (encoding/bdecode (encoding/bencode 1)))))
  (testing "can bencode strings"
    (is (= "" (encoding/bdecode (encoding/bencode ""))))
    (is (= "foo" (encoding/bdecode (encoding/bencode "foo"))))
    (is (= "\uD83D\uDE01" (encoding/bdecode (encoding/bencode "\uD83D\uDE01")))))
  (testing "can bencode lists and vectors"
    (is (= [] (encoding/bdecode (encoding/bencode []))))
    (is (= [1 2 3] (encoding/bdecode (encoding/bencode [1 2 3]))))
    (is (= [1 2 3] (encoding/bdecode (encoding/bencode '(1 2 3)))))
    (is (= [{:a 1 :b 2} "foo"] (encoding/bdecode (encoding/bencode [{:a 1 :b 2} "foo"])))))
  (testing "can bencode maps with non-namespaced keyword keys"
    (is (= {} (encoding/bdecode (encoding/bencode {}))))
    (is (= {:x 1} (encoding/bdecode (encoding/bencode {:x 1}))))
    (is (= {:x {:a 1 :b 2} :y [3 4]} (encoding/bdecode (encoding/bencode {:x {:a 1 :b 2} :y [3 4]})))))
  (testing "generated bencodeables"
    (doseq [bencodeable (gen/sample (s/gen ::encoding/bencodeable))]
      (is (= bencodeable (-> (encoding/bencode bencodeable)
                             (encoding/bdecode)))))))

(deftest bencode-gotchas
  (testing "cannot bencode floats"
    (is (thrown? js/Error (encoding/bencode 0.1)))
    (is (thrown? js/Error (encoding/bencode -0.1))))
  (testing "cannot bencode nil"
    (is (thrown? js/Error (encoding/bencode nil))))
  (testing "cannot bencode sets"
    (is (thrown? js/Error (encoding/bencode #{:a}))))
  (testing "cannot bencode keywords unless they are map keys"
    (is (thrown? js/Error (encoding/bencode :kw)))
    (is (thrown? js/Error (encoding/bencode {:k :v}))))
  (testing "cannot bencode anything but keywords as map keys"
    (is (thrown? js/Error (encoding/bencode {"a-string" 1})))
    (is (thrown? js/Error (encoding/bencode {[42] 1}))))
  (testing "cannot bencode namespaced keywords"
    (is (thrown? js/Error (encoding/bencode {:foo/bar 1})))))

(deftest bencode-bijection
  (testing "equivalent maps are bencoded the same"
    (is (= (encoding/bencode {:a 1 :b 2})
           (encoding/bencode {:b 2 :a 1}))))
  (testing "order of lists and vectors matters"
    (is (not= (encoding/bencode [1 2])
              (encoding/bencode [2 1])))))

(deftest base58->buffer->base58
  (doseq [b58 (gen/sample (s/gen ::encoding/base58) 20)]
    (is (= b58 (-> (encoding/base58->buffer b58)
                   (encoding/buffer->base58))))))