# GateL-1A Exchange Adapter Contract Review Freeze

任务：NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE
日期：2026-06-22
分支：dev
结论：**PASS**
状态：**GATEL-1 REVIEW FACT BASELINE = FROZEN / ACCEPTED**；P1/P2 **OPEN / RETAINED**；adapter readiness **NOT READY / NOT FROZEN**；GateL implementation **NOT STARTED**。

> 本冻结对象是 `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md` 已确认的事实、P1/P2 和处理顺序。
> 本冻结不代表 adapter 已可接真实交易所，不代表 P1 已修复，也不授权代码实现、真实 provider、RealClient、LIVE、AI 或 DH runtime。

## 1. Task classification

- Primary：`DOCUMENTATION`。
- Auxiliary：`ARCHITECTURE_REVIEW`、`SECURITY_BOUNDARY_REVIEW`、`GATEL_BASELINE_FREEZE`。
- Task level：L 级 freeze-review / docs-only。
- Primary skill：`nq-dh-workflow-router`，用于固定 GateL canonical、安全边界与禁止范围。
- Implementation skill：未使用；本轮不允许代码实现。

## 2. Scope

### 已审查

- `docs/current/GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md`。
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。
- P1 定向证据：
  - `backend/nq-adapter-binance/.../BinanceRuntimeConfig.java`、`BinanceExchangeAdapter.java`。
  - `backend/nq-adapter-okx/.../OkxRuntimeConfig.java`、`OkxExchangeAdapter.java`。
  - `backend/nq-adapter-api/.../AdapterOrderAck.java`、`AdapterOrderSnapshot.java`、`NoopMarketDataAdapter.java`。

### 未审查 / 不涉及

- 未扫描其他 backend 模块；未审查 composition root、scheduler、infra wiring。
- 未读取 `.env`、credential material、日志 dump、backup、key/certificate。
- 未访问外网、交易所、数据库、容器或 GitHub。
- 未修改 Java / TypeScript / Python、API、migration、workflow、frontend、research、scripts、deploy。

## 3. Freeze verdict

**GateL-1 review 可以作为事实基线冻结，结论为 PASS。**

冻结成立的理由：

1. review 对四项 P1 的描述均可由当前允许范围内源码直接复核。
2. P1/P2 分级与 No-Real canonical、安全边界一致。
3. review 明确区分“review 交付完成”与“adapter readiness 完成”。
4. review 没有授权真实 endpoint、真实 credential、真实 permission probe、LIVE、AI 或 DH runtime。
5. 后续可以按独立、小步、可审查任务处理开放项，不需要在 freeze-review 中修改代码。

冻结语义：**事实与风险描述不再漂移；开放问题继续保持 OPEN。** 后续若源码、配置、adapter contract 或 GateK 安全边界发生变化，必须重新 review，并以 addendum 或重新 freeze 更新基线。

## 4. Frozen facts

1. GateL canonical = **No-Real Exchange / MarketData Readiness**。
2. GateL-1 implementation = **NOT STARTED**。
3. OKX/Binance adapter 含 legacy network-capable REST/WS 代码，不能整体描述为纯 NoReal stub。
4. OKX 默认 endpoint 已使用 `disabled://` sentinel。
5. Binance 默认 REST/WS endpoint 仍指向 testnet/mainnet 外部 host。
6. OKX/Binance runtime config 直接解析进程 credential，尚未绑定 NQ credential governance/account/tenant/active version。
7. `AdapterOrderAck` / `AdapterOrderSnapshot` 暴露 `rawPayload`。
8. `NoopMarketDataAdapter` 对订阅返回普通 success，缺少 `STUB / NO_REAL` 标记。
9. 当前 adapter **不得标记为 future-real-ready**；adapter readiness = **NOT READY / NOT FROZEN**。
10. 真实交易所接入继续禁止；`LIVE = DISABLED`；`AI = NOT STARTED`；`DH runtime = NOT INTEGRATED`；RealClient / real provider / real permission probe = NOT IMPLEMENTED。

## 5. P1 findings retained

### P1-1 Binance default endpoint

- 状态：**OPEN / RETAINED**。
- 证据：`BinanceRuntimeConfig.java:48-51` 硬编码 testnet/mainnet REST/WS URL；`:77-78` 按环境选择这些默认值。
- 冻结结论：属于 GateL-1B No-Real hardening plan 的 P1；testnet 也是外部网络，不能作为 No-Real 默认值。

### P1-2 Process credential parsing

- 状态：**OPEN / RETAINED**。
- 证据：`OkxRuntimeConfig.java:59-97`、`OkxExchangeAdapter.java:849-881`；`BinanceRuntimeConfig.java:63-100`、`BinanceExchangeAdapter.java:550-568`。
- 冻结结论：属于 GateL-1B P1；future-real 前必须规划 account/tenant/active-version-bound credential handle，adapter 不得自行拥有 credential lifecycle 主权。

