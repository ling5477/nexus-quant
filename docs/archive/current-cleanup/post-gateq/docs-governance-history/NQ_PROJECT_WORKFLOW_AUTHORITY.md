# NQ Project Workflow Authority

## 1. Purpose

This document is the current NexusQuant workflow authority for task routing, documentation authority, review policy, docs budget, test budget, `/goal` checkpoint usage, and GateM entry control.

It consolidates process rules only. It does not change runtime behavior, backend code, frontend code, research code, CI workflow, migrations, deployment, Gate archives, credentials, LIVE trading, AI runtime, DH runtime, real exchange access, or release tags.

## 2. Documentation Authority

Canonical documentation topology:

- `docs/current/` = current facts, active control, active baseline, current runbook, and current index.
- `docs/gates/` = immutable completed Gate archive.
- `docs/gates/gate-x/` = archive directory for a completed Gate.
- `docs/gates/gate-x/source/` = historical source evidence copied for traceability; it preserves original wording and is not current control.
- `docs/evidence/` = historical evidence that is not active current control.
- root-level `gate/` = forbidden.
- `docs/current/gates/` = forbidden.

Gate completion rule:

- A Gate may be archived only after the Gate is complete and accepted.
- Completed Gate archives belong under `docs/gates/gate-x/` using lowercase kebab naming.
- Gate archive files must not be edited to drive active implementation. Active planning and control stay in `docs/current/`.
- Historical source evidence may remain historically inconsistent by design. Do not rewrite source evidence unless a task explicitly authorizes historical correction.

Current authority rule:

- `docs/current/README.md` is the entry index.
- `STATUS.md`, `ROADMAP.md`, `TESTING.md`, `RUNBOOK.md`, `API.md`, `DB_SCHEMA.md`, `ARCHITECTURE.md`, and `MODULES.md` are current-state surfaces only when they directly match the task domain.
- The newest task prompt and verified current worktree state are authoritative for the current operation.
- Archived Gate documents are historical evidence unless the user explicitly asks for historical comparison or archive review.

## 3. Task Classification

Use exactly one primary classification per task, with auxiliary classification only when it affects validation or boundary control.

- `CODE_CHANGE`: production code, runtime wiring, API, UI, domain logic, adapter logic, scheduler, or service behavior changes.
- `TEST_CHANGE`: tests, fixtures, smoke specs, test helpers, or verification-only code changes.
- `DOCS_ONLY`: documentation changes that do not modify runtime, tests, CI, migrations, or deployment.
- `CI_CHANGE`: workflow, action, CI guard, artifact, cache, matrix, or runner behavior changes.
- `SECURITY_FIX`: credential, secret handling, auth, signing, redaction, no-outbound, permission, tenant, audit, or trust-boundary fixes.
- `REVIEW_ONLY`: read-only analysis, audit, topology review, architecture review, or evidence review with no file changes.
- `FREEZE_REVIEW`: phase/gate/slice acceptance review that may produce a freeze record only when explicitly requested.
- `ARCHIVE`: materializing or validating a completed Gate archive under `docs/gates/`.
- `RELEASE_TAG`: release note, tag prep, final release verification, or tag operation.

Default classification guard:

- If a task says review-only or audit-only, do not edit files.
- If a task says docs-only, do not edit code, CI, tests, migrations, deployment, or scripts.
- If a task touches LIVE, real exchange, credentials, AI runtime, DH runtime, migrations, CI, or backend trading path, treat it as high-risk even when the requested diff is small.

## 4. Review Policy

Mandatory review before commit:

- Any P0 or P1 finding.
- CI workflow or CI artifact/redaction behavior.
- Security, credential, auth, permission, tenant, secret, no-outbound, LIVE, real exchange, real provider, or RealClient changes.
- Database migration, schema semantics, backfill, constraints, or Flyway/Liquibase changes.
- Backend trading path, order state, risk gate, ledger, scheduler execution, exchange adapter execution, marketdata subscription, or credential permission probe behavior.
- Release tag, public release note, or Gate archive freeze.

Review not mandatory when scope is narrow and validation is appropriate:

- Ordinary frontend component extraction with no API/contract/runtime change.
- Route-local UI cleanup when the page still passes the relevant build or E2E scope.
- Test-only changes when the target is explicit and the intended tests pass.
- Docs-only cleanup outside phase freeze/archive/release boundaries.
- Mechanical typo/path/reference fixes inside a single allowed documentation area.

Review-only rule:

- `REVIEW_ONLY` produces findings and recommendations, not commits.
- Do not create review-of-review loops. A second review is allowed only when P0/P1 remains open, the evidence changed, or the user explicitly asks for another review.
- Do not create freeze-of-freeze loops. Freeze records are phase/slice boundary artifacts, not a default closeout for every small task.

## 5. Docs Budget Policy

Default docs budget:

- Default: do not edit docs for ordinary code tasks.
- If durable recording is necessary for a normal task, prefer one precise `WORKLOG.md` line only when the user or current process explicitly allows it.
- Do not update `STATUS.md`, `ROADMAP.md`, `TESTING.md`, and `WORKLOG.md` for every small implementation step.

Required docs updates:

- New or changed public API: update `API.md` if the API contract actually changed.
- Migration or schema semantics: update `DB_SCHEMA.md` if schema or migration semantics actually changed.
- Phase boundary, Gate completion, freeze, archive, or release: update the relevant current-state and archive documents explicitly authorized by the task.
- Workflow authority changes: update the entry index that makes the authority discoverable.

Forbidden docs patterns:

- Do not use docs churn to compensate for missing tests or weak implementation.
- Do not rewrite historical evidence to make current status look cleaner.
- Do not duplicate the same status across five or six current documents for intermediate steps.
- Do not create docs-only follow-up tasks merely to keep documents synchronized when no operational boundary changed.

## 6. Test Budget Policy

Code change validation:

- Run the smallest relevant test set that proves the changed behavior.
- Backend changes should use Maven module tests or full backend tests depending on blast radius.
- Frontend changes should use build and route/page E2E when behavior or rendered UI changes.
- Python changes should use `pytest`, `mypy`, and `ruff` for the relevant research package scope.

Test-only validation:

- A test-only task must name the behavior it protects.
- If the new or changed test passes and no production code changed, do not force a separate review loop unless it covers P0/P1, security, CI, migration, LIVE, real exchange, or backend trading path risk.

Frontend E2E boundary:

- Backend-dependent E2E must state its environment requirements.
- Local backend E2E and CI backend E2E must not be mixed into one status sentence.
- If `127.0.0.1:18888` or the required backend is unavailable, report `environment missing backend`; do not label it as a frontend regression without stronger evidence.

Test churn guard:

- Do not create a separate test task for every small UI state.
- Prefer route-level or workflow-level tests that protect actual regressions.
- Do not weaken security/no-outbound/credential boundaries to make tests green.

## 7. `/goal` Mode Policy

Allowed use:

- `/goal` may coordinate a multi-step task when the checkpoints are explicitly stated by the user.
- Each checkpoint must have its own scope, evidence, validation, and commit recommendation.
- Checkpoint evidence must be based on current worktree and current external state, not memory or intent.

Stop conditions:

- Stop when a checkpoint produces P0/P1.
- Stop when the next checkpoint would cross a Gate, release, runtime, LIVE, real exchange, credential, AI, DH, CI, migration, or backend trading boundary that was not explicitly authorized.
- Stop when validation evidence is missing or contradictory.

Execution rule:

- Do not auto-execute the next checkpoint merely because it is a plausible follow-up.
- A later checkpoint may proceed in the same persisted goal only when it was explicitly included in the original goal and its stated preconditions are satisfied.
- Batch 4, Batch 5, GateM, release/tag, and cleanup work must not be mixed into one commit unless the user explicitly defines that exact combined commit scope.

Closeout rule:

- Do not mark a goal complete until every explicit artifact, validation command, forbidden-path check, risk matrix, and final recommendation required by the original goal has current evidence.
- If the goal remains incomplete, keep it active and report concrete progress rather than redefining success.

## 8. GateM Entry Policy

GateM entry rule:

- GateM can start only after GateK release/tag handoff is explicit.
- GateM initial entry must be planning-only unless a later task explicitly authorizes a bounded implementation.
- Existing adapter readiness work must remain fail-closed and must not be interpreted as LIVE, real-provider, real-credential, or future-real authorization.

GateM forbidden direct entry:

- No LIVE enablement.
- No real exchange trading.
- No real credential read.
- No AI runtime trading.
- No DH runtime trading.
- No direct nine-exchange expansion.
- No RealClient or real provider wiring without a separate reviewed Gate.
- No permission probe against a real exchange without explicit authorization and security review.

GateM planning requirements:

- Define exact capability boundary.
- Define fail-closed behavior.
- Define no-outbound and credential handling.
- Define API, UI, backend, CI, and test blast radius.
- Define rollback.
- Define P0/P1 stop rules.

## 9. Final Workflow Summary

Canonical workflow:

```text
Plan -> Implementation -> Self-review -> Validation -> Commit
```

Phase-boundary workflow:

```text
Plan -> Implementation -> Self-review -> Validation -> Commit -> Gate archive / Release only at phase boundary
```

Forbidden workflow patterns:

- plan -> review -> review -> freeze -> freeze-of-freeze
- docs churn for every small task
- test churn for every small UI state
- unrelated plugin or skill activation
- checkpoint auto-run without explicit authorization
- combining unrelated Gate, batch, release, and cleanup work into one commit

Commit guidance:

- Review-only tasks do not produce commits.
- Docs-only non-freeze cleanup may be committed after `git diff --check` and forbidden-path status checks.
- Code changes require relevant tests before commit recommendation.
- CI/security/migration/LIVE/real exchange/backend trading path changes require review before commit recommendation.

## 10. Current Findings

P0:

- None.

P1:

- None.

P2:

- Current project stage wording remains split across older GateJ/GateK planning language, GateK archive language, and later GateM adapter readiness language. This authority document defines workflow control but does not rewrite all stage facts.
- Global agent entry files may still need a later minimal stage/authority sync after GateK release/tag preparation is complete.

## 11. Final Recommendation

Use this document as the task-flow authority before GateK release/tag preparation and before any further GateM work.

Recommended next task after this consolidation:

```text
NQ-GATEK-RELEASE-NOTE-AND-TAG-PREP
```
