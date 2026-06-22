# GateL-1B No-Real Hardening Plan

任务：NQ-GATEL-1B-NO-REAL-HARDENING-PLAN
日期：2026-06-22
分支：dev
结论：**PASS / PLAN READY FOR REVIEW**
状态：**PLANNING ONLY / NOT IMPLEMENTED**；P1 **OPEN / RETAINED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**；GateL implementation **NOT STARTED**。

> 本文只规划四项 P1 的最小 No-Real hardening 切片，不修代码，不授权 implementation。
> hardening 即使后续全部完成，也不等于 future-real-ready；真实交易所、LIVE、真实 credential、AI 与 DH runtime 继续禁止。

## 1. Task classification

- Primary：`DOCUMENTATION`。
- Auxiliary：`ARCHITECTURE_PLANNING`、`SECURITY_BOUNDARY_PLANNING`、`NO_REAL_HARDENING_PLAN`。
- Task level：L 级 planning-only / docs-only。
- Primary skill：`nq-dh-workflow-router`。
- Implementation skill：未使用；本轮禁止代码实现。

## 2. Scope

### 已检查

- `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md`。
- `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md`。
- `GATEL_PLAN.md` 与 current README/ROADMAP/STATUS/TESTING/WORKLOG。
- `BinanceRuntimeConfig.java`、`BinanceExchangeAdapter.java` 及相关 runtime config/adapter tests。
- `OkxRuntimeConfig.java`、`OkxExchangeAdapter.java` 及相关 runtime config/adapter tests。
- `AdapterOrderAck.java`、`AdapterOrderSnapshot.java`、`MarketDataSubscriptionAck.java`、`AdapterError.java`、`AdapterResultCategory.java`、`NoopMarketDataAdapter.java`。
- OKX/Binance adapter 中 `AdapterOrderAck` / `AdapterOrderSnapshot` 构造点。

### 明确不涉及

- Java / TypeScript / Python 实现、HTTP API、migration、workflow、frontend、research、scripts、deploy。
- `.env`、真实 credential、日志 dump、backup、key/certificate。
- 外网、真实交易所、数据库、容器、GitHub Actions。
- RealClient、real provider、真实 permission probe、LIVE、AI、DH runtime。

## 3. Current frozen baseline

- GateL-1 review fact baseline：**FROZEN / ACCEPTED**。
- GateL-1A freeze：**PASS / FROZEN / ACCEPTED**。
- 四项 P1、四项 P2：**OPEN / RETAINED**。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- GateL implementation：**NOT STARTED**。
- `LIVE = DISABLED`；`AI = NOT STARTED`；`DH runtime = NOT INTEGRATED`。
- RealClient / real provider / real permission probe：**NOT IMPLEMENTED**。

本计划不得改变上述状态。只有后续独立 implementation、验证、review 和 freeze 才能逐项关闭 P1。

## 4. Mandatory questions

| 问题 | 规划结论 |
| --- | --- |
| 四项 P1 最小顺序 | A endpoint → B credential source → C raw payload → D Noop semantics。先阻断默认网络和 credential acquisition，再收口数据传播与能力语义。 |
| 哪些可以合并 | A/B 技术上可合并为 runtime-boundary batch，但建议拆开；C/D 必须拆开。不得合成单个大 implementation。 |
| 哪些需要测试 | A/B/C/D 的未来实现全部需要单测/回归；文档同步只需文档检查。 |
| 是否需要 migration | **不需要。** 四项均不改变数据库 schema。 |
| 是否需要新增 API | **不需要。** 不新增 HTTP API；现有 `MarketDataSubscriptionAck + AdapterError` 足以表达 No-Real disabled。 |
| 是否允许真实交易所接入 | **不能。** |
| 是否允许启用 LIVE | **不能。** |
| 是否允许读取真实 credential | **不能。** |
| 是否允许接 AI / DH runtime | **不能。** |
| hardening 后能否直接进入 real adapter | **不能。** 仍需 capability contract、error contract、readiness checklist、安全审计、CI evidence/freeze、用户授权并另起 Gate。 |

若后续 implementation 发现必须新增 HTTP API、migration 或无法用现有 DTO 表达 No-Real，必须停止写操作并返回 plan-fix/review，不得自行扩大范围。

## 5. P1 hardening plan

### GateL-1B-A：Binance endpoint default hardening

**目标**

- Binance REST/WS 默认值改为非网络 `disabled://` sentinel 或等价不可路由值。
- 未经受信任、显式、默认关闭的 outbound safety decision，不得构造真实网络 endpoint。

