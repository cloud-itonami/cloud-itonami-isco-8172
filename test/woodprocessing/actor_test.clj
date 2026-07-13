(ns woodprocessing.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [woodprocessing.actor :as actor]
            [woodprocessing.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Sawmill"})
    (store/register-plant! st {:plant-id "P-1" :client-id "client-1"
                               :name "line-2-planer"
                               :max-dust-level-mgm3 5.0
                               :max-throughput-m3-per-hour 20})
    st))

(deftest commits-an-in-ceiling-batch-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-batch-run :stake :low
                 :plant-id "P-1" :dust-level-mgm3 3.0 :throughput-m3-per-hour 15}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-throughput-batch-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-batch-run :stake :low
                 :plant-id "P-1" :dust-level-mgm3 3.0 :throughput-m3-per-hour 40}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-blade-change-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-blade-change-maintenance :stake :low
                 :plant-id "P-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
