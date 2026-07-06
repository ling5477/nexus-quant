# GateL-1B-C Implementation Freeze Review

任务：NQ-GATEL-1B-C-IMPL-FREEZE
日期：2026-06-22
分支：dev
结论：**PASS / FROZEN / ACCEPTED**
状态：**P1-C producer suppression CLOSED / ACCEPTED（OKX/Binance AdapterOrderAck / AdapterOrderSnapshot rawPayload producer suppression 已冻结）**；P1-A **CLOSED / ACCEPTED**；P1-B **CLOSED / ACCEPTED**；P1-C rawPayload 字段删除 **NOT DONE / SEPARATE COMPATIBILITY TASK**；P1-D **OPEN / RETAINED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**；GateL-1B No-Real hardening 整体 freeze **NOT DONE（待 D）**。

> 本卷宗只冻结 GateL-1B-C producer suppression 实现与 review 证据，并正式关闭 P1-C producer suppression。
> 冻结不删除 `AdapterOrderAck` / `AdapterOrderSnapshot` 的 `rawPayload` 字段，不修改 `nq-adapter-api`，不代表 future-real-ready，不代表允许真实 OKX/Binance 接入。
> P1-D 未在本轮处理，保持 OPEN / RETAINED；GateL-1B overall hardening 仍待 D 独立完成。

## 1. Task classification

- Primary：`IMPLEMENTATION_FREEZE`（per-slice freeze-close）。
- Auxiliary：`DOCUMENTATION_REVIEW`、`RAW_PAYLOAD_BOUNDARY_FREEZE`、`SECURITY_BOUNDARY_REVIEW`。
- Task level：GateL-1B-C freeze-close（docs-only freeze；不实现新代码）。
- Primary skill：`nq-dh-workflow-router`（任务分类与 Gate 边界检查）。

## 2. Scope

### 冻结对象

- GateL-1B-C implementation commit `316497ad`（`feat(adapter-okx,adapter-binance): suppress order rawPayload producers`）的 OKX/Binance order ack/snapshot rawPayload producer suppression 实现与测试。
- 关联 review 证据：`NQ-GATEL-1B-C-IMPL` = PASS，`NQ-GATEL-1B-C-IMPL-REVIEW` = PASS / APPROVED FOR COMMIT。

### 明确不涉及

- `rawPayload` record component 删除（NOT DONE，另起兼容性任务）。
- P1-D（NoopMarketDataAdapter 普通 success 语义）。
- `nq-adapter-api`、API、DTO、migration、workflow、frontend、research、scripts、deploy。
- 真实交易所、LIVE、真实 credential、真实 credential governance bridge、AI、DH runtime、RealClient、real provider、真实 permission probe。

## 3. Files inspected（只读）

- `backend/nq-adapter-okx/src/main/java/.../service/OkxExchangeAdapter.java`（@HEAD）。
- `backend/nq-adapter-binance/src/main/java/.../service/BinanceExchangeAdapter.java`（@HEAD）。
- `backend/nq-adapter-okx/src/test/java/.../service/OkxExchangeAdapterRawPayloadSuppressionTest.java`（@HEAD）。
- `backend/nq-adapter-binance/src/test/java/.../service/BinanceExchangeAdapterTest.java`（@HEAD）。
- `backend/nq-adapter-api/src/main/java/.../model/AdapterOrderAck.java`、`AdapterOrderSnapshot.java`（只读确认字段仍保留）。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`、`GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

## 4. Commands run（只读 / offline）

- `git status --short`（仅本轮允许的 docs/current freeze-close 文档变更；freeze review 文件处于未跟踪待提交状态）、`git branch --show-current`（dev）、`git log --oneline -5`。
- `git show --stat --oneline HEAD`（commit `316497ad`；11 files changed，全部在 GateL-1B-C 允许范围）。
- `git show --check HEAD`（无 whitespace 错误）、`git diff --check HEAD^ HEAD`（无 whitespace 错误）。
- `git grep` / `rg`：确认 OKX/Binance ack/snapshot producer 均使用 `suppressedOrderRawPayload()`，helper 返回 `null`。
- `git grep`：确认 `AdapterOrderAck` / `AdapterOrderSnapshot` 的 `rawPayload` 字段仍保留，`backend/nq-adapter-api` 无 diff。
- `git grep`：确认 P1-A Binance `disabled://` sentinel 未回退；P1-B OKX/Binance `*.unconfigured()` credential baseline 未回退。
- 禁止路径扫描：`backend/nq-adapter-api`、workflow、migration、frontend、research、scripts、deploy diff = NONE。
- `mvn -f backend/pom.xml -o -pl nq-adapter-okx,nq-adapter-binance -am test`（offline，未外联）→ **BUILD SUCCESS**。