**规划变更点**

- `BinanceRuntimeConfig.java`：
  - `DEFAULT_DOME_BASE_URL` / `DEFAULT_REAL_BASE_URL` 改为 `disabled://binance-not-configured` 或等价 sentinel。
  - `DEFAULT_DOME_WS_URL` / `DEFAULT_REAL_WS_URL` 改为 `disabled://binance-ws-not-configured` 或等价 sentinel。
  - `normalizeWsUrl` 不得在 blank/legacy URL 情况下回退到 testnet/mainnet。
  - 显式 endpoint override 必须受 central outbound safety decision 约束；该 decision 默认 false，不能仅以“设置了 URL”视为授权。
  - 不在本批次命名或实现 future-real enable 开关；若当前安全基线没有可复用 decision，implementation 必须停在 sentinel-only 并另起设计 review。
- `BinanceRuntimeConfigTest.java`：补默认 dome/real REST/WS 均为 sentinel 的断言。
- Binance adapter/bootstrap tests：证明默认构造不会解析出 HTTP/WSS endpoint、不会发生构造期 outbound。
- current docs：同步 Binance no-real default 事实，不能提前写 P1 closed。

**验收**

- 空环境、dome、real 三种默认配置均不含 Binance 外部 host。
- blank/legacy WS URL 不回退真实 host。
- 未取得显式 outbound safety decision 时，任何 HTTP/WSS endpoint override 都 fail-closed。
- 测试不访问网络；日志不输出 endpoint query、credential 或 headers。

**回滚**

- 仅还原 Binance runtime config、对应测试和 docs。回滚到旧真实默认值会重新打开 P1，必须立即恢复 NOT READY 状态，不得静默回滚。

### GateL-1B-B：Runtime credential source hardening

**目标**

- OKX/Binance default runtime config 与 default adapter construction 不再从进程环境读取真实 credential material。
- No-Real runtime 只允许 unconfigured/placeholder credential state，不持有真实 secret。

**规划变更点**

- `OkxRuntimeConfig.java` / `BinanceRuntimeConfig.java`：
  - 删除或禁用 default `fromSystemEnv/fromEnvironment` 对 API key/secret/passphrase/private key material 的解析。
  - runtime config 只保留非敏感 endpoint/timeout/reconnect 等 transport metadata；若暂时保留 credential 字段，必须固定为 unconfigured placeholder，并登记后续删除。
- `OkxExchangeAdapter.java` / `BinanceExchangeAdapter.java` default dependencies：只接收 unconfigured placeholder，private API 调用 fail-closed。
- Future-real credential 方式只冻结原则，不在 GateL-1B 实现：由 NQ credential governance 按 owner/account/tenant/credential type/active version/permission scope 解析短生命周期 handle；adapter 不读取环境、不选择 active version、不记录 material。
- 未来 governance bridge 必须另起 Gate 和安全审查，不属于 No-Real hardening implementation。

**测试**

- Runtime config tests 使用不可打印的 dummy marker map，断言 credential keys 被忽略且结果为 unconfigured；不得输出 marker。
- Default dependency tests 断言 private client 没有 configured credential，任何 private operation 在网络前失败。
- 日志/异常断言不得包含 API key、secret、passphrase、private key、signature 或完整 header/body。

**验收**

- 默认 No-Real 启动不读取、持有、打印或传播真实 credential material。
- account/tenant/active-version 不得由 adapter 猜测或从进程全局环境派生。
- 未实现 governance bridge 时，private operation 必须 fail-closed，不能 fallback 到环境变量。

**回滚**

- 独立回滚 OKX/Binance runtime credential source 和测试；任何恢复进程 credential parsing 的回滚都重新打开 P1，必须阻断合并。

### GateL-1B-C：rawPayload boundary hardening

**目标**

- Provider 原始响应不得通过 `AdapterOrderAck` / `AdapterOrderSnapshot` 进入 core、HTTP API、logs、audit、ledger 或 persistence。

**最小两阶段方案**

1. **Producer suppression（GateL-1B implementation 必做）**
   - OKX/Binance 所有 ack/snapshot producer 将 `rawPayload` 固定为 `null`，不得传 `JsonNode.toString()`、response body、headers、signature source 或 request body。
   - 诊断只允许 allowlisted sanitized metadata：platform error code、结果 category、retryable、traceId、HTTP status class；不得包含 provider body、query、credential 或 header。
