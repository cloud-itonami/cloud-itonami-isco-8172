(ns woodprocessing.governor
  "WoodProcessingGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating the
  robot-dispensed physical work (blade-guard checks, dust-level
  sensing, sample collection) an advisor may propose. The governor
  never dispatches hardware itself. Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Run twist: a
  proposed run's measured dust level and throughput are each
  arithmetic comparison against their own registered ceiling — dust
  exposure is measured against occupational safety limits, not judged
  by visibility, and exceeding rated throughput capacity is a
  mechanical risk, not efficiency.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose (the
                           governor never dispatches hardware; it only
                           gates what the robot may execute).
    3. plant basis          — a run approval must cite a REGISTERED
                           plant belonging to this client.
    4. dust-level ceiling   — the proposed measured dust level must
                           not exceed the plant's registered
                           :max-dust-level-mgm3 (measured against
                           occupational safety limits, not judged by
                           visibility).
    5. throughput ceiling   — the proposed throughput must not exceed
                           the plant's registered
                           :max-throughput-m3-per-hour (exceeding
                           rated capacity is a mechanical risk, not
                           efficiency).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-blade-proximity-operation (no robot dispatch near
                           cutting blades without the governor gate).
    7. :op :approve-blade-change-maintenance (blade-change/
                           maintenance procedures require human
                           sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [woodprocessing.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-blade-proximity-operation
                                     :approve-blade-change-maintenance})

(defn- hard-violations [{:keys [request proposal]} client-record p]
  (let [{:keys [op dust-level-mgm3 throughput-m3-per-hour]} proposal
        run? (= :approve-batch-run op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and run? (nil? p))
      (conj {:rule :unknown-plant :detail "未登録 plant への稼働承認は不可"})

      (and run? p (not= (:client-id p) (:client-id request)))
      (conj {:rule :plant-wrong-client :detail "plant が別 client のもの"})

      (and run? p (number? dust-level-mgm3) (> dust-level-mgm3 (:max-dust-level-mgm3 p)))
      (conj {:rule :dust-level-exceeds-ceiling
             :detail (str "測定粉じん濃度 " dust-level-mgm3 "mg/m3 > 登録済み上限 "
                          (:max-dust-level-mgm3 p) "mg/m3（粉じん曝露は職業安全限度に対する測定であって視認判断ではない）")})

      (and run? p (number? throughput-m3-per-hour)
           (> throughput-m3-per-hour (:max-throughput-m3-per-hour p)))
      (conj {:rule :throughput-exceeds-rated-capacity
             :detail (str "処理量 " throughput-m3-per-hour "m3/h > 登録済み定格 "
                          (:max-throughput-m3-per-hour p) "m3/h（定格超過は機械的リスクであって効率ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `woodprocessing.store/Store`. Pure — never
  mutates the store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        p (some->> (:plant-id proposal) (store/plant store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record p)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
