# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。用途是给后续 GateP、GateO residual、Integration-1、AI、LIVE、real provider、private trading 和 research 相关任务提供统一入口，避免把历史冻结证据、mock/test-support 或 readiness 误写成当前 runtime 授权。

## 1. 当前事实源优先级

当仓库事实发生冲突时，按以下顺序解释当前状态：

1. `docs/current/STATUS.md`：当前项目状态与最新任务结论。
2. `docs/current/README.md`：当前事实入口索引。
3. `docs/current/ROADMAP.md`：当前阶段路线与下一批入口。
4. `docs/current/TESTING.md`：当前验证记录与未运行说明。
5. `docs/current/WORKLOG.md`：当前任务执行记录。
6. `docs/current/API.md`：已实现 HTTP API 当前事实，不记录未来 API 为已实现。
7. `docs/current/DB_SCHEMA.md`：已落地 Flyway schema 当前事实，不记录未来 schema 为已实现。
8. `docs/gates/gate-o/README.md`：GateO freeze / acceptance / plan / key evidence 历史归档入口，只作 GateO 证据引用，不覆盖 current facts。

`docs/gates/**`、`docs/archive/**` 和历史 review/freeze 文档只作为证据或归档引用，不覆盖 `docs/current` 当前事实入口。

## 2. 当前阶段声明

- GateO：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）。
- GateP：`PLANNING`（规划中）/ `BATCH 1 FACT SOURCE CLOSEOUT`（第一批事实源收口）。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- Python Research：offline research minimal skeleton；dataset manifest、evaluation skeleton、experiment metadata 均为后续工作。

GateP 不是已实现、已冻结或已接受。GateP Batch 1 只收口事实源和状态边界，不启动真实交易所接入、LIVE、AI、DH runtime、RealClient、real provider、private trading 或真实 permission probe。

## 3. GateO 完成边界

GateO 已完成并接受的边界：

- GateO process and evidence archive 已收口到 `docs/gates/gate-o/`。
- public readonly smoke 已作为 O-5 accepted evidence 消费。
- `DataOrigin.PUBLIC_OUTBOUND` decision = `ALLOW_FUTURE_IMPLEMENTATION`（允许后续单独实现）。
- O-1 controlled public outbound guard baseline 已冻结。
- O-2 Data Quality Center baseline 已冻结。
- O-3 `GET /api/marketdata/readiness` read-only API baseline 已冻结。
- O-4 `/marketdata` Quality / Readiness 只读 UI baseline 已冻结。
- O-5 manual public outbound smoke baseline 已冻结。

GateO 未完成或未授权的内容：

- `DataOrigin.PUBLIC_OUTBOUND` 尚未实现到 Data Quality / readiness API / frontend。
- 未实现 real provider、RealClient、private trading adapter 或真实 permission probe。
- 未开启 LIVE、AI runtime 或 DH runtime。
- 未授权下单、撤单、转账、提现、account / balance / order private endpoint 或 signed request。
- public marketdata readiness 不等于 trading authorization。

## 4. GateP 主线边界

GateP 主线为真实数据质量与交易准备阶段。当前 Batch 1 只完成事实源与状态收口，后续方向只能在单独任务中逐批推进：

- true data quality。
- single venue readiness。
- permission probe observability。
- risk preflight。
- Shadow Live design。

GateP 当前不做：

- no live trading。
- no private trading。
- no real provider enablement。
- no RealClient implementation。
- no real permission probe implementation。
- no AI / DH runtime integration。
- no migration / API / CI / frontend / Python implementation in Batch 1。

## 5. 禁止误写清单

- 不得把 readiness 写成 authorization。
- 不得把 mock / fixture / stub / test-support 写成 runtime integration。
- 不得把 public outbound 写成 private trading。
- 不得把 `DataOrigin.PUBLIC_OUTBOUND` decision 写成 implemented。
- 不得把 AI recommendation 写成 execution。
- 不得把 Paper / SIM 写成 LIVE。
- 不得把 GateO public readonly smoke 写成 production readiness。
- 不得把 Integration-1 mock-only baseline 写成 runtime started。
- 不得把 Python Research skeleton 写成 ML ready / direct execution ready。
- 不得把 existing OKX/Binance legacy network-capable code 写成 real provider ready。

## 6. 下一批任务入口

- Batch 2：Market Data Data Quality Center 后端只读切片。
- Batch 3：前端 Data Quality Center 与 Runtime 放行矩阵。
- Batch 4：单交易所账户权限与风险前置只读基线。
- Batch 5：Python 研究域地基工程化。

所有后续批次必须重新声明 allowed files、forbidden areas、validation commands 和 no-LIVE / no-AI / no-DH / no-real-provider / no-private-trading 边界。
