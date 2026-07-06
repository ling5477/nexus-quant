# NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION

状态：`PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。

本文是 GateR-0 planning 文档。GateR 只进入 Shadow Run 运行化规划、事实源对齐、边界审查和批次设计；不实现功能，不改业务代码，不新增 API，不新增 migration，不改 CI，不新增前端页面，不新增测试，不启动 Shadow runner。

## 1. GateR Current Baseline

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ tagged commit：`d4bafe47729c0007b7ef9f1bda9cd578dfd1e7e4`。
- GateQ archive：`docs/gates/gate-q/`。
- GateQ-0..6：`COMPLETED`（已完成）。
- GateR：`PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient、real provider、private trading adapter、real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow Live trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

GateR 的当前事实只表示 planning 文档已经建立，不表示 GateR implementation started，不表示 Shadow Run local fact 已落地，也不表示任何交易授权。

## 2. GateQ Freeze Evidence Summary

GateQ 冻结证据来自 `docs/gates/gate-q/`，其中：

- `docs/gates/gate-q/README.md` 记录 GateQ archive 总入口、release tag、GateQ-0..6 完成范围和禁止边界。
- `docs/gates/gate-q/GATEQ_FREEZE_CLOSEOUT.md` 记录 GateQ freeze closeout 结论。
- `docs/gates/gate-q/GATEQ_BOUNDARY_STATEMENT.md` 记录 GateQ 只读 readiness / preview / comparison baseline 的负向边界。

GateQ 已完成的能力边界是：

- Strategy Evaluation Gate：只读评估证据门，不是 trading authorization。
- Paper vs Shadow Comparison：只读对照 baseline，不创建 Shadow run。
- Shadow Live Preview：no-side-effect preview，不写库、不外联、不读 credential material、不提交真实订单。
- Python Evaluation Artifact Binding Preview：只读 request-body 校验预览，不导入、不上传、不持久化，不代表 ML ready 或 live execution ready。
- 前端 `/strategies/validation`：只读展示 GateQ-1 / GateQ-2 / GateQ-3 响应和 Evidence Matrix，不是 Shadow Run detail 或 replay 页面。

## 3. GateR Objective

GateR 主线目标是把 GateQ 的只读 readiness / preview / comparison baseline 推进为可审计、可复盘、无真实交易副作用的 Shadow Run 运行化设计。GateR 规划的核心输出包括：

- Shadow Run 本地事实模型、状态机、审计日志和只读输出。
- Shadow Run 如何消费 strategy version、dataset、evaluation、publish、paper run 和 no-side-effect preview。
- Shadow decision trace、marketdata input snapshot、risk preflight snapshot、order intent preview snapshot。
- Paper vs Shadow consistency report。
- Shadow Run 启动、停止、失败、回放、复盘边界。
- 前端 Shadow Run detail / replay / evidence 页面候选设计。
- no-side-effect、no-egress、no-credential、no-LIVE、no-AI、no-DH runtime 边界。

## 4. GateR Non-goals

GateR 不是以下阶段或能力：

- 真实交易阶段。
- LIVE 阶段。
- private trading adapter 阶段。
- 真实 permission probe 阶段。
- AI / DH runtime 接入阶段。
- 高频交易阶段。
- 钱包、主网资金或真实资金阶段。
- RealClient 或 real provider 实现阶段。
- Integration-1 runtime 阶段。

本轮 GateR-0 不实现后端、前端、Python、CI、migration、测试或 Shadow runner。

## 5. Shadow Run Definition

Shadow Run 是本地无真实交易副作用的影子运行事实记录。它可以基于已冻结的 strategy version、dataset、evaluation、publish、paper run 和 public marketdata snapshot 生成只读 shadow facts、decision trace、risk snapshot 和 order intent preview。

Shadow Run 必须满足：

- 不提交真实订单。
- 不调用 private endpoint。
- 不读取 credential material。
- 不改变真实账户、资金、订单或 ledger 状态。
- 不调用真实交易所写接口。
- 不启用 LIVE。
- 不接 AI 自动交易。
- 不接 DH runtime 写 NQ。

