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
  "uBHufbzMbExodFuodWi7tFkNXYhzCzgBhmUCs2KKcd9M1FZX4")

(deftest keypair->base58-test
  (async done
    (go
      (let [keypair (a/<! (keys/<keypair test-private-key))]
        (is (= test-private-key (keys/keypair->base58 keypair)))
        (done)))))

(def test-signature
  "iaXcKgRFJcSAPWw6qFj1UAs59sHePsqe3irN1MKMg5F2GYGUPJrWSmkNN84isA5B8vEKS3JF6NjSe1wiXLo2Tvpfb9ZXy183")

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
  "rxptixh3Nt8eHSi7wmbKgRgqd35tnZLpLJ8ZUKQRtAL32")

(def test-valid-message
  {::keys/signature test-signature
   :data "foo"
   :other 1})

(def test-bad-signature
  "6eDdvNzkobyYAV4ggpsWhDRdBJeNxFmCj92QKW92Gj9bgGdBczZy6jfw5iZpM1z8qB8dyRQDBz8Qnt6EFnRQf973JJZXy183")

(deftest <validated-test
  (async done
    (go
      (let [test-keypair {::keys/public test-public-key}
            invalid-data-message (assoc test-valid-message :data "FOO")
            invalid-signature-message (assoc test-valid-message ::keys/signature test-bad-signature)]
        (testing "valid signature"
          (let [{:keys [data other] ::keys/keys [veridic]} (a/<! (keys/<verified test-keypair test-valid-message))]
            (is (= (:data test-valid-message) data))
            (is (= (:other test-valid-message) other))
            (is (true? veridic))))
        (testing "invalid data"
          (is (false? (::keys/veridic (a/<! (keys/<verified test-keypair invalid-data-message))))))
        (testing "invalid signature"
          (is (false? (::keys/veridic (a/<! (keys/<verified test-keypair invalid-signature-message))))))
        (done)))))