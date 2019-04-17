(ns kahuin.p2p.keys-test
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [cljs.test :refer [deftest testing is async]]
    [cljs.spec.alpha :as s]
    [kahuin.p2p.keys :as keys]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(deftest <new-keypair-test
  (async done
    (go
      (let [keypair (a/<! (keys/<new-keypair))]
        (is (s/valid? ::keys/keypair keypair))
        (done)))))

(def test-private-key
  "4XZF1M9dcKK2sCUmhBgzCzhYXNkFt7iWdouFdoxEbMzbfuHBu")

(deftest keypair->base58-test
  (async done
    (go
      (let [keypair (a/<! (keys/<keypair test-private-key))]
        (is (= test-private-key (keys/keypair->base58 keypair)))
        (done)))))

(def test-signature
  "AN1rKvtFoBbDRBQpdj4XDPjorcqb8HYbiPigkYQp9rP3bEJCKG99n9vbH1YaCuBFK6ZLemVTzS1R7Qxwn15JXuqHWnXJ1cbyH")

(deftest <signed-test
  (async done
    (go
      (let [keypair (a/<! (keys/<keypair test-private-key))
            {:keys [data other] ::keys/keys [signature]} (a/<! (keys/<signed keypair {:data "foo" :other :bar}))]
        (is (= "foo" data))
        (is (= :bar other))
        (is (= test-signature signature))
        (done)))))

(def test-public-key
  "GZsJqUscK3AeyFzCo5UphzHQe6UjxKX6vfSKe6ebP9pmbkaZTp")

(def test-valid-message
  {::keys/signature test-signature
   :data "foo"
   :other 1})

(def test-bad-signature
  "381yXZJJ379fQRnFE6tnQ8zBDQRyd8Bq8z1MpZi5wfj6yZzcBdGgb9jG29WKQ29jCmFxNeJBdRDhWspgg4VAYybokzNvdDe6")

(deftest <validated-test
  (async done
    (go
      (let [test-keypair {::keys/public test-public-key}
            invalid-data-message (assoc test-valid-message :data "FOO")
            invalid-signature-message (assoc test-valid-message ::keys/signature test-bad-signature)]
        (testing "valid signature"
          (let [{:keys [data other] ::keys/keys [valid]} (a/<! (keys/<validated test-keypair test-valid-message))]
            (is (= (:data test-valid-message) data))
            (is (= (:other test-valid-message) other))
            (is (true? valid))))
        (testing "invalid data"
          (is (false? (::keys/valid (a/<! (keys/<validated test-keypair invalid-data-message))))))
        (testing "invalid signature"
          (is (false? (::keys/valid (a/<! (keys/<validated test-keypair invalid-signature-message))))))
        (done)))))