Shadow Run 是否允许写数据库：GateR-0 不允许任何数据库写入。后续若 GateR-1 migration review 明确批准，可规划新增本地 Shadow Run fact / audit 表，用于写入本地、可审计、无交易副作用的 Shadow Run 事实；该写入不得触碰真实账户、资金、订单、ledger 或 credential material。

## 6. Shadow Run vs Shadow Live Preview Boundary

Shadow Live Preview 是 GateQ 已有的 no-side-effect preview：

- 不写数据库。
- 不启动 runner。
- 不创建 shadow run。
- 不读取 credential material。
- 不调用 private endpoint。
- 不提交订单。
- 不改变账户、资金、订单或 ledger。

Shadow Run 是 GateR 候选的本地运行化事实记录：

- 可以在后续批准的 migration 之后写入本地 shadow facts / audit records。
- 可以记录状态机、输入快照、decision trace、risk snapshot、order intent preview 和 consistency report。
- 仍然不得真实下单、不得调用 private endpoint、不得读取 credential material、不得改变真实账户/资金/订单/ledger。

因此，Shadow Run 比 Shadow Live Preview 多了候选的本地事实持久化和 replay / review 模型；但两者都不等于 live execution ready，也都不等于 trading authorization。

## 7. Shadow Run Candidate State Machine

候选状态机只用于后续实现审查，不代表当前已实现：

```text
CREATED
  -> PRECHECKING
  -> READY
  -> RUNNING
  -> COMPLETED

RUNNING
  -> STOP_REQUESTED
  -> STOPPED

PRECHECKING
  -> BLOCKED

CREATED / PRECHECKING / READY / RUNNING / STOP_REQUESTED
  -> FAILED

CREATED / READY / BLOCKED
  -> CANCELLED
```

候选状态语义：

- `CREATED`（已创建）：本地 Shadow Run 请求已记录，但未做 precheck。
- `PRECHECKING`（预检中）：检查 strategy version、dataset、evaluation、publish、paper run、public marketdata snapshot 和 no-side-effect policy。
- `READY`（就绪）：本地事实完整，可启动无副作用 Shadow Run。
- `RUNNING`（运行中）：仅执行本地 shadow decision / trace 生成，不外联、不下单、不读 credential。
- `COMPLETED`（已完成）：Shadow facts、trace 和 summary 生成完毕。
- `STOP_REQUESTED`（停止请求中）：用户或 guard 请求停止。
- `STOPPED`（已停止）：无副作用停止完成，保留 audit event。
- `BLOCKED`（已阻断）：precheck 发现缺失或禁止条件。
- `FAILED`（失败）：运行时失败，必须记录 failure_code、failure_message、traceId。
- `CANCELLED`（已取消）：启动前取消，不生成执行 trace。

Replay 建议作为独立 replay job 或 replay event 记录，不直接改写原 Shadow Run 终态。

## 8. Shadow Run Candidate Data Model

候选最小数据模型必须在 GateR-1 单独做 migration review；本轮不新增 migration，不更新 `DB_SCHEMA.md`。

候选表/对象：

- `shadow_runs`：Shadow Run 主记录。字段候选包括 `shadow_run_id`、`strategy_version_id`、`dataset_id`、`evaluation_id`、`publish_id`、`paper_run_id`、`status`、`run_window_start`、`run_window_end`、`side_effect_policy_version`、`created_at`、`started_at`、`finished_at`、`failure_code`、`failure_message`、`trace_id`、`request_id`。
- `shadow_run_events`：append-only 生命周期审计。字段候选包括 `event_id`、`shadow_run_id`、`event_type`、`from_status`、`to_status`、`reason_code`、`message`、`created_at`、`trace_id`。
- `shadow_input_snapshots`：public marketdata input snapshot 锚点。字段候选包括 `snapshot_id`、`shadow_run_id`、`source_type`、`symbol`、`interval`、`window_start`、`window_end`、`checksum`、`row_count`、`quality_status`。
- `shadow_decision_traces`：decision trace 明细。字段候选包括 `trace_item_id`、`shadow_run_id`、`sequence_no`、`event_time`、`marketdata_snapshot_hash`、`strategy_decision_snapshot`、`risk_preflight_snapshot`、`order_intent_preview`、`blockers`、`warnings`。
- `shadow_consistency_reports`：Paper vs Shadow consistency report。字段候选包括 `report_id`、`shadow_run_id`、`paper_run_id`、`comparison_status`、`decision_delta_summary`、`risk_delta_summary`、`order_intent_delta_summary`、`metric_delta_summary`、`limitations`、`generated_at`。

