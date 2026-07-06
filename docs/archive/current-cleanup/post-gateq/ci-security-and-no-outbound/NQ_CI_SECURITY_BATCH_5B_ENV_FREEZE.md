# NQ CI Security Batch 5B-ENV Freeze

任务：NQ-CI-SECURITY-BATCH-5B-ENV-FREEZE
日期：2026-06-21
分支：dev
结论：**PASS / FROZEN / ACCEPTED**。

```text
Batch 5B-ENV = FROZEN / ACCEPTED
Batch 5B-SMOKE = STILL BLOCKED
```

本文件是 Batch 5B-ENV 的不可变 freeze 卷宗。固化对象是已通过的 green run 证据与冻结边界，不引入任何代码 / workflow / 配置变更，不启动 5B-SMOKE。

---

## 1. Freeze object

- Freeze object：**Batch 5B-ENV**（GateK CI/security 环境安全边界：启动期 fail-closed env guard + CI no-outbound 兼容 + secret placeholder 边界 + CI trigger 边界）。
- Freeze 类型：green run evidence freeze + env guard baseline freeze + 文档状态冻结。
- 不在本 freeze 范围：5B-SMOKE、LIVE、AI、DH runtime、RealClient、real provider、real exchange adapter、real permission probe。

## 2. Freeze evidence（immutable green run）

| 项 | 值 |
| --- | --- |
| Freeze evidence run ID | `27876451289` |
| Freeze evidence headSha | `8ba140d96d84b7e2ae5f379043779bfeb925e2fc` |
| branch | dev |
| Freeze evidence workflow | NQ CI Baseline |
| Freeze evidence event | push |
| status | completed |
| Freeze evidence conclusion | **success** |

run 链路证据：`8ba140d9` 为 fix commit（`ci(security): fix 5B env guard workflow env conflict`）；其后 `dev` 仅有纯文档提交 `06d8fc62 docs(ci): record 5B env fix rerun green`，`dev` HEAD == `origin/dev` == `06d8fc62`，且 `8ba140d9` 是 HEAD 的 ancestor。green run 对应的 `.github/workflows/ci.yml` 自 `8ba140d9` 起未再变更（`git diff 8ba140d9..HEAD -- .github/workflows/ci.yml` 为空），故 freeze 的 workflow 状态与 green run 验证的 workflow 完全一致。

## 3. 8 个 job 结果（全部 success）

| Job | conclusion |
| --- | --- |
| diff-check | success |
| no-outbound-guard | success |
| backend | success |
| postgres-flyway | success |
| frontend | success |
| frontend-no-backend-e2e (Batch 5A) | success |
| research | success |
| secret-scan | success |

## 4. 关键测试证据（来自 green run 27876451289 日志）

