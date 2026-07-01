---
name: nq-docs-writer
description: NQ/DH documentation writer and governance skill for NexusQuant and Decision Hub work. Use for DOCUMENTATION, DOCS_ONLY, DOCUMENTATION_CLEANUP, DOCUMENTATION_RECONCILIATION, FACT_SOURCE_SYNC, ROADMAP_CLEANUP, PLAN, PLANNING_ONLY, REVIEW, FREEZE_REVIEW, FINAL_FREEZE, STATUS_SYNC, TESTING_SYNC, WORKLOG_SYNC, API_DOC_UPDATE, DB_SCHEMA_DOC_UPDATE, FRONTEND_DOC_UPDATE, CI_DOC_UPDATE, GATE_PLAN, GATE_FREEZE, ACCEPTANCE_REPORT, IMPLEMENTATION_REPORT, RELEASE_HANDOFF, POST_FREEZE_FIX_DOCS, STAGE_TRANSITION_ARCHIVE, ARCHIVE_INVENTORY, ARCHIVE_PLAN_REVIEW, ARCHIVE_MOVE_BATCH, ARCHIVE_CLOSEOUT, RELEASE_TAG_AND_ARCHIVE, FINAL_CLOSURE, DOCS_GOVERNANCE, and CURRENT_DOCS_CLASSIFICATION tasks; also use as a supporting skill when implementation work needs TESTING/WORKLOG/API/DB_SCHEMA/frontend docs synchronization while preserving NQ/DH Gate, no-real, credential, LIVE, AI, and DH runtime boundaries.
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

## Language Governance

Use these language rules for all new NQ/DH documentation, documentation governance, templates, and implementation or review reports:

- 文档正文必须中文为主；`README`、`STATUS`、`ROADMAP`、`TESTING`、`WORKLOG` 和 `docs/current` 说明文档不得整篇英文化。
- 允许保留英文任务名、状态枚举、类名、接口名、字段名、文件名、路径、命令、配置键、commit message 和协议原文。
- 英文状态值首次出现时必须附中文解释，例如 `PASS`（通过）、`FROZEN`（已冻结）、`READY TO COMMIT`（可进入提交前复核）。
- 代码注释中的业务规则说明优先使用中文；协议字段、API contract、enum、wire format 可保留英文或使用中英双语。
- DB comment 必须使用中文业务语义；表名、字段名、索引名、约束名保持英文。
- 不翻译 `docs/archive/**`、`docs/gates/**` 历史文档；旧文档只在后续因任务触碰时顺手修正语言漂移。
- Agent 输出报告的栏目名可以保留英文，但每个栏目内容必须中文为主，并保留精确英文状态值和文件路径。
- 不得为了中文化而改写英文枚举、API 字段、类名、接口名、文件名或历史 release/tag 事实。

Prefer append-only updates. Do not rewrite history unless the task explicitly asks for a reconciliation edit and the current-control source proves the old line is stale. `WORKLOG.md` and `TESTING.md` should append this turn's result; do not overwrite existing GateK, GateL, GateM, frontend, CI, or integration records.

`docs/current` must not expand without current-control value. New docs need a clear reason: stage freeze, P0/P1 risk, security boundary, migration, CI workflow, credential, LIVE, real provider, API contract, DB schema, frontend current fact, acceptance report, or explicit user instruction.

After a freeze, do not rewrite tag-bound history. Use post-freeze fix, hotfix, addendum, or a new tag path.

## Stage Transition Archive Governance

Use this section when a Gate, phase, or release line has finished and current-control docs need archive classification. This is a governance workflow only; it does not authorize business code, CI, migration, backend, frontend, Python, LIVE, AI, DH runtime, RealClient, real provider, credential, or exchange changes.

Trigger stage transition archive governance when any of these signals appear:

- A Gate or phase is marked `COMPLETED`, `CLOSED`, or `FROZEN`.
- A Gate or phase release tag is pushed.
- The next phase is selected, or the current route switches to a new Gate or phase.
- `docs/current` contains accumulated completed-stage evidence that no longer guides current implementation.

Recommended stage-completion order:

1. Stage freeze readiness review.
2. Stage freeze review.
3. Release tag and archive record.
4. Post-stage current archive inventory.
5. Archive plan review.
6. Archive move batch.
7. Archive closeout.
8. Start next phase plan or review.

`docs/current` should keep only current-control material:

- `STATUS.md`, `ROADMAP.md`, `README.md`, `TESTING.md`, `WORKLOG.md`, `API.md`, and `DB_SCHEMA.md`.
- `ARCHITECTURE.md` and `MODULES.md` when they remain current fact sources.
- Current-phase plan, review, contract baseline, active implementation guidance, and authority docs still required by the current phase.

`docs/current` should not retain completed-stage evidence long term:

- Completed-stage implementation, smoke, closeout, freeze readiness, freeze, release, CI, security, or docs-governance evidence.
- Superseded old routes or old Gate plans.
- Documents that now serve only as historical audit evidence.

Archive target rules:

- Completed Gate evidence belongs under `docs/gates/<gate-name>/`, following the repository convention such as `docs/gates/gate-j/`, `docs/gates/gate-k/`, `docs/gates/gate-m/`, or `docs/gates/gate-n/`.
- Use focused subdirectories when useful: `docs/gates/<gate-name>/frontend/`, `docs/gates/<gate-name>/testing/`, `docs/gates/<gate-name>/freeze/`, or `docs/gates/<gate-name>/ci/`.
- Superseded non-Gate material may move to `docs/archive/superseded/`.
- If a prompt spells examples as `docs/gates/GateJ/`, normalize to the repo's current lowercase kebab-case convention unless an existing authoritative directory proves otherwise.
- Move historical evidence; do not delete it, rewrite freeze or release facts, overwrite tag-bound history, or present moved evidence as current state.
- Do not add redirect stubs unless the project already has an explicit stub policy. Prefer updating indexes and references.
- Keep `TESTING.md` and `WORKLOG.md` append-only and current by default unless the project defines a separate volume strategy.

Archive work must be split into at least three tasks. Do not collapse inventory, approval, and movement into one step.

1. Inventory: list candidates only; do not move files. Classify each file as `KEEP_IN_CURRENT`, `MOVE_TO_docs/gates/<gate-name>`, `MOVE_TO_docs/archive/superseded`, `KEEP_BUT_REVIEW_LATER`, or `DO_NOT_TOUCH`. Include reason, risk if moved, and references to update.
2. Plan review: review one stage candidate set, approve move batches, target paths, and index/reference update scope. Do not move files.
3. Move batch: move only the approved batch, update indexes and references, never delete evidence, and verify current docs consistency after each batch.

Round 4 boundary:

- Do not continue or generalize `docs/current cleanup Round 4`.
- Stage archive work must be named and scoped as `stage transition archive`.
- Bind the target to a concrete completed stage, for example `Post-GateM archive`.
- Do not move unrelated files under the rationale of opportunistic `docs/current` cleanup.

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

Archive inventory docs must include: Task classification, Scope, Files inspected, Archive classification, Keep in current, Move candidates, References to update, Validation, Boundary confirmation, P0/P1/P2/P3 findings, Final decision, Recommended next task, and Commit recommendation.

Archive plan review docs must include: Review target, Candidate set, Approved move batches, Target paths, Index/reference update scope, Forbidden files, Validation, Boundary confirmation, P0/P1/P2/P3 findings, Final decision, Recommended next task, and Commit recommendation.

Archive move batch reports must include: Task classification, Scope, Files moved, References updated, Validation, Boundary confirmation, P0/P1/P2/P3 findings, Final decision, Rollback method, Recommended next task, and Commit recommendation.

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

For stage transition archive governance, also run:

```powershell
git diff -- docs/current
rg -n "archive|current|docs/gates|superseded|Round 4|stage transition|freeze|release tag" .agents/skills AGENTS.md README.md
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

Use this shape for archive governance work:

```text
Task classification:
Scope:
Files inspected:
Files changed:
Archive classification:
Keep in current:
Move candidates:
References updated:
Validation:
Boundary confirmation:
P0/P1/P2/P3 findings:
Final decision:
Recommended next task:
Commit recommendation:
```

For review-only work, put findings before summary and cite exact files or evidence. For implementation-report docs, include rollback and whether prohibited NQ/DH scope was touched.
