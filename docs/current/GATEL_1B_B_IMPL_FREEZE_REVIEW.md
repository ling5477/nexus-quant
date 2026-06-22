# GateL-1B-B Implementation Freeze Review

任务：NQ-GATEL-1B-B-IMPL-FREEZE
日期：2026-06-22
分支：dev
结论：**PASS / FROZEN / ACCEPTED**
状态：**P1-B CLOSED / ACCEPTED（OKX/Binance runtime credential source hardening 已冻结）**；P1-A **CLOSED / ACCEPTED**；P1-C / P1-D **OPEN / RETAINED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**；GateL-1B No-Real hardening 整体 freeze **NOT DONE（待 C/D）**。

> 本卷宗只冻结 GateL-1B-B（四项 P1 中的 P1-B）实现与 review 证据，并正式关闭 P1-B。
> 冻结不代表 future-real-ready，不代表允许真实 OKX/Binance 接入；真实交易所、LIVE、真实 credential、真实 credential governance bridge、AI、DH runtime 继续禁止。
> P1-C / P1-D 未在本轮处理，保持 OPEN / RETAINED；GateL-1B 整体 No-Real hardening freeze 仍待 C/D 全部独立完成。

## 1. Task classification

- Primary：`IMPLEMENTATION_FREEZE`（per-slice freeze-close）。
- Auxiliary：`DOCUMENTATION_REVIEW`、`CREDENTIAL_SOURCE_HARDENING_FREEZE`、`SECURITY_BOUNDARY_REVIEW`。
- Task level：GateL-1B-B freeze-close（docs-only freeze；不实现新代码）。
- Primary skill：`nq-dh-workflow-router`（任务分类与 Gate 边界检查）。

## 2. Scope

### 冻结对象

- GateL-1B-B implementation commit `ad7f58b0`（`feat(adapter-okx,adapter-binance): drop process-env credential source`）的 OKX/Binance runtime credential source hardening 实现与测试。
- 关联 review 证据：`NQ-GATEL-1B-B-IMPL` = PASS、`NQ-GATEL-1B-B-IMPL-REVIEW` = PASS / APPROVED FOR COMMIT。

### 明确不涉及

- P1-C（AdapterOrderAck/Snapshot rawPayload）、P1-D（NoopMarketDataAdapter status）。
- 真实 credential governance bridge、真实交易所、LIVE、真实 credential、AI、DH runtime、RealClient、real provider、真实 permission probe。
- Java/TS/Python 代码、API、DTO、migration、workflow、frontend、research、scripts、deploy。本轮 docs-only。

## 3. Files inspected（只读）

- `OkxRuntimeConfig.java`、`OkxApiCredentials.java`、`OkxHttpClient.java`、`OkxWsClient.java`（@HEAD）。
- `BinanceRuntimeConfig.java`、`BinanceApiCredentials.java`、`BinanceHttpClient.java`（@HEAD）。
- `OkxRuntimeConfigTest.java`、`OkxNoRealCredentialHardeningTest.java`、`BinanceRuntimeConfigTest.java`、`BinanceNoRealCredentialHardeningTest.java`、`BinanceWsClientTest.java`（@HEAD）。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`（§17 实现进度）、`GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

## 4. Commands run（只读 / offline）

- `git status --short`（clean）、`git branch --show-current`（dev）、`git log --oneline -5`。
- `git show --stat --oneline HEAD`（16 文件，全部在 GateL-1B-B 允许范围）。
- `git show --check HEAD`（无 whitespace）、`git diff --check HEAD^ HEAD`（无 whitespace）。
- `git grep ... HEAD -- OkxRuntimeConfig.java / BinanceRuntimeConfig.java`（credential env 读取）→ **NONE**。
- `git grep -c disabled://...` HEAD（P1-A sentinel 仍在：Binance 4 / OKX 3 引用）；`git grep isConfigured() HEAD -- OkxWsClient.java`（login 守卫 251/336 仍在）。
- 禁止路径扫描（adapter-api/Noop/MarketData/frontend/research/scripts/deploy/workflow/migration）→ **NONE**。
- `git show HEAD:...` 确认两 runtime config 默认 `*.unconfigured()` 在提交内。
- `mvn -f backend/pom.xml -o -pl nq-adapter-okx,nq-adapter-binance -am test`（offline，未外联）→ **BUILD SUCCESS**。

