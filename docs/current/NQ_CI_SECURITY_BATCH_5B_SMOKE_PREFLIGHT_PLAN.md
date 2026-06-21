# NQ CI Security Batch 5B Smoke Preflight Plan

任务：NQ-CI-SECURITY-BATCH-5B-SMOKE-PREFLIGHT-PLAN
日期：2026-06-21
状态：**REVIEWED / ACCEPTED**；**Batch 5B-ENV = FROZEN / ACCEPTED**；**Batch 5B-SMOKE = PLANNED / NOT STARTED**。

## 1. 任务边界

本文件只做 Batch 5B-SMOKE 的 preflight 与 plan，不实现 smoke，不修改 workflow，不修改 Java / TypeScript / Python 代码，不修改 `application*.yml` 或 `.env.example`，不启动真实外联。

目标：

1. 判断 5B-ENV-A..E 门槛是否满足。
2. 将 5B-SMOKE 从 `STILL BLOCKED` 收口为 `PLANNED / NOT STARTED`，供后续 plan review 使用。
3. 定义最小、可审计、无外联、无真实凭证读取的 CI smoke 设计。
4. 明确后续 implementation 的禁止范围、验收条件和回滚边界。

非目标：

1. 不新增或修改 `.github/workflows/ci.yml`。
2. 不新增 smoke job、测试、脚本、provider、adapter、client 或 migration。
3. 不读取真实 `.env`、secret、credential、log、dump、backup。
4. 不访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken / Crypto / Hyperliquid。
5. 不开启 LIVE / AI / DH runtime，不实现 RealClient / real provider / real exchange adapter。

## 2. Preflight 证据源

| 证据 | 当前结论 |
| --- | --- |
| 5B-ENV freeze 卷宗 | `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md` 已记录 **FROZEN / ACCEPTED** |
| Immutable run | `27876451289` / NQ CI Baseline / push / completed / success |
| Freeze headSha | `8ba140d96d84b7e2ae5f379043779bfeb925e2fc` |
| CI job baseline | 8 jobs all success：diff-check / no-outbound-guard / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan |
| EnvSafety evidence | `EnvSafetyValidatorTest` 8/0/0/0；`NoOutboundExchangeGuardTest` 3/0/0/0，均实跑非 skip |
| Workflow trigger | `pull_request:[dev]` + `push:[dev]` + `workflow_dispatch`；`permissions: contents: read` |
| Runtime boundary | LIVE disabled；AI not started；DH runtime not integrated；RealClient / real provider / real exchange adapter not implemented |

## 3. 5B-ENV-A..E 门槛判定

| 门槛 | 判定 | 证据 | 对 5B-SMOKE 的影响 |
| --- | --- | --- | --- |
| 5B-ENV-A：ci / test / paper profile 边界是否 frozen | **满足** | `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md` 冻结 env guard 与 profile/provider 隔离；`application-ci.yml` / `application-test.yml` / `application-paper.yml` 只读确认存在 | smoke 只能使用 CI/test/paper 安全边界，不得启用 real provider |
| 5B-ENV-B：`EnvSafetyValidator` fail-closed guard 是否 frozen | **满足** | freeze run 记录 `EnvSafetyValidatorTest` 8/0/0/0；`EnvSafetyGuardConfiguration` 在启动期 fail closed | smoke 必须保留启动期 fail-closed，不得通过关闭 guard 获得 green |
| 5B-ENV-C：no-outbound guard 与 EnvSafety guard 兼容是否 frozen | **满足** | freeze run 记录 `NoOutboundExchangeGuardTest` 3/0/0/0；RED 修复为删除 forbidden env-name 注入，不放宽 guard | smoke 必须继续执行 no-outbound guard，不得真实 HTTP 探活 |
| 5B-ENV-D：secret placeholder / `.env.example` 边界是否 frozen | **满足** | `.env.example` 与 CI env 使用 placeholder-only 语义；secret-scan job 保留且 freeze run success | smoke 不得读取真实 `.env`，只允许 placeholder / mock / fake 路径 |
| 5B-ENV-E：CI trigger 与 8 job baseline 是否 frozen | **满足** | `.github/workflows/ci.yml` 只读确认 8 job baseline 与 trigger；freeze run all success | 后续 smoke implementation 应新增独立 job，不得破坏既有 8 jobs |

结论：5B-ENV-A..E 对 planning 的前置门槛已满足。5B-SMOKE 不再是 `STILL BLOCKED`，但仍不得进入 implementation；当前状态只能写为 **PLANNED / NOT STARTED**。

## 4. Smoke 最小目标

5B-SMOKE 只验证 CI 安全边界与最小应用启动 / mock 路径：

1. no-real permission probe remains `SKIPPED`。
2. no-outbound guard remains enforced。
3. `EnvSafetyValidator` still fail-closed。
4. CI/test/paper profile does not enable real provider。
5. placeholder-only credential path does not trip false positive。
6. forbidden env names are not injected into CI jobs。
7. 最小应用启动或 mock path 只证明安全边界可启动，不证明真实交易所连通性。

