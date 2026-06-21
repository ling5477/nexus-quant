# NQ CI Security Batch 5B Smoke First Run Evidence

任务：NQ-CI-SECURITY-BATCH-5B-SMOKE-FIRST-RUN-EVIDENCE
日期：2026-06-21
状态：**First run evidence = PASS / READY FOR REVIEW**；**Freeze = NOT STARTED**。

本文件只记录 Batch 5B-SMOKE implementation（commit `9b467fbc`）的 CI first run evidence。本轮只取证，不 freeze、不补修 implementation、不修改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`、不新增测试。

## 1. Run metadata

| 字段 | 值 |
| --- | --- |
| workflow name | NQ CI Baseline |
| run ID | 27903497008 |
| run URL | https://github.com/ling5477/nexus-quant/actions/runs/27903497008 |
| event | push |
| headBranch | dev |
| headSha | 9b467fbc21e3ce685572dc3ec84104fd945fa0fb |
| status | completed |
| conclusion | success |
| createdAt | 2026-06-21T11:54:52Z |
| updatedAt | 2026-06-21T11:56:34Z |

headSha `9b467fbc21e3ce685572dc3ec84104fd945fa0fb` 即 implementation commit `9b467fbc ci(security): add ci-security-smoke job`，本 run 直接覆盖该 commit；`.github/workflows/ci.yml` 自 `9b467fbc` 起未再修改（`git diff 9b467fbc..HEAD -- .github/workflows/ci.yml` 为空）。

## 2. Jobs（9 个，全部 success）

| Job（workflow 显示名） | Conclusion |
| --- | --- |
| diff-check（Diff check） | success |
| no-outbound-guard（No-outbound guard） | success |
| ci-security-smoke（CI security smoke） | success |
| backend（Backend Maven test） | success |
| postgres-flyway（PostgreSQL / Flyway smoke） | success |
| frontend（Frontend build） | success |
| frontend-no-backend-e2e（Frontend no-backend E2E (Batch 5A)） | success |
| research（Research quality gate） | success |
| secret-scan（Secret scan） | success |

job 数 = 9；workflow conclusion = success。

## 3. ci-security-smoke job 证据

- "Verify no exchange credential / forbidden runtime env is injected" step：通过（job success；任一 forbidden exchange credential env 或 forbidden true-flag 存在都会 `exit 1` fail job，本 run 未触发）。
- "Run CI security smoke tests" step（`mvn -f backend/pom.xml -pl nq-app,nq-infra -am test ...`，跨两 module）：**BUILD SUCCESS**。
  - `NoRealExchangeCredentialPermissionProbePortTest`（nq-infra）：Tests run 1，Failures 0，Errors 0，Skipped 0。
  - `EnvSafetyValidatorTest`（nq-app）：Tests run 8，Failures 0，Errors 0，Skipped 0。
  - `NoOutboundExchangeGuardTest`（nq-app）：Tests run 3，Failures 0，Errors 0，Skipped 0。
  - 合计 **12 tests / 0 failures / 0 errors / 0 skipped**。
- NoReal permission probe remains **SKIPPED**：`NoRealExchangeCredentialPermissionProbePortTest` CI 实跑通过，证明 `NoRealExchangeCredentialPermissionProbePort` 返回 SKIPPED / `REAL_EXCHANGE_PROBE_DISABLED`，未触发真实 permission probe。

## 4. 安全边界确认

No real credential read；No outbound call；No LIVE；No Paper trading runtime；No DH runtime；No AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。run 未注入真实交易所 credential env、未注入 forbidden live / real-provider / real-client true-flag、未访问真实 provider / exchange endpoint。

## 5. 状态

**NQ-CI-SECURITY-BATCH-5B-SMOKE-FIRST-RUN-EVIDENCE = PASS / READY FOR REVIEW**。
Batch 5B-SMOKE = IMPLEMENTED / READY FOR REVIEW。
First run evidence = PASS / READY FOR REVIEW（run `27903497008`）。
Freeze = NOT STARTED。

## 6. 下一步

进入 NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_REVIEW，只读复核本 evidence；不提交、不 freeze。本 evidence 文档与状态入口将在后续单独的 evidence commit gate 提交，本轮不 commit。
