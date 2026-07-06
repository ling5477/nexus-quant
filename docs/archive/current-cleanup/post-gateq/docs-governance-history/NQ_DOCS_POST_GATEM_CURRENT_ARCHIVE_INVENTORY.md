# NQ-DOCS-POST-GATEM-CURRENT-ARCHIVE-INVENTORY

## Status

**PASS / INVENTORY ONLY / READY TO COMMIT**

This document is a Post-GateM current-document archive inventory. It does not move, delete, rename, stub, copy, or archive any file.

Follow-up review completed: `NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_PLAN_REVIEW.md` records **PASS / PLAN REVIEW ONLY / READY TO COMMIT** for the 22 GateM archive candidates. That review approves later batched movement only; no archive movement has been executed by this inventory or by the plan review.

Archive closeout completed: `NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_CLOSEOUT.md` records **PASS / GATEM ARCHIVE CLOSED / READY TO COMMIT**. Subsequent Batch 1-4 archive move tasks moved all 22 approved GateM candidates under `docs/gates/gate-m/`; missing candidates = 0; `docs/current` GateM long evidence residuals = 0. This inventory remains an inventory record and did not itself move files.

## Current Baseline

- GateM is **FINALIZED / FROZEN / ACCEPTED / TAGGED**.
- GateM release tag: `nq-gatem-freeze`.
- GateM baseline: no-real runtime readiness baseline.
- `NQ-NEXT-PHASE-PLAN` recommends GateN Public MarketData / Exchange Sandbox Planning.
- `NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN` is **PASS / PLAN ONLY / READY TO COMMIT**.
- GateN implementation remains **NOT STARTED**.
- LIVE remains **DISABLED**.
- AI remains **NOT STARTED**.
- DH runtime remains **NOT_INTEGRATED**.
- RealClient / real provider remain **NOT_IMPLEMENTED**.
- real exchange private trading remains **NOT_IMPLEMENTED**.
- real permission probe execution remains **NOT_IMPLEMENTED**.
- `NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE` is already **PASS / ACCEPTED / FROZEN**. Round 4 cleanup remains **NOT ALLOWED**.

This task is not docs/current cleanup Round 4. It is only a Post-GateM stage-transition inventory for a later archive plan.

## Scope

Inspected:

- `docs/current/*.md`.
- `docs/current/frontend/*.md`.
- root `README.md` as read-only context.
- `docs/current/README.md`, `STATUS.md`, `ROADMAP.md`, `TESTING.md`, and `WORKLOG.md` as current authority context.

Excluded:

- `docs/archive/**`.
- `docs/gates/**`.
- `target/**`.
- `node_modules/**`.
- `dist/**`.
- `build/**`.
- `logs/**`.
- `.git/**`.

Allowed writes in this task:

- `docs/current/NQ_DOCS_POST_GATEM_CURRENT_ARCHIVE_INVENTORY.md`.
- `docs/current/README.md` index entry only.
- `docs/current/WORKLOG.md`.
- `docs/current/TESTING.md`.

Forbidden in this task:

- Move, delete, rename, stub, copy, or archive any file.
- Modify backend, frontend, research, scripts, deploy, `.github`, or migration files.
- Add API, page, E2E, CI workflow, migration, LIVE, AI runtime, DH runtime, RealClient, real provider, real exchange call, or credential handling.
- Write GateN as implementation started.
- Write public marketdata sandbox as trading authorization.

## Inventory Summary

Scanned Markdown files:

- `docs/current/*.md`: 97 files.
- `docs/current/frontend/*.md`: 19 files.
- Total inventory rows: 116 files.

Recommended classification:

| Recommended action | Count | Meaning |
| --- | ---: | --- |
| `DO_NOT_TOUCH` | 18 | Current control / append-only / active authority. Do not move without a separate authority rewrite. |
| `KEEP_IN_CURRENT` | 11 | Still current fact source, current route, active runbook, active design standard, or current integration contract. |
| `KEEP_BUT_REVIEW_LATER` | 8 | May be archiveable later, but current inbound references or future safety value require manual review first. |
| `MOVE_TO_docs/gates/GateM` | 22 | GateM implementation, smoke, closeout, freeze, release, or GateM frontend evidence. |
| `MOVE_TO_docs/gates/GateK` | 35 | GateK planning, CI, docs-governance cleanup, architecture, security, no-outbound, credential governance, or GateK-era evidence. |
| `MOVE_TO_docs/gates/GateJ` | 3 | GateJ known compatibility residuals; move only after link-rewrite proposal. |
| `MOVE_TO_docs/archive/superseded` | 19 | Superseded GateL / old no-real route records retained as historical evidence. |

Notes:

- Target folder spelling should follow existing repository convention if implemented later: `docs/gates/gate-m/`, `docs/gates/gate-k/`, and `docs/gates/gate-j/`. The classification labels keep the user-requested `GateM` / `GateK` / `GateJ` wording.
- `TESTING.md` and `WORKLOG.md` are append-only current logs. They should remain in current even though they contain historical GateJ/K/M entries.
- Broad `rg` hits for `LIVE`, `AI`, `DH runtime`, `RealClient`, `real provider`, `order`, or `cancel` are expected in current docs because those terms are used for historical notes and forbidden-boundary statements. They are not implementation evidence by themselves.

## Classification Table

| Current path | Title / inferred topic | Phase | Current relevance | Recommended action | Recommended target path | Reason | Risk if moved | References that must be updated |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `docs/current/API.md` | Current API | General | Current API authority | `DO_NOT_TOUCH` | N/A | Real current API contract; required by current docs. | Broken API authority and inbound links. | N/A |
| `docs/current/ARCHITECTURE.md` | Current Architecture | General | Current architecture authority | `DO_NOT_TOUCH` | N/A | Current architecture baseline and module boundaries. | Current architecture source would disappear. | N/A |
| `docs/current/BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md` | Backtest equity/drawdown follow-up plan | GateK | Possible active backlog | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md` | GateK-era planning, but may still guide frontend/backtest follow-up. | Backtest UI/API backlog may lose discoverability. | `docs/current/README.md`; possible frontend docs. |
| `docs/current/CODEX_PROJECT_INSTRUCTIONS.md` | Codex project instructions | General | Current agent instruction entry | `DO_NOT_TOUCH` | N/A | Current workflow / boundary instructions. | Agent routing and safety guidance drift. | N/A |
| `docs/current/CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md` | Credential active uniqueness review | GateK | Security baseline evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md` | Completed GateK-era credential governance review. | Future real-provider planning may still reference it. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md` | Credential active material selection review | GateK | Security baseline evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md` | Completed GateK-era credential governance review. | Future credential work may lose direct current link. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md` | Credential enable governance review | GateK | Security baseline evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md` | Completed GateK-era governance review. | Future enable/probe reviews may need redirected links. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md` | Credential governance freeze review | GateK | Frozen security baseline evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md` | Freeze evidence belongs with GateK governance archive. | Current security baseline references may break. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md` | Permission probe code/API/test design review | GateK | Future-real safety evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md` | Completed no-real permission-probe design/implementation evidence. | Future real probe planning may need it visible. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md` | Permission probe design review | GateK | Future-real safety evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md` | Completed design review, not current active task. | Future real probe design may need redirected links. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md` | Permission probe freeze review | GateK | Frozen safety evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md` | Freeze evidence for no-real guarded baseline. | Future GateN/real-provider planning may cite no-real boundary. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` | Credential revocation governance plan | GateK | Security governance baseline | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` | Long-running credential governance record; some future safety value remains. | Credential lifecycle context may become harder to find. | `README.md`; `docs/current/README.md`; `STATUS.md`; credential docs. |
| `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md` | Credential revocation governance review | GateK | Security governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md` | Completed review; archive candidate. | Credential lifecycle references need update. | `README.md`; `docs/current/README.md`; credential docs. |
| `docs/current/CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md` | Credential rotate governance review | GateK | Security governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md` | Completed review; archive candidate. | Rotate-governance references need update. | `README.md`; `docs/current/README.md`; credential docs. |
| `docs/current/DB_SCHEMA_GOVERNANCE_PLAN.md` | DB schema governance plan | GateK | Governance baseline | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/DB_SCHEMA_GOVERNANCE_PLAN.md` | Governance plan may still guide future schema work. | DB governance discoverability risk. | `docs/current/README.md`; `DB_SCHEMA.md`; `STATUS.md`. |
| `docs/current/DB_SCHEMA_GOVERNANCE_REVIEW.md` | DB schema governance review | GateK | Governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/DB_SCHEMA_GOVERNANCE_REVIEW.md` | Completed GateK-era review. | DB governance references need update. | `docs/current/README.md`; `DB_SCHEMA.md`; `STATUS.md`. |
| `docs/current/DB_SCHEMA.md` | Current DB Schema | General | Current DB authority | `DO_NOT_TOUCH` | N/A | Real current schema documentation. | DB authority would be lost. | N/A |
| `docs/current/FRONTEND_DESIGN_SYSTEM.md` | NQ frontend design system v1 | General | Current frontend design authority | `KEEP_IN_CURRENT` | N/A | Still guides frontend product/UI work. | Frontend design source would be hidden. | N/A |
| `docs/current/GATEJ_API_PLAN.md` | GateJ API plan residual | GateJ | Accepted known compatibility residual | `MOVE_TO_docs/gates/GateJ` | `docs/gates/gate-j/GATEJ_API_PLAN.md` | GateJ artifact should eventually live with GateJ archive. | R3 freeze says this needs separate link-rewrite proposal; current inbound link risk. | `API.md`; `README.md`; `docs/current/README.md`; any GateJ links. |
| `docs/current/GATEJ_DB_PLAN.md` | GateJ DB plan residual | GateJ | Accepted known compatibility residual | `MOVE_TO_docs/gates/GateJ` | `docs/gates/gate-j/GATEJ_DB_PLAN.md` | GateJ artifact should eventually live with GateJ archive. | R3 freeze says this needs separate link-rewrite proposal; current inbound link risk. | `DB_SCHEMA.md`; `README.md`; `docs/current/README.md`; any GateJ links. |
| `docs/current/GATEJ_TEST_PLAN.md` | GateJ test plan residual | GateJ | Accepted known compatibility residual | `MOVE_TO_docs/gates/GateJ` | `docs/gates/gate-j/GATEJ_TEST_PLAN.md` | GateJ artifact should eventually live with GateJ archive. | R3 freeze says this needs separate link-rewrite proposal; current inbound link risk. | `TESTING.md`; `README.md`; `docs/current/README.md`; any GateJ links. |
| `docs/current/GATEK_ARCHITECTURE_BASELINE_REVIEW.md` | GateK architecture baseline review | GateK | Completed review evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/GATEK_ARCHITECTURE_BASELINE_REVIEW.md` | GateK is finalized/frozen/tagged. | Architecture follow-up context may need redirect. | `README.md`; `docs/current/README.md`; `ROADMAP.md`; `STATUS.md`. |
| `docs/current/GATEK_PLAN.md` | GateK plan | GateK | Completed planning baseline | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/GATEK_PLAN.md` | GateK completed and tagged. | Current route links must point to archive. | `README.md`; `docs/current/README.md`; `ROADMAP.md`; `STATUS.md`. |
| `docs/current/GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md` | GateL-1A freeze review | GateL | Superseded by GateM no-real runtime readiness | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md` | GateL detailed no-real evidence is historical after GateM freeze. | GateM/GateN may still cite GateL boundary; update links first. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateM docs. |
| `docs/current/GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md` | GateL-1 contract review | GateL | Superseded by GateM no-real runtime readiness | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md` | Completed review; no longer current route. | Same GateL reference risk. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateM docs. |
| `docs/current/GATEL_1B_A_IMPL_FREEZE_REVIEW.md` | GateL-1B-A freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_A_IMPL_FREEZE_REVIEW.md` | GateL slice freeze evidence. | No-real regression references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_B_IMPL_FREEZE_REVIEW.md` | GateL-1B-B freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_B_IMPL_FREEZE_REVIEW.md` | GateL slice freeze evidence. | Credential-source references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_C_IMPL_FREEZE_REVIEW.md` | GateL-1B-C freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_C_IMPL_FREEZE_REVIEW.md` | GateL slice freeze evidence. | RawPayload references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_D_IMPL_FREEZE_REVIEW.md` | GateL-1B-D freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_D_IMPL_FREEZE_REVIEW.md` | GateL slice freeze evidence. | Noop no-real references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN_FREEZE_REVIEW.md` | GateL-1B plan freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_NO_REAL_HARDENING_PLAN_FREEZE_REVIEW.md` | Completed plan freeze. | GateL chain links need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN_REVIEW.md` | GateL-1B plan review | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_NO_REAL_HARDENING_PLAN_REVIEW.md` | Completed plan review. | GateL chain links need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md` | GateL-1B hardening plan | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_NO_REAL_HARDENING_PLAN.md` | GateL implementation chain completed. | Internal section references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateL docs. |
| `docs/current/GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md` | GateL-1B overall freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md` | GateL no-real hardening closed. | GateM no-real baseline references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md` | GateL-1C freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md` | GateM readiness now carries current runtime baseline. | Capability matrix references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateM docs. |
| `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md` | GateL-1C review | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md` | Historical review. | Capability references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md` | GateL-1C capability matrix | GateL | Superseded historical contract | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md` | Historical contract under no-real line. | Future-real checklist may cite status enum. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateN plan. |
| `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md` | GateL-1D freeze | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md` | Historical freeze. | Error model references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md` | GateL-1D review | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md` | Historical review. | Error model references may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md` | GateL-1D error model | GateL | Superseded historical contract | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1D_ERROR_MODEL_CONTRACT.md` | GateM runtime readiness now current authority. | Future adapter error work may cite it. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateN plan. |
| `docs/current/GATEL_1E_READINESS_CHECKLIST_REFINEMENT_REVIEW.md` | GateL-1E checklist review | GateL | Superseded historical evidence | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1E_READINESS_CHECKLIST_REFINEMENT_REVIEW.md` | Historical review. | Future-real readiness planning may need redirect. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateN plan. |
| `docs/current/GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md` | GateL-1E readiness checklist | GateL | Superseded but useful checklist | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md` | Checklist is historical after GateM freeze, but useful as reference. | Future-real readiness may need it. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; GateN plan. |
| `docs/current/GATEL_PLAN.md` | GateL planning baseline | GateL | Superseded route record | `MOVE_TO_docs/archive/superseded` | `docs/archive/superseded/gatel/GATEL_PLAN.md` | GateL completed; old AI/GateL route has been superseded. | Route history references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; root `README.md`. |
| `docs/current/MODULES.md` | Current modules | General | Current module authority | `DO_NOT_TOUCH` | N/A | Current module boundary doc. | Module boundary authority lost. | N/A |
| `docs/current/NQ_CI_BASELINE_PLAN.md` | CI baseline plan | GateK | CI current authority | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/NQ_CI_BASELINE_PLAN.md` | README marks it as CI current authority even though GateK is frozen. | CI current-status authority may break if moved too early. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; CI docs. |
| `docs/current/NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md` | Frontend backend E2E CI baseline | GateK | CI evidence/current hybrid | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md` | GateK CI evidence completed/frozen. | CI matrix references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; `TESTING.md`. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md` | 5B env first-run review | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md` | Completed CI review evidence. | CI freeze chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md` | 5B env freeze | GateK | Frozen CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md` | Completed/frozen GateK CI evidence. | CI baseline references need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md` | 5B env plan review | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md` | Completed review. | CI chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md` | 5B env plan | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md` | Plan is accepted/frozen evidence. | CI chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_EVIDENCE.md` | 5B smoke first-run evidence | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_EVIDENCE.md` | Completed CI evidence. | CI chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md` | 5B smoke freeze | GateK | Frozen CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md` | Completed/frozen GateK CI evidence. | CI baseline references need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md` | 5B smoke implementation plan | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md` | Completed plan/evidence. | CI chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md` | 5B smoke preflight plan | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md` | Completed preflight plan. | CI chain links need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md` | CI security final baseline review | GateK | Historical CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md` | Final baseline review was consumed by freeze. | CI references need update. | `docs/current/README.md`; `STATUS.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_FINAL_FREEZE.md` | CI security final freeze | GateK | Frozen CI evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_CI_SECURITY_FINAL_FREEZE.md` | GateK CI/security frozen. | Current CI authority links need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; CI docs. |
| `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` | CI security guard plan | GateK | CI current authority | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/NQ_CI_SECURITY_GUARD_PLAN.md` | README marks CI guard as current authority. | CI guard baseline discoverability risk. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; CI docs. |
| `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` | Codex plugin workflow | General | Current workflow authority | `DO_NOT_TOUCH` | N/A | Agent routing rule source. | Workflow routing risk. | N/A |
| `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md` | Codex task templates | General | Current workflow template | `DO_NOT_TOUCH` | N/A | Active task prompt/template source. | Agent task consistency risk. | N/A |
| `docs/current/NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md` | NQ-DH Integration-0 acceptance | General | Current DH boundary baseline | `KEEP_IN_CURRENT` | N/A | Integration-0 remains a current boundary reference; DH runtime not integrated. | DH boundary evidence may become less visible. | N/A |
| `docs/current/NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md` | NQ-DH Integration-0 contract freeze | General | Current DH contract boundary | `KEEP_IN_CURRENT` | N/A | Current no-runtime contract boundary. | DH contract links may break. | N/A |
| `docs/current/NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md` | NQ-DH Integration-0 contract test plan | General | Current DH contract-test boundary | `KEEP_IN_CURRENT` | N/A | Still documents allowed mock/contract line. | Contract-test plan visibility risk. | N/A |
| `docs/current/NQ_DH_INTEGRATION0_SECURITY_POLICY.md` | NQ-DH Integration-0 security policy | General | Current DH security boundary | `KEEP_IN_CURRENT` | N/A | Still required for DH not-integrated boundary. | Security policy visibility risk. | N/A |
| `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md` | Workflow router skill spec | General | Current skill/router authority | `DO_NOT_TOUCH` | N/A | Source spec for active router skill. | Skill routing drift. | N/A |
| `docs/current/NQ_DOCS_AUTHORITY_INDEX.md` | Documentation authority index | General | Current docs governance authority | `DO_NOT_TOUCH` | N/A | Current authority index. | Docs authority map lost. | N/A |
| `docs/current/NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md` | Current cleanup R1 implementation | GateK | Historical docs-governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md` | Cleanup chain is closed/frozen; no Round 4. | Governance history references need update. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md` | Current cleanup R2 review | GateK | Historical docs-governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md` | Cleanup chain is closed/frozen; no Round 4. | Governance history references need update. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md` | Current cleanup R3 final freeze | GateK | Frozen docs-governance evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md` | Freeze completed; no further cleanup round. | R3 no-Round-4 rule must remain visible after move. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/NQ_DOCS_EVIDENCE_INDEX.md` | Documentation evidence index | General | Current docs governance authority | `DO_NOT_TOUCH` | N/A | Current evidence navigation. | Evidence routing loss. | N/A |
| `docs/current/NQ_DOCS_G1_IMPLEMENTATION.md` | Docs governance G1 implementation | General | Governance baseline evidence | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/NQ_DOCS_G1_IMPLEMENTATION.md` | Listed with governance authority baseline; may move only after replacing authority references. | Governance chain references may break. | `docs/current/README.md`; docs governance files. |
| `docs/current/NQ_DOCS_GOVERNANCE_PLAN.md` | Docs governance inventory and plan | General | Current docs governance authority | `DO_NOT_TOUCH` | N/A | Current governance plan and corrected counts. | Governance authority loss. | N/A |
| `docs/current/NQ_DOCS_MIGRATION_MAP.md` | Docs migration map | General | Current docs governance authority | `DO_NOT_TOUCH` | N/A | Current migration map and authority. | Future archive planning loses map. | N/A |
| `docs/current/NQ_GATEK_ARCHITECTURE_FREEZE.md` | GateK architecture freeze | GateK | Frozen GateK evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_GATEK_ARCHITECTURE_FREEZE.md` | GateK finalized/frozen/tagged. | Architecture freeze references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEK_ARCHIVE_AND_HANDOVER.md` | GateK archive and handover | GateK | Closed GateK handover evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_GATEK_ARCHIVE_AND_HANDOVER.md` | Archive status is closed. | GateK handover link updates required. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEK_CI_SECURITY_CONTRACT.md` | GateK CI security contract | GateK | Frozen GateK contract | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_GATEK_CI_SECURITY_CONTRACT.md` | GateK CI/security contract frozen. | CI/security contract references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEK_CI_SECURITY_FINAL_FREEZE_SPEC.md` | GateK CI security final freeze spec | GateK | Frozen GateK evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_GATEK_CI_SECURITY_FINAL_FREEZE_SPEC.md` | Final freeze spec belongs to GateK archive. | CI/security freeze references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md` | GateK paper execution intelligence plan | GateK | Possible active backlog | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md` | GateK plan may still guide future product work. | Product backlog discoverability risk. | `docs/current/README.md`; `ROADMAP.md`; frontend/product docs. |
| `docs/current/NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md` | GateK paper trading page split plan | GateK | Possible active backlog | `KEEP_BUT_REVIEW_LATER` | `docs/gates/gate-k/NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md` | GateK plan may still guide future frontend page split. | Product backlog discoverability risk. | `docs/current/README.md`; `ROADMAP.md`; frontend/product docs. |
| `docs/current/NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md` | GateK post-freeze handoff | GateK | Completed handoff evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md` | GateK handoff consumed by GateM/GateN planning. | Handoff references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md` | GateM-2D source health plan | GateM | GateM planning evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md` | GateM completed/frozen; plan is historical. | MarketData readiness lineage links need update. | `docs/current/README.md`; `STATUS.md`; `WORKLOG.md`; frontend GateM docs. |
| `docs/current/NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md` | GateM-6E local runbook | GateM | GateM local validation runbook | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md` | GateM-6 closed; runbook is GateM evidence. | Local validation reference may need current pointer. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md` | GateM-6 operational readiness | GateM | GateM closeout evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md` | GateM-6 implemented/final-smoke/closed. | Operational readiness lineage links need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; frontend GateM docs. |
| `docs/current/NQ_GATEM_FREEZE_READINESS_REVIEW.md` | GateM freeze readiness review | GateM | GateM freeze evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_FREEZE_READINESS_REVIEW.md` | Consumed by GateM freeze/release. | Freeze readiness references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; root `README.md`. |
| `docs/current/NQ_GATEM_FREEZE_REVIEW.md` | GateM freeze review | GateM | GateM freeze evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_FREEZE_REVIEW.md` | GateM finalized/frozen/tagged. | Freeze references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`; root `README.md`. |
| `docs/current/NQ_GATEM_RELEASE_TAG_AND_ARCHIVE.md` | GateM release tag and archive | GateM | GateM release evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/NQ_GATEM_RELEASE_TAG_AND_ARCHIVE.md` | Release tag and archive record belongs to GateM archive after transition. | Current release tag discoverability risk; keep index pointer. | `README.md`; `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md` | GateN public marketdata sandbox plan | GateN | Current next-phase planning baseline | `KEEP_IN_CURRENT` | N/A | GateN is current plan-only route and implementation not started. | Moving now would remove current GateN entry. | N/A |
| `docs/current/NQ_NEXT_PHASE_PLAN.md` | Next phase plan | GateN | Current next-phase authority | `KEEP_IN_CURRENT` | N/A | Current post-GateM route authority. | GateN route would lose authority. | N/A |
| `docs/current/NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md` | OKX endpoint defense addendum | GateK | Frozen no-outbound evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md` | GateK post-freeze evidence completed. | Endpoint-defense references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md` | OKX endpoint defense plan | GateK | Frozen no-outbound evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md` | GateK post-freeze evidence completed. | Endpoint-defense references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_PROJECT_WORKFLOW_AUTHORITY.md` | Project workflow authority | General | Current workflow authority | `DO_NOT_TOUCH` | N/A | Active docs budget / review / route authority. | Workflow governance lost. | N/A |
| `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md` | OKX bootstrap no-outbound freeze | GateK | Frozen GateK evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md` | GateK post-freeze evidence completed. | Test isolation references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` | OKX bootstrap no-outbound review | GateK | GateK evidence | `MOVE_TO_docs/gates/GateK` | `docs/gates/gate-k/NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` | Review consumed by freeze/addendum. | Test isolation references need update. | `docs/current/README.md`; `STATUS.md`; `ROADMAP.md`. |
| `docs/current/README.md` | Current Stage index | General | Current index authority | `DO_NOT_TOUCH` | N/A | Current facts entrance. | Current entrypoint broken. | N/A |
| `docs/current/ROADMAP.md` | Roadmap | General | Current roadmap authority | `DO_NOT_TOUCH` | N/A | Current route authority. | Route authority broken. | N/A |
| `docs/current/RUNBOOK.md` | Current runbook | General | Current runbook | `KEEP_IN_CURRENT` | N/A | Operational current reference. | Runbook discoverability risk. | N/A |
| `docs/current/STATUS.md` | Current Status | General | Current status authority | `DO_NOT_TOUCH` | N/A | Top current status source. | Current status authority lost. | N/A |
| `docs/current/TESTING.md` | Testing log | General | Append-only current testing authority | `DO_NOT_TOUCH` | N/A | Required append-only current testing log. | Validation history and current test facts lost. | N/A |
| `docs/current/WORKLOG.md` | Worklog | General | Append-only current worklog authority | `DO_NOT_TOUCH` | N/A | Required append-only current worklog. | Audit/work history lost. | N/A |
| `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` | Design tokens v2 | General | Current frontend design baseline | `KEEP_IN_CURRENT` | N/A | Active design system token source. | Frontend design consistency risk. | N/A |
| `docs/current/frontend/NQ_FRONTEND_BUILD_MATRIX.md` | Frontend build matrix | General | Current frontend product/build plan | `KEEP_IN_CURRENT` | N/A | Still guides frontend implementation sequencing. | Frontend planning discoverability risk. | N/A |
| `docs/current/frontend/NQ_FRONTEND_CHART_FOUNDATION_B0_4.md` | Chart foundation B0.4 | General | Current frontend chart foundation | `KEEP_IN_CURRENT` | N/A | Reusable current chart foundation. | Chart implementation guidance risk. | N/A |
| `docs/current/frontend/NQ_GATEM_2_MARKETDATA_KLINE_READINESS.md` | GateM-2 marketdata kline readiness | GateM | GateM frontend evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2_MARKETDATA_KLINE_READINESS.md` | GateM completed/frozen. | MarketData UI lineage links need update. | `docs/current/README.md`; frontend docs; `STATUS.md`; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_2B_MARKETDATA_QUALITY_READINESS_VIEW.md` | GateM-2B quality readiness view | GateM | GateM frontend evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2B_MARKETDATA_QUALITY_READINESS_VIEW.md` | GateM completed/frozen. | MarketData UI lineage links need update. | `docs/current/README.md`; frontend docs; `STATUS.md`; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_2C_MARKETDATA_REAL_BACKEND_SMOKE.md` | GateM-2C real backend smoke | GateM | GateM smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2C_MARKETDATA_REAL_BACKEND_SMOKE.md` | GateM smoke evidence. | Smoke evidence links need update. | `docs/current/README.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_2G_MARKETDATA_READINESS_REAL_BACKEND_SMOKE.md` | GateM-2G real backend readiness smoke | GateM | GateM smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2G_MARKETDATA_READINESS_REAL_BACKEND_SMOKE.md` | GateM smoke evidence. | Smoke evidence links need update. | `docs/current/README.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_2H_MARKETDATA_POSITIVE_BARS_FIXTURE_PLAN.md` | GateM-2H positive bars fixture plan | GateM | GateM plan evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2H_MARKETDATA_POSITIVE_BARS_FIXTURE_PLAN.md` | GateM plan is historical after freeze. | Future fixture backlog may need redirect. | `docs/current/README.md`; frontend docs; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_2I_MARKETDATA_POSITIVE_BARS_FIXTURE_SMOKE.md` | GateM-2I positive bars fixture smoke | GateM | GateM smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_2I_MARKETDATA_POSITIVE_BARS_FIXTURE_SMOKE.md` | GateM smoke evidence. | Fixture smoke links need update. | `docs/current/README.md`; `TESTING.md`; `WORKLOG.md`. |
| `docs/current/frontend/NQ_GATEM_5_RUNTIME_GUARDED_UI_PLAN.md` | GateM-5 runtime guarded UI plan | GateM | GateM historical plan | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5_RUNTIME_GUARDED_UI_PLAN.md` | GateM-5 is closed. | Runtime UI lineage links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_5_RUNTIME_UI_5A_RUNTIME_READINESS_OVERVIEW.md` | GateM-5A runtime readiness overview | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5_RUNTIME_UI_5A_RUNTIME_READINESS_OVERVIEW.md` | GateM-5 closed. | Runtime UI evidence links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_5B_RUNTIME_UI_MARKETDATA_READINESS_DEEP_LINK.md` | GateM-5B marketdata deep link | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5B_RUNTIME_UI_MARKETDATA_READINESS_DEEP_LINK.md` | GateM-5 closed. | Runtime UI evidence links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_5C_RUNTIME_UI_PAPER_BOUNDARY_BANNERS.md` | GateM-5C paper boundary banners | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5C_RUNTIME_UI_PAPER_BOUNDARY_BANNERS.md` | GateM-5 closed. | Runtime UI evidence links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_5D_RUNTIME_UI_DASHBOARD_SUMMARY_CARD.md` | GateM-5D dashboard summary card | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5D_RUNTIME_UI_DASHBOARD_SUMMARY_CARD.md` | GateM-5 closed. | Runtime UI evidence links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_5E_RUNTIME_UI_FINAL_SMOKE.md` | GateM-5E final smoke | GateM | GateM smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_5E_RUNTIME_UI_FINAL_SMOKE.md` | GateM-5 final smoke is closed. | Smoke evidence links need update. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_6A_RUNTIME_HEALTH_CONFIG_PROFILE_OVERVIEW.md` | GateM-6A runtime health/config/profile | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_6A_RUNTIME_HEALTH_CONFIG_PROFILE_OVERVIEW.md` | GateM-6 closed. | Operational readiness links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_6C_OPERATIONAL_READINESS_FRONTEND_INTEGRATION.md` | GateM-6C frontend integration | GateM | GateM implementation evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_6C_OPERATIONAL_READINESS_FRONTEND_INTEGRATION.md` | GateM-6 closed. | Operational readiness links need update. | `docs/current/README.md`; `STATUS.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_6D_OPERATIONAL_READINESS_REAL_BACKEND_SMOKE.md` | GateM-6D real backend smoke | GateM | GateM smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_6D_OPERATIONAL_READINESS_REAL_BACKEND_SMOKE.md` | GateM-6 smoke evidence. | Smoke evidence links need update. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; frontend docs. |
| `docs/current/frontend/NQ_GATEM_6F_OPERATIONAL_READINESS_FINAL_SMOKE.md` | GateM-6F final smoke | GateM | GateM final smoke evidence | `MOVE_TO_docs/gates/GateM` | `docs/gates/gate-m/frontend/NQ_GATEM_6F_OPERATIONAL_READINESS_FINAL_SMOKE.md` | GateM-6 final smoke closed. | Smoke evidence links need update. | `docs/current/README.md`; `STATUS.md`; `TESTING.md`; frontend docs. |

## Keep In Current

Recommended `DO_NOT_TOUCH` / `KEEP_IN_CURRENT` set:

- `API.md`, `DB_SCHEMA.md`, `ARCHITECTURE.md`, `MODULES.md`.
- `README.md`, `STATUS.md`, `ROADMAP.md`, `TESTING.md`, `WORKLOG.md`, `RUNBOOK.md`.
- `NQ_PROJECT_WORKFLOW_AUTHORITY.md`, `CODEX_PROJECT_INSTRUCTIONS.md`, `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`, `NQ_DH_WORKFLOW_ROUTER_SKILL.md`, `NQ_DH_CODEX_TASK_TEMPLATES.md`.
- `NQ_DOCS_AUTHORITY_INDEX.md`, `NQ_DOCS_EVIDENCE_INDEX.md`, `NQ_DOCS_GOVERNANCE_PLAN.md`, `NQ_DOCS_MIGRATION_MAP.md`.
- `NQ_NEXT_PHASE_PLAN.md`, `NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md`.
- `NQ_DH_INTEGRATION0_*`.
- `FRONTEND_DESIGN_SYSTEM.md` and the active frontend design/build/chart foundation docs.

## Move To GateM

Recommended GateM archive candidates:

- Root GateM docs: `NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md`, `NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md`, `NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md`, `NQ_GATEM_FREEZE_READINESS_REVIEW.md`, `NQ_GATEM_FREEZE_REVIEW.md`, `NQ_GATEM_RELEASE_TAG_AND_ARCHIVE.md`.
- Frontend GateM docs: all `docs/current/frontend/NQ_GATEM_*`.

Rationale: GateM is finalized/frozen/tagged. These docs are now stage evidence rather than current control facts.

Required before moving:

- Create `docs/gates/gate-m/` archive with a README / freeze summary.
- Rewrite current indexes to point to the GateM archive.
- Preserve `nq-gatem-freeze` tag facts and no-real boundaries.
- Run link checks and forbidden-scope diff checks.

Plan review note: `NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_PLAN_REVIEW.md` reviewed all 22 GateM candidates and recommends later Batch 1-4 movement under `docs/gates/gate-m/`, with `freeze/`, `frontend/`, and `testing/` subfolders. The review does not authorize moving files in this inventory task.

## Move To GateK

Recommended GateK archive candidates:

- GateK route and architecture docs: `GATEK_PLAN.md`, `GATEK_ARCHITECTURE_BASELINE_REVIEW.md`, `NQ_GATEK_*`.
- GateK CI docs: `NQ_CI_*`.
- GateK no-outbound / OKX endpoint defense docs: `NQ_TEST_ISOLATION_*`, `NQ_OKX_*`.
- GateK credential governance review/freeze docs: `CREDENTIAL_*`, with `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` requiring manual review before move.
- Docs cleanup evidence: `NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md`, `NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md`, `NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md`.
- DB schema governance review docs where not serving as current authority.

Required before moving:

- Decide whether `NQ_CI_BASELINE_PLAN.md` and `NQ_CI_SECURITY_GUARD_PLAN.md` remain current CI authority or become GateK archive docs with a current pointer.
- Preserve the R3 rule: cleanup Round 4 remains **NOT ALLOWED**.
- Update `README.md`, `docs/current/README.md`, `STATUS.md`, `ROADMAP.md`, `TESTING.md`, and `WORKLOG.md` references.

## Move To GateJ

GateJ candidates:

- `GATEJ_API_PLAN.md`.
- `GATEJ_DB_PLAN.md`.
- `GATEJ_TEST_PLAN.md`.

These are accepted known compatibility residuals from current-cleanup R3. They should only move after a separate small link-rewrite proposal. This inventory does not authorize moving them.

## Move To archive/superseded

Recommended superseded candidates:

- All `GATEL_*`.
- `GATEL_PLAN.md`.

Rationale: GateL no-real exchange/marketdata readiness is completed and superseded by GateM no-real runtime readiness plus GateN current planning. These docs should remain historical evidence and must not be used to imply LIVE, real provider, RealClient, real permission probe, AI, or DH runtime readiness.

Required before moving:

- Update current route text in `README.md`, `docs/current/README.md`, `STATUS.md`, and `ROADMAP.md`.
- Preserve no-real/fail-closed boundary references that GateM/GateN still rely on.
- Avoid rewriting old GateL route as current.

## Needs Review

Manual review required before any actual archive move:

- `NQ_CI_BASELINE_PLAN.md` and `NQ_CI_SECURITY_GUARD_PLAN.md`: current CI authority vs GateK archive.
- `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`: future credential safety baseline vs GateK evidence.
- `NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md`, `NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md`, and `BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md`: may still be future product backlog.
- `NQ_DOCS_G1_IMPLEMENTATION.md`: governance evidence but still referenced by current governance baseline.
- `DB_SCHEMA_GOVERNANCE_PLAN.md`: may still guide future DB governance.
- GateJ residuals: must not be moved without link rewrite.

## Current Route Conflict Check

No P0/P1/P2 current route conflict was found in this inventory.

Known historical noise:

- Historical append-only entries mention older GateL / GateM / GateN route wording.
- Current authority entries now state GateM = finalized/frozen/tagged, GateN = planning-only, AI not started, DH runtime not integrated, LIVE disabled, RealClient / real provider not implemented.
- The inventory recommendations do not change current route facts.

## Boundary Confirmation

This inventory did not:

- Move, delete, rename, stub, copy, or archive files.
- Modify backend, frontend, research, scripts, deploy, `.github`, migration, API, CI workflow, page, E2E, or generated artifacts.
- Enable LIVE.
- Start AI or DH runtime.
- Implement RealClient, real provider, private trading, or real permission probe.
- Call real exchange APIs.
- Read or output credential material.
- Write GateN as implementation started.
- Write public marketdata sandbox as trading authorization.

## P0 / P1 / P2 / P3 Findings

### P0

- None.

### P1

- None.

### P2

- None blocking this inventory.

### P3

- `docs/current` again contains many completed-stage evidence docs after GateM freeze; this is not a correctness blocker but creates current-folder noise.
- Several docs are current-authority / historical-evidence hybrids, especially CI and credential governance docs. They require manual link and authority review before any move.
- GateJ residuals remain accepted from R3 and must not be treated as ordinary move candidates.

## Recommended Next Task

Recommended next task:

```text
NQ-DOCS-POST-GATEM-GATEM-ARCHIVE-PLAN-REVIEW
```

Scope for that next task should be docs-only and should only plan, not move, unless explicitly authorized.

## Final Decision

Decision: **PASS / INVENTORY ONLY / READY TO COMMIT**.

This is an inventory and archive plan only. Actual archive movement requires a separate task with explicit file list, link rewrite plan, target directory convention, rollback plan, and validation.

Commit recommendation:

```text
docs(governance): inventory post-GateM current archive candidates
```
