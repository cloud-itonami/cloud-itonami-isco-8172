(ns woodprocessing.store
  "SSoT for the ISCO-08 8172 independent wood processing plant
  operations actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a
  plant-monitoring robot performs blade-guard checks, dust-level
  sensing and sample collection under this advisor/governor pair,
  which never dispatches hardware itself). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    plant  — a registered processing plant {:plant-id :client-id
             :name :max-dust-level-mgm3 number
             :max-throughput-m3-per-hour number}.
             `:max-dust-level-mgm3` is the registered occupational
             safety ceiling a proposed run's measured dust level must
             not exceed (dust exposure is measured against
             occupational safety limits, not judged by visibility);
             `:max-throughput-m3-per-hour` is the registered rated
             capacity a proposed run's throughput must not exceed
             (exceeding rated capacity is a mechanical risk, not
             efficiency).
    record — a committed operating record (approved batch run) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (plant [s plant-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-plant! [s p])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (plant [_ plant-id] (get-in @a [:plants plant-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-plant! [s p]
    (swap! a assoc-in [:plants (:plant-id p)] p) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :plants {} :records [] :ledger []}
                                   seed)))))
