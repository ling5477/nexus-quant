---
name: nq-docs-writer
description: NQ/DH documentation writer and governance skill for NexusQuant and Decision Hub work. Use for DOCUMENTATION, DOCS_ONLY, DOCUMENTATION_CLEANUP, DOCUMENTATION_RECONCILIATION, FACT_SOURCE_SYNC, ROADMAP_CLEANUP, PLAN, PLANNING_ONLY, REVIEW, FREEZE_REVIEW, FINAL_FREEZE, STATUS_SYNC, TESTING_SYNC, WORKLOG_SYNC, API_DOC_UPDATE, DB_SCHEMA_DOC_UPDATE, FRONTEND_DOC_UPDATE, CI_DOC_UPDATE, GATE_PLAN, GATE_FREEZE, ACCEPTANCE_REPORT, IMPLEMENTATION_REPORT, RELEASE_HANDOFF, and POST_FREEZE_FIX_DOCS tasks; also use as a supporting skill when implementation work needs TESTING/WORKLOG/API/DB_SCHEMA/frontend docs synchronization while preserving NQ/DH Gate, no-real, credential, LIVE, AI, and DH runtime boundaries.
---

# NQ Docs Writer

Use this skill for NQ/DH documentation writing, synchronization, review, reconciliation, freeze records, acceptance reports, implementation reports, and current-control documentation updates.

This skill does not authorize business implementation. It only governs documentation decisions and the evidence needed before writing documentation facts.

## Preflight

1. Read `AGENTS.md`, `README.md`, `docs/current/README.md`, and the current target documentation before writing.
2. For current state work, read `docs/current/STATUS.md`, `docs/current/ROADMAP.md`, `docs/current/TESTING.md`, and `docs/current/WORKLOG.md` as needed.
3. For API, schema, frontend, CI, or integration docs, read the corresponding current docs first: `docs/current/API.md`, `docs/current/DB_SCHEMA.md`, `docs/current/frontend/**`, CI current authority docs, or NQ-DH Integration-0 docs.
4. Check existing diffs before editing so pre-existing user changes are not overwritten.
5. Define allowed files, forbidden files, expected output, and validation commands before making changes.

Do not scan or read credential-bearing or generated areas for docs work unless the user explicitly authorizes a bounded audit: `.env`, `*.key`, `*.pem`, `secrets`, `credentials`, `logs`, `dumps`, `backups`, `target`, `node_modules`, `dist`, `build`, `.git`, `coverage`, and `test-results`.

## Fact Source Priority

Use this priority order when facts conflict:

1. Current code and actual test/CI results outrank old documentation.
2. `docs/current/STATUS.md`, `ROADMAP.md`, `TESTING.md`, and `WORKLOG.md` are the current status facts.
3. `README.md` and `docs/current/README.md` are entry indexes and current-stage summaries.
4. `docs/current/API.md` documents only real current API, not planned API.
5. `docs/current/DB_SCHEMA.md` documents only real current schema, not future migrations.
6. `docs/current/frontend/**` documents frontend design, pages, E2E status, and UI facts.
7. Historical Gate docs, archive docs, old reviews, and old freeze records are evidence, not authority over current code.
8. If attachments, prompts, old docs, and current repository facts conflict, perform documentation reconciliation first and do not overwrite current facts blindly.

Do not hard-code the project stage from this skill. Re-read current facts each turn before writing state, Gate, LIVE, AI, DH, real-provider, or test-result claims.

## Writing Rules

Write documentation only from verified facts. If a command was not run, say it was not run. If evidence is missing, write `BLOCKED`, `PENDING`, `NOT RUN`, `CONDITIONAL PASS`, or another precise state instead of inferring success.

Never write:

- `planned` as `implemented`.
- `pending` as `passed`.
- `mock`, `stub`, `fixture`, `fake`, or `no-real` as `real`.
- `no-real` as `real-ready`.
- `Paper` or `SIM` as `LIVE`.
- `AI not started` as `AI started`.
- `DH runtime not integrated` as `DH integrated`.
- `RealClient`, `real provider`, or `real exchange adapter` as implemented unless code and verification prove it.
- backend-free smoke as real backend E2E.
- empty/no-data smoke as positive bars smoke.
- source health pending backend support as source health completed.
- skipped, blocked, or failed validation as passed.

Never output credential material, API keys, exchange secrets, tokens, cookies, signatures, private keys, passphrases, mnemonic material, raw credential payloads, or raw provider responses.

