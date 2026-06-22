# NQ Test Isolation OKX Bootstrap No-Outbound Freeze

任务：NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FREEZE
日期：2026-06-22
状态：**FROZEN / ACCEPTED**
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

本卷宗把已通过的 OKX bootstrap / test isolation / no-outbound 专项复审固化为 **FROZEN / ACCEPTED**。本轮为 docs-only 落档：未修复 P2、未修改代码、未修改 workflow、未修改 `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy / 测试、未真实外联、未读取真实凭证。

## 1. Freeze object

OKX bootstrap / test isolation / no-outbound boundary。

## 2. Freeze result

**FROZEN / ACCEPTED**。

## 3. Review commit

`0b9c0b203a6be49d256bb431a441ab7875b066fa`（`docs(test): record OKX bootstrap no-outbound isolation review`）。

## 4. Review result

**PASS / READY FOR FREEZE**（详见 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` §13）。

## 5. Review HEAD

`e3b12e33788bd23e3d96507dd8efcc511db33043`（复审执行时 HEAD；复审前 working tree clean）。GateK CI/security final freeze commit = `8d126f9f`，HEAD 领先部分为后续 docs-only 提交，代码 / workflow / migration 无漂移。

## 6. Review scope

- `.github/workflows/ci.yml`
- `.env.example`
- `application.yml` / `-local` / `-test` / `-gated-verify` / `-freeze` / `-prod`
- OKX bootstrap / adapter / runtime / probe boundary
- `ExchangeNoOutboundGuard` / `NoOutboundExchangeGuardTest`
- `EnvSafetyValidator` / `EnvSafetyGuardConfiguration`
- `NoRealExchangeCredentialPermissionProbePort`
- `ExchangeAdapterConfiguration`
- `LocalTestFallbackConfiguration`
- `AccountModuleConfiguration`
- `OkxRecoveryService` 启动钩子（`@EventListener(ContextRefreshedEvent.class)`）
- `docs/current/**`

## 7. Frozen conclusions

- OKX bootstrap exists only as bootstrap stub / fallback boundary, not real trading readiness（`OkxBootstrapFallbackFactory` stub baseUrl=`http://127.0.0.1`，public stub 返回内置 payload，authenticated stub 直接抛 `OKX_ADAPTER_BOOTSTRAP_STUB`）。
- Adapter 构造惰性：`OkxInstrumentsCache` 构造期不发起 HTTP；instruments 仅在首次 `snapshot` / `getRequired` 时拉取。
- Startup does not access real OKX（local full Spring context 启动期对 `www.okx.com/api/v5/public/instruments` 访问次数断言为 0，且无 `okx_adapter_bootstrap_fallback_enabled` 日志）。
- test / ci / paper / local do not auto-enable real exchange（`okx.ws.enabled` / `binance.ws.enabled` 默认 false；`okx.recovery.enabled` local/freeze=false；`instrument.catalog-sync.enabled` freeze=false；test profile `no-outbound=true`；`OkxRecoveryService.onContextRefreshed` 在 recovery 关闭时仅打印脱敏日志后返回，不外联）。
- Permission probe default is NoReal / SKIPPED / REAL_EXCHANGE_PROBE_DISABLED（`AccountModuleConfiguration` 默认装配 `NoRealExchangeCredentialPermissionProbePort`，不创建 HTTP client、不访问交易所）。
- No-outbound guard covers OKX and exchange hosts（denylist 覆盖 OKX / Binance / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto / Hyperliquid，含子域 `endsWith` 匹配，`select()` / `connectFailed()` fail-closed；CI `no-outbound-guard` job 保留）。
- profile boundary：`LIVE / AI / DH / real-provider / real-client / real-exchange` 全部 `absence => false`；`EnvSafetyValidator` 启动期对冲突组合一次性 fail-closed（`effectiveNoOutbound = configured || ci || testProfile`）。
- `.env.example` is placeholder-only（仅 `PLACEHOLDER_ONLY` / `DO_NOT_COMMIT_REAL_VALUE` / `REPLACE_WITH_LOCAL_PLACEHOLDER`）。
- LIVE / AI / DH / RealClient / real provider / real exchange adapter / real permission probe remain disabled / not implemented。

## 8. Test evidence

本地只读复核（CI / no-outbound 环境，无真实外联、无真实凭证读取）：

