(ns woodprocessing.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [woodprocessing.store :as store]
            [woodprocessing.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Sawmill"})
    (store/register-plant! st {:plant-id "P-1" :client-id "client-1"
                               :name "line-2-planer"
                               :max-dust-level-mgm3 5.0
                               :max-throughput-m3-per-hour 20})
    st))

(defn- batch-run [dust throughput]
  {:op :approve-batch-run :effect :propose :plant-id "P-1"
   :dust-level-mgm3 dust :throughput-m3-per-hour throughput :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-dust-and-throughput-ceilings
  (let [st (fresh-store)
        v (governor/check req {} (batch-run 3.0 15) st)]
    (is (:ok? v))))

(deftest ok-at-exact-ceilings
  (testing "both ceilings are inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (batch-run 5.0 20) st)]
      (is (:ok? v)))))

(deftest hard-on-dust-level-exceeds-ceiling
  (testing "dust exposure is measured against occupational safety limits, not judged by visibility"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (batch-run 12.0 15) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :dust-level-exceeds-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-throughput-exceeds-rated-capacity
  (testing "exceeding rated capacity is a mechanical risk, not efficiency"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (batch-run 3.0 40) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :throughput-exceeds-rated-capacity (:rule %)) (:violations v))))))

(deftest hard-on-unknown-plant
  (let [st (fresh-store)
        v (governor/check req {} (assoc (batch-run 3.0 15) :plant-id "P-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-plant (:rule %)) (:violations v)))))

(deftest hard-on-foreign-plant
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (batch-run 3.0 15) st)]
      (is (:hard? v))
      (is (some #(= :plant-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (batch-run 3.0 15) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (batch-run 3.0 15) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-blade-proximity-operation-even-at-high-confidence
  (testing "no robot dispatch near cutting blades without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-blade-proximity-operation :effect :propose
                                    :plant-id "P-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-blade-change-maintenance-even-at-high-confidence
  (testing "blade-change/maintenance procedures require human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-blade-change-maintenance :effect :propose
                                    :plant-id "P-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (batch-run 3.0 15) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
