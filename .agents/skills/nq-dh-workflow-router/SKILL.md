---
name: nq-dh-workflow-router
description: NQ/DH workflow router for NexusQuant and Decision Hub tasks. Use when a task mentions NexusQuant, NQ, Decision Hub, DH, quant trading platform work, Gate or FREEZE planning, stage transition archive governance, frontend optimization, architecture review, deployment, security audit, exchange integration, documentation, spreadsheets, presentations, or domain websites, and Codex must classify the task, choose only relevant plugins or project skills, route pure documentation and archive governance work to nq-docs-writer when appropriate, define scope, preserve Gate boundaries, and produce the standard NQ/DH execution report.
---

# NQ-DH Workflow Router

Use this skill before executing NexusQuant or Decision Hub work. The goal is to classify the task, select the minimum relevant tools, and keep NQ/DH Gate, trading, credentials, and module boundaries explicit.

## Source Of Truth

Read these repository documents only as needed for the current task:

- `docs/current/STATUS.md` first; parse the `nq-current-authority` block for current/next Gate and safety state.
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` for plugin routing and standard workflow.
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md` for the original router specification.
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md` for common task output templates.
- `docs/current/README.md` and `AGENTS.md` for indexes, prohibited scope, and validation rules; they do not override `STATUS.md`.

Skills, templates, router docs, and `AGENTS.md` must not copy a concrete current Gate or next Gate. Examples use `<CURRENT_GATE>` and `<NEXT_GATE>` placeholders.

Do not treat archived documents as current facts unless the user explicitly asks for historical comparison.

## Step 1: Classify The Task

Choose exactly one primary type. Record auxiliary types only when they materially affect tool selection or validation.

- `CODE_ANALYSIS`
- `CODE_CHANGE`
- `FRONTEND_UI`
- `DATA_VISUALIZATION`
- `SECURITY_AUDIT`
- `EXCHANGE_INTEGRATION`
- `DOCUMENTATION`
- `SPREADSHEET_MATRIX`
- `DEPLOYMENT`
- `CI_CD`
- `PRODUCT_DESIGN`
- `INVESTMENT_RESEARCH`
- `PRESENTATION`
- `DOMAIN_WEBSITE`

For `DOCUMENTATION`, record a subtype when it changes routing or validation: `DOCS_ONLY`, `DOCUMENTATION_CLEANUP`, `DOCUMENTATION_RECONCILIATION`, `FACT_SOURCE_SYNC`, `ROADMAP_CLEANUP`, `PLAN`, `PLANNING_ONLY`, `REVIEW`, `FREEZE_REVIEW`, `FINAL_FREEZE`, `STATUS_SYNC`, `TESTING_SYNC`, `WORKLOG_SYNC`, `API_DOC_UPDATE`, `DB_SCHEMA_DOC_UPDATE`, `FRONTEND_DOC_UPDATE`, `CI_DOC_UPDATE`, `GATE_PLAN`, `GATE_FREEZE`, `ACCEPTANCE_REPORT`, `IMPLEMENTATION_REPORT`, `RELEASE_HANDOFF`, `POST_FREEZE_FIX_DOCS`, `STAGE_TRANSITION_ARCHIVE`, `ARCHIVE_INVENTORY`, `ARCHIVE_PLAN_REVIEW`, `ARCHIVE_MOVE_BATCH`, `ARCHIVE_CLOSEOUT`, `RELEASE_TAG_AND_ARCHIVE`, `FINAL_CLOSURE`, `DOCS_GOVERNANCE`, or `CURRENT_DOCS_CLASSIFICATION`.

If the user request is ambiguous, make a conservative default assumption, state it, and avoid crossing module, Gate, trading, or credential boundaries.

## Step 2: Select Plugins And Skills

Select only the tools needed for the classified task. Do not enable every available plugin or skill.

- Use `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` as the routing table for plugins.
- Use project active skills only when directly relevant to the task.
- Use at most one primary skill; supporting skills must have a clear reason.
- If plugin or skill guidance conflicts with Gate, security, trading, module, or user instructions, follow the stricter project boundary.
- Use `nq-docs-writer` as the primary skill for pure documentation work: `DOCUMENTATION`, `DOCS_ONLY`, planning-only docs, review/freeze docs, current fact-source reconciliation, status/testing/worklog sync, API docs, DB schema docs, frontend docs, CI docs, acceptance reports, implementation reports, release handoffs, and post-freeze fix docs.
- Route stage archive governance to `nq-docs-writer`: task names or prompts matching `*_ARCHIVE_INVENTORY`, `*_ARCHIVE_PLAN_REVIEW`, `*_ARCHIVE_MOVE_BATCH`, `*_ARCHIVE_CLOSEOUT`, `POST_*CURRENT_ARCHIVE*` / `POST_**CURRENT_ARCHIVE**`, `STAGE_TRANSITION_ARCHIVE`, `RELEASE_TAG_AND_ARCHIVE`, `FINAL_CLOSURE`, `DOCS_GOVERNANCE`, or `CURRENT_DOCS_CLASSIFICATION`.
- Archive governance must stay separate from implementation. If a task also includes backend, frontend, DB, CI, security, credential, LIVE, real-provider, or exchange implementation, keep the implementation skill primary and let `nq-docs-writer` only synchronize TESTING, WORKLOG, current state, or archive policy inside the approved docs budget.
- If the task implements backend, frontend, DB, CI, security, credential, LIVE, real-provider, or exchange changes and also needs documentation, keep the domain skill primary and use `nq-docs-writer` only as a supporting documentation skill.
- If the task exposes fact-source conflict between attachments, prompts, old docs, current docs, tests, or code, select `nq-docs-writer` first for documentation reconciliation before editing current facts.
- For migration work, keep `db-schema-migration-review` as primary; `nq-docs-writer` may only synchronize `DB_SCHEMA.md`, `TESTING.md`, and `WORKLOG.md`.
- For frontend implementation, keep the relevant frontend skill primary; `nq-docs-writer` may only synchronize frontend docs, `TESTING.md`, and `WORKLOG.md`.
- For CI workflow, security, credential, LIVE, or real-provider work, keep the CI/security/domain review primary; `nq-docs-writer` may only keep documentation facts from crossing the approved boundary.

Never use a plugin or skill to bypass restrictions on AI, DH integration, LIVE trading, credentials, real providers, RealClient, migrations, or production paths.

## Step 3: Define Scope

Before reading broadly or modifying files, state:

- `repository`
- `module`
- `target files`
- `excluded files`
- `expected output`

Exclude by default:

- `node_modules`
- `target`
- `build`
- `dist`
- `.git`
- `test-results`
- `logs`
- `secrets`
- `credentials`

Do not scan the full repository unless the task explicitly requires a repository-wide review.

## Step 4: Apply Documentation Budget

Default to code-first / test-first work. Documentation is not a default deliverable.

- Review-only and audit-only tasks are no-diff by default; write docs only for stage freeze, contract freeze, high-risk plans, or explicit user instruction.
- Ordinary code tasks do not update docs by default; if a durable note is necessary, add at most one `docs/current/WORKLOG.md` line.
- Test-baseline tasks may update `docs/current/TESTING.md` and `docs/current/WORKLOG.md`.
- Stage completion or Gate freeze may update `docs/current/STATUS.md`, `docs/current/ROADMAP.md`, `docs/current/TESTING.md`, and `docs/current/WORKLOG.md`.
- `README.md` changes require entry-point, architecture, startup, or overall stage-status impact.
- Dedicated PLAN docs are reserved for CI, migration, security, LIVE, credential, API contract, or similarly high-risk epics.
- Do not create docs-only follow-up tasks merely to keep documents synchronized.
- Prompts for future NQ/DH tasks should state the docs budget explicitly, for example: "docs default unchanged; if recording is needed, only one WORKLOG line is allowed."
- When documentation is explicitly authorized, apply `nq-docs-writer` rules for fact-source priority, anti-churn, output shape, and validation.

## Step 4.1: Apply Archive And Authority Fail-fast

- `docs/current/STATUS.md` is the only current-stage authority. Conflicting current docs require `BLOCKED / CURRENT_AUTHORITY_CONFLICT`.
- `scripts/docs/gate-archive-manifest.json` is a hard gate for Gate freeze work, not a suggestion.
- Before writing a freeze archive, derive mandatory and applicable conditional roles from the manifest and compare them with the task allowlist.
- If the allowlist cannot contain every required role, stop with `BLOCKED / ARCHIVE_ALLOWLIST_INCOMPLETE`.
- If files or independent evidence bodies are missing, stop with `BLOCKED / ARCHIVE_MANIFEST_INCOMPLETE`.
- An ordinary Gate freeze may create the complete pre-tag archive in one task. Inventory -> review -> move is reserved for large historical migrations, multi-Gate moves, current-doc physical slimming, destructive cleanup, or bulk relocation.
- Before freeze/tag handoff, run the archive, authority, and link checkers under `scripts/docs/`.

## Step 4.2: Apply Documentation Language Rules

When a task writes or updates documentation, templates, task prompts, skill instructions, review reports, implementation reports, `README`, `STATUS`, `ROADMAP`, `TESTING`, `WORKLOG`, or any `docs/current` explanatory document, enforce the `nq-docs-writer` language governance rules:

- 文档正文必须中文为主，不能把 current docs 或入口文档整篇英文化。
- 英文任务名、状态枚举、类名、接口名、字段名、文件名、路径、命令、commit message 和协议原文可以保留英文。
- 英文状态值首次出现时必须附中文解释；后续可复用精确英文状态值。
- 代码注释的业务规则说明优先中文；API contract、协议字段和 enum 可保留英文或中英双语。
- DB comment 使用中文业务语义，表名和字段名保持英文。
- 不翻译 `docs/archive/**` 或 `docs/gates/**` 历史文档；只在后续自然触碰时修正旧文档语言漂移。
- Agent 输出栏目名可以保留英文，但栏目内容必须中文为主。

## Step 5: Enforce NQ Boundaries

For NexusQuant tasks:

- Do not enable LIVE trading.
- Do not add real order or cancel paths unless the user explicitly asks and the current Gate allows it.
- Do not expose credentials, API keys, exchange secrets, tenant data, tokens, cookies, or production env values.
- Keep PAPER and LIVE isolated; explain isolation points, failure modes, and rollback when touched.
- Do not write `<NEXT_GATE>` planning as implementation started; read both names and statuses from `STATUS.md` each turn.
- Do not claim AI, DH integration, multi-exchange expansion, public production readiness, or UI/UX professionalism is complete unless current docs and verification prove it.

Current baseline is never hard-coded in this skill. Parse `docs/current/STATUS.md` before every task and fail closed when it is missing, malformed, or conflicts with current entry documents.

## Step 6: Enforce DH Boundaries

For Decision Hub tasks:

- Do not connect DH to NQ for real.
- Do not allow DH to place orders, cancel orders, start Paper Runs, access credentials, or modify NQ trading state.
- Treat Integration-0 as read-only boundary and contract freeze preparation only.
- Do not add real providers, RealClient, third-party relays, or production trading paths.
- For security-sensitive DH work, explicitly check HMAC, timestamp, nonce, source allowlist, payload size, tenant binding, replay protection, provider trust policy, and audit trail.

## Step 7: Execute

Use the smallest workflow that satisfies the request.

- Read `AGENTS.md`, `README.md`, `docs/current/README.md`, and relevant current docs before code or documentation changes.
- Read target files before editing.
- Keep edits scoped to the selected module and task type.
- Do not modify unrelated frontend, backend, Python, deployment, and documentation areas in the same turn unless the user explicitly requested a cross-stack change.
- Do not update current docs to claim validation passed unless the command actually ran and passed.

## Step 8: Validate

Run validation based on the changed area, or explain why a narrower validation is sufficient.

- Backend: `mvn -f backend/pom.xml test` or a justified module-specific Maven test.
- Frontend: `Set-Location frontend; npm run build; npm run test:e2e`; page work should also use Browser or Chrome verification.
- Python: `Set-Location research/py; python -m pytest -q; python -m mypy src; python -m ruff check .`.
- Docs: run `scripts/docs/check-current-authority.ps1`; for Gate freeze/tag work also run `check-gate-archive.ps1` and `check-doc-links.ps1`. Check paths, forbidden boundaries, duplicate entry points, and whether validation claims match executed commands.
- Deployment: check Docker, env examples, health checks, migrations, and rollback.

If validation fails, report the root cause, apply the smallest fix when feasible, and rerun the relevant validation.

## Step 9: Report

Use this output shape for NQ/DH work:

```text
Task classification:
Plugins selected:
Scope:
Files inspected:
Files changed:
Findings:
Validation:
Risks:
Next concrete action:
```

For code review tasks, put findings first with file and line references, then summarize. For completed implementation tasks, include changed files, validation result, rollback method, and whether prohibited NQ/DH scope was touched.