## 5. Freeze verdict

**PASS / FROZEN / ACCEPTED。** GateL-1B-C implementation 已提交（`316497ad`），提交范围符合 allowlist，review 已 PASS / APPROVED FOR COMMIT，OKX/Binance `AdapterOrderAck` / `AdapterOrderSnapshot` producer 不再传播 provider raw response，测试覆盖 success 与 error snapshot suppression，offline Maven 通过，P1-A/P1-B 回归未破坏，adapter-api 未改。满足 freeze-close 条件，正式关闭 P1-C producer suppression。

## 6. Frozen implementation facts（commit `316497ad`）

- `OkxExchangeAdapter`：place ack、query-confirm ack、order snapshot、error snapshot 的 `rawPayload` 参数统一为 `suppressedOrderRawPayload()`。
- `BinanceExchangeAdapter`：place ack、query-confirm ack、order snapshot、error snapshot 的 `rawPayload` 参数统一为 `suppressedOrderRawPayload()`。
- `suppressedOrderRawPayload()` 返回 `null`，并用注释明确：保留 adapter-api 字段只是兼容性措施，不允许 provider full body、headers、signature 或异常诊断文本继续传给 core/API/audit。
- `AdapterOrderAck` / `AdapterOrderSnapshot` 的 `rawPayload` record component 仍保留；字段删除 NOT DONE，另起兼容性任务。
- Provider full body / full headers / request body / response body / API key / secret / passphrase / private key / signature / auth header / set-cookie 不进入 ack/snapshot `rawPayload`。
- `OkxExchangeAdapterRawPayloadSuppressionTest` 使用本地 mock server + provider marker 覆盖 OKX place ack、get snapshot、list snapshot、error snapshot。
- `BinanceExchangeAdapterTest` 使用 provider marker 覆盖 Binance place ack、get snapshot、list snapshot。
- `BinanceExchangeAdapter` 中 `AdapterTradeReport` 的 `item.toString()` 仍存在；该点不属于本轮 `AdapterOrderAck` / `AdapterOrderSnapshot` P1-C 范围，作为后续独立 P2 follow-up 保留。

## 7. Validation

| 项 | 证据 | 结果 |
| --- | --- | --- |
| 提交范围 | `git show --stat HEAD` = 3 adapter main/test 改 + 1 OKX 新增测试 + 7 docs | 仅 GateL-1B-C 允许范围 ✓ |
| whitespace | `git show --check HEAD` / `git diff --check HEAD^ HEAD` | 无错误 ✓ |
| OKX ack producer | `git grep` @HEAD lines 429 / 458 / 566 | `suppressedOrderRawPayload()` ✓ |
| OKX snapshot producer | `git grep` @HEAD lines 615 / 1018 | `suppressedOrderRawPayload()` ✓ |
| Binance ack producer | `git grep` @HEAD lines 301 / 330 / 407 | `suppressedOrderRawPayload()` ✓ |
| Binance snapshot producer | `git grep` @HEAD lines 433 / 630 | `suppressedOrderRawPayload()` ✓ |
| helper return | `suppressedOrderRawPayload()` returns `null` | ✓ |
| adapter-api 字段保留 | `git grep` @HEAD AdapterOrderAck / AdapterOrderSnapshot | `rawPayload` component still present ✓ |
| adapter-api 未修改 | `git diff --name-only HEAD^ HEAD -- backend/nq-adapter-api` | NONE ✓ |
| P1-A sentinel | `git grep disabled://binance-not-configured / disabled://binance-ws-not-configured` | present ✓ |
| P1-B unconfigured | `git grep *.unconfigured()` | present ✓ |
| 禁止路径 | workflow / migration / frontend / research / scripts / deploy diff | NONE ✓ |
| Maven offline | `mvn -f backend/pom.xml -o -pl nq-adapter-okx,nq-adapter-binance -am test` | BUILD SUCCESS；OKX 34 / Binance 51（0 fail / 0 error / 1 skipped）✓ |

