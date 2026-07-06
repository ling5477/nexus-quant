# NQ OKX Runtime Config Default Endpoint Defense — Post-Freeze Addendum

任务：NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-POST-FREEZE-ADDENDUM
日期：2026-06-22
状态：**FROZEN / ACCEPTED**
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

本卷宗是 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md` 的 post-freeze addendum，把 commit `c749cef7` 的 OKX runtime default endpoint sentinel 改动固化为 parent freeze 的 addendum，并关闭原 P2 backlog。本轮为 docs-only：未改代码、workflow、配置、测试。

## 1. Addendum object

OKX runtime config default endpoint defense（`OkxRuntimeConfig` 代码级默认 endpoint 改为非真实 `disabled://` sentinel）。

## 2. Addendum result

**FROZEN / ACCEPTED**。

## 3. Parent freeze

`NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED`（卷宗 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md`，freeze commit `8a2fbe4a`）。本 addendum 不推翻 parent freeze，只对其 §10 backlog / §11 regression boundary 范围内的 OKX runtime config 改动补充固化。

## 4. Original finding

P2 / non-blocking / defense-in-depth：`OkxRuntimeConfig` code-level real host defaults（`DEFAULT_BASE_URL=https://www.okx.com`、`DEFAULT_DOME_WS_PRIVATE_URL=wss://wspap.okx.com:8443/...`、`DEFAULT_REAL_WS_PRIVATE_URL=wss://ws.okx.com:8443/...`）not covered by startup `EnvSafetyValidator` endpoint validation —— 当 `NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL` 缺省时 `fromSystemEnv()` 取真实默认值，而启动期 guard 只校验已注入 env/property 值，看不见代码内部默认。

## 5. Fix commit

`c749cef7b9731284208acccadf321cf89c5e4fbe`（`fix(okx): replace runtime default endpoints with disabled sentinels`）。

修复路径：Path A（PLAN-REVIEW accepted）—— 从源头把默认 endpoint 改为非真实 `disabled://` sentinel；Path B（桥接进 EnvSafetyValidator）rejected。

## 6. CI evidence

- run ID：`27926903155`。
- workflow：`NQ CI Baseline`。
- event：`push`。
- branch：`dev`。
- headSha：`c749cef7b9731284208acccadf321cf89c5e4fbe`。
- status：`completed`。
- conclusion：`success`。
- 9 jobs all success：`diff-check` / `no-outbound-guard` / `ci-security-smoke` / `backend` / `postgres-flyway` / `frontend` / `frontend-no-backend-e2e` / `research` / `secret-scan`。
- URL：https://github.com/ling5477/nexus-quant/actions/runs/27926903155

## 7. Frozen sentinel values

- `DEFAULT_BASE_URL = "disabled://okx-not-configured"`。
- `DEFAULT_DOME_WS_PRIVATE_URL = "disabled://okx-ws-not-configured"`。
- `DEFAULT_REAL_WS_PRIVATE_URL = "disabled://okx-ws-not-configured"`。

## 8. Scope of change

- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java`（3 个默认常量 + Why 注释）。
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfigTest.java`（默认断言更新 + 新增 sentinel-default 用例）。
- `docs/current` only（PLAN / 本 addendum / parent freeze / CI baseline plan / README / STATUS / TESTING / WORKLOG）。

## 9. Explicitly unchanged

- `EnvSafetyValidator` unchanged。
- `EnvSafetyGuardConfiguration` unchanged。
- `NoOutboundExchangeGuardTest` unchanged。
- `NoRealExchangeCredentialPermissionProbePort` unchanged。
- `.github/workflows/ci.yml` unchanged。
- `application*.yml` unchanged。
- `.env.example` unchanged。
- migration unchanged（无新增、无历史修改）。
- frontend / research / scripts / deploy unchanged。

## 10. Test evidence

CI run `27926903155`（9 jobs success）+ 本地复核（2026-06-22）：

- `OkxRuntimeConfigTest` success（4/0/0/0，含新增 `shouldDefaultToNonRealSentinelEndpointsWhenEnvAbsent`）。
- `OkxExchangeAdapterBootstrapNoOutboundTest` success（1/0/0/0）。
- `NoRealExchangeCredentialPermissionProbePortTest` success（1/0/0/0）。
- `EnvSafetyValidatorTest` success（8/0/0/0）。
- `NoOutboundExchangeGuardTest` success（3/0/0/0）。
- `OkxBootstrapNoOutboundLocalContextTest` success（1/0/0/0，sentinel 默认未导致构造期失败 / 启动期 0 outbound）。
- full backend `mvn -f backend/pom.xml test` **BUILD SUCCESS**（0 fail / 0 error）。

## 11. Security conclusion

- code-level real OKX default host removed（默认常量已为非真实 sentinel）。
- explicit env override behavior preserved（`NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL` 及 dome/real 前缀仍覆盖默认）。
- no-real default boundary strengthened（真实 endpoint 仅显式 opt-in；请求期非法 scheme loud fail-closed，不命中真实 OKX）。
- no-outbound boundary not weakened（denylist / guard / EnvSafetyValidator 均未改）。
- no real credential read。
- no outbound call。

## 12. Backlog closure

- 原 P2（OkxRuntimeConfig default real endpoint defense）= **CLOSED / ACCEPTED**。
- **NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = FROZEN / ACCEPTED**。

## 13. Regression boundary

后续若改动以下任一对象，须重新 review + 采集 CI evidence + addendum/freeze，不得直接沿用本 addendum：

- `OkxRuntimeConfig` 默认值 / endpoint 解析。
- exchange adapter construction（`OkxExchangeAdapter` / `ExchangeAdapterConfiguration` / `OkxBootstrapFallbackFactory`）。
- `EnvSafetyValidator` / `EnvSafetyGuardConfiguration`。
- no-outbound guard（`ExchangeNoOutboundGuard` / `NoOutboundExchangeGuardTest` / denylist）。
- permission probe（`NoRealExchangeCredentialPermissionProbePort` / `AccountModuleConfiguration` 绑定）。
- CI env guard（`ci.yml` no-outbound-guard / ci-security-smoke job、forbidden env 名单）。
- profile defaults（`application*.yml` 的 LIVE/AI/DH/real-* / no-outbound / ws / recovery / catalog-sync 开关）。

## 14. Rollback

- revert 本 addendum docs commit → addendum status 回到 READY（CI GREEN / READY FOR POST-FREEZE ADDENDUM）。
- revert `c749cef7` → 恢复旧默认值（www.okx.com），用于 implementation rollback；显式 env 下 adapter 行为不变。
- 本 docs addendum 无 DB / runtime / credential / provider / exchange 副作用。

## 15. 固定状态口径

```text
GateK CI/security = FROZEN / ACCEPTED
NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED
NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = FROZEN / ACCEPTED
P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED
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
