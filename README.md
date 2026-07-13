# cloud-itonami-isco-8172

Open Occupation Blueprint for **ISCO-08 8172**: Wood Processing Plant Operators.

**Maturity: `:implemented`** — ProcessAdvisor ⊣ WoodProcessingGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 14 tests / 29 assertions
green. The governor never dispatches hardware — it only gates what
the plant-monitoring robot below may execute.

The batch-run HARD invariants — arithmetic, not visibility or
efficiency judgement:

1. **Dust-level ceiling** — the measured dust level must not exceed
   the registered occupational safety ceiling.
2. **Throughput ceiling** — the proposed throughput must not exceed
   the registered rated capacity.

`:approve-blade-proximity-operation` and
`:approve-blade-change-maintenance` **always** escalate to human
sign-off regardless of confidence, per this repo's Trust Controls
(business-model.md).

This repository designs a forkable OSS business for an independent wood processing plant operator: a plant-monitoring robot performs safety checks and sampling near operating machinery under a governor-gated actor, so the operator keeps their own process and safety records instead of renting a closed plant-control SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a plant-monitoring robot performs blade-guard checks, dust-level sensing and sample collection near operating machinery under an actor that proposes
actions and an independent **Wood Processing Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near cutting blades, or during blade-change/maintenance procedures) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
production order + material spec + safety envelope
        |
        v
Process Advisor -> Wood Processing Governor -> process/monitor, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8172`). Required capabilities:

- :robotics
- :telemetry
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
