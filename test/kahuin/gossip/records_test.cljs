(ns kahuin.gossip.records-test
  (:require
    [cljs.core.async :as a :refer-macros [go]]
    [cljs.spec.alpha :as s]
    [cljs.spec.gen.alpha :as gen]
    [cljs.test :refer [deftest testing is async]]
    [kahuin.gossip.records :as records]
    [kahuin.p2p.crypto :as crypto]
    [kahuin.p2p.keys :as keys]
    [orchestra-cljs.spec.test :as st]))

(st/instrument)

(def ^:private test-public-key
  "xFfBjQfhQFz9wk54QCm8CxCyrE7yAFNPvx9WAZ2ewTzg")

(def ^:private test-private-key
  "sZqtCKLdGBV5MdFSdc4L9C9xCv9V1vSZcRXsPzjn2whN1FZX4")

(def ^:private test-gossip-record
  {::records/type :gossip
   ::records/author test-public-key
   ::records/inst #inst"2017-12-10T19:27:38"
   ::records/contents "Lorem ipsum dolor sit amet, consectetur adipiscing elit."})

(def ^:private test-gossip-record-dht-key
  "PQhaECeLu9mkdAs6gzRv5Mq37A9apuLQF3vz6RbFw2BJ")

(def ^:private test-gossip-record-dht-value
  [["gossip"
    test-public-key
    1512934058
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit."]
   "ftWV2y3CpfMhuct62QRdv52kLWVg8EVzU3VyVz4VNGYfMYmn8yTKUpbmL6LTQ5Yr1gUfVJjsue2QMhRGhkM25TvHK5tvKr1NA"])

(def ^:private test-profile-record {::records/type :profile
                                    ::records/author test-public-key
                                    ::records/inst #inst"2017-12-10T19:27:38"
                                    ::records/contents {:nick "foo"
                                                        :pinned [test-gossip-record-dht-key]}})

(def ^:private test-profile-record-dht-key
  "NPdXTD9mGgZjqi5GEeeBoY7TTRwpYheBuNPokthZKt8C")

(def ^:private test-profile-record-dht-value
  [["profile"
    test-public-key
    1512934058
    {:nick "foo"
     :pinned [test-gossip-record-dht-key]}]
   "KD96LJxaMaGGryoufzwnViNJJJBUdmuprZwAWkdHamuz12f9eeQEDYJXFHZ6tkVYZty3oFYVdCHwPmK4r9PgZPzQFSZXy183"])

(deftest <dht-key-test
  (async done
    (go
      (testing "immutable dht key"
        (is (= (a/<! (crypto/<hashed (first test-gossip-record-dht-value)))
               (a/<! (records/<dht-key test-gossip-record))))
        (is (= test-gossip-record-dht-key
               (a/<! (records/<dht-key test-gossip-record)))))
      (testing "mutable dht key"
        (is (= (a/<! (crypto/<hashed ["profile" test-public-key]))
               (a/<! (records/<dht-key test-profile-record))))
        (is (= test-profile-record-dht-key
               (a/<! (records/<dht-key test-profile-record)))))
      (done))))

(deftest <to-dht-key-value-test
  (async done
    (go
      (let [keypair (a/<! (keys/<keypair test-private-key))]
        (testing "valid immutable record"
          (is (= [::records/dht-key-value test-gossip-record-dht-key test-gossip-record-dht-value]
                 (a/<! (records/<to-dht-key-value keypair test-gossip-record)))))
        (testing "valid mutable record"
          (is (= [::records/dht-key-value test-profile-record-dht-key test-profile-record-dht-value]
                 (a/<! (records/<to-dht-key-value keypair test-profile-record)))))
        (st/with-instrument-disabled
          (testing "invalid record"
            (is (= ::records/error (first (a/<! (records/<to-dht-key-value keypair []))))))))
      (done))))

(deftest <from-dht-key-value
  (async done
    (go
      (testing "valid immutable record"
        (is (= [::records/record (assoc test-gossip-record ::records/key {::keys/public test-public-key})]
               (a/<! (records/<from-dht-key-value test-gossip-record-dht-key test-gossip-record-dht-value)))))
      (testing "valid mutable record"
        (is (= [::records/record (assoc test-profile-record ::records/key {::keys/public test-public-key})]
               (a/<! (records/<from-dht-key-value test-profile-record-dht-key test-profile-record-dht-value)))))
      (st/with-instrument-disabled
        (testing "bad signature"
          (is (= [::records/error "Bad record signature"]
                 (a/<! (records/<from-dht-key-value
                         test-profile-record-dht-key
                         [(first test-profile-record-dht-value) ""]))))
          (is (= [::records/error "Bad record signature"]
               (a/<! (records/<from-dht-key-value
                       test-profile-record-dht-key
                       [(first test-profile-record-dht-value) (gen/generate (s/gen ::keys/signature))]))))
          (is (= [::records/error "Bad record signature"]
                 (a/<! (records/<from-dht-key-value
                         test-profile-record-dht-key
                         [(first test-gossip-record-dht-value) (gen/generate (s/gen ::keys/signature))]))))))
      (testing "wrong dht-key"
        (let [dht-key (gen/generate (s/gen ::records/dht-key))]
          (is (= [::records/error "Invalid record hash" dht-key]
                 (a/<! (records/<from-dht-key-value dht-key test-profile-record-dht-value))))
          (is (= [::records/error "Invalid record hash" dht-key]
                 (a/<! (records/<from-dht-key-value dht-key test-gossip-record-dht-value))))))
      (done))))