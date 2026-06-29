---
name: nq-dh-workflow-router
description: NQ/DH workflow router for NexusQuant and Decision Hub tasks. Use when a task mentions NexusQuant, NQ, Decision Hub, DH, quant trading platform work, Gate or FREEZE planning, frontend optimization, architecture review, deployment, security audit, exchange integration, documentation, spreadsheets, presentations, or domain websites, and Codex must classify the task, choose only relevant plugins or project skills, route pure documentation work to nq-docs-writer when appropriate, define scope, preserve Gate boundaries, and produce the standard NQ/DH execution report.
---

# NQ-DH Workflow Router

Use this skill before executing NexusQuant or Decision Hub work. The goal is to classify the task, select the minimum relevant tools, and keep NQ/DH Gate, trading, credentials, and module boundaries explicit.

## Source Of Truth

Read these repository documents only as needed for the current task:

- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` for plugin routing and standard workflow.
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md` for the original router specification.
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md` for common task output templates.
- `docs/current/README.md` and `AGENTS.md` for current stage, Gate status, prohibited scope, and validation rules.

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

For `DOCUMENTATION`, record a subtype when it changes routing or validation: `DOCS_ONLY`, `DOCUMENTATION_CLEANUP`, `DOCUMENTATION_RECONCILIATION`, `FACT_SOURCE_SYNC`, `ROADMAP_CLEANUP`, `PLAN`, `PLANNING_ONLY`, `REVIEW`, `FREEZE_REVIEW`, `FINAL_FREEZE`, `STATUS_SYNC`, `TESTING_SYNC`, `WORKLOG_SYNC`, `API_DOC_UPDATE`, `DB_SCHEMA_DOC_UPDATE`, `FRONTEND_DOC_UPDATE`, `CI_DOC_UPDATE`, `GATE_PLAN`, `GATE_FREEZE`, `ACCEPTANCE_REPORT`, `IMPLEMENTATION_REPORT`, `RELEASE_HANDOFF`, or `POST_FREEZE_FIX_DOCS`.

If the user request is ambiguous, make a conservative default assumption, state it, and avoid crossing module, Gate, trading, or credential boundaries.

## Step 2: Select Plugins And Skills

Select only the tools needed for the classified task. Do not enable every available plugin or skill.

- Use `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` as the routing table for plugins.
- Use project active skills only when directly relevant to the task.
- Use at most one primary skill; supporting skills must have a clear reason.
- If plugin or skill guidance conflicts with Gate, security, trading, module, or user instructions, follow the stricter project boundary.
- Use `nq-docs-writer` as the primary skill for pure documentation work: `DOCUMENTATION`, `DOCS_ONLY`, planning-only docs, review/freeze docs, current fact-source reconciliation, status/testing/worklog sync, API docs, DB schema docs, frontend docs, CI docs, acceptance reports, implementation reports, release handoffs, and post-freeze fix docs.
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

## Step 5: Enforce NQ Boundaries

For NexusQuant tasks:

- Do not enable LIVE trading.
- Do not add real order or cancel paths unless the user explicitly asks and the current Gate allows it.
- Do not expose credentials, API keys, exchange secrets, tenant data, tokens, cookies, or production env values.
- Keep PAPER and LIVE isolated; explain isolation points, failure modes, and rollback when touched.
- Do not write GateK-PLAN as GateK implementation started.
- Do not claim AI, DH integration, multi-exchange expansion, public production readiness, or UI/UX professionalism is complete unless current docs and verification prove it.

Current baseline: GateJ completed; Next is GateK-PLAN; AI not started; DH integration not started and not connected to NQ.

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
- Docs: check links, paths, stage state, forbidden boundaries, duplicate entry points, whether verification claims match executed commands, and `nq-docs-writer` validation when documentation was created or synchronized.
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