2. **Contract removal（后续独立 contract change）**
   - 在全仓 consumer inventory 与编译回归证明无依赖后，删除 `rawPayload` record component/constructor parameter。
   - 删除属于 breaking internal contract change，必须独立 review；本 plan 不实施，也不新增 HTTP API。

**测试**

- OKX/Binance place/cancel/get/list-open-orders success、failure、timeout/query-confirm fixtures 均断言 raw payload 不跨 adapter boundary。
- 敏感字段 marker 测试断言 ack/snapshot/error/log 不包含 marker。
- 如删除字段，至少运行 adapter-api + OKX + Binance + 依赖模块编译/测试，并做 repository-wide consumer grep；不得机械修改而不审查语义。

**验收**

- 所有 adapter producer 的 ack/snapshot 不含 raw provider payload。
- API/audit/log/ledger/persistence 不出现 response body、credential、signature、headers 或完整 query。
- sanitized metadata 使用显式 allowlist，未知字段默认丢弃。

**回滚**

- Producer suppression 与字段删除必须分 commit；可以单独回滚字段删除，不得回滚 raw producer suppression。

### GateL-1B-D：Noop marketdata status hardening

**目标**

- Noop adapter 不能把占位行为表达为真实订阅成功。

**规划方案**

- 不新增 DTO、不新增 HTTP API、不在本批次扩展 `AdapterResultCategory`。
- 复用现有 `MarketDataSubscriptionAck`：
  - `subscribed=false`。
  - `AdapterError.code=NO_REAL_DISABLED`。
  - `AdapterError.category=FATAL_FAILURE`（临时映射；GateL-1D error contract 再决定专用 category）。
  - `retryable=false`。
  - message 仅说明 No-Real disabled，不含 endpoint 或配置值。
- bars/trades/orderbook 三条路径使用同一 helper，避免语义漂移。

**测试**

- 新增/补充 `NoopMarketDataAdapterTest`：三种订阅均 `subscribed=false`、code/category/retryable 一致、无副作用。
- 验证 venue/channel/traceId 仍可追踪，且没有网络 client 或异步资源创建。

**验收**

- 调用方能稳定区分真实订阅成功与 No-Real disabled。
- stub 不返回普通 success，不产生连接、线程、定时任务或网络副作用。
- 若现有调用方不能处理 `subscribed=false + AdapterError`，implementation 必须停止并转 plan-fix；不得新增 HTTP API 绕过。

**回滚**

- 独立还原 Noop adapter 与单测；回滚会重新打开能力误判 P1，不能与其他切片绑定回滚。

## 6. Implementation sequencing

推荐实施顺序与 review gate：

1. `GateL-1B-A-IMPL`：Binance endpoint default hardening。
2. `GateL-1B-A-IMPL-REVIEW`：验证 sentinel/no-outbound，P1-1 未经 review 不关闭。
3. `GateL-1B-B-IMPL`：runtime credential source hardening。
4. `GateL-1B-B-IMPL-REVIEW`：验证 no credential read/retain/log，P1-2 未经 review 不关闭。
5. `GateL-1B-C-IMPL`：raw producer suppression；字段删除另起 contract task。
6. `GateL-1B-C-IMPL-REVIEW`：验证跨层与敏感 marker，P1-3 未经 review 不关闭。
7. `GateL-1B-D-IMPL`：Noop status hardening。
8. `GateL-1B-D-IMPL-REVIEW`：验证 stub semantics，P1-4 未经 review 不关闭。
9. `GateL-1B-HARDENING-FREEZE`：只有 A-D 全部独立验证通过后，才可冻结 No-Real hardening 结果。

### 合并规则

- A/B 技术上都触及 runtime boundary，可在 plan review 后合并成一个 implementation batch；**本计划不推荐合并**，因为 A 只涉及 Binance endpoint，而 B 同时涉及 OKX/Binance credential ownership，风险和回滚不同。
- C 必须独立：影响 adapter-api 构造合同和所有 producer，blast radius 最大。
- D 必须独立：虽也在 adapter-api，但属于能力语义而非敏感数据传播，需单独调用方兼容性检查。
- 禁止 A-D 一次性实现，禁止把格式化、capability/error refactor、真实 provider、permission probe 混入。

## 7. Validation matrix for future implementation