所有 JSON / JSONB 字段必须明确禁止保存 API key、secret、token、cookie、private key、credential material、未脱敏请求头、未脱敏响应体、真实私有 endpoint payload。

## 9. Traceability Model

GateR traceability 必须保持从源事实到输出证据的链路：

```text
strategy version
  -> dataset
  -> evaluation
  -> publish
  -> paper run
  -> public marketdata snapshot
  -> shadow run
  -> shadow decision trace
  -> risk preflight snapshot
  -> order intent preview snapshot
  -> Paper vs Shadow consistency report
  -> replay / review evidence
```

每个节点至少需要：

- stable id 或 checksum。
- 来源文件、表或 API fact-source。
- 时间窗口。
- status。
- traceId / requestId。
- blockers / warnings。
- no-side-effect boundary。

Traceability 不得补造成功态。缺失、未知、未实现、不可用必须按 fail-closed 展示。

## 10. Paper vs Shadow Consistency Plan

Paper vs Shadow consistency report 应包含：

- report id、shadow_run_id、paper_run_id、strategy_version_id、dataset_id、evaluation_id、publish_id。
- 输入窗口、symbol、interval、public marketdata snapshot checksum。
- Paper run status、Shadow Run status、两侧 trace completeness。
- strategy decision 数量、方向、信号强度、过滤条件差异。
- risk preflight blockers / warnings 差异。
- order intent preview 的 side、quantity、notional、symbol、time-in-force、reject reason 差异。
- Paper execution 与 Shadow order intent 的可比性说明；Shadow 不产生真实成交。
- metrics delta：return、drawdown、exposure、turnover 等可用指标；不可用指标必须显式为 `NOT_AVAILABLE`（不可用）。
- divergence reasons。
- limitations。
- nextSteps。
- 明确声明：comparison 不代表 trading authorization，不代表 LIVE ready。

## 11. Risk / Order Intent Preview Boundary

Risk preflight snapshot 只能记录本地预检结果：

- 输入事实是否完整。
- strategy version / dataset / evaluation / publish / paper run 是否可追溯。
- no-side-effect policy 是否满足。
- public marketdata snapshot 是否可复现。
- risk blockers / warnings。

Order intent preview snapshot 只能记录拟议订单意图，不得提交订单：

- 可记录 symbol、side、quantity、price policy、time-in-force、notional、strategy signal、risk rejection reason。
- 不得调用 `placeOrder`、`cancelOrder`、`amendOrder`、withdraw、transfer 或任何 private trading endpoint。
- 不得写真实订单表、真实账户表、资金表或 ledger。
- 不得把 order intent preview 写成 order approval。

## 12. Backend Candidate API / DTO / Migration Plan

本节只描述候选，不代表已实现，不更新 `API.md` 或 `DB_SCHEMA.md`。

候选 API 需要拆到后续 GateR 批次逐项 review：

- `POST /api/shadow-runs`：候选创建本地 Shadow Run 请求。仅允许在本地 fact model 完成、migration review 通过、no-side-effect guard 完成后实现。
- `POST /api/shadow-runs/{shadowRunId}/start`：候选启动本地 Shadow Run。必须只触发本地无副作用 runner，不外联、不下单、不读 credential。
- `POST /api/shadow-runs/{shadowRunId}/stop`：候选停止本地 Shadow Run。必须幂等，不能触达交易路径。
- `GET /api/shadow-runs/{shadowRunId}`：候选读取 detail。
- `GET /api/shadow-runs/{shadowRunId}/trace`：候选读取 decision trace。
- `GET /api/shadow-runs/{shadowRunId}/consistency-report`：候选读取 Paper vs Shadow consistency report。
- `POST /api/shadow-runs/{shadowRunId}/replay-preview`：候选 replay preview，只读重放，不改写原始 facts。

候选 DTO 必须包含：

- `sideEffectPolicy`。
- `authorizationBoundary`。
- `status`。
- `blockers` / `warnings` / `nextSteps`。
- `traceId` / `requestId`。

候选 migration 必须拆到 GateR-1 单独审查，重点检查：