Prefer append-only updates. Do not rewrite history unless the task explicitly asks for a reconciliation edit and the current-control source proves the old line is stale. `WORKLOG.md` and `TESTING.md` should append this turn's result; do not overwrite existing GateK, GateL, GateM, frontend, CI, or integration records.

`docs/current` must not expand without current-control value. New docs need a clear reason: stage freeze, P0/P1 risk, security boundary, migration, CI workflow, credential, LIVE, real provider, API contract, DB schema, frontend current fact, acceptance report, or explicit user instruction.

After a freeze, do not rewrite tag-bound history. Use post-freeze fix, hotfix, addendum, or a new tag path.

## Anti-Churn Rules

1. Do not create standalone review docs unless the task is a stage freeze, P0/P1 risk, security boundary, migration, CI workflow, credential, LIVE, real provider, or explicit user request.
2. Ordinary code tasks can end with `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT` and do not need a separate review or freeze doc.
3. Docs-only tasks must not chain plan -> review -> freeze unless the user explicitly asks or a stage checkpoint requires it.
4. Do not create separate docs tasks for every small UI state, button state, or E2E state.
5. Documentation must serve the next implementation or review decision; it must not replace code progress.
6. If the task is only continuing to expand existing docs without a control reason, recommend stopping and moving to implementation or verification.
7. If a docs/current physical slimming line is closed, do not continue cleanup rounds unless the user explicitly authorizes a known compatibility residual.

## Document Type Requirements

Plan docs must include: Current state, Goal, Non-goals, Scope, Allowed changes, Forbidden changes, Existing capability, Proposed model/API/flow, Testing strategy, Security boundary, P0/P1/P2/P3 risks, Recommended next implementation task, and Final decision.

Review docs must include: Review target, Evidence checked, Validation, Findings P0/P1/P2/P3, Boundary confirmation, Decision, Follow-up, and Commit recommendation.

Freeze docs must include: Frozen baseline, Accepted evidence, Validation commands, Known residuals, Post-freeze rules, Tag/commit info if applicable, No-goals, and Next phase gate.

Implementation reports must include: Task classification, Scope, Files inspected, Files changed, Implementation, Validation, Boundary confirmation, P0/P1/P2/P3 findings, Final decision, and Commit recommendation.

`TESTING.md` updates must include: Command, Result, Scope, Environment, Known warnings, What was not run, and whether the result is blocking or non-blocking.

`WORKLOG.md` updates must include: Task name, Date or sequence marker, Scope, Result, Validation, Boundary, and Next step.

`API.md` updates must include: Real endpoint only, request params/body, response fields, error semantics, security constraints, and not-implemented items clearly marked as not implemented.

`DB_SCHEMA.md` updates must include: Current tables/columns only, migration version if relevant, no future schema as current fact, sensitive field rules, and append-only/retention/deletion semantics when relevant.

Frontend docs must include: Page/component scope, data source, mock vs real backend status, E2E status, UI states, not-implemented states, and no LIVE / real exchange / AI / DH boundary when relevant.

## Validation

For docs-only or docs-governance work, run at least:

```powershell
git status --short
git diff --check
git diff --stat
```

When scope boundaries matter, also run scoped forbidden-area checks:

```powershell
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
```

When security, credential, LIVE, real provider, or exchange boundaries are relevant, search with generated and sensitive directories excluded:

```powershell
rg -n --glob '!node_modules/**' --glob '!target/**' --glob '!build/**' --glob '!dist/**' --glob '!.git/**' --glob '!logs/**' --glob '!test-results/**' --glob '!coverage/**' --glob '!dumps/**' --glob '!backups/**' "LIVE=true|LIVE_ENABLED|RealClient|real provider|apiKey|secret|passphrase|private key|mnemonic|WebSocket|order|cancel|withdraw|transfer|OKX|Binance|Bybit|Gate|Coinbase|Kraken"
```

Treat broad search hits as prompts for review, not automatic failures. Separate positive current facts from negated, historical, or forbidden-scope mentions.

Run Maven, frontend build/E2E, Python pytest/mypy/ruff, or GitHub CI checks only when the changed area requires them. For pure docs work, explain why code tests were not run.

## Output Format

Use this shape for documentation work:

```text
Task classification:
Scope:
Files inspected:
Files changed:
Documentation decision:
Validation:
Boundary confirmation:
P0/P1/P2/P3 findings:
Final decision:
Commit recommendation:
Next concrete action:
```

For review-only work, put findings before summary and cite exact files or evidence. For implementation-report docs, include rollback and whether prohibited NQ/DH scope was touched.