| Slice | 必须测试 | 文档/合同检查 | 阻断条件 |
| --- | --- | --- | --- |
| A | Binance runtime config + adapter bootstrap/no-outbound tests | sentinel、explicit safety decision、回滚状态 | 任一默认外部 host；guard off 仍可构造网络 URI |
| B | OKX/Binance runtime config/default dependencies/private-operation fail-closed tests | credential ownership、脱敏、无 fallback | 读取/持有/打印进程 credential；private call 到达网络 |
| C | OKX/Binance ack/snapshot success/failure/timeout tests + sensitive marker | allowlist metadata、consumer inventory | raw body/header/query/signature 跨层；未知字段保留 |
| D | Noop bars/trades/orderbook tests | STUB/NO_REAL 语义与调用方兼容 | `subscribed=true`；无 error；调用方必须新增 HTTP API 才能处理 |

Future implementation 的最低命令由 implementation plan review 最终确认，至少包括相关 adapter 模块测试；涉及 adapter-api 构造签名时必须扩大到依赖模块编译/测试。不得使用真实外部服务。

## 8. Acceptance criteria

### Plan acceptance

- A-D 各自有目标、文件、测试、验收、回滚和禁止边界。
- 明确不需要 migration、不需要新增 HTTP API。
- 明确 P1 仍 OPEN、implementation NOT STARTED、adapter NOT READY。
- 后续只能先 plan review，不能直接 implementation。

### Hardening completion（未来，不是本轮状态）

- A-D implementation 与独立 review 全部通过。
- 默认 endpoint/credential/payload/stub semantics 全部 fail-closed。
- 本地/CI no-outbound、secret scan、redaction 和相关模块测试重新取证。
- P1 只能在对应 implementation review 中逐项关闭，最终由 hardening freeze 接受。
- 完成后仍不得标记 future-real-ready。

## 9. Forbidden boundaries

- 禁止真实 exchange endpoint、HTTP/WS 调用、下单、撤单、转账、提现。
- 禁止读取真实 credential 或把 credential 写入测试、日志、文档、artifact。
- 禁止 RealClient、real provider、真实 permission probe。
- 禁止 LIVE；禁止 AI；禁止 DH runtime。
- 禁止 API、migration、workflow、frontend、research、scripts、deploy 变更。
- 禁止在 plan review 前进入 implementation。
- 禁止把 A-D 计划写成已实施或把 P1 写成已关闭。

## 10. Findings

### P0

- 无。本轮 docs-only，没有 runtime 或数据写风险。

### P1

- 四项冻结 P1 均 **OPEN / RETAINED**；本计划为每项给出最小修复切片，但未修复任何一项。
- A/B 若错误合并，可能把 endpoint 与 credential ownership 风险耦合，故默认拆分。
- C producer suppression 与字段删除必须拆成两阶段，避免为了删除字段扩大无审查机械改动。

### P2

- 四项冻结 P2 不在 GateL-1B 修复；继续由 GateL-1C capability、GateL-1D error 与后续 architecture contract 处理。
- `NO_REAL_DISABLED -> FATAL_FAILURE` 是 1B-D 的临时兼容映射，不代表 error contract 已冻结。

## 11. Commands run

- `Get-Location`、`git status --short`、`git branch --show-current`。
- `Get-Content -Raw`：任务附件、router skill、review/freeze/plan/current docs。
- 限定 adapter-api/OKX/Binance 的 `rg -n`、`rg --files`：runtime defaults、credential parsing、raw payload producers、Noop DTO/error 表达能力和相关测试。
- 文档完成后执行 link、scope、stage wording、P1 status、secret pattern、`git diff --check`。

## 12. Recommended next task

**NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW**。

下一轮只读复核本计划是否可作为 A-D implementation 的分批基线；不得直接执行 A/B/C/D implementation。

## 13. Rollback

