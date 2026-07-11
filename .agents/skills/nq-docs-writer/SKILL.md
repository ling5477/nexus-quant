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

1. Current code and actual test/CI results determine capability facts.
2. `docs/current/STATUS.md` and its `nq-current-authority` block are the only current-stage authority.
3. `docs/current/ROADMAP.md` defines the next allowed action but cannot override `STATUS.md`.
4. Root/current README files are entry indexes and short summaries only.
5. `API.md`, `DB_SCHEMA.md`, `ARCHITECTURE.md`, `MODULES.md`, and frontend docs describe current capabilities; they do not decide the current Gate.
6. `TESTING.md` and `WORKLOG.md` are append-only evidence ledgers. Historical states in them do not participate in current-stage decisions.
7. Historical Gate docs, archive docs, old reviews, and old freeze records are evidence, not current authority.
8. Any current-document conflict with `STATUS.md` requires `BLOCKED / CURRENT_AUTHORITY_CONFLICT` before writing new stage facts.

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

Current-control files may replace stale current facts when repository/tag/CI evidence proves the correction. Only `WORKLOG.md` and `TESTING.md` are append-only by default; never rewrite their historical records.

`docs/current` must not expand without current-control value. New docs need a clear reason: stage freeze, P0/P1 risk, security boundary, migration, CI workflow, credential, LIVE, real provider, API contract, DB schema, frontend current fact, acceptance report, or explicit user instruction.

After a freeze, do not rewrite tag-bound history. Use post-freeze fix, hotfix, addendum, or a new tag path.

## Stage Transition Archive Governance

Use this section when a Gate, phase, or release line has finished and current-control docs need archive classification. This is a governance workflow only; it does not authorize business code, CI, migration, backend, frontend, Python, LIVE, AI, DH runtime, RealClient, real provider, credential, or exchange changes.

Trigger stage transition archive governance when any of these signals appear:

- A Gate or phase is marked `COMPLETED`, `CLOSED`, or `FROZEN`.
- A Gate or phase release tag is pushed.
- The next phase is selected, or the current route switches to a new Gate or phase.
- `docs/current` contains accumulated completed-stage evidence that no longer guides current implementation.

Ordinary Gate freeze completion flow:

1. In one authorized task, verify readiness and create the complete pre-tag archive from the machine-readable manifest.
2. Commit/push the archive and wait for exact archive-commit CI success.
3. Create/push the annotated tag only after CI success.
4. In the next authorized current-authority sync, record the verified tag in `STATUS.md`; the tagged commit is not required to predict its future tag-object SHA.
5. Start the next Gate only in a separate authorized task.

`docs/current` should keep only current-control material:

- `STATUS.md`, `ROADMAP.md`, `README.md`, `TESTING.md`, `WORKLOG.md`, `API.md`, and `DB_SCHEMA.md`.
- `ARCHITECTURE.md` and `MODULES.md` when they remain current fact sources.
- Current-phase plan, review, contract baseline, active implementation guidance, and authority docs still required by the current phase.

`docs/current` should not retain completed-stage evidence long term:

- Completed-stage implementation, smoke, closeout, freeze readiness, freeze, release, CI, security, or docs-governance evidence.
- Superseded old routes or old Gate plans.
- Documents that now serve only as historical audit evidence.

Archive target rules:

- Completed Gate evidence belongs under the repository convention `docs/gates/<gate-name>/`.
- Use focused subdirectories when useful: `docs/gates/<gate-name>/frontend/`, `docs/gates/<gate-name>/testing/`, `docs/gates/<gate-name>/freeze/`, or `docs/gates/<gate-name>/ci/`.
- Superseded non-Gate material may move to `docs/archive/superseded/`.
- If a prompt uses a mixed-case Gate archive example, normalize it to `docs/gates/<gate-name>/` lowercase kebab-case unless an existing authoritative directory proves otherwise.
- Move historical evidence; do not delete it, rewrite freeze or release facts, overwrite tag-bound history, or present moved evidence as current state.
- Do not add redirect stubs unless the project already has an explicit stub policy. Prefer updating indexes and references.
- Keep `TESTING.md` and `WORKLOG.md` as append-only evidence ledgers; they may remain under `docs/current` but never decide the current Gate.

Gate archive manifest hard gate:

- `scripts/docs/gate-archive-manifest.json` is authoritative for mandatory roles, conditional roles, aliases, strict Gate overrides, and legacy warning policy.
- Before editing, derive the required paths/roles and compare them with the explicit task allowlist. Missing allowlist coverage requires `BLOCKED / ARCHIVE_ALLOWLIST_INCOMPLETE`.
- After editing, missing roles, thin evidence bodies, or core broken links require `BLOCKED / ARCHIVE_MANIFEST_INCOMPLETE` or `BLOCKED / ARCHIVE_LINK_BROKEN`.
- The following list explains roles; it is not a substitute for running the checker.

- Every Gate freeze archive should include `README.md` as the archive entry.
- Include `<GATE>_FREEZE_CLOSEOUT.md`.
- Include `<GATE>_FREEZE_READINESS_REVIEW.md`.
- Include `<GATE>_PLAN.md`, a plan archive copy, or a clearly named plan source file under the Gate archive. A file that only says "see docs/current" is not enough for a durable archive.
- Include `<GATE>_BATCH_0_N_EVIDENCE_MATRIX.md` or the repository's established equivalent.
- Include `<GATE>_TESTING_EVIDENCE_SUMMARY.md` or an equivalent testing / CI evidence summary.
- Include `<GATE>_API_EVIDENCE_SUMMARY.md` when the Gate adds or changes API behavior.
- Include `<GATE>_FRONTEND_EVIDENCE_SUMMARY.md` when the Gate changes frontend pages, routes, hooks, clients, components, or E2E coverage.
- Include `<GATE>_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md` when the Gate touches backend, DB schema, migrations, repository behavior, SQL, or backend tests.
- Include `<GATE>_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md` when the Gate touches Python, research tooling, artifacts, ML/readiness wording, pytest, mypy, or ruff.
- Include `<GATE>_RUNTIME_OR_SCHEDULING_BOUNDARY_SUMMARY.md` when the Gate touches runtime, scheduler, runner, background jobs, refresh loops, replay, recovery, alerts, or operational readiness.
- Include `<GATE>_BOUNDARY_STATEMENT.md`.
- Include `<GATE>_KNOWN_LIMITATIONS_AND_RESIDUALS.md` or an equivalent section that explicitly lists known limitations, deferred items, and allowed residuals.
- Pre-tag closeout records implementation/archive candidates and CI evidence available at that time. Post-tag current authority records local/remote tag, tag object, peeled commit, and exact tagged-commit CI; do not require a commit to predict its future tag object SHA.
- Include a `docs/current` cleanup pointer that says which current files remain authoritative, which historical files were moved or are allowed residuals, and what follow-up move batch remains.

Current cleanup hard gate after freeze closeout:

- After a Gate freeze closeout, `docs/current` must not keep that Gate's process-oriented WO / PLAN / REVIEW / CLOSEOUT / EVIDENCE long documents as fact sources.
- `docs/current` should keep current authority docs and the next phase's active plan only.
- Completed Gate process documents should move to `docs/gates/<gate-name>/` when they are Gate evidence, or to `docs/archive/` when they are superseded non-Gate evidence.
- If a compatibility link requires a historical process document to remain in `docs/current`, mark it as `Allowed residual` in `FACT_SOURCE_INDEX.md`, record the reason, and create a follow-up move batch.
- A Gate archive must not depend on `docs/current` historical process copies for durable evidence. Archive files may point to old current paths only as migration context, not as the only evidence body.

Evidence matrix minimum fields:

- `batch name`
- `status`
- `commit hash`
- `tag / release relation`
- `files changed summary`
- `API / UI / backend / DB / Python summary`
- `tests run`
- `CI evidence`
- `no-real / no-live / no-side-effect boundary`
- `credential / secret boundary`
- `known limitations`
- `remaining residuals`
- `freeze readiness verdict`

Thin archive detection:

- An archive file that only says "see `docs/current/<file>`" is thin.
- A pre-tag freeze closeout that lacks archive candidate commit/CI semantics is thin. A post-tag current sync that omits the verified tag/peeled/CI facts is a current-authority conflict, not a reason to rewrite the tagged archive.
- A Gate that adds or changes API behavior but lacks API evidence summary is thin.
- A Gate that adds or changes frontend behavior but lacks frontend evidence summary is thin.
- A Gate that adds or changes backend, DB, SQL, or migration behavior but lacks backend / DB / migration summary is thin.
- A Gate archive that only repeats boundary statements but lacks an evidence matrix is thin.
- A Gate archive that does not list known limitations and residuals is thin.
- Thin archive does not automatically invalidate the freeze, but it must produce a `FIX RECOMMENDED` or `BLOCK_NEXT_GATE` decision before the next Gate plan proceeds.

Residual document taxonomy:

- `Current authority`: `STATUS.md` only for stage state; `ROADMAP.md` only for the next allowed route.
- `Capability authority`: API, DB schema, architecture, module, runbook, and frontend facts; these do not decide the current Gate.
- `Evidence ledger`: `TESTING.md` and `WORKLOG.md`; append-only and non-authoritative for current stage.
- `Active current plan`: the next phase or currently authorized plan that still guides implementation.
- `Allowed residual`: a historical file temporarily kept in `docs/current` for compatibility, audit chain, or unresolved move batch, and explicitly listed in `FACT_SOURCE_INDEX.md`.
- `Archive pointer only`: a current entry that should retain only a short summary and a pointer to `docs/gates/**` or `docs/archive/**`.
- `Should move to docs/gates`: completed Gate process evidence that belongs under the Gate archive.
- `Should move to docs/archive`: superseded non-Gate evidence, legacy cleanup material, or historical workflow material that is not a Gate archive source.
- `User decision`: a file whose authority cannot be determined from repository facts without user confirmation.
- `Delete candidate`: generated or duplicate material that might be removable only after explicit user approval. Historical evidence must not be deleted by default.

`FACT_SOURCE_INDEX.md` update rule:

- Every freeze, archive, current residual inventory, archive move batch, or archive closeout must update `FACT_SOURCE_INDEX.md` in the same task unless the user explicitly forbids it.
- The index must distinguish NQ current authority, NQ capability authority, evidence ledger, NQ-DH Integration boundary, DH external authority, Gate archive, historical evidence, and allowed residual.
- The index must state that `docs/gates/**` and `docs/archive/**` are historical evidence and do not override current status in `docs/current`.
- The index must avoid NQ / DH / NQ-DH Integration line mixing. NQ-only status, DH-only status, and Integration status must be separate facts with separate authority.
- Allowed residual entries must include reason, target archive path or decision point, and follow-up move batch.

Cross-line isolation rule:

- NQ-only tasks must not modify DH status or declare DH runtime integration.
- NQ-only tasks must not create Integration runtime conclusions, real HTTP conclusions, or provider readiness claims.
- Integration tasks must not modify the NQ current/next Gate unless explicitly authorized and verified from the `STATUS.md` authority block.
- Shared docs such as `README.md`, `STATUS.md`, `ROADMAP.md`, `TESTING.md`, and `WORKLOG.md` require staged diff review to confirm no NQ / DH / Integration line was accidentally changed.

Freeze / tag verification rule:

- Before freeze closeout or tag creation, verify local tag, remote tag, peeled commit, `HEAD`, `origin/<branch>`, and latest CI.
- Do not overwrite an existing tag.
- Do not create or push a freeze tag when latest CI is not `success` for the exact current `HEAD`.
- Do not create or push a freeze tag when `HEAD` and `origin/<branch>` differ, unless the task explicitly covers the divergence and the user authorizes the release decision.
- A complete pre-tag archive may state `TAG PENDING`. After tag creation, sync the exact CI run id, conclusion, head SHA, tag name, tag object, peeled commit, and remote verification into current authority without rewriting tag-bound history.
- Before freeze/tag handoff run `scripts/docs/check-current-authority.ps1`, `check-gate-archive.ps1`, and `check-doc-links.ps1`.

Docs-only churn prevention rule:

- Docs-only review must not expand indefinitely into review after review without changing a real decision boundary.
- Except for release tag, freeze, archive, governance hardening, high-risk security / credential / LIVE / real provider decisions, or explicit user request, do not open a standalone docs-only task for wording-only status changes.
- Minor status wording can be synchronized during the next real development, verification, or archive task when doing so does not blur current facts.
- The next Gate must not be delayed for ordinary wording polish, but P1 archive/current-authority conflicts must be resolved before its planning starts.

No next Gate until archive audit passed rule:

- If the latest completed Gate archive still depends on `docs/current` historical process docs for core evidence, `<NEXT_GATE>` planning is blocked until archive/current authority is separated.
- `<NEXT_GATE>` planning may proceed only after manifest, current authority, tag state, and allowed residuals pass the checkers.
- This blocks next-Gate planning readiness only; it does not invalidate a verified previous release tag.

Only large historical migrations, multi-Gate mixed migrations, `docs/current` physical slimming, high-risk deletion, or bulk relocation must be split into inventory -> review -> move. Ordinary Gate freeze archive creation must not be split solely to satisfy this migration workflow.

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

Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1`. For freeze/tag work also run `check-gate-archive.ps1` and `check-doc-links.ps1`; checker failures are blocking, not advisory.

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