### P1-3 rawPayload cross-layer propagation

- 状态：**OPEN / RETAINED**。
- 证据：`AdapterOrderAck.java:12-23`、`AdapterOrderSnapshot.java:22-39`。
- 冻结结论：属于 GateL-1B P1；provider 原始响应不得无约束进入 core、persistence、audit、ledger 或 API。

### P1-4 Stub success ambiguity

- 状态：**OPEN / RETAINED**。
- 证据：`NoopMarketDataAdapter.java:29-44` 对 bars/trades/orderbook 返回 `MarketDataSubscriptionAck(true, ...)`。
- 冻结结论：属于 GateL-1B P1；NoReal/stub 必须显式标记，不能伪装成真实 venue subscription success。

## 6. P2 findings retained

1. **OPEN / RETAINED**：缺少统一 `AdapterCapability / VenueCapability` contract，调用前无法 fail-fast 判断 venue/market/operation 支持度。
2. **OPEN / RETAINED**：adapter result 与 permission probe error 分类分叉，retry policy 尚未成为单一事实源。
3. **OPEN / RETAINED**：historical/realtime/account/trading port 已拆分，但 public marketdata 与 private execution WS 边界仍未集中冻结。
4. **OPEN / RETAINED**：缺少 architecture/contract rule，类型层不能阻止 adapter 被 NQ execution orchestration 之外直接调用。

P2 不在本轮修复，也不能因 review baseline freeze 被写成 CLOSED。

## 7. Future-real readiness verdict

**NOT READY / NOT FROZEN / NOT AUTHORIZED。**

- Freeze 只接受 review baseline，不接受 adapter readiness。
- 四项 P1 任一开放时，都禁止 real exchange readiness acceptance。
- Capability/error/checklist 文档完成也不等于允许真实接入。
- 真实接入仍须：P1 全部关闭、No-Real hardening 独立实现与验证、readiness checklist 全满足、专项安全审计、重新 CI evidence/freeze、用户显式授权，并另起 Gate。

## 8. Frozen follow-up order

后续顺序冻结为：

1. **GateL-1B：No-Real hardening plan**
   只规划四项 P1 的最小、安全、可回滚修复批次；不在 plan 阶段改代码。
2. **GateL-1C：Capability matrix contract**
   在 No-Real 默认边界方案明确后，冻结 venue/market/operation/status/credential-scope 能力合同。
3. **GateL-1D：Error model contract**
   统一 platform error、permission probe error、retry/fail-closed/query-confirm 规则。
4. **GateL-1E：Future-real readiness checklist refinement**
   只完善 checklist；不得写成真实交易所接入许可。

该顺序取代 GateL-1 review 中原先的 capability → error → hardening 顺序。先处理 No-Real hardening plan，是因为 capability/error 合同必须建立在可靠的默认禁用、credential ownership、payload boundary 和 stub semantics 之上。

## 9. Commands run

- `Get-Location`
- `git status --short`
- `git branch --show-current`
- `Get-Content -Raw`：router skill、GateL-1 review、GateL plan 与 current docs。
- 限定文件 `rg -n`：Binance endpoint、OKX/Binance credential parsing、`rawPayload`、Noop marketdata success、current 状态与后续顺序。
- 文档变更后执行 `git diff --check`、Markdown link check、scope check、stage wording check、secret value pattern check。

## 10. Validation

- Docs-only：检查路径、链接、GateL/GateM/LIVE/AI/DH/RealClient 状态、P1/P2 retained 口径、后续顺序和 diff 范围。
- 未运行 Maven/frontend/Python：本轮没有 runtime 代码变更。
- 未访问网络、真实交易所、数据库、容器、GitHub Actions；未读取 credential material。

## 11. Risks

### P0

- 无。本轮仅冻结文档事实，不执行运行时写操作。

### P1

- 四项 P1 全部 **OPEN / RETAINED**；它们阻止 adapter readiness，但不阻止 review fact baseline freeze。

### P2

- 四项 P2 全部 **OPEN / RETAINED**；按 GateL-1C/1D 与后续 architecture contract 处理。

## 12. Recommended next task

**NQ-GATEL-1B-NO-REAL-HARDENING-PLAN**。

该任务只能输出四项 P1 的 hardening plan、影响面、验证与回滚方案；不得直接改代码，不得访问真实交易所，不得读取真实 credential，不得启用 LIVE。

## 13. Rollback

删除本文件，并还原 `GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的 GateL-1A freeze 条目与顺序调整即可。无代码、workflow、DB、credential、provider、交易或 runtime 副作用。

## 14. Final recommendation

**NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE：PASS。**

接受并冻结 GateL-1 review 的事实、P1/P2 和处理顺序；不接受 adapter readiness。P1/P2 保持 OPEN，GateL implementation 保持 NOT STARTED，真实交易所/LIVE/AI/DH runtime 继续禁止。
