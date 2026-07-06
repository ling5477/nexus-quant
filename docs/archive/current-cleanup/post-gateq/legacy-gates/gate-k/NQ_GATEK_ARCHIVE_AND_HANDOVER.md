# NQ GateK Archive And Handover

## Archive Identity

- Document id: `NQ_GATEK_ARCHIVE_AND_HANDOVER`
- Archive status: `ARCHIVED / CLOSED`
- Handover scope: GateK CI security, backend intelligence, frontend K5 split, data-flow isolation, E2E conditionality, and runtime lock.

This document materializes the GateK archive and handover state as a repository-tracked current document. It records freeze facts and maintenance rules only. It does not add features, runtime behavior, workflow changes, API changes, migration changes, tests, AI runtime, DH runtime, LIVE trading, or real exchange access.

## K1 To K5 Evolution Timeline

- K1 execution diagnostics: established the backend causal diagnostics foundation for Paper Trading execution review.
- K2 diagnostics UI: exposed execution diagnostics through a dedicated UI responsibility without widening runtime permissions.
- K3 strategy evaluation: added bounded backend intelligence for strategy-level evaluation.
- K4 auto review: added read-only post-run review capability for Paper Trading outcomes.
- K5 frontend split architecture: separated execution, portfolio analytics, diagnostics, and reviews into route-owned surfaces and removed the all-in-one page from the frozen model.

## System Decomposition History

GateK moved the system from a coupled Paper Trading surface to a layered freeze model.

- CI security became the proof and redaction authority for safe evidence.
- Backend intelligence became a bounded analytics layer, not an execution authority.
- Frontend K5 split converted the Paper Trading UI into route-owned work surfaces.
- Data flow moved from shared-page risk to explicit query ownership.
- E2E was separated into stable product/route smoke and environment-dependent backend smoke.
- Runtime boundary remained locked against LIVE, AI, DH, real exchange, and credential material access.

The archive records the decomposition result, not a redesign.

## Design Principles

- Read-only aggregation: analytics may summarize, diagnose, rank, and review, but must not mutate trading state.
- No per-run explosion: backend and frontend surfaces must avoid unbounded per-run query fan-out.
- Single-query ownership: `portfolioQuery` must remain owned by the portfolio route only.
- Route isolation: execution, portfolio, diagnostics, and reviews must not mount each other's route-local queries.
- Fail-closed CI: CI security proof must fail closed when evidence is missing, ambiguous, or unsafe.
- No LIVE / no AI / no DH: GateK archive does not authorize runtime expansion.

## Query Ownership Model

The frozen query ownership model is:

- `/paper-trading/runs`: run lifecycle and execution-only state.
- `/paper-trading/portfolio`: `portfolioQuery`, portfolio aggregation, risk, and ranking analytics.
- `/paper-trading/diagnostics`: execution diagnostics query.
- `/paper-trading/reviews`: strategy evaluation query and auto review query.

Ownership rule: a page owns only the queries required for its route responsibility. Shared shells and sibling routes must not mount hidden analytics queries.

## Runtime Boundary Model

Runtime boundary is locked:

- LIVE: off.
- AI runtime: off.
- DH runtime: off.
- Real exchange execution: off.
- Credential material access: forbidden for this archive surface.

The archive cannot be used to infer runtime permission. It records that these surfaces remain unavailable within the GateK frozen state.

## Risk Closure Statement

GateK archive closes the active architecture risks represented by this freeze:

- No active security risk in the accepted CI security contract.
- No credential exposure through the documented freeze surfaces.
- No live execution risk because LIVE and real exchange execution remain locked.
- No cross-module query leakage risk in the frozen K5 route ownership model.
- No backend mutation risk in the analytics layer because it remains read-only.
- Environment dependency remains only for backend-dependent smoke and does not invalidate the architecture.

## Maintenance Rules

These rules protect the archive from regression:

- Do not reintroduce the all-in-one Paper Trading page as a query owner.
- Keep `portfolioQuery` single-instance under `/paper-trading/portfolio`.
- Keep diagnostics, evaluation, and auto review queries route-local.
- Keep CI log redaction fail-closed.
- Keep artifacts gated and policy-compliant.
- Keep backend analytics read-only.
- Keep E2E backend conditionality documented as an environment dependency, not as an architecture defect.
- Do not reinterpret this archive as authorization for LIVE, AI, DH runtime, real exchange execution, or credential material access.

## Final Archive State

```text
CI: FROZEN
Backend: FROZEN
Frontend: FROZEN
Data Flow: ISOLATED
E2E: CONDITIONAL
Runtime: LOCKED
Archive: MATERIALIZED
```