skipped = `BinanceWsClientLiveDiagnosticTest`（系统属性门禁，默认不执行，不连真实 Binance）。

## 8. P1 status

- **P1-A：CLOSED / ACCEPTED**（Binance endpoint default sentinel / no-outbound frozen，commit `04ddb774`；本轮回归未破坏）。
- **P1-B：CLOSED / ACCEPTED**（OKX/Binance runtime credential source hardening frozen，commit `ad7f58b0`；本轮回归未破坏）。
- **P1-C producer suppression：CLOSED / ACCEPTED**（本卷宗冻结 commit `316497ad`）。
- **P1-C rawPayload field deletion：NOT DONE / SEPARATE COMPATIBILITY TASK**。
- **P1-D：OPEN / RETAINED**（NoopMarketDataAdapter 普通 success，未修）。

## 9. Findings / follow-up

### P0

- 无。

### P1

- 无。

### P2

- Binance `AdapterTradeReport` 仍使用 `item.toString()` 作为 trade report raw payload（`BinanceExchangeAdapter.java:241`）。该点不属于本轮 `AdapterOrderAck` / `AdapterOrderSnapshot` producer suppression，不阻断本 freeze；后续若收口 trade report raw payload，应另起独立边界任务。

## 10. Adapter readiness verdict

**NOT READY / NOT FROZEN / NOT AUTHORIZED。** P1-C producer suppression 关闭只证明 OKX/Binance order ack/snapshot 不再跨层传播 provider raw response；它不删除 `rawPayload` 字段，不修 P1-D，不补 capability/error/readiness contracts，不授权真实交易所、LIVE、真实 credential、AI 或 DH runtime。本卷宗不得被引用为 OKX/Binance future-real-ready 或允许真实接入的依据。

## 11. Forbidden boundaries（本轮遵守）

- 未修改 Java/TS/Python 代码；未删除 `rawPayload` 字段；未修改 `nq-adapter-api`。
- 未新增 API / DTO / migration / workflow；未改 frontend / research / scripts / deploy。
- 未读取 `.env` / 真实 credential；未访问外网；未调用任何交易所。
- 未启用 LIVE；未接 AI；未接 DH runtime；未实现 RealClient / real provider / 真实 permission probe / real credential governance bridge；未下单/撤单/转账。
- 未修 P1-D；未把 adapter 标记 future-real-ready；未把 hardening 写成允许真实 OKX/Binance 接入。

## 12. Regression boundary

后续若改动以下任一，须重新 review + 重新 freeze（addendum 或新 freeze），不得静默并入本 freeze：

- `OkxExchangeAdapter` / `BinanceExchangeAdapter` 中 `AdapterOrderAck` / `AdapterOrderSnapshot` producer 的 rawPayload 参数。
- `suppressedOrderRawPayload()` 返回值或注释约束。
- `AdapterOrderAck` / `AdapterOrderSnapshot` constructor / record component 兼容性。
- 任何把 provider full body、headers、signature、request/response body、credential-like value 放入 ack/snapshot rawPayload 的改动。

回滚到 provider raw response 传播会重新打开 P1-C，须立即恢复 adapter NOT READY 状态并阻断合并。

## 13. Rollback

- `git revert 316497ad`，并还原本轮 current docs 与本 freeze 卷宗。
- 回滚使 P1-C producer suppression 重新 OPEN；P1-A/P1-B 既有 freeze 不受影响；无 DB/runtime/provider/exchange 副作用。

## 14. Recommended next task

**NQ-GATEL-1B-D-IMPL**（NoopMarketDataAdapter no-real status hardening），只处理 P1-D；不得删除 rawPayload 字段，不得进入 real adapter。GateL-1B overall hardening freeze 须待 D 独立 implementation/review/freeze 后另行执行。

## 15. Final recommendation

**NQ-GATEL-1B-C-IMPL-FREEZE：PASS / FROZEN / ACCEPTED。** P1-C producer suppression 正式 CLOSED / ACCEPTED；P1-A/P1-B 保持 CLOSED / ACCEPTED；P1-C rawPayload 字段删除 NOT DONE；P1-D 保持 OPEN / RETAINED；adapter readiness 保持 NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE DISABLED、AI NOT STARTED、DH runtime NOT INTEGRATED、RealClient/real provider/real permission probe/real credential governance bridge NOT IMPLEMENTED。下一步 `NQ-GATEL-1B-D-IMPL`，本轮不进入。
