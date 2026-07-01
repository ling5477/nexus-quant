# NQ-NEXT-PHASE-PLAN

## Status

**PASS / PLAN ONLY / READY TO COMMIT**

This document plans the next phase after GateM. It does not start implementation.

## Current Frozen Baseline

GateM is frozen as the current no-real runtime readiness baseline.

- GateM release tag: `nq-gatem-freeze`.
- Tag type: annotated tag.
- Tag object: `f44c62833c5c9f895ee292eef7f5d497b23089cc`.
- Tagged commit: `64194844813bdd3d6541d5a07c576af27b28e5db`.
- Tagged commit subject: `docs(gatem): freeze GateM runtime readiness baseline`.
- Remote ref: `origin refs/tags/nq-gatem-freeze`.
- CI evidence: GitHub Actions `NQ CI Baseline` run `28435425742`, conclusion `success`.

Evidence checked in this planning task:

- `git tag --list "nq-gatem-freeze"` returned `nq-gatem-freeze`.
- `git show --stat --oneline --decorate nq-gatem-freeze` showed the annotated tag and tagged commit `64194844`.
- `git ls-remote --tags origin refs/tags/nq-gatem-freeze` returned tag object `f44c62833c5c9f895ee292eef7f5d497b23089cc`.

## GateM Final State

GateM final state is **FINALIZED / FROZEN / ACCEPTED / TAGGED**.

Frozen GateM meaning:

- Authoritative definition: Exchange / MarketData Runtime Readiness.
- Baseline: no-real runtime readiness baseline.
- Adapter readiness: fail-closed.
- MarketData readiness: diagnostic only, not trading authorization.
- Operational readiness: safe summary only, not LIVE authorization.
- `/actuator/health`: process health only, not readiness or LIVE authorization.

Current negative boundaries remain unchanged:

- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.
- real exchange private trading: **NOT_IMPLEMENTED**.
- permission probe real execution: **NOT_IMPLEMENTED**.
- OKX / Binance existing adapters are not future-real-ready and are not authorized as real execution providers.
- GateN-0 exchange docs and existing adapter reconciliation is **PASS / RECONCILIATION BASELINE / READY TO COMMIT**; it is a documentation baseline only and does not start GateN implementation.

## Goal

Choose the safest and most useful post-GateM mainline and define the planning baseline for the next phase.

The next phase must continue from a frozen no-real runtime readiness baseline without crossing into private exchange APIs, credentials, LIVE trading, AI runtime, DH runtime, RealClient, real provider, or wallet spending.

## Non-Goals

- Do not implement any feature in this task.
- Do not add backend API, frontend page, E2E, migration, CI workflow, deploy script, or business behavior.
- Do not connect OKX / Binance private APIs.
- Do not read, print, copy, validate, or probe real credentials.
- Do not enable LIVE.
- Do not start AI runtime, AI signal generation, AI Paper Trading, or AI-controlled trading.
- Do not start DH runtime integration.
- Do not implement RealClient or real provider.
- Do not run real permission probe.
- Do not treat MarketData readiness, operational readiness, `/actuator/health`, Paper-only, `SKIPPED`, or no-real status as live-ready.

## Scope

Allowed planning outputs:

- `docs/current/NQ_NEXT_PHASE_PLAN.md`.
- Current-entry synchronization in `docs/current/STATUS.md`, `docs/current/ROADMAP.md`, `docs/current/README.md`, `docs/current/WORKLOG.md`, `docs/current/TESTING.md`.
- Root `README.md` only for next-phase entry and no-real boundary.

Forbidden implementation scope:

- `backend/**`.
- `frontend/**`.
- `research/**`.
- `scripts/**`.
- `deploy/**`.
- `.github/**`.
- `backend/**/db/migration/**`.

## Existing Capability

The current frozen system already has:

- Paper Trading runtime and stability baseline from GateJ.
- GateL No-Real exchange / marketdata readiness contracts, error model, capability matrix, and future-real checklist.
- GateM runtime adapter readiness service and guards.
- Read-only adapter readiness API and UI.
- MarketData readiness diagnostic path.
- Runtime operational readiness safe summary API and UI.
- CI/security/no-outbound baseline and GateM release tag.

Important backend readonly evidence:

- `DefaultAdapterReadinessService` is a static fail-closed policy with no IO, credential, network, or runtime side effect.
- `OperationalReadinessService` returns a safe summary with no adapter, permission probe, HTTP, database, file, or exchange client dependency.
- `HistoricalKlineAdapter`, `OkxHistoricalKlineAdapter`, `BinanceHistoricalKlineAdapter`, and `AdapterHistoricalKlineProvider` are existing public historical marketdata inventory for GateN-1 review, not current real-provider authorization.

Important frontend readonly evidence:

- Runtime readiness UI presents LIVE disabled, Paper-only, adapter no-real, permission probe disabled / skipped, and operational readiness fail-closed states.
- Paper Trading route shell explicitly marks `SIM/Paper only`, `LIVE 未开启`, and `不接真实交易所`.

## Next Phase Candidate Options

### Option 1: Public MarketData Sandbox

Candidate name: **GateN Public MarketData / Exchange Sandbox Planning**.

Purpose:

- Establish a safe path toward real-exchange preparation by using only public marketdata, fake server, testnet-like fixtures, or no-egress sandbox validation.
- Prove parsing, normalization, rate-limit handling, stale/gap diagnostics, and source boundary behavior without private credentials or trading side effects.

Non-goals:

- No private exchange API.
- No order, cancel, transfer, withdraw, balance, position, or account endpoint.
- No credential read.
- No real permission probe.
- No LIVE.
- No RealClient / real provider.

Security boundary:

- Public-only or fake-source-only.
- No private endpoint, no signed request, no exchange account identity.
- No production endpoint as default.
- No outbound path until a no-egress/fake-server checklist is accepted.
- All failures map to explicit internal status and fail closed.

First implementation task:

- `NQ-GATEN-1B-PUBLIC-MARKETDATA-SANDBOX-HARNESS`: implement a fake-source / no-egress harness after `NQ-GATEN-1A-PUBLIC-MARKETDATA-SANDBOX-PLAN-REVIEW` is accepted.

Review requirement:

- Must pass plan review before implementation.
- Must have no-egress, no-credential, no-private-API, no-LIVE review before any code.

### Option 2: Exchange Adapter Readiness Next Phase

Candidate name: **GateN Exchange Adapter Sandbox Readiness**.

Purpose:

- Continue hardening OKX / Binance adapter contract around fake server, no-egress, no-real provider checklist, error mapping, retry policy, and sandbox capability declarations.

Non-goals:

- Do not implement true OKX / Binance private provider.
- Do not enable `*.unconfigured()` credential replacement with real credentials.
- Do not remove fail-closed guards.
- Do not use sandbox readiness as trading authorization.

Security boundary:

- Keep `DefaultAdapterReadinessService` fail-closed as the default.
- Any new adapter sandbox must be opt-in, local-only or test-only, and explicitly no-real.
- No credential material in logs, DTOs, tests, fixtures, comments, or docs.

First implementation task:

- `NQ-GATEN-1C-EXCHANGE-ADAPTER-FAKE-SERVER-CONTRACT`: document and implement fake-server contract tests only after sandbox plan review.

Review requirement:

- Must pass backend design review and security boundary review before implementation.

### Option 3: Paper Trading Productization

Candidate name: **GateN Productization / Paper Trading Workbench**.

Purpose:

- Improve Paper Trading Workbench, run timeline, risk/event replay, daily report, alert visualization, diagnostics, and review workflows.

Non-goals:

- No LIVE.
- No real exchange action.
- No AI decisioning.
- No real credential.
- No backend contract expansion unless separately planned.

Security boundary:

- Paper-only and SIM-only wording must remain visible.
- Any risky action must show impact and confirmation.
- Existing backend permission and account boundaries cannot be bypassed by frontend state.

First implementation task:

- `NQ-PAPER-WORKBENCH-RUN-TIMELINE-PLAN-REVIEW`, followed by a self-reviewed frontend-only slice if accepted.

Review requirement:

- Product/UI plan review first; implementation can be self-reviewed only for frontend-only display improvements that do not add API, migration, scheduler behavior, or trading side effects.

### Option 4: DH Integration-1 Planning

Candidate name: **DH/NQ Integration-1 Planning**.

Purpose:

- Plan a future read-only integration boundary after Integration-0 contract / mock / docs acceptance.

Non-goals:

- DH cannot call NQ write APIs.
- DH cannot place orders, cancel orders, start Paper Runs, read credentials, or modify trading state.
- No real runtime integration in this phase.

Security boundary:

- HMAC, timestamp, nonce, source allowlist, payload size, tenant binding, replay protection, provider trust policy, and audit trail remain mandatory.
- Existing DH P1-4 residuals block real Integration-1 runtime.

First implementation task:

- `NQ-DH-INTEGRATION1-READONLY-BOUNDARY-PLAN-REVIEW`, docs-only.

Review requirement:

- Must remain planning-only until DH residuals and NQ inbound/outbound security design are accepted.

### Option 5: Deployment / Observability Hardening

Candidate name: **Deployment / Observability Next Phase**.

Purpose:

- Build production-like runbooks, safe health/readiness checks, lightweight metrics, log boundaries, startup diagnostics, rollback rules, and incident checklists.

Non-goals:

- No production deployment.
- No LIVE authorization.
- No cloud resource creation.
- No real provider.
- No secret export.

Security boundary:

- Health checks must not be described as trading readiness.
- Logs and metrics must not include credential material, token, cookie, raw request, raw response, signature, or private key material.

First implementation task:

- `NQ-DEPLOY-OBSERVABILITY-LOCAL-RUNBOOK-PLAN-REVIEW`, docs-only first.

Review requirement:

- Plan review required before changes to deploy scripts, Docker, CI, health endpoints, or observability config.

### Option 6: Wallet / Agent Wallet Boundary Planning

Candidate name: **Wallet / Agent Wallet Boundary Planning**.

Purpose:

- Define NQ wallet, funds, signature, custody, and AI/DH spending boundaries before any wallet-like capability exists.

Non-goals:

- No mainnet wallet.
- No signing key storage.
- No transfer.
- No withdraw.
- No AI spending.
- No DH wallet runtime.

Security boundary:

- Wallet boundary is design-only.
- No private key, mnemonic, seed phrase, signing payload, transaction broadcast, or chain RPC integration.

First implementation task:

- `NQ-WALLET-BOUNDARY-THREAT-MODEL-PLAN`, docs-only.

Review requirement:

- Security review required before any code, storage, key management, connector, or external network implementation.

## Recommended Next Phase

Recommended next phase: **GateN Public MarketData / Exchange Sandbox Planning**.

Recommended status after this document:

- GateN planning baseline: **PLAN ONLY / NOT IMPLEMENTED**.
- GateN implementation: **NOT STARTED**.
- LIVE: **DISABLED**.
- AI: **NOT STARTED**.
- DH runtime: **NOT_INTEGRATED**.
- RealClient / real provider: **NOT_IMPLEMENTED**.

GateN planning baseline document:

- `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`.
- Status: **PASS / PLAN ONLY / READY TO COMMIT**.
- Scope: public marketdata / fake-server / no-egress / exchange sandbox planning only.
- Implementation remains **NOT STARTED**.

## Recommended Name

Use:

```text
GateN Public MarketData / Exchange Sandbox Planning
```

Rationale:

- The old roadmap line `GateN：AI 小资金 LIVE` is too high-risk immediately after a no-real GateM freeze.
- GateM produced runtime readiness, not real exchange authorization.
- A public marketdata / exchange sandbox planning phase is the correct bridge between no-real runtime readiness and any future real-provider work.
- AI small-funds LIVE must be deferred to a later separately approved phase after public/sandbox evidence, credential governance, real-provider checklist, AI/DH runtime boundary planning, and explicit LIVE authorization.

## Why This Next Phase

This is the safest useful continuation because it creates evidence for future real exchange preparation while keeping the system inside no-real constraints.

Decision factors:

- GateM already proved runtime fail-closed readiness; the next useful evidence is controlled public/sandbox data behavior, not private trading.
- Existing OKX/Binance adapters remain legacy network-capable code but are not authorized real providers.
- MarketData readiness is diagnostic-only today; improving a sandboxed public marketdata path gives value without touching order execution.
- Paper Trading productization remains important, but it does not reduce the largest post-GateM safety gap: how to approach external exchange data without credentials or private trading.
- DH Integration-1 and wallet boundaries are higher-risk and should wait behind stricter security design reviews.

## Security Boundary

GateN Public MarketData / Exchange Sandbox Planning must enforce:

- Public data only or fake-source only.
- No private exchange endpoint.
- No signed request.
- No account, balance, position, order, cancel, transfer, or withdraw API.
- No credential read.
- No credential output.
- No raw provider payload in logs or docs.
- No default real endpoint.
- No production endpoint.
- No LIVE.
- No AI runtime.
- No DH runtime.
- No RealClient / real provider.
- No permission probe real execution.
- No wallet, key, mnemonic, or signing capability.

Any future implementation must:

- Set explicit timeout, retry, rate-limit, and error mapping rules.
- Avoid unbounded fetch, unbounded cache, and unbounded logs.
- Preserve no-egress test modes and fake-server tests.
- Fail closed on unknown venue, unknown capability, unavailable sandbox, malformed payload, stale data, or unsupported operation.
- Keep MarketData readiness as diagnostic only.

## Workstream Breakdown

### GateN-0: Plan Review And Freeze

- Type: docs-only review/freeze.
- Goal: accept or reject this planning baseline.
- Output: plan review and optional freeze doc.
- Implementation: none.

### GateN-1: Public MarketData Sandbox Contract

- Type: backend design review before code.
- Goal: define allowed source types, public-only fields, no-egress harness, fake-server requirements, payload schema, error model, and audit-safe logging.
- Implementation: not started.

### GateN-2: Fake-Server / No-Egress Harness

- Type: backend test infrastructure after review.
- Goal: prove parsing and error handling without real network or credentials.
- Implementation: allowed only after GateN-1 review.

### GateN-3: Public MarketData Readiness Refinement

- Type: backend / frontend narrow implementation after review.
- Goal: refine diagnostic status, freshness, gap, stale, malformed payload, and unavailable source states.
- Implementation: must remain diagnostic-only and no-trading.

### GateN-4: Exchange Adapter Sandbox Checklist

- Type: contract and safety checklist.
- Goal: decide what must exist before a future real provider Gate.
- Implementation: docs-first; code only after review.

### GateN-5: Product Surface Follow-Up

- Type: frontend productization optional.
- Goal: expose sandbox/public-readiness state without implying real readiness.
- Implementation: self-reviewed only if frontend-only and no API/migration/backend changes.

## Suggested Task Order

1. `NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN-REVIEW`
2. `NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN-FREEZE`
3. `NQ-GATEN-1A-PUBLIC-MARKETDATA-SANDBOX-CONTRACT`
4. `NQ-GATEN-1A-PUBLIC-MARKETDATA-SANDBOX-CONTRACT-REVIEW`
5. `NQ-GATEN-1B-PUBLIC-MARKETDATA-SANDBOX-HARNESS`
6. `NQ-GATEN-1B-PUBLIC-MARKETDATA-SANDBOX-HARNESS-REVIEW`
7. Optional: `NQ-PAPER-WORKBENCH-RUN-TIMELINE-PLAN-REVIEW`
8. Optional: `NQ-DEPLOY-OBSERVABILITY-LOCAL-RUNBOOK-PLAN-REVIEW`
9. Later only: `NQ-DH-INTEGRATION1-READONLY-BOUNDARY-PLAN-REVIEW`
10. Later only: `NQ-WALLET-BOUNDARY-THREAT-MODEL-PLAN`