5B-SMOKE 不验证：

1. 真实交易所连通性。
2. 真实凭证权限。
3. LIVE 下单、撤单、转账、提现。
4. AI signal / AI runtime。
5. DH runtime integration。

## 5. Smoke 禁止范围

后续 5B-SMOKE implementation 必须保持：

```text
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

明确禁止访问或探活：

```text
OKX
Binance
Bybit
Gate
Coinbase
Kraken
Crypto
Hyperliquid
```

明确禁止交易动作：

```text
order
cancel
transfer
withdraw
LIVE permission probe
real provider permission probe
```

## 6. 候选 CI job 设计

推荐后续 implementation 新增独立 job：`ci-security-smoke`（备选名：`env-smoke`）。

设计原则：

1. 独立于 `backend` 与 `no-outbound-guard` 的语义，但必须依赖其不退化。
2. 不复用真实 exchange adapter。
3. 不注入 GitHub secret。
4. 不注入 `NQ_LIVE_ENABLED` / `NQ_REAL_PROVIDER_ENABLED` / `NQ_REAL_CLIENT_ENABLED` 等 forbidden env names。
5. 不启动外部网络探活，不执行 `curl` / HTTP call 到真实 host。
6. 使用 Maven test-scope 的 mock / fake / NoReal 路径。
7. 只上传普通测试报告；不得上传 raw env、raw headers、raw response 或 credential material。

候选 job 骨架（仅计划，未实现）：

```yaml
ci-security-smoke:
  name: CI security smoke
  runs-on: ubuntu-latest
  permissions:
    contents: read
  env:
    CI: "true"
    NQ_PROFILE: ci
    NQ_NO_OUTBOUND: "true"
  steps:
    - checkout
    - setup-java
    - run targeted Maven tests for EnvSafety + no-outbound + NoReal smoke
```

## 7. 候选测试设计

推荐后续 implementation 只新增或复用 test-scope 测试，不改生产交易路径：

| 候选测试 | 目的 | 禁止点 |
| --- | --- | --- |
| `EnvSafetyValidatorTest` targeted run | 证明 fail-closed 矩阵未退化 | 不放宽 validator，不输出 secret value |
| `NoOutboundExchangeGuardTest` targeted run | 证明真实交易所 host 与 forbidden env name 仍被拒绝 | 不访问真实 host，只做 deterministic guard |
| `NoRealExchangeCredentialPermissionProbePort` smoke | 证明默认 permission probe remains NoReal / SKIPPED | 不新增 RealClient / real provider |
| Placeholder credential smoke | 证明 placeholder-only 配置不误判为真实 credential | 不读取真实 `.env` |
| CI env-name assertion | 证明 forbidden env names 未注入 smoke job | 不把 false 值当作安全替代，变量名存在即违规 |

后续 implementation 的最小 Maven 命令建议：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test "-Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest,<FutureNoRealSmokeTest>" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnq.no-outbound.guard.required=true"
```

`<FutureNoRealSmokeTest>` 是未来实现占位名；本轮不创建该测试。

## 8. 验收条件

后续 5B-SMOKE implementation 只有满足全部条件才可进入 first green review：

1. GitHub Actions target run green。
2. 既有 `diff-check` / `no-outbound-guard` / `backend` / `postgres-flyway` / `frontend` / `frontend-no-backend-e2e` / `research` / `secret-scan` 不退化。
3. 新 smoke job green。
4. `NoOutboundExchangeGuardTest` 与 `EnvSafetyValidatorTest` 不 skip、不放宽。
5. migration / frontend / research / scripts / deploy 不受影响。
6. 未读取真实 `.env` 或任何 secret。
7. 未产生 outbound call。

## 9. P0/P1/P2/P3 findings

| Priority | Finding |
| --- | --- |
| P0 | 0。未发现阻断 5B-SMOKE planning 的缺口。 |
| P1 | 0。5B-ENV-A..E 对 planning 的前置门槛均已满足。 |
| P2 | 1。后续 implementation 仍需新增独立 smoke job 与最小 NoReal smoke 测试；本轮按边界不实现。 |
| P3 | 1。候选 job 名 `ci-security-smoke` / `env-smoke` 需在 implementation review 中二选一，避免 CI matrix 命名漂移。 |

## 10. 回滚边界

本轮是 docs-only plan。回滚方式：

1. 删除本文件。
2. 还原 `NQ_CI_BASELINE_PLAN.md`、`README.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md` 中本任务新增段落。

后续 implementation 的回滚边界必须保持为：删除新增 job / 测试 / 文档即可；不得涉及 DB schema、runtime data、credentials 或外部 provider 状态。

## 11. 最终状态声明

```text
NQ-CI-SECURITY-BATCH-5B-SMOKE-PREFLIGHT-PLAN：REVIEWED / ACCEPTED
Batch 5B-ENV = FROZEN / ACCEPTED
Batch 5B-SMOKE = PLANNED / NOT STARTED
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