- 新增表和字段都有中文 comment。
- 状态字段有允许值说明。
- JSONB 字段不存 credential material。
- 索引覆盖查询路径。
- 不修改历史 migration。
- 不写真实账户、资金、订单或 ledger。

## 13. Frontend Candidate Pages

前端最小页面候选：

- Shadow Run 列表：展示 status、strategyVersionId、datasetId、paperRunId、createdAt、startedAt、finishedAt、blockers。
- Shadow Run detail：展示状态机、scope、sideEffectPolicy、trace summary、risk snapshot、order intent preview。
- Shadow Run replay / evidence：展示 replay input、输出 diff、不可用项、limitations。
- Paper vs Shadow consistency report：展示 summary、divergence matrix、risk delta、order intent delta、metric delta。
- Boundary panel：固定展示不代表 trading authorization、不代表 LIVE ready、不读取 credential、不调用 private endpoint、不提交真实订单。

当前 GateQ `/strategies/validation` 只读页可以作为未来入口参考，但本轮不新增页面，不改 frontend。

## 14. Test Strategy

GateR 测试策略候选：

- GateR-1：migration review，检查 DDL comment、状态枚举、JSONB 敏感字段禁用、索引、回滚说明。
- GateR-2：repository / local fact model 单测和集成测试，覆盖 created、blocked、completed、failed、append-only event。
- GateR-3：command skeleton 测试，覆盖 start/stop 幂等、非法状态流转、no-side-effect guard。
- GateR-4：decision trace / risk snapshot / order intent preview 测试，覆盖缺失输入、风险阻断、禁止真实订单字段。
- GateR-5：Paper vs Shadow consistency golden cases，覆盖一致、偏离、缺失、不可比、指标不可用。
- GateR-6：前端 build 与 Playwright smoke，覆盖 loading、empty、error、blocked、completed、failed、no authorization copy、no forbidden request。
- GateR-7：no-egress / no-credential smoke，覆盖无外联、无 credential read、无 private endpoint、无 LIVE、无 AI/DH runtime。

本轮不新增测试，也不运行 Maven、frontend build、Playwright、pytest、mypy 或 ruff；验证仅限 docs-only diff、边界搜索和 forbidden-scope diff。

## 15. Security / Credential / LIVE Boundary

GateR 必须 fail-closed：

- Shadow Run 不允许真实下单。
- Shadow Run 不允许调用 private endpoint。
- Shadow Run 不允许读取 credential material。
- Shadow Run 不允许修改真实账户、资金、订单或 ledger。
- Shadow Run 不允许调用 withdraw、transfer、cancel、amend 或任何真实交易写接口。
- Shadow Run 不允许开启 LIVE。
- Shadow Run 不允许把 public marketdata readiness、risk preflight、order intent preview、Paper vs Shadow consistency 写成 trading authorization。

后续实现必须有 no-egress guard、no-credential guard、private endpoint denylist、side-effect policy 和审计日志。

## 16. AI / DH Runtime Boundary

GateR 不接 AI runtime，不接 DH runtime：

- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- DH 不允许修改 NQ 交易状态，不允许启动 Paper Run 或 Shadow Run，不允许访问 credential material。
- Python Evaluation Artifact Binding Preview 不代表 ML ready，不代表 live execution ready。

## 17. P0 / P1 / P2 / P3 Risk List

### P0

- 将 Shadow Run、Paper vs Shadow consistency 或 Shadow Live Preview 写成 trading authorization。
- 允许 Shadow Run 真实下单、撤单、转账、提现或调用 private endpoint。
- 读取或输出 credential material。
- 修改真实账户、资金、订单或 ledger。
- 将 LIVE、AI、DH runtime 或 Integration-1 runtime 写成已启用。

### P1

- 未经 GateR-1 migration review 直接新增 shadow run 表。
- 状态机允许无条件覆盖终态或缺少非法状态流转保护。
- 缺少 no-egress / no-credential guard。
- consistency report 把不可比或缺失事实显示为成功态。

### P2

- Shadow facts 缺少 traceId / requestId / checksum，导致不可复盘。
- JSONB 字段边界不清，存在敏感字段误入风险。
- 前端缺少 loading、empty、error、blocked、failed 状态。
- replay 设计会改写原始 run 事实。