## Implementation Readiness

Next phase planning readiness: **READY TO COMMIT**.

Next phase implementation readiness:

- Not allowed in this task.
- Allowed only after this plan is accepted and a separate implementation task explicitly authorizes the first narrow slice.
- First implementation must be preceded by plan review.
- First implementation must be no-real, public-only or fake-source-only, no-private-API, no-credential, no-LIVE, no-AI, no-DH-runtime, and no-wallet.

## Review Requirements

Must be reviewed before implementation:

- Any backend adapter, marketdata source, retry, rate-limit, HTTP client, parser, DTO, API, migration, scheduler, CI, or deploy change.
- Any DH/NQ integration step.
- Any wallet, funds, signing, custody, or spending boundary.
- Any use of real endpoint, testnet endpoint, public network, or exchange-specific behavior.
- Any change that could affect trading, risk, order, ledger, audit, credential, or account context.

Can be self-reviewed ready to commit:

- Docs-only synchronization within the approved files.
- Frontend-only wording or layout refinement that does not add API calls, change backend contracts, alter trading behavior, or hide risk states.
- Test-only fake fixture improvements that do not touch real network, credential material, or production config.

## P0 / P1 / P2 / P3 Risks

### P0

- None found in current post-GateM planning scope.

### P1

- None found as a blocker for planning.

Potential P1 if violated later:

- Treating public marketdata sandbox as trading authorization.
- Adding private exchange API or credential access.
- Enabling LIVE or real provider without explicit gate authorization.

### P2

- Public marketdata sandbox still needs a concrete no-egress / fake-server contract before code.
- Any future real public endpoint use needs explicit timeout, retry, rate-limit, bounded payload, and logging policy.
- Existing roadmap previously named GateN as AI small-funds LIVE; this plan recommends renaming GateN to the safer public marketdata / sandbox bridge and deferring AI small-funds LIVE.

### P3

- Paper Trading Workbench productization remains valuable but should be a parallel follow-up, not the primary post-GateM safety bridge.
- UI/UX professionalism remains a known post-freeze remediation area; do not mark it complete as part of this plan.
- Some historical docs still contain older GateK/GateL/GateM route wording; current `docs/current` entries must remain the authority.

## First Recommended Implementation Task

First recommended implementation task after plan review:

```text
NQ-GATEN-1B-PUBLIC-MARKETDATA-SANDBOX-HARNESS
```

Entry conditions:

- `NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN-REVIEW` accepted.
- `NQ-GATEN-1A-PUBLIC-MARKETDATA-SANDBOX-CONTRACT` accepted.
- No P0/P1/P2 blocker in review.
- Explicit user authorization for implementation.

Allowed first-slice behavior:

- Fake-server or no-egress harness only.
- Public marketdata shape only.
- Local/test-only fixture or controlled public-read sandbox only.
- No credentials, no private endpoint, no order/cancel/transfer/withdraw, no LIVE.

## Final Decision

Decision: **PASS / PLAN ONLY / READY TO COMMIT**.

Recommended next phase:

```text
GateN Public MarketData / Exchange Sandbox Planning
```

Final boundary:

- This plan does not start GateN implementation.
- GateN implementation remains **NOT STARTED**.
- GateN planning baseline is now tracked in `NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`.
- AI small-funds LIVE is deferred to a later separately planned phase.
- DH Integration-1 remains planning-only until security blockers and runtime boundaries are separately accepted.
- Wallet / Agent Wallet remains design-only until a separate threat model and security review are accepted.

Commit recommendation:

```text
docs(roadmap): plan post-GateM next phase
```
