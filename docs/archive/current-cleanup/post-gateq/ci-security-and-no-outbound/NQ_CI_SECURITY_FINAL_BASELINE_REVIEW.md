# NQ CI Security Final Baseline Review

任务：NQ-CI-SECURITY-FINAL-BASELINE-REVIEW
日期：2026-06-21
分支：dev
任务类型：CI_SECURITY_FINAL_REVIEW + BASELINE_CONSISTENCY_REVIEW + FREEZE_READINESS_REVIEW + DOCUMENTATION_REVIEW
状态：**NQ-CI-SECURITY-FINAL-BASELINE-REVIEW = PASS / READY FOR FINAL FREEZE**。

本文件只做 GateK CI/security final freeze 前的只读总复核，不修改 workflow / code / migration / runtime，不启动新功能，不直接 final freeze。GateK CI/security 本轮只写 **READY FOR FINAL FREEZE**，不写 FROZEN。

## 1. 本地与远端状态

| 项 | 值 |
| --- | --- |
| HEAD | `3158e8ad`（docs(ci): freeze Batch 5B smoke baseline） |
| 分支 | dev |
| local dev / origin/dev | 均为 `3158e8ad`（已同步） |
| working tree | clean（`git status --short` 为空） |
| 最新提交 | 5B-SMOKE freeze docs commit（docs-only，7 文件） |

`git diff --check` clean；`.github/workflows/ci.yml` / backend / `backend/**/db/migration` / frontend / research / scripts / deploy / `.env.example` 工作树 diff 均为空。

## 2. 5B-SMOKE freeze evidence

| 项 | 值 |
| --- | --- |
| run ID | 27903497008 |
| workflow | NQ CI Baseline |
| event | push |
| headSha | 9b467fbc21e3ce685572dc3ec84104fd945fa0fb |
| conclusion | success |
| jobs | 9 / 9 success |

headSha `9b467fbc` = implementation commit `9b467fbc`。`.github/workflows/ci.yml` 自 `9b467fbc` 起至 HEAD `3158e8ad` 未变（`git diff 9b467fbc..HEAD -- .github/workflows/ci.yml` 为空），freeze docs commit `3158e8ad` 为 docs-only。最新 run `27904207910`（docs freeze commit）亦 success（docs-only，不改变 evidence baseline）。

## 3. 9 个 CI job 结果（run 27903497008，全 success）

| Job | Conclusion |
| --- | --- |
| diff-check | success |
| no-outbound-guard | success |
| ci-security-smoke | success |
| backend | success |
| postgres-flyway | success |
| frontend | success |
| frontend-no-backend-e2e（Batch 5A） | success |
| research | success |
| secret-scan | success |

ci-security-smoke 内：`NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0 + `EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0 = 12 tests / 0 fail；NoReal permission probe remains SKIPPED。

## 4. Batch 状态矩阵

| Batch | 范围 | 状态 | evidence |
| --- | --- | --- | --- |
| Batch 1 | CI baseline / diff-check / 基础 jobs | implemented / first green confirmed | baseline plan |
| Batch 2A–2E | PostgreSQL / Flyway（job / artifact / smoke / context / seed cleanup） | FROZEN / ACCEPTED | runs 27501253175 / 27521750442 / 27535619157 / 27601707199 / 27614046762 |
| Batch 3 | no-outbound guard | FROZEN / ACCEPTED | run 27634370657 |
| Batch 4B | secret scan | FROZEN / ACCEPTED | run 27674393780，commit 31540de8 |
| Batch 4C | artifact / log redaction baseline | FROZEN / ACCEPTED | runs 27701669084 / 27732660516 |
| Batch 4F-A | dependency-audit preflight | FROZEN / ACCEPTED | baseline plan |
| Batch 5A | frontend no-backend E2E（4-spec allowlist） | FROZEN / ACCEPTED（job success，未退化） | run 27903497008 job |
| Batch 5B-ENV | environment safety guard | FROZEN / ACCEPTED | run 27876451289 |
| Batch 5B-SMOKE | ci-security-smoke | FROZEN / ACCEPTED | run 27903497008 |
| Batch 5（overall） | — | CLOSED / ACCEPTED（5B-ENV + 5B-SMOKE 均 FROZEN） | — |
| Batch 4F-B..4F-F | optional dependency / SBOM / pinning / governance | OPTIONAL BACKLOG / NOT STARTED（不阻断 final freeze） | — |
| Static workflow assertion | optional | OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED（不阻断） | — |

## 5. 一致性与冲突检查

- 当前权威 5B-SMOKE 状态全部为 FROZEN / ACCEPTED（BASELINE / README / ROADMAP / STATUS 顶部 / FREEZE 卷宗）。
- 无 "Batch 5B BLOCKED" 当前声明。
- 无 5B-SMOKE 被写成 LIVE / real / outbound enabled。
- GateK CI/security 未被写成 FROZEN 或 final freeze completed；本轮口径 = READY FOR FINAL FREEZE。
- STATUS / WORKLOG / TESTING 中 5B-SMOKE 早期 "PLANNED / NOT STARTED" / "STILL BLOCKED" 条目均为按时间线保留的历史里程碑快照（preflight / implementation-plan / 5B-ENV freeze-time），非当前状态，不构成冲突。
- P3（已在本轮修复）：README preflight-plan 索引条目原描述 "Batch 5B-SMOKE PLANNED / NOT STARTED" 与当前 FROZEN 并列易读混淆，本轮已就近加时间限定澄清（plan 编写时为 PLANNED / NOT STARTED，current FROZEN / ACCEPTED）。

## 6. 边界确认

No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。本轮 docs-only，未改 workflow / code / migration / runtime / `.env.example`，未触发新 Actions（仅只读读取既有 run），未 final freeze。

## 7. 结论

**NQ-CI-SECURITY-FINAL-BASELINE-REVIEW = PASS / READY FOR FINAL FREEZE**。

Batch 5B-ENV = FROZEN / ACCEPTED
Batch 5B-SMOKE = FROZEN / ACCEPTED
Batch 5B = CLOSED / ACCEPTED
GateK CI/security = READY FOR FINAL FREEZE

## 8. 风险与回滚边界

- 风险：无 P0/P1/P2；唯一 P3（README 索引措辞）已修复。
- 回滚：本轮仅 docs-only（新增本 review 卷宗 + 若干 docs/current 状态/索引入口）；回退这些 docs 即可，无 workflow / code / migration / runtime / credential / provider / exchange 副作用。

## 9. 下一步

进入 GateK CI/security final freeze gate（单独工作单）。本轮不 final freeze、不改 workflow/code/migration、不启动新功能。