- `NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0。
- `EnvSafetyValidatorTest` 8/0/0/0。
- `NoOutboundExchangeGuardTest` 3/0/0/0（0 skipped，CI-required env-absence 断言已执行通过）。
- `BUILD SUCCESS`。

命令：`mvn -f backend/pom.xml -pl nq-app,nq-infra -am test -Dtest=NoRealExchangeCredentialPermissionProbePortTest,EnvSafetyValidatorTest,NoOutboundExchangeGuardTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.no-outbound.guard.required=true`。

## 9. Findings

### P0

- 0。

### P1

- 0。

### P2（non-blocking）

- 1：`OkxRuntimeConfig` code-level real host defaults（`DEFAULT_BASE_URL=https://www.okx.com`、真实 WS 默认）仅在 `NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL` 完全缺省时取用，且 not covered by startup `EnvSafetyValidator` endpoint validation（该 guard 只检查已注入的 env/property 值）。当前由惰性构造 + test/CI ProxySelector denylist + CI 注入 `PLACEHOLDER_ONLY` + ws/recovery/catalog-sync 关闭/手动 + 无 real provider/RealClient 多重缓解，当前任何受控 profile 下不产生真实外联。Must be handled by a later dedicated task；本轮不修复。

### P3（non-blocking）

- 1：`application-ci.yml` / `application-paper.yml` naming expectation drift。CI 以 `CI=true` + test / no-outbound 语义生效，`EnvSafetyValidator.testProfileActive()` 已识别 `ci` / `paper` / `*-smoke` profile 名，语义无缺口。

## 10. Backlog pointer

- **NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-PLAN**（处理 P2：让 `OkxRuntimeConfig` 在 env 缺省时默认改为非真实 sentinel，或把解析后的 baseUrl/wsUrl 纳入启动期 `EnvSafetyValidator` endpoint 校验）。
  - **状态更新（2026-06-22）**：Path A 已实现，P2 从 “open backlog” 转为 **IMPLEMENTED / PENDING CI RUN**。`OkxRuntimeConfig` 默认 endpoint 已改为 `disabled://okx-not-configured`（base）/ `disabled://okx-ws-not-configured`（dome+real WS），真实 endpoint 仅显式 env opt-in。未改 `EnvSafetyValidator` / workflow / `application*.yml` / `.env.example`。详见 `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md`。
  - 该实现属本卷宗 §11 regression boundary（OKX runtime config 改动），**不静默并入本 freeze**：须经 `NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-CI-RUN-REVIEW` 采证后，以 post-freeze addendum 形式触发本专项复审 + freeze addendum，再把 P2 标记 CLOSED。本 freeze 卷宗结论（FROZEN / ACCEPTED）不受影响。

## 11. Regression boundary

后续若改动以下任一对象，必须重新 review + freeze，不得直接沿用本 freeze：

- OKX runtime config（`OkxRuntimeConfig`、endpoint 默认值、env 解析）。
- exchange adapter construction（`OkxExchangeAdapter` / `ExchangeAdapterConfiguration` / `OkxBootstrapFallbackFactory`）。
- no-outbound guard（`ExchangeNoOutboundGuard` / `NoOutboundExchangeGuardTest` / denylist）。
- `EnvSafetyValidator` / `EnvSafetyGuardConfiguration`。
- profile defaults（`application*.yml` 的 LIVE/AI/DH/real-* / no-outbound / ws / recovery / catalog-sync 开关）。
- permission probe（`NoRealExchangeCredentialPermissionProbePort` / `AccountModuleConfiguration` 绑定）。
- CI env guard（`ci.yml` no-outbound-guard / ci-security-smoke job、forbidden env 名单、denylist）。

## 12. Rollback

- revert 本 freeze docs commit 即回到 **PASS / READY FOR FREEZE**。
- 本轮为 docs-only，无 runtime / DB / credential / provider / exchange 副作用。

## 13. Boundary confirmation

- 本轮未修改 `.github/workflows/ci.yml`。
- 本轮未修改 backend / Java / TypeScript / Python 代码。
- 本轮未修改 `application*.yml`。
- 本轮未修改 `.env.example`。
- 本轮未新增 migration，未修改历史 migration。
- 本轮未修改 frontend / research / scripts / deploy。
- 本轮未新增测试、未修改测试。
- 本轮未读取真实 `.env` / secret / credential / logs / dump / backup。
- 本轮未调用 OKX 或任何真实交易所，未真实 HTTP 探活，未 DNS 探测交易所。
- 本轮未下单 / 撤单 / 转账 / 提现。
- 本轮未开启 LIVE，未接 AI，未接 DH runtime，未实现 RealClient / real provider / real exchange adapter。
- 本轮未修复 P2。

## 14. 固定状态口径

```text
GateK CI/security = FROZEN / ACCEPTED
Batch 5B-ENV = FROZEN / ACCEPTED
Batch 5B-SMOKE = FROZEN / ACCEPTED
NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED
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