删除本文件，并还原 `GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的 GateL-1B plan 条目即可。无代码、workflow、DB、credential、provider、交易或 runtime 副作用。

## 14. Final recommendation

**NQ-GATEL-1B-NO-REAL-HARDENING-PLAN：PASS / PLAN READY FOR REVIEW。**

接受 A → B → C → D 的分批 planning baseline，下一步仅允许 plan review。P1/P2 继续 OPEN；adapter readiness 继续 NOT READY / NOT FROZEN / NOT AUTHORIZED。

## 15. GateL-1B-A implementation update（2026-06-22）

> 本节为实现进度追加，不改写上文 frozen plan 正文；上文 planning baseline 仍为冻结基线。

- 任务：`NQ-GATEL-1B-A-IMPL` = **PASS / IMPLEMENTED；PENDING `NQ-GATEL-1B-A-IMPL-REVIEW`**。
- 仅实现 **P1-A**（Binance endpoint default sentinel / no-outbound hardening）；未夹带 B/C/D，未接真实交易所，未启用 LIVE，未读取 credential，未外联。
- 实现：
  - `BinanceRuntimeConfig`：`DEFAULT_BASE_URL=disabled://binance-not-configured`、`DEFAULT_WS_URL=disabled://binance-ws-not-configured`（dome/real 共用）；删除 `DEFAULT_DOME_BASE_URL`/`DEFAULT_REAL_BASE_URL`/`DEFAULT_DOME_WS_URL`/`DEFAULT_REAL_WS_URL` 四个真实 host 默认常量；`normalizeWsUrl` 移除 blank/legacy → testnet/mainnet 回退，仅去尾部 `/`。
  - `BinanceWsProtocol.resolveUserDataWsApiUrl`（WS 连接路径实际解析点，原计划 §5 仅列出 `normalizeWsUrl`，但该方法属同一 P1-A endpoint 边界且会在 guard 关闭时构造真实网络 URI，故一并 harden）：blank → no-real sentinel，移除 legacy stream → 真实 ws-api host 的静默改写，删除四个真实 host 常量并去掉 `envName` 入参；`BinanceWsClient` 3 处调用同步。
- 验收对照（plan §5 GateL-1B-A）：空环境/dome/real 默认均为 sentinel 且不含 binance host ✓；blank/legacy WS 不回退真实 host ✓；`disabled://` 请求期 loud fail-closed（REST `HttpRequest.Builder.uri()` / WS `WebSocket.Builder.buildAsync()` 对非 http(s)/ws(s) scheme 抛 `IllegalArgumentException`）、构造期与默认配置不外联 ✓；测试不访问网络、日志不输出 endpoint query/credential/headers ✓。
- 测试：`BinanceRuntimeConfigTest`、`BinanceWsProtocolTest`、`BinanceWsClientTest` 更新 + 新增 `BinanceNoRealEndpointHardeningTest`；`mvn -f backend/pom.xml -o -pl nq-adapter-binance -am test` BUILD SUCCESS（50 / 0 / 0 / 1 skipped）。
- 状态保持：**P1-A IMPLEMENTED / PENDING REVIEW（未经 `NQ-GATEL-1B-A-IMPL-REVIEW` 不正式关闭）**；**P1-B / P1-C / P1-D 仍 OPEN / RETAINED**；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**；本节不代表允许真实 Binance 接入或 future-real-ready。
- 回滚：还原 `BinanceRuntimeConfig` / `BinanceWsProtocol` / `BinanceWsClient` 与四个测试文件（删除 `BinanceNoRealEndpointHardeningTest`），并还原本轮 current docs。回滚到旧真实默认会重新打开 P1-A，须立即恢复 NOT READY 状态，不得静默回滚。

## 16. GateL-1B-A freeze-close（2026-06-22）

> 本节为 freeze-close 追加，不改写上文 frozen plan 正文。

- 任务：`NQ-GATEL-1B-A-IMPL-FREEZE` = **PASS / FROZEN / ACCEPTED**，详见 `GATEL_1B_A_IMPL_FREEZE_REVIEW.md`。
- 冻结对象：implementation commit `04ddb774`（`feat(adapter-binance): default endpoints to no-real sentinel`），`git show --check` / `git diff --check HEAD^ HEAD` 无 whitespace，`mvn -f backend/pom.xml -o -pl nq-adapter-binance -am test` BUILD SUCCESS（50 / 0 / 0 / 1 skipped），main src `git grep` 无 testnet/mainnet 默认 host。
- **P1-A = CLOSED / ACCEPTED**（Binance endpoint default sentinel / no-outbound hardening frozen）；**P1-B / P1-C / P1-D 仍 OPEN / RETAINED**；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**；不代表允许真实 Binance 接入或 future-real-ready。
- GateL-1B 整体 No-Real hardening freeze **NOT DONE**，待 B/C/D 全部独立完成后另行执行。
- Regression boundary：后续改动 `BinanceRuntimeConfig` 默认 endpoint / `normalizeWsUrl` / `BinanceWsProtocol.resolveUserDataWsApiUrl` / `BinanceWsClient` WS endpoint 解析，须重新 review + freeze。
- 下一步 `NQ-GATEL-1B-B-IMPL`。
