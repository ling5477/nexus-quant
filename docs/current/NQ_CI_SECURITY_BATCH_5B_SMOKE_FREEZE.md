# NQ CI Security Batch 5B Smoke Freeze

任务：NQ-CI-SECURITY-BATCH-5B-SMOKE-FREEZE
日期：2026-06-21
状态：**Batch 5B-SMOKE = FROZEN / ACCEPTED**；**Batch 5B = CLOSED / ACCEPTED**；**Freeze = FROZEN / ACCEPTED**。

本文件冻结 Batch 5B-SMOKE 的最小 CI 安全 smoke 实现与其 first run evidence。本轮只生成 freeze docs/status 入口并自检，不修改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`，不补修 implementation，不新增测试，不触发新的 GitHub Actions。本 freeze 为 docs-only。

## 1. 冻结对象

| 项 | 值 |
| --- | --- |
| implementation commit | `9b467fbc`（ci(security): add ci-security-smoke job） |
| first run evidence commit | `9a98041a`（docs: record 5B smoke first run evidence） |
| first run evidence run ID | 27903497008 |
| workflow name | NQ CI Baseline |
| run URL | https://github.com/ling5477/nexus-quant/actions/runs/27903497008 |
| event | push |
| headSha | 9b467fbc21e3ce685572dc3ec84104fd945fa0fb |
| headBranch | dev |
| status / conclusion | completed / success |
| createdAt / updatedAt | 2026-06-21T11:54:52Z / 2026-06-21T11:56:34Z |
| evidence 卷宗 | `NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_EVIDENCE.md` |

`.github/workflows/ci.yml` 自 implementation commit `9b467fbc` 起未再修改（`git diff 9b467fbc..HEAD -- .github/workflows/ci.yml` 为空），故 freeze 锁定的就是该 commit 落地的 `ci-security-smoke` job。

## 2. 9 个 job 冻结结果（全 success）

| Job | Conclusion |
| --- | --- |
| diff-check | success |
| no-outbound-guard | success |
| ci-security-smoke | success |
| backend | success |
| postgres-flyway | success |
| frontend | success |
| frontend-no-backend-e2e | success |
| research | success |
| secret-scan | success |

job 数 = 9；workflow conclusion = success。

## 3. ci-security-smoke 冻结边界

- env-name assertion step 通过（forbidden exchange credential env / forbidden true-flag 名称级检查，fail-closed）。
- smoke 测试 BUILD SUCCESS：`NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0（nq-infra）+ `EnvSafetyValidatorTest` 8/0/0/0（nq-app）+ `NoOutboundExchangeGuardTest` 3/0/0/0（nq-app），合计 **12 tests / 0 fail**。
- NoReal permission probe remains **SKIPPED / REAL_EXCHANGE_PROBE_DISABLED**。
- 复用既有 guard / validator / port 测试，未引入真实 adapter / provider / exchange client；未新增业务测试。

## 4. 安全边界（freeze 时确认）

No real credential read；No outbound call；No LIVE；No Paper trading runtime；No DH runtime；No AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe（NoReal probe remains SKIPPED）。freeze 无 DB / runtime / credential / provider / exchange 副作用；本 freeze 仅 docs-only。

## 5. 状态

**NQ-CI-SECURITY-BATCH-5B-SMOKE-FREEZE = FROZEN / ACCEPTED**。
Batch 5B-SMOKE = FROZEN / ACCEPTED。
Batch 5B = CLOSED / ACCEPTED（5B-ENV FROZEN / ACCEPTED + 5B-SMOKE FROZEN / ACCEPTED）。
Implementation commit = 9b467fbc。
First run evidence run = 27903497008 / success。
Freeze = FROZEN / ACCEPTED。

## 6. 回滚

回退 freeze docs / status 入口即可（删除本文件 + 还原 6 份 docs 的 freeze 状态入口）；不涉及 ci.yml / backend / migration / runtime / credential / provider / exchange。implementation commit `9b467fbc` 与 evidence commit `9a98041a` 不受本 freeze 回滚影响。

## 7. 下一步

进入 NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE_COMMIT_GATE，仅提交 freeze docs（本文件 + 6 份 docs/current 状态入口），确认 working tree clean，结束 5B-SMOKE。本轮不提交 freeze docs。