### P3

- 文档入口不同步。
- 状态名缺少中文解释。
- batch 验收证据不够具体。

## 18. GateR Batch Plan

| Batch | 目标 | 允许输出 | 禁止 |
| --- | --- | --- | --- |
| GateR-0 | Plan / fact-source reconciliation | `docs/current/GATER_PLAN.md` 与 current 入口同步 | 实现、API、migration、页面、测试、CI |
| GateR-1 | Shadow Run data model & migration plan review | DDL 设计审查、表/字段/comment/索引/回滚方案 | 写 migration、改历史 migration |
| GateR-2 | Shadow Run local fact model / repository implementation | 本地 fact model 与 repository | 真实账户/资金/订单/ledger 写入 |
| GateR-3 | Shadow Run no-side-effect command skeleton | start/stop/precheck skeleton 与状态机保护 | 外联、credential、private endpoint、下单 |
| GateR-4 | Shadow decision trace / risk snapshot / order intent preview | trace、risk snapshot、order intent preview | 真实订单、真实风控放行、交易授权 |
| GateR-5 | Paper vs Shadow consistency report | consistency report 与 golden cases | 将 comparison 写成 trading authorization |
| GateR-6 | Frontend Shadow Run detail / replay view | detail、replay、evidence、consistency 页面 | LIVE 操作入口、真实交易按钮 |
| GateR-7 | Operational guard / no-egress / no-credential smoke | no-egress、no-credential、forbidden request smoke | 读取 credential 或真实外联 |
| GateR-FREEZE | GateR freeze closeout | freeze review、evidence matrix、current docs sync | 提前写 accepted/frozen |

## 19. Validation Commands

GateR-0 docs-only 验证命令：

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/dev
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- "backend/**/db/migration"
rg "GateQ|GateR|Shadow Run|Shadow Live|shadow|Paper|strategy version|dataset version|evaluation|publish|paper run|shadow run|LIVE|AI|DH|Integration-1|RealClient|real provider|private trading|permission probe|credential|order|cancel|withdraw|transfer|trading authorization|ML ready|live execution" README.md docs/current docs/gates backend frontend research/py
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
```

代码测试不在 GateR-0 运行范围内；原因是本轮只改 documentation，不修改 backend、frontend、research、scripts、deploy、`.github`、migration、API、页面或测试。

## 20. Acceptance Criteria

GateR freeze 的候选验收标准：

- GateR-1..7 均完成并通过各自 review。
- P0 / P1 findings 为 0。
- Shadow Run local fact model 只写本地 shadow facts / audit，不写真实账户、资金、订单或 ledger。
- no-egress / no-credential / no-private-endpoint / no-order-submission guard 有自动化证据。
- 状态机非法流转、重复 start/stop、失败路径和 replay 边界有测试证据。
- Paper vs Shadow consistency report 覆盖一致、偏离、缺失、不可比和指标不可用场景。
- 前端 detail / replay / evidence 页面覆盖 loading、empty、error、blocked、failed、completed。
- 文档同步 `README.md`、`docs/current/README.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`FACT_SOURCE_INDEX.md`。
- 仍明确声明 Shadow Run 不代表 trading authorization，不代表 LIVE ready。

## 21. Exit Criteria

GateR-0 exit criteria：

- `docs/current/GATER_PLAN.md` 建立并覆盖 GateR planning 必答问题。
- current 入口文档同步 GateR `PLAN READY / NOT IMPLEMENTED`。
- forbidden-scope diff 显示 backend、frontend、research、scripts、deploy、`.github`、migration 未改。
- staged checks 不包含禁止路径。
- 最终报告明确本轮未实现功能、未新增 API、未新增 migration、未新增页面、未新增测试、未启动 Shadow Run。

GateR-FREEZE exit criteria 必须在后续 GateR-FREEZE 单独执行，GateR-0 不声明 frozen 或 accepted。

## 22. Next Concrete Action

下一步只能进入 GateR-1：Shadow Run data model & migration plan review。GateR-1 必须是单独任务，先审查候选表、字段、状态枚举、JSONB 边界、索引、回滚和 forbidden writes；不得在 GateR-0 中实现 migration。

当前最终状态：

```text
NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED
```
