# NQ GateK CI Security Contract

## Contract Identity

- Document id: `NQ_GATEK_CI_SECURITY_CONTRACT`
- Contract status: `FINAL CONTRACT LOCKED`
- Batch status: Batch 4C `FROZEN`; Batch 4C-C `FROZEN`
- Scope: GateK CI evidence, log shape, artifact retention, backend stdout discipline, and fail-closed security proof.

This document persists the accepted GateK CI security contract. It is not a CI implementation document and does not change workflow files, backend code, frontend code, tests, API, migration, runtime configuration, or exchange integration.

## Secret Leakage Definition

Secret leakage means any CI-visible output that exposes protected runtime material or enough unredacted shape to reconstruct protected material.

Leakage includes:

- Direct value disclosure of credential material.
- Unredacted authorization headers, signing material, session material, or private exchange account material.
- Environment dumps that include protected key/value pairs.
- Raw provider payloads that carry protected request or response material.
- Artifact files that preserve protected material after a job completes.

The contract treats partial disclosure as unsafe when the visible shape could identify, reconstruct, or validate protected material.

## Forbidden Log Shapes

Forbidden log shapes are any CI log, backend stdout line, or retained artifact line that exposes protected material or sensitive runtime context.

Forbidden shapes include:

- Raw environment variable dumps.
- Raw request or response payloads from private provider paths.
- Unmasked authentication, signing, session, account, or exchange material.
- Full stack context that prints protected configuration values.
- Debug output that echoes private headers or private query parameters.
- File artifacts that retain unsafe log bodies after redaction should have applied.

Forbidden shapes fail the contract even if the surrounding job succeeds.

## Allowed Log Shapes

Allowed log shapes are bounded, deterministic, and non-sensitive.

Allowed shapes include:

- Job status labels such as `PASS`, `FAIL`, `FROZEN`, `ACCEPTED`, and `BLOCKED`.
- Counts, booleans, route names, test names, and file names that do not expose protected material.
- Redacted placeholders that preserve evidence shape without exposing values.
- Public status categories, adapter capability categories, and fail-closed reason labels.
- Artifact names and checksums when the artifact body is policy-compliant.

Allowed logs must remain sufficient for review while avoiding protected value disclosure.

## CI Proof Contract

The CI proof contract is based on two explicit outcomes:

- `PROOF_OK`: the checked surface produced deterministic evidence and no forbidden log or artifact shape was observed.
- `REDACTION_HIT`: the redaction detector encountered a protected shape and handled it as a redaction event rather than allowing raw value disclosure.

`PROOF_OK` is not valid if the proof path is skipped, ambiguous, or unable to inspect the intended surface. `REDACTION_HIT` is valid only when the protected material remains non-disclosed and the hit is represented as bounded evidence.

The proof contract is fail-closed: missing evidence, malformed evidence, or unsafe output fails the contract.

## Artifact Policy

Artifacts are gated evidence surfaces.

- Artifact upload must be intentional and narrow.
- Text artifacts must pass the same redaction contract as logs.
- Binary or rich debugging artifacts are not part of this GateK security freeze.
- Artifacts must not retain raw backend stdout if stdout contains forbidden shapes.
- Artifact existence does not imply safety; artifact content must satisfy the policy.

The artifact policy protects the repository and CI evidence trail from retaining unsafe material after a job completes.

## Backend Stdout Rules

Backend stdout is part of the CI security contract whenever backend-dependent smoke or proof jobs capture it.

Backend stdout must:

- Emit bounded status, category, route, count, and failure-reason information only.
- Avoid raw environment dumps.
- Avoid raw provider payloads.
- Avoid protected account or exchange material.
- Preserve fail-closed reason labels without exposing protected values.

Backend stdout must not become a side channel for leaking protected material through successful tests.

## Fail-Closed Rule

The fail-closed rule is active.

If CI cannot prove a surface is safe, the contract outcome is failure or blocked review, not success. Ambiguous log shape, missing proof, redaction detector failure, artifact scan failure, or unexpected backend stdout must not be interpreted as acceptance.

Fail-closed behavior is part of the freeze boundary and must remain stronger than convenience logging or partial proof.

## Batch 4C / 4C-C / Batch 5 Boundary

- Batch 4C: `FROZEN`.
- Batch 4C-C: `FROZEN`.
- GateK CI Security Spec: `FINAL CONTRACT LOCKED`.
- Batch 5 boundary: this document persists the already accepted boundary and does not expand Batch 5 execution, workflow scope, frontend E2E scope, backend behavior, runtime behavior, LIVE behavior, AI behavior, DH behavior, or real exchange behavior.

This contract is a documentation freeze artifact only. It is not a Batch 5 implementation vehicle and does not mutate CI.