## 5. Freeze verdict

**PASS / FROZEN / ACCEPTED。** GateL-1B-B 实现已提交（`ad7f58b0`），工作区 clean，提交范围仅含允许文件，credential source hardening 与测试在 HEAD 完整、offline 测试通过、whitespace 干净、runtime config 无 credential env 读取、P1-A sentinel 未回退，review 已 APPROVED。满足 freeze-close 条件，正式关闭 P1-B。

## 6. Frozen implementation facts（commit `ad7f58b0`）

- `OkxRuntimeConfig.fromEnvironment` / `BinanceRuntimeConfig.fromEnvironment` 删除对 apiKey/secret/passphrase/private key/private key path/key type 的进程环境（env / system property / .env）解析；credential 默认改为 `OkxApiCredentials.unconfigured()` / `BinanceApiCredentials.unconfigured()`（`isConfigured()=false`）。
- 非敏感 transport metadata（endpoint/timeout/reconnect/heartbeat/diagnostic/refresh）仍按显式 env 解析；`fromSystemEnv` 经 `ProcessEnvironmentResolver` 仍解析进程 env，但 credential key 不再注入凭证对象。
- `OkxApiCredentials.unconfigured()` = `("","","")`；`BinanceApiCredentials.unconfigured()` = `("","")`（HMAC 默认、private key/path 为 null）；均 `isConfigured()=false`。
- authenticated/signed/private 请求在 unconfigured 时由 `OkxHttpClient`/`BinanceHttpClient` 在网络前 fail-closed（`OKX_CREDENTIALS_MISSING` / `BINANCE_CREDENTIALS_MISSING`）；异常仅含 endpoint/traceId/code，不含 credential material。
- `OkxWsClient` login 仍受 `isConfigured()` 守卫（line 251/336），不以空凭证登录。
- `fingerprint()` 输出 `apiKey=missing`（脱敏），不回显 credential 或 env marker。
- 未实现真实 credential governance bridge：owner/account/tenant/credential type/active version/permission scope 仅冻结原则，另起 Gate。
- P1-A endpoint sentinel 未回退（`disabled://binance-not-configured` / `disabled://binance-ws-not-configured` / `disabled://okx-not-configured` / `disabled://okx-ws-not-configured` 仍为默认）。

## 7. Validation

| 项 | 证据 | 结果 |
| --- | --- | --- |
| 提交范围 | `git show --stat HEAD` = 9 adapter（4 main + 3 test 改 + 2 test 新增）+ 7 docs | 仅 GateL-1B-B 允许范围 ✓ |
| whitespace | `git show --check HEAD` / `git diff --check HEAD^ HEAD` | 无错误 ✓ |
| OKX cred env 读取移除 | `git grep` @HEAD OkxRuntimeConfig | NONE ✓ |
| Binance cred env 读取移除 | `git grep` @HEAD BinanceRuntimeConfig | NONE ✓ |
| 默认 unconfigured | `git show HEAD:...` = `*.unconfigured()` + `*NoRealCredentialHardeningTest` | ✓ |
| fail-closed before network | `Okx/BinanceHttpClient` guard + `*NoRealCredentialHardeningTest` | ✓ |
| 错误/日志无 credential | fingerprint `apiKey=missing`；异常仅 endpoint/trace/code；测试断言无 secret/passphrase/apiKey | ✓ |
| OkxWsClient login 守卫 | `isConfigured()` line 251/336（未触碰） | ✓ |
| P1-A sentinel 未回退 | `git grep -c disabled://...` HEAD | Binance 4 / OKX 3 ✓ |
| 未改 rawPayload / Noop / API/DTO/migration/workflow | 禁止路径扫描 = NONE | ✓ |
| Maven offline | `mvn -o -pl nq-adapter-okx,nq-adapter-binance -am test` | BUILD SUCCESS；OKX 32 / Binance 51（0 fail / 0 error / 1 skipped）✓ |

