# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。用途是给后续 GateQ archive/tag、GateR planning、GateP/GateO historical archive、Integration-1、AI、LIVE、real provider、private trading 和 research 相关任务提供统一入口，避免把历史冻结证据、mock/test-support、readiness、preview 或 comparison 误写成当前 runtime 授权。

## 1. 当前事实源优先级

当仓库事实发生冲突时，按以下顺序解释当前状态：

1. `docs/current/STATUS.md`：当前项目状态与最新任务结论。
2. `docs/current/README.md`：当前事实入口索引。
3. `docs/current/GATEQ_FREEZE_CLOSEOUT.md`：GateQ final freeze closeout 当前权威入口；结论为 `PASS`（通过）/ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `READY FOR ARCHIVAL`（可归档）。
4. `docs/current/GATEQ_FREEZE_READINESS_REVIEW.md`：GateQ-0..6 freeze readiness 前置证据；结论为 `PASS`（通过）/ `READY FOR FREEZE CLOSEOUT`（可进入冻结收口），已由 final closeout 消费。
5. `docs/current/GATEQ_PLAN.md`：GateQ-0 planning baseline；记录 Shadow Live readiness、Paper vs Shadow boundary、strategy evaluation gate、traceability model、Python artifact binding 和 batch plan；该计划已由 GateQ-1..6 消费，不得单独解释为当前全阶段未实现。
6. `docs/current/ROADMAP.md`：当前阶段路线与下一批入口。
7. `docs/current/TESTING.md`：当前验证记录与未运行说明。
8. `docs/current/WORKLOG.md`：当前任务执行记录。
9. `docs/current/API.md`：已实现 HTTP API 当前事实，不记录未来 API 为已实现。
10. `docs/current/DB_SCHEMA.md`：已落地 Flyway schema 当前事实，不记录未来 schema 为已实现。
11. `docs/gates/gate-p/README.md`：GateP freeze / release tag / Batch 1-6A evidence matrix / testing summary 历史归档入口；当前摘要为 GateP `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag），release tag `nq-gatep-freeze` 已推送。
12. `docs/gates/gate-o/README.md`：GateO freeze / acceptance / plan / key evidence 历史归档入口，只作 GateO 证据引用，不覆盖 current facts。

`docs/current/GATEP_FREEZE_CLOSEOUT_REVIEW.md` 与 `docs/current/GATEP_FREEZE_READINESS_REVIEW.md` 仅保留 tag/archive pointer 和历史过渡证据，不再作为 current authority 入口；GateP 过程型长证据以 `docs/gates/gate-p/` 为归档入口。

`docs/gates/**`、`docs/archive/**` 和历史 review/freeze 文档只作为证据或归档引用，不覆盖 `docs/current` 当前事实入口。

## 2. 当前阶段声明

- GateO：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）。
- GateP：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag）；Batch 1-6A `COMPLETED`（已完成）；final closeout `PASS`（通过）/ `READY FOR ARCHIVAL`（可归档）；release tag `nq-gatep-freeze` 已推送。
- GateQ：GateQ-0 planning 已完成；GateQ-1 Strategy Evaluation Gate read-only baseline、GateQ-2 Paper vs Shadow Comparison read-only baseline、GateQ-3 Shadow Live no-side-effect preview skeleton、GateQ-4 Python Evaluation Artifact Binding Preview contract、GateQ-5 frontend Paper / Shadow Comparison view、GateQ-6 Strategy Lifecycle Trace view enhancement 均为 `COMPLETED`（已完成）；final freeze closeout 为 `PASS`（通过）/ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `READY FOR ARCHIVAL`（可归档）。GateQ 当前最终状态为 `FROZEN / ACCEPTED`。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- Python Research：reproducible offline experiment foundation；dataset manifest、experiment metadata、evaluation metrics skeleton 与 CLI run summary 已完成。该能力仍不是 ML ready、live execution ready 或 Java runtime bridge。

GateP 已冻结并接受的是“真实数据质量与交易准备阶段”的只读诊断、前端诊断视图、交易前置只读基线、Python offline foundation 与 current fact-source closeout。该冻结不启动真实交易所接入、LIVE、AI、DH runtime、RealClient、real provider、private trading 或真实 permission probe。

GateQ 当前已完成 GateQ-0 planning、GateQ-1 到 GateQ-4 后端只读 / preview API baseline、GateQ-5 / GateQ-6 前端只读验证视图、freeze readiness review 与 final freeze closeout。已冻结内容仍限定于 read-only aggregation、fail-closed comparison、no-side-effect preview、Python offline artifact binding preview 和 frontend evidence display；不代表真实 Shadow Live runner、shadow run 写侧 fact source、LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe 已启动。

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

GateP 主线为真实数据质量与交易准备阶段。当前事实为 Batch 1-6A 已完成，final freeze closeout 已给出 `PASS / FROZEN / ACCEPTED / READY FOR ARCHIVAL`，release tag `nq-gatep-freeze` 已推送，历史归档入口为 `docs/gates/gate-p/README.md`。GateP 主线冻结的能力边界仍是：

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
- no additional implementation / API / migration / CI workflow / frontend / backend change in this closeout；本轮仅因用户追加 CI 修复授权，最小修改 Python test fixture path 与 mypy cache backend 配置，不新增 Python Research 能力。

## 5. 禁止误写清单

- 不得把 readiness 写成 authorization。
- 不得把 mock / fixture / stub / test-support 写成 runtime integration。
- 不得把 public outbound 写成 private trading。
- 不得把 `DataOrigin.PUBLIC_OUTBOUND` decision 写成 implemented。
- 不得把 AI recommendation 写成 execution。
- 不得把 Paper / SIM 写成 LIVE。
- 不得把 GateO public readonly smoke 写成 production readiness。
- 不得把 Integration-1 mock-only baseline 写成 runtime started。
- 不得把 Python Research offline foundation 写成 ML ready / live execution ready / direct execution ready。
- 不得把 existing OKX/Binance legacy network-capable code 写成 real provider ready。
- 不得把 Shadow Live 写成真实交易、LIVE readiness、private trading 或 order execution。
- 不得把 `READY_FOR_SHADOW_REVIEW`、`READY_FOR_COMPARISON`、`READY_FOR_NO_SIDE_EFFECT_PREVIEW` 或 `VALID_FOR_BINDING_PREVIEW` 写成 trading authorization、LIVE enablement、strategy approval、ML ready 或 live execution ready。

## 6. GateP Batch 1-6A 与 freeze closeout 当前事实

- Batch 1：事实源与状态收口已完成。
- Batch 2：Market Data Data Quality Center 后端只读切片已完成。
- Batch 3：前端 Data Quality Center 与 Runtime 放行矩阵已完成。
- Batch 4：单交易所账户权限与风险前置只读基线已完成。
- Batch 5：Python offline research foundation 已完成；当前是 reproducible offline experiment foundation，不是 ML ready 或 live execution ready。
- Batch 6：freeze readiness review 为 `CONDITIONAL PASS`（有条件通过）/ `FIX REQUIRED`（需要修复）；P1 finding 是 current fact-source drift，已由 Batch 6A 关闭。
- Batch 6A：current fact-source drift fix 已完成。
- Final closeout：`NQ-GATEP-FREEZE-CLOSEOUT-REVIEW` 为 `PASS`（通过）/ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `READY FOR ARCHIVAL`（可归档）。
- Release tag / archive：`NQ-GATEP-RELEASE-TAG-AND-ARCHIVE` 为 `PASS`（通过）/ `COMPLETED`（已完成）/ `RELEASE TAG PUSHED`（release tag 已推送）；tag `nq-gatep-freeze` 指向 commit `3650714ae9cd441e59eb5b09c605a14bbc9998dc`。
- GateP archive 后的 GateQ-0 planning 已由 GateQ-1..6 消费；GateQ final freeze closeout 已完成。当前下一步只能另起 GateQ archive/tag 任务或 GateR `PLAN / NOT STARTED` planning 任务，不得借 closeout 新增 API、migration、页面、测试、CI、runtime 或业务能力。

## 7. GateQ-0..6 与 final freeze 当前事实

- GateQ-0：`NQ-GATEQ-PLAN-SHADOW-LIVE-READINESS` 已完成 planning-only baseline；该计划定义 strategy evaluation gate、Paper vs Shadow 只读对照、Python artifact binding、Shadow Live no-side-effect 边界和 Q0..FREEZE batch plan。
- GateQ-1：`GET /api/strategies/evaluation-gate` 只读 baseline 已完成；`READY_FOR_SHADOW_REVIEW` 只表示研究与评估证据可进入后续 review，不代表交易授权。
- GateQ-2：`GET /api/strategies/paper-shadow/comparison` 只读 baseline 已完成；Shadow runner / fact source 未实现时 fail-closed 为 `BLOCKED_SHADOW_NOT_IMPLEMENTED` / `NOT_IMPLEMENTED`。
- GateQ-3：`GET /api/strategies/shadow-live/preview` no-side-effect preview skeleton 已完成；只组合 GateQ-1 / GateQ-2 结果，不写库、不外联、不读 credential、不启动 runner。
- GateQ-4：`POST /api/research/evaluation-artifacts/binding-preview` binding preview contract 已完成；只校验 request body artifact JSON、checksum、parametersHash、metrics、offline boundary 与 traceability，不导入、不上传、不持久化。
- GateQ-5：`/strategies/validation` Paper / Shadow Comparison 前端只读视图已完成；只消费 GateQ-1 / GateQ-2 / GateQ-3 GET API，不新增后端能力。
- GateQ-6：Strategy Lifecycle Trace / Evidence Matrix 前端增强已完成；GateQ-4 在前端显示 `PENDING_FRONTEND_SUPPORT`（等待前端接入支持）/ `NOT_CONNECTED`（未接入）。
- Freeze readiness review：`NQ-GATEQ-FREEZE-READINESS-REVIEW` 为 `PASS`（通过）/ `READY FOR FREEZE CLOSEOUT`（可进入冻结收口）；P0/P1=0，已由 final freeze closeout 消费。
- Final freeze closeout：`NQ-GATEQ-FREEZE-CLOSEOUT` 为 `PASS`（通过）/ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `READY FOR ARCHIVAL`（可归档）；GateQ 当前最终状态为 `FROZEN / ACCEPTED`。

所有后续批次必须重新声明 allowed files、forbidden areas、validation commands 和 no-LIVE / no-AI / no-DH / no-real-provider / no-private-trading 边界。
