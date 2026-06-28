# GateL-1B-A Implementation Freeze Review

任务：NQ-GATEL-1B-A-IMPL-FREEZE
日期：2026-06-22
分支：dev
结论：**PASS / FROZEN / ACCEPTED**
状态：**P1-A CLOSED / ACCEPTED（Binance endpoint default sentinel / no-outbound hardening 已冻结）**；P1-B / P1-C / P1-D **OPEN / RETAINED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**；GateL-1B No-Real hardening 整体 freeze **NOT DONE（待 B/C/D）**。

> 本卷宗只冻结 GateL-1B-A（四项 P1 中的 P1-A）实现与 review 证据，并正式关闭 P1-A。
> 冻结不代表 future-real-ready，不代表允许真实 Binance 接入；真实交易所、LIVE、真实 credential、AI、DH runtime 继续禁止。
> P1-B / P1-C / P1-D 未在本轮处理，保持 OPEN / RETAINED；GateL-1B 整体 No-Real hardening freeze 仍待 B/C/D 全部独立完成。

## 1. Task classification

- Primary：`IMPLEMENTATION_FREEZE`（per-slice freeze-close）。
- Auxiliary：`DOCUMENTATION_REVIEW`、`SECURITY_BOUNDARY_REVIEW`、`NO_REAL_HARDENING_FREEZE`。
- Task level：GateL-1B-A freeze-close（docs-only freeze；不实现新代码）。
- Primary skill：`nq-dh-workflow-router`（任务分类与 Gate 边界检查）。

## 2. Scope

### 冻结对象

- GateL-1B-A implementation commit `04ddb774`（`feat(adapter-binance): default endpoints to no-real sentinel`）的 Binance endpoint default sentinel / no-outbound hardening 实现与测试。
- 关联 review 证据：`NQ-GATEL-1B-A-IMPL` = PASS、`NQ-GATEL-1B-A-IMPL-REVIEW` = PASS / APPROVED FOR COMMIT。

### 明确不涉及

- P1-B（OKX/Binance process credential source）、P1-C（AdapterOrderAck/Snapshot rawPayload）、P1-D（NoopMarketDataAdapter status）。
- 真实交易所、LIVE、真实 credential、AI、DH runtime、RealClient、real provider、真实 permission probe。
- Java/TS/Python 代码、API、DTO、migration、workflow、frontend、research、scripts、deploy。本轮 docs-only。

## 3. Files inspected（只读）

- `backend/nq-adapter-binance/src/main/java/.../service/BinanceRuntimeConfig.java`（@HEAD）。
- `backend/nq-adapter-binance/src/main/java/.../ws/BinanceWsProtocol.java`（@HEAD）。
- `backend/nq-adapter-binance/src/main/java/.../ws/BinanceWsClient.java`（@HEAD）。
- `backend/nq-adapter-binance/src/test/java/.../service/BinanceRuntimeConfigTest.java`、`BinanceNoRealEndpointHardeningTest.java`（@HEAD）。
- `backend/nq-adapter-binance/src/test/java/.../ws/BinanceWsProtocolTest.java`、`BinanceWsClientTest.java`（@HEAD）。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`（§15 实现进度）、`GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

## 4. Commands run（只读 / offline）

- `git status --short`（clean）、`git branch --show-current`（dev）、`git log --oneline -5`。
- `git show --stat --oneline HEAD`（14 文件，全部在 GateL-1B-A 允许范围）。
- `git show --check HEAD`（无 whitespace 错误）、`git diff --check HEAD^ HEAD`（无 whitespace 错误）。
- `git grep -nE "testnet\.binance|binance\.com|stream\.binance" HEAD -- backend/nq-adapter-binance/src/main` → **NONE**。
- 禁止路径扫描（OKX/adapter-api/frontend/research/scripts/deploy/workflow/migration）→ **NONE**。
- `git show HEAD:...BinanceRuntimeConfig.java` / `...BinanceWsProtocol.java` 确认 sentinel 常量与 hardened 解析点在提交内。
- `mvn -f backend/pom.xml -o -pl nq-adapter-binance -am test`（offline，未外联）→ **BUILD SUCCESS**。

## 5. Freeze verdict

**PASS / FROZEN / ACCEPTED。** GateL-1B-A 实现已提交（`04ddb774`），工作区 clean，提交范围仅含允许文件，sentinel / no-outbound hardening 与测试在 HEAD 完整且 offline 测试通过，review 已 APPROVED。满足 freeze-close 条件，正式关闭 P1-A。

## 6. Frozen implementation facts（commit `04ddb774`）

- **Binance REST 默认 endpoint = `disabled://binance-not-configured`**（`BinanceRuntimeConfig.DEFAULT_BASE_URL`，dome/real 共用）。
- **Binance WS 默认 endpoint = `disabled://binance-ws-not-configured`**（`BinanceRuntimeConfig.DEFAULT_WS_URL`，dome/real 共用）。
- 删除 4 个真实 host 默认常量 `DEFAULT_DOME/REAL_BASE_URL`、`DEFAULT_DOME/REAL_WS_URL`。
- `BinanceRuntimeConfig.normalizeWsUrl(String)`：blank → sentinel；显式配置仅去尾部 `/`；移除 blank/legacy → testnet/mainnet 回退。
- `BinanceWsProtocol.resolveUserDataWsApiUrl(String)`（WS 连接路径实际解析点）：blank → `BinanceRuntimeConfig.DEFAULT_WS_URL`；移除 legacy stream → 真实 ws-api host 静默改写；去掉 `envName` 入参。
- `BinanceWsClient` 3 处调用同步为单参签名。
- `disabled://` 请求期 loud fail-closed：REST 经 `HttpRequest.Builder.uri()`、WS 经 `WebSocket.Builder.buildAsync()` 对非 http(s)/ws(s) scheme 抛 `IllegalArgumentException`，不触达网络。
- 显式 env override（`NQ_BINANCE_<DOME|REAL>_BASE_URL` / `_WS_URL`）行为不变，真实 endpoint 仅显式 opt-in。
- `BinanceWsClient` 作为 Spring bean 构造惰性，无 `.start()` auto-invoke / 无 lifecycle 钩子，构造期不外联。

