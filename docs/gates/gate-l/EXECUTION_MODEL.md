# GateL Execution Model

## Final State

```text
GateL: COMPLETED / ARCHIVED / NON-ACTIVE
```

GateL has no active execution layer in this archive. The execution model below records the accepted No-Real behavior and the constraints that protect NQ from accidental real-exchange execution.

## Execution Flow

GateL execution flow is fail-closed by design:

1. Runtime defaults start from disabled exchange configuration.
2. OKX/Binance endpoint defaults remain `disabled://` sentinels.
3. OKX/Binance credential defaults remain unconfigured.
4. Authenticated/private paths fail before network access when credentials are unavailable.
5. Noop marketdata subscription paths return disabled acknowledgements, not success-shaped live subscriptions.
6. Permission probe defaults remain NoReal / skipped / disabled.
7. Any future real behavior requires a separate Gate and explicit authorization outside GateL.

The archive does not run this flow. It records the expected boundary semantics.

## Data Flow

GateL data flow is contract-level:

- Adapter contracts describe request/ack/snapshot/error shapes.
- No-Real adapter surfaces return disabled, stub, or unconfigured results.
- Error model maps no-real, network-disabled, credential-missing, permission, rate-limit, unavailable, risk, order, ledger, and unknown categories into fail-closed interpretations.
- Capability matrix records what is disabled, stub-only, future-real gated, or forbidden.
- Readiness checklist records prerequisites for any separate future-real Gate.

No real provider payload, credential material, private response body, signature, token, cookie, or secret is part of the GateL archive data flow.

## Constraints

GateL constraints are:

- No LIVE.
- No real exchange access.
- No real provider.
- No RealClient.
- No real permission probe.
- No credential material read.
- No AI runtime.
- No DH runtime.
- No order, cancel, withdrawal, or transfer execution through real venues.
- No adapter bypass of `RiskGate`, `OrderStateMachine`, ledger, or audit ownership.
- No raw provider payload exposure from OKX/Binance order ack or snapshot producers.
- No interpretation of `disabled://`, unconfigured credentials, or `NO_REAL_DISABLED` as readiness.

## Runtime Boundary

The runtime boundary is locked:

```text
LIVE: OFF
AI: OFF
DH: OFF
REAL EXCHANGE: OFF
CREDENTIAL ACCESS: FORBIDDEN
ADAPTER READINESS: NOT READY / NOT FROZEN / NOT AUTHORIZED
```

GateL archive is non-active. It does not define a service to start, a job to run, or a test to execute.

## CI Boundary

GateL does not mutate CI. It inherits CI/security as a protection boundary:

- no-outbound remains fail-closed.
- secret scan and redaction rules remain mandatory.
- evidence must be explicit and reviewable.
- CI success must not be interpreted as runtime permission.

No workflow file is part of this archive change.

## Backend Boundary

Backend modules remain unchanged by this archive. The archived model requires backend adapters to preserve:

- disabled endpoint defaults.
- unconfigured credential defaults.
- pre-network credential failure for private paths.
- Noop marketdata disabled acknowledgements.
- NQ-owned risk, order state, ledger, and audit boundaries.

## Frontend Boundary

GateL has no active frontend surface in this archive. Any frontend mention of adapter or exchange readiness must remain read-only and must not display real exchange availability unless a separate authorized Gate changes the system state.

## E2E Boundary

GateL archive does not add, run, or require E2E tests. Existing source documents may reference prior validation evidence. This directory migration does not reinterpret that evidence and does not create new validation results.