skipped = `BinanceWsClientLiveDiagnosticTest`（`-Dnq.binance.ws.live.diagnostic` 系统属性门禁，默认不执行，不连真实 Binance）。

## 8. P1 status

- **P1-A：CLOSED / ACCEPTED**（GateL-1B-A endpoint sentinel / no-outbound frozen，commit `04ddb774`；本轮回归未破坏）。
- **P1-B：CLOSED / ACCEPTED**（OKX/Binance runtime credential source hardening frozen，commit `ad7f58b0`）。
- **P1-C：OPEN / RETAINED**（AdapterOrderAck / AdapterOrderSnapshot rawPayload，未修）。
- **P1-D：OPEN / RETAINED**（NoopMarketDataAdapter 普通 success，未修）。

## 9. Adapter readiness verdict

**NOT READY / NOT FROZEN / NOT AUTHORIZED。** P1-B 关闭只是把 OKX/Binance 默认 runtime credential source 收口为 no-real unconfigured，不改变 adapter readiness，也未引入真实 credential governance bridge。real adapter 仍需 capability contract、error contract、credential governance bridge、readiness checklist、专项安全审计、CI evidence/freeze 与用户显式授权并另起 Gate。本卷宗不得被引用为允许真实 OKX/Binance 接入或 future-real-ready 的依据。

## 10. Forbidden boundaries（本轮遵守）

- 未修改 Java/TS/Python 代码；未新增 API / DTO / migration / workflow；未改 frontend / research / scripts / deploy。
- 未实现真实 credential governance bridge；未读取 `.env`/真实 credential；未访问外网；未调用任何交易所。
- 未启用 LIVE；未接 AI；未接 DH runtime；未实现 RealClient/real provider/真实 permission probe；未下单/撤单/转账。
- 未修 P1-C/P1-D；未改 rawPayload / NoopMarketDataAdapter；未把 adapter 标记 future-real-ready；未把 hardening 写成允许真实 OKX/Binance 接入。

## 11. Regression boundary

后续若改动以下任一，须重新 review + 重新 freeze（addendum 或新 freeze），不得静默并入本 freeze：

- `OkxRuntimeConfig` / `BinanceRuntimeConfig` 的 `fromEnvironment` / `fromSystemEnv` credential 解析（任何恢复进程环境 credential 读取）。
- `OkxApiCredentials.unconfigured()` / `BinanceApiCredentials.unconfigured()` 语义。
- `OkxHttpClient` / `BinanceHttpClient` 的 credential fail-closed 守卫；`OkxWsClient` login 的 `isConfigured()` 守卫。
- 任何引入真实 credential governance bridge / future-real enable 的改动（须另起 Gate + 安全审计）。

回滚到旧进程环境 credential 解析会重新打开 P1-B，须立即把 P1-B 恢复为 OPEN 并恢复 adapter NOT READY 状态。

## 12. Rollback

- `git revert ad7f58b0`（或还原两个 runtime config、两个 credential 模型与相关测试、删除两个新增 credential hardening 测试），并还原本轮 current docs 与本 freeze 卷宗。
- 回滚使 P1-B 重新 OPEN；显式注入凭证路径（canonical constructor）不受影响；无 runtime/DB/credential/provider/exchange 副作用。

## 13. Recommended next task

**NQ-GATEL-1B-C-IMPL**（rawPayload boundary hardening：OKX/Binance ack/snapshot producer 将 `rawPayload` 固定为 sanitized/null，字段删除另起独立 contract task），按冻结顺序 A → B → C → D 推进。GateL-1B 整体 No-Real hardening freeze 须待 C/D 全部独立完成后另行执行。

## 14. Final recommendation

**NQ-GATEL-1B-B-IMPL-FREEZE：PASS / FROZEN / ACCEPTED。** P1-B 正式 CLOSED / ACCEPTED；P1-A 保持 CLOSED / ACCEPTED；P1-C/P1-D 保持 OPEN / RETAINED；adapter readiness 保持 NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE DISABLED、AI NOT STARTED、DH runtime NOT INTEGRATED、RealClient/real provider/real permission probe/real credential governance bridge NOT IMPLEMENTED。下一步 `NQ-GATEL-1B-C-IMPL`，本轮不进入。
