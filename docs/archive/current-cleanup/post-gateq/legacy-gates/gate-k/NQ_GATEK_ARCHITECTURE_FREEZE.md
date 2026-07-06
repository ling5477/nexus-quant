# NQ GateK Architecture Freeze

## Freeze Identity

- Document id: `NQ_GATEK_ARCHITECTURE_FREEZE`
- Freeze scope: GateK architecture, CI security boundary, backend intelligence boundary, frontend K5 split, data-flow isolation, E2E conditionality, and runtime lock.
- Freeze status: `FROZEN / ACCEPTED`
- Materialization type: repository-level documentation freeze.

This document records the accepted GateK architecture as a frozen structure. It does not start a runtime system, does not add executable behavior, and does not change backend, frontend, CI, test, API, migration, AI, DH, LIVE, or real exchange paths.

## CI Layer

The CI layer is frozen as a security contract boundary.

- Security contract: accepted and locked for GateK.
- Log redaction: deterministic, explicit, and fail-closed.
- Artifact policy: gated; only approved proof surfaces may be retained.
- Proof posture: CI proof must distinguish safe evidence from redaction hits.
- Failure posture: ambiguous or unsafe output is treated as failure, not as a partial pass.

The CI layer is part of the architecture freeze because it defines how evidence is allowed to exist. It is not a new workflow implementation in this document.

## Backend Layer

The backend intelligence layer is frozen as bounded, read-only Paper Trading analytics.

- K1 execution diagnostics: done.
- K3 strategy evaluation: done.
- K4 auto review: done.
- Portfolio aggregation: done.
- Batch-read optimization: done.

Backend intelligence remains bounded by analytics semantics. It may explain, evaluate, aggregate, and review Paper Trading results, but this freeze does not authorize mutation of trading state, new order paths, new cancellation paths, LIVE execution, AI execution, DH runtime integration, or real exchange access.

## Frontend K5 Split

The frontend architecture is frozen as a split route model.

- `/paper-trading/runs`: execution layer.
- `/paper-trading/portfolio`: portfolio, risk, and ranking analytics layer.
- `/paper-trading/diagnostics`: causal diagnostics layer.
- `/paper-trading/reviews`: strategy evaluation and auto review layer.
- Route shell: completed.
- All-in-one Paper Trading page: removed from the frozen architecture.

The split exists to keep execution, analytics, diagnostics, and review responsibilities separately mounted. The frozen frontend shape prevents the previous all-in-one surface from becoming the implicit owner of every query and every panel.

## Data Flow Layer

The data-flow layer is frozen around explicit query ownership.

- `portfolioQuery`: single-instance ownership under `/paper-trading/portfolio`.
- Diagnostics query: route-local ownership under `/paper-trading/diagnostics`.
- Evaluation query: route-local ownership under `/paper-trading/reviews`.
- Auto review query: route-local ownership under `/paper-trading/reviews`.
- Run lifecycle query ownership: execution-only under `/paper-trading/runs`.

No cross-page query leakage is allowed by the GateK freeze. Shared parents and sibling routes must not mount page-specific analytics queries as hidden dependencies.

## E2E Layer

The E2E layer is frozen as a mixed stability model.

- Product-loop baseline: stable.
- Route smoke layer: stable.
- Backend-dependent smoke: conditional because it depends on the availability and shape of the target backend environment.

Environment-dependent smoke is not an architecture risk by itself. It is a validation dependency boundary: frontend route independence and query ownership remain valid even when backend-dependent smoke requires a live local or CI backend.

## Runtime Boundary

The runtime boundary is locked.

- LIVE: off.
- AI runtime: off.
- DH runtime: off.
- Real exchange execution: off.
- Credential material access: forbidden for this GateK freeze surface.

GateK freeze does not authorize live trading, real provider execution, real permission probing, credential reads, AI order generation, DH control paths, or exchange-side state mutation.

## Why This Is A Frozen Structure, Not A Runtime System

GateK architecture freeze is a persistence layer for already accepted structure and contracts. It records what the system shape is allowed to mean; it does not execute that shape.

The freeze is structural because:

- It persists layer ownership and boundary semantics.
- It records accepted query isolation rules.
- It freezes the CI evidence contract without modifying workflows.
- It preserves frontend route responsibilities without adding UI behavior.
- It captures backend analytics boundaries without adding service logic.
- It locks runtime prohibitions without starting any runtime path.

The freeze is not a runtime system because:

- It does not introduce code.
- It does not run tests or CI.
- It does not call backend services.
- It does not call exchanges.
- It does not read or transform credentials.
- It does not create API, migration, or workflow changes.

## Final State

```text
CI: FROZEN
Backend: FROZEN
Frontend: FROZEN
Data Flow: ISOLATED
E2E: CONDITIONAL
Runtime: LOCKED
```
