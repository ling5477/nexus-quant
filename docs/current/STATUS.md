# Current Status

## 1. 当前总状态

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/`。
- GateS closeout：`docs/gates/gate-s/GATES_FREEZE_CLOSEOUT.md`。
- GateS-0：`COMPLETED`（已完成），Plan / fact-source reconciliation。
- GateS-1：`COMPLETED`（已完成），Shadow Run overview backend read model + frontend overview summary。
- GateS-2：`COMPLETED`（已完成），Paper vs Shadow consistency drilldown backend + frontend。
- GateS-3：`COMPLETED`（已完成），Strategy Evaluation Gate overview backend + frontend。
- GateS-4：`COMPLETED`（已完成），Python offline evaluation artifact baseline。
- GateS-5：`COMPLETED`（已完成），Strategy Validation / Shadow Workbench frontend。
- GateS-6：`COMPLETED`（已完成），Incident / Replay overview backend + frontend。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；archive：`docs/gates/gate-r/`。
- GateQ / GateP / GateO 及更早 Gate：历史证据入口为 `docs/gates/**` 或 `docs/archive/**`。
- 当前阶段：GateT-1 implementation 已进入 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-0：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 `docs/current/GATET_PLAN.md`。
- GateT-1 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 `docs/current/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`。
- GateT-1 implementation：`GET /api/shadow-validation/workflow/overview` 后端 read model 已实现；只派生 derived / deterministic operator items，不持久化、不新增 migration、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。

## 2. GateS Freeze Closeout Evidence

- Readiness review：`docs/gates/gate-s/GATES_FREEZE_READINESS_REVIEW.md`。
- Evidence matrix：`docs/gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md`。
- API summary：`docs/gates/gate-s/GATES_API_EVIDENCE_SUMMARY.md`。
- Frontend summary：`docs/gates/gate-s/GATES_FRONTEND_EVIDENCE_SUMMARY.md`。
- Python summary：`docs/gates/gate-s/GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md`。
- Boundary statement：`docs/gates/gate-s/GATES_BOUNDARY_STATEMENT.md`。
- 最新 closeout precondition CI：GitHub Actions run `28932927935` / `NQ CI Baseline` / `success`（成功），`headSha=5f0fcb9d4dacab95202dc7a9fb78911e60c06afe`。

## 3. 禁止边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 4. GateS 能力边界

- GateS 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization。
- GateS frontend panels 均为只读诊断展示，不提供 start / stop / execute / trade / placeOrder / cancelOrder / withdraw / transfer 入口。
- GateS Python artifact 只属于 offline research diagnostic baseline，不绑定 Java production，不导入 DB，不驱动 Paper / Shadow / LIVE。
- GateS freeze closeout 不新增 API、migration、frontend page、E2E、CI workflow、Python runtime、runner、scheduler、真实 provider、private trading adapter 或真实交易行为。

## 5. GateT-0 Planning Decision

- GateT 主线目标：把 GateS 只读诊断能力推进为 Shadow Validation Operations / 策略验证运营闭环的规划基线。
- GateT 与 GateS 边界：GateS 提供 read-only evidence；GateT 规划 operator workflow、evidence refinement、incident / replay review、Python artifact read-only binding preview 和 frontend workbench，但不启动 runtime。
- GateT 后续可规划本地 review / acknowledge 写侧 workflow，但必须限定为本地复核记录；不得触发交易、runner、scheduler、private endpoint、account / ledger / order / position mutation。
- GateT 后续可规划 no-side-effect scheduler readiness review；不得连接真实交易所、不得调用 private endpoint、不得创建真实订单。
- GateT 第一批应先做 backend read model / operator model plan，再做 frontend workbench；原因是前端已有 GateS 只读面板，缺口在统一 operator item、review state 和 evidence anchor 语义。
- DB migration：默认不新增；只有 durable review / acknowledge audit 被证明必须持久化时，才另起 DB schema review。
- Python artifact：只允许 read-only binding preview；不导入、不写库、不驱动 Java production runtime。
- AI / DH：默认不接，仍保持 `NOT STARTED`（未开始）和 `NOT INTEGRATED`（未集成）。

## 6. GateT-1 Implementation Decision

- GateT-1 主线目标：实现 Shadow Validation Workflow backend read model / derived operator item model。
- 已实现 endpoint：`GET /api/shadow-validation/workflow/overview`；详见 `docs/current/API.md`。
- Operator item：derived / deterministic，不持久化；`operatorItemId` 由稳定事实锚点派生，不依赖数据库自增。
- Operator review / acknowledge：仍未实现；不得触发交易、runner、scheduler、private endpoint、account / ledger / order / position mutation。
- DB migration：本轮未新增；durable review / acknowledge 若后续必须持久化，必须另起 DB schema review。
- Safety flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
- 验证状态：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）。
- 下一步只能是提交前复核、commit，或后续另起 GateT 任务；不得直接进入 frontend workbench、Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。