`no-outbound-guard` job（`-Dnq.no-outbound.guard.required=true`）与 `backend` job 均运行并通过下列测试，无失败、无 skip：

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ...app.config.env.EnvSafetyValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...app.smoke.NoOutboundExchangeGuardTest
```

- `NoOutboundExchangeGuardTest`：无失败。`shouldRejectExchangeCredentialEnvWhenCiGuardIsRequired` 实跑通过（3 run / 0 skip），不再因 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 非空而失败。
- `EnvSafetyValidatorTest`：无失败（8 run / 0 fail / 0 error / 0 skip）。

## 5. RED 根因与修复方式

- root cause：workflow 注入 forbidden env names —— `.github/workflows/ci.yml` 在 `no-outbound-guard` 与 `backend` job 的 job-level `env:` 注入了 `NQ_LIVE_ENABLED="false"` / `NQ_REAL_PROVIDER_ENABLED="false"` / `NQ_REAL_CLIENT_ENABLED="false"`；既有 `NoOutboundExchangeGuardTest` 在 CI-guard-required 模式下将这三个变量名列为禁止存在的 exchange credential/live env（值为 `"false"` 同样违规），导致首跑 RED（run `27875157176`，失败 job `Backend Maven test` + `No-outbound guard`）。
- fix：删除 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 的 CI job env 注入（fix commit `8ba140d9`）。依据 `EnvSafetyGuardConfiguration` 对这些开关缺省即按 `false` 处理（absence => false），不注入不削弱 5B-ENV 启动期 fail-closed 语义。
- no-outbound guard 未放宽：`NoOutboundExchangeGuardTest` 的 `FORBIDDEN_EXCHANGE_ENV_NAMES` 与 `forbidden_true_names` 校验步骤均保留，未降低安全边界。
- `NoOutboundExchangeGuardTest` 未修改；`EnvSafetyValidator` / `EnvSafetyValidatorTest` / `EnvSafetyGuardConfiguration` / `application*.yml` / `.env.example` 均未修改。

## 6. 冻结边界（frozen boundaries）

| 边界 | 状态 |
| --- | --- |
| env guard | **FROZEN** —— `EnvSafetyGuardConfiguration` + `EnvSafetyValidator` 启动期 fail-closed（LIVE/no-outbound、CI/LIVE、CI/real provider·client、CI/test real exchange、no-outbound real endpoint、CI/test credential material、CI/test AI/DH） |
| no-outbound compatibility | **FROZEN** —— CI job 不再注入被 `NoOutboundExchangeGuardTest` 禁止的 env 名；guard 与 validator 共存，11/0/0/0 |
| secret placeholder boundary | **FROZEN** —— `.env.example` 与 CI env 仅用 `PLACEHOLDER_ONLY` / `DO_NOT_COMMIT_REAL_VALUE` / `REPLACE_WITH_LOCAL_PLACEHOLDER`；secret-scan job 保留 |
| CI trigger boundary | **FROZEN** —— `pull_request:[dev]` + `push:[dev]` + `workflow_dispatch`；`permissions: contents: read`；8 个 job 未删；未新增 GitHub secret |
| Batch 5B-SMOKE | **STILL BLOCKED** —— 不在本 freeze 范围；不得写成 READY / STARTED |

## 7. 边界确认（本轮 freeze 未触碰）

- workflow / Java / TypeScript / Python 代码：未改。
- `EnvSafetyValidator` / `EnvSafetyGuardConfiguration` / `NoOutboundExchangeGuardTest` / `EnvSafetyValidatorTest`：未改。
- `application*.yml` / `.env.example`：未改。
- migration（新增/历史）：未改。
- frontend / research / scripts / deploy：未改。
- 未读取真实 `.env` / secret / credential / logs / dump / backup；未调用真实交易所；未真实 HTTP 探活。

## 8. 风险与回滚边界

- 风险：低。本 freeze 为 docs-only，不改变运行态；冻结对象是 immutable green run `27876451289` 与未变更的 workflow 状态。残余不确定性仅为“未来对 `ci.yml` / guard 的任何改动都需重新 first-run + freeze”。
- 回滚：`git revert` 本 freeze 文档提交，或删除 `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md` 并还原 8 个状态文档的 5B-ENV 段落即可，回到 `FIX RERUN GREEN / READY FOR FREEZE`。回滚无运行态副作用（不涉及代码 / workflow / 配置 / migration）。
- green run evidence 不可回滚（GitHub Actions 历史 run 为不可变记录）。

## 9. 解除阻塞判定

- 是否允许解除 5B-ENV 阻塞：**是**。Batch 5B-ENV = **FROZEN / ACCEPTED**。
- 是否继续阻塞 5B-SMOKE：**是**。Batch 5B-SMOKE = **STILL BLOCKED**；5B-SMOKE 须按 `NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md` §7 的启动门槛（5B-ENV-A..E）满足后另起工作单，本 freeze 不解除、不预告 5B-SMOKE。

## 10. 边界声明

```text
Batch 5B-ENV = FROZEN / ACCEPTED
Batch 5B-SMOKE = STILL BLOCKED
Freeze evidence run = 27876451289 / NQ CI Baseline / push / completed / success
Freeze evidence headSha = 8ba140d96d84b7e2ae5f379043779bfeb925e2fc
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
No real exchange adapter
No real permission probe
```