## 7. Validation

| 项 | 证据 | 结果 |
| --- | --- | --- |
| 提交范围 | `git show --stat HEAD` = 7 adapter（6 改 + 1 新增测试）+ 7 docs | 仅 GateL-1B-A 允许范围 ✓ |
| whitespace | `git show --check HEAD` / `git diff --check HEAD^ HEAD` | 无错误 ✓ |
| REST 默认 sentinel | `DEFAULT_BASE_URL="disabled://binance-not-configured"` @HEAD | ✓ |
| WS 默认 sentinel | `DEFAULT_WS_URL="disabled://binance-ws-not-configured"` @HEAD | ✓ |
| blank override 不回退 | `normalizeWsUrl`/`resolveUserDataWsApiUrl` blank → sentinel；测试覆盖 | ✓ |
| main 无真实默认 host | `git grep` @HEAD main = NONE | ✓ |
| WsProtocol hardened | `resolveUserDataWsApiUrl` 单参 + sentinel + 无 legacy 改写 | ✓ |
| WsClient 同步 | 3 处单参调用 | ✓ |
| fail-closed no-outbound | `BinanceNoRealEndpointHardeningTest` REST `IllegalArgumentException` 不外联 | ✓ |
| Maven offline | `mvn -o -pl nq-adapter-binance -am test` BUILD SUCCESS | 50 / 0 fail / 0 error / 1 skipped ✓ |

skipped = `BinanceWsClientLiveDiagnosticTest`（`-Dnq.binance.ws.live.diagnostic` 系统属性门禁，默认不执行，不连真实 Binance）。

## 8. P1 status

- **P1-A：CLOSED / ACCEPTED**（GateL-1B-A Binance endpoint default sentinel / no-outbound hardening frozen，commit `04ddb774`）。
- **P1-B：OPEN / RETAINED**（OKX/Binance process credential source，未修）。
- **P1-C：OPEN / RETAINED**（AdapterOrderAck / AdapterOrderSnapshot rawPayload，未修）。
- **P1-D：OPEN / RETAINED**（NoopMarketDataAdapter 普通 success，未修）。

## 9. Adapter readiness verdict

**NOT READY / NOT FROZEN / NOT AUTHORIZED。** P1-A 关闭只是把 Binance 默认 endpoint 收口为 no-real sentinel，不改变 adapter readiness。real adapter 仍需 capability contract、error contract、readiness checklist、专项安全审计、CI evidence/freeze 与用户显式授权并另起 Gate。本卷宗不得被引用为允许真实 Binance 接入或 future-real-ready 的依据。

## 10. Forbidden boundaries（本轮遵守）

- 未修改 Java/TS/Python 代码；未新增 API / DTO / migration / workflow；未改 frontend / research / scripts / deploy。
- 未读取 `.env` / 真实 credential；未访问外网；未调用任何交易所。
- 未启用 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider / 真实 permission probe；未下单/撤单/转账。
- 未修 P1-B/C/D；未把 adapter 标记 future-real-ready；未把 hardening 写成允许真实 Binance 接入。

## 11. Regression boundary

后续若改动以下任一，须重新 review + 重新 freeze（addendum 或新 freeze），不得静默并入本 freeze：

- `BinanceRuntimeConfig` 默认 endpoint 常量 / `normalizeWsUrl` 解析。
- `BinanceWsProtocol.resolveUserDataWsApiUrl` 的 blank/sentinel/fallback 行为。
- `BinanceWsClient` WS endpoint 解析或连接构造路径。
- 任何把 Binance 默认 endpoint 改回真实 host 或新增 future-real enable switch 的改动。

回滚到旧真实默认会重新打开 P1-A，须立即把 P1-A 恢复为 OPEN 并恢复 adapter NOT READY 状态。

## 12. Rollback

- `git revert 04ddb774`（或还原 `BinanceRuntimeConfig` / `BinanceWsProtocol` / `BinanceWsClient` 与四个测试文件、删除 `BinanceNoRealEndpointHardeningTest`），并还原本轮 current docs 与本 freeze 卷宗。
- 回滚使 P1-A 重新 OPEN；显式 env override 下行为不变；无 runtime/DB/credential/provider/exchange 副作用。

## 13. Recommended next task

**NQ-GATEL-1B-B-IMPL**（runtime credential source hardening；OKX/Binance default runtime config / default adapter construction 不再从进程环境读取真实 credential material），按冻结顺序 A → B → C → D 推进。GateL-1B 整体 No-Real hardening freeze 须待 B/C/D 全部独立完成后另行执行。

## 14. Final recommendation

**NQ-GATEL-1B-A-IMPL-FREEZE：PASS / FROZEN / ACCEPTED。** P1-A 正式 CLOSED / ACCEPTED；P1-B/C/D 保持 OPEN / RETAINED；adapter readiness 保持 NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE DISABLED、AI NOT STARTED、DH runtime NOT INTEGRATED、RealClient/real provider/real permission probe NOT IMPLEMENTED。下一步 `NQ-GATEL-1B-B-IMPL`，本轮不进入。
