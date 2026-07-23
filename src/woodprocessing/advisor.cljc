(ns woodprocessing.advisor
  "ProcessAdvisor — the advisor named in this repository's README,
  proposing a plant operation (approve a batch run, approve
  blade-proximity operation, approve blade-change maintenance) from a
  production order, material spec and safety envelope. Swappable
  mock/llm; the advisor ONLY proposes — `woodprocessing.governor`
  checks the dust-level and throughput ceilings independently and
  always escalates blade-proximity/blade-change decisions. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-batch-run|:approve-blade-proximity-operation|:approve-blade-change-maintenance
               :effect :propose :plant-id str :dust-level-mgm3 number
               :throughput-m3-per-hour number :stake kw :confidence n
               :rationale str}"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake plant-id dust-level-mgm3 throughput-m3-per-hour] :as request}]
  {:op op
   :effect :propose
   :plant-id plant-id
   :dust-level-mgm3 dust-level-mgm3
   :throughput-m3-per-hour throughput-m3-per-hour
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a wood-processing-plant advisor. Given a request, propose
   an :op, the :plant-id, :dust-level-mgm3 and
   :throughput-m3-per-hour, an honest :confidence and a :stake. Never
   call an over-ceiling dust level or an over-capacity throughput
   conforming — the governor checks both against the registered plant
   record. Blade-proximity and blade-change decisions always require
   human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
