# CI Historical Evidence

本目录承载 canonical CI historical evidence。G4 将 Migration Map 中明确标记为 `migration_batch = G4`、`FUTURE_MOVE_CANDIDATE` 且非 current authority 的 CI 历史证据归位到此目录。

本目录不取代 `docs/current/STATUS.md` 的 current-status 权威地位；当前 CI 状态仍以 `docs/current/STATUS.md` 为准。CI baseline / security guard 的当前权威 pointer 仍保留在 `docs/current/NQ_CI_BASELINE_PLAN.md` 与 `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`，并由 `docs/baselines/CI_BASELINE_INDEX.md` 统一导航。

## G4 Routed Evidence

- `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`
- `NQ_CI_POSTGRES_FLYWAY_PLAN.md`
- `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`
- `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`
- `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`
- `NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`
- `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`
- `NQ_CI_LOG_REDACTION_PROOF_PLAN.md`
- `NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`
- `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`
- `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`
- `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`
- `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`
- `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`
- `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`
- `NQ_CI_FRONTEND_E2E_PLAN.md`
- `NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`
- `NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`
- `NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`
- `NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`

## Current Authority And Backlog Boundary

- Current CI status authority: `docs/current/STATUS.md`.
- CI baseline pointer: `docs/current/NQ_CI_BASELINE_PLAN.md`.
- CI security guard pointer: `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md`.
- Baseline navigation index: `docs/baselines/CI_BASELINE_INDEX.md`.

Backlog / residual status remains unchanged: Batch 5B-ENV = `P1 SECURITY ENHANCEMENT / NOT STARTED`; Batch 5B-SMOKE = `BLOCKED BY 5B-ENV`; Batch 4F-B to 4F-F = `OPTIONAL BACKLOG / NOT STARTED`; Static workflow assertion = `OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED`.
