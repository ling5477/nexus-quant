# NQ-GATET-6-RUNTIME-SCHEDULING-READINESS-WO

Status: `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

Scope: NQ-only；documentation-only；Runtime Scheduling Readiness Review work order。本文只定义事实源、边界、候选只读运行模式和测试计划，不实现 scheduler、runner、runtime、API、migration、frontend、backend、Python 或 CI。

## 1. GateT-6 Objective

GateT-6 的目标是审查当前系统是否具备后续规划“只读诊断刷新 / operator review refresh / evidence snapshot refresh”的基础，并给出 runtime scheduling readiness matrix（运行调度就绪矩阵）。

GateT-6 是 readiness review，不是 runtime implementation。它不启动 scheduler、不启动 runner、不创建 Paper run、不创建 Shadow run、不创建 consistency report、不创建 incident / alert / replay / review 记录、不 append event、不触发 Paper / Shadow / LIVE 执行。

## 2. Current Runtime / Scheduler Fact Sources

| Fact source | Current behavior | GateT-6 decision |
| --- | --- | --- |
| `StrategyScheduleScanService` | 扫描 enabled strategy schedules，可触发 strategy run dispatch 并更新 lastTriggeredAt。 | 只读审查事实源；不得复用为 GateT-6 refresh，因为存在 run dispatch / schedule mutation 语义。 |
| `StrategyScheduleService` | 创建、启用、禁用、列出 strategy schedule，并维护触发时间。 | 只读审查事实源；GateT-6 不创建或修改 schedule。 |
| `ShadowRunRunnerService` | Shadow Run runner skeleton；写入 Shadow Run local facts、audit、snapshots、events 并转换 run 状态。 | 禁止启动；即使不触发真实交易，也会创建 run / event / snapshot，不符合 GateT-6 no-side-effect 边界。 |
| `ShadowRunRunnerStep` | 注释明确 step 仅用于 local audit / tests，不代表 scheduler、background task 或 Shadow Live trading。 | 只作为边界证据；不得包装成 GateT-6 runtime。 |
| `PaperRunScheduleService` | `runScheduleOnce` 会写入 schedule fire，`runHeartbeatOnce` 会写入 heartbeat。 | 禁止启动；会创建运行记录。 |
| `PaperRunMonitorRunService` | 检测 heartbeat lag / failed schedule fire，并可能创建 alert。 | 禁止启动；会创建 alert。 |
| `PaperRunRecoveryService` | 写入 recovery records，支持恢复或重试失败步骤。 | 禁止启动；会创建 recovery event。 |
| `PaperTradingScheduleController` | 暴露 schedule list / create / update / run-once 等 paper scheduling endpoints。 | GateT-6 不新增、不调用、不消费写侧 endpoint。 |
| `backend/nq-scheduler/**` | 包含 `@Scheduled` maintenance / reconcile / recovery 路径，并可能访问交易所 adapter 或写 audit / event。 | GateT-6 禁止接入；不能作为 read-only refresh 基线。 |
| `StrategyValidationPage` / `ValidationOperationsWorkbench` | 现有页面通过 TanStack Query 手动 refetch GateT GET-only overview 数据。 | 可作为当前人工只读刷新事实源；本轮不实现自动刷新。 |
| `research/py/src/nq_research/evaluation/**` | 离线 Python evaluation artifact baseline，标记 `diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`。 | 只读代码事实源；GateT-6 不执行 Python、不读取 artifact 文件、不接 artifact source。 |

## 3. Existing GateT Capabilities Reused

- GateT-1：`GET /api/shadow-validation/workflow/overview`，derived / deterministic / not persisted operator workflow overview。
- GateT-2：`GET /api/paper-shadow/consistency/evidence/overview`，derived / deterministic consistency evidence overview，不创建 report。
- GateT-3：`GET /api/incidents/replay/review/overview`，derived / deterministic review items，不创建 review / acknowledge / escalation / closeout / incident / alert / replay 记录。
- GateT-4：`GET /api/strategy-validation/evaluation-artifacts/preview/overview`，No-file baseline，不读取 artifact 文件、不执行 Python。
- GateT-5：现有 `/strategies/validation` 页面内 `ValidationOperationsWorkbench`，手动整合 top summary、evidence matrix、operator queue preview、boundary strip 和 detail sections；不新增 route、API、migration、写侧操作或交易入口。

## 4. Runtime Scheduling Readiness Definition

GateT-6 readiness 表示：当前 facts、read-only endpoints、frontend workbench 和测试策略是否足以支持未来另起任务规划一个 no-side-effect diagnostic refresh。

GateT-6 readiness 不表示：

- scheduler 已实现或已启动。
- runner 已实现或已启动。
- GateT overview 已自动刷新。
- Paper / Shadow / LIVE run 可以被创建。
- consistency report、event、incident、alert、review 或 recovery record 可以被自动创建。
- LIVE、real provider、RealClient、private trading adapter、real permission probe、AI runtime、DH runtime 或 Python live execution ready。
- validation pass 等于 trading authorization。

## 5. Non-goals

- 不实现 scheduler / runner / runtime。
- 不新增 API、DTO、Controller、Repository、SQL、migration。
- 不改 frontend、backend、research、scripts、deploy、CI。
- 不新增 route、button、auto-refresh、polling、upload、import、file path input 或 Python execution。
- 不创建 Paper run、Shadow run、consistency report、incident、alert、replay、review、recovery record、event 或 snapshot。
- 不新增 approve / reject / acknowledge / escalate / closeout 写侧操作。
- 不新增 start / stop / execute / trade。
- 不调用真实交易所，不读取 credential，不接 AI / DH runtime。

## 6. Candidate Strategy Decision

Selected strategy: **1. Readiness-review only**（只输出 readiness matrix，不实现 runtime）。

Reason:

- 当前 backend 已存在 strategy / paper / shadow / scheduler 运行能力，但多数具有写侧、runner、alert、recovery、adapter 或状态转换语义，不满足 GateT-6 no-side-effect 边界。
- GateT-1 到 GateT-5 已提供 GET-only overview 和手动 frontend refetch，足够支持本轮 readiness review。
- No-op scheduler contract plan 或 read-only refresh plan 可以作为未来候选，但本轮若进入 contract / endpoint / job 设计会扩大范围并误导为 runtime started。

Rejected:

- **2. No-op scheduler contract plan**：本轮不定义 runtime contract，避免被误解为 scheduler implementation 输入。
- **3. Read-only refresh plan**：只在候选矩阵中记录未来可能性，不定义 endpoint / job，也不实现。

## 7. No-side-effect Scheduling Boundary

未来若另起任务规划 no-side-effect scheduler，只能限定为只读诊断刷新：

- 只能读取既有 GET-only overview fact 或等价 read-only query。
- 只能刷新内存 / 前端 query / operator review display 语义，不创建持久化记录。
- 不得调用 POST / PUT / PATCH / DELETE。
- 不得创建 Paper run、Shadow run、report、event、snapshot、incident、alert、replay、review、recovery record。
- 不得启动任何 runner、strategy dispatcher、paper schedule fire、heartbeat、monitor、recovery、exchange reconcile 或 maintenance job。
- 不得读取 credential、调用真实交易所、执行 Python 或访问 artifact 文件。
- 必须 fail-closed：`notTradingAuthorization=true`、`liveDisabled=true`。

## 8. Read-only Refresh Candidate Matrix

| Candidate | Input | Output | Allowed now | Reason / guard |
| --- | --- | --- | --- | --- |
| Manual operator refresh | Existing frontend TanStack Query refetch | Updated UI view only | Yes, already exists | 只触发 GET-only queries；不创建 server record。 |
| Readiness matrix refresh | Docs / code facts | Documentation decision only | Yes, this work order | 本轮唯一落地内容。 |
| Future read-only endpoint refresh | Existing GET-only overview services | Existing response shape or aggregate view | No, plan only | 若未来需要，必须另起 work order；默认不新增 API。 |
| Future background no-op scheduler | Existing GET-only overview services | No persistent output | No | 风险高，必须证明无 runner start、无 write side effect、无 credential / exchange / Python。 |
| Existing strategy scheduler reuse | Strategy schedule scan / dispatch | Strategy run dispatch / schedule mutation | No | 存在 run dispatch 和 schedule state update。 |
| Existing paper scheduler reuse | Paper schedule fire / heartbeat | Schedule fire / heartbeat records | No | 会创建运行记录。 |
| Existing shadow runner reuse | Shadow run runner | Run events / snapshots / status transitions | No | 会创建 Shadow run facts。 |
| Existing monitor / recovery reuse | Monitoring / recovery services | Alert / recovery records | No | 会创建 incident / alert / recovery facts。 |

## 9. Runner / Scheduler Forbidden Boundary

GateT-6 禁止：

- 启动 `StrategyScheduleScanService` 或任何 strategy dispatcher。
- 启动 `ShadowRunRunnerService` 或 `ShadowRunRunnerStep`。
- 启动 `PaperRunScheduleService.runScheduleOnce` / `runHeartbeatOnce`。
- 启动 `PaperRunMonitorRunService` 或 `PaperRunRecoveryService`。
- 调用 `PaperTradingScheduleController` 的 create / update / run-once 写侧接口。
- 接入 `backend/nq-scheduler/**` 中任何 `@Scheduled` maintenance / reconcile / recovery 路径。
- 启动真实交易所 adapter、private endpoint、order reconciliation、ledger reconciliation 或 recovery。

## 10. API / DTO Decision

Decision: **No new API / DTO**（不新增 API / DTO）。

GateT-6 当前只需要 work order 和 readiness matrix。现有 GateT GET-only endpoints 足以作为事实源；本轮不新增 endpoint、controller、client、query key、DTO 或 response shape。

## 11. DB / Migration Decision

Decision: **No DB migration**（不新增数据库迁移）。

GateT-6 不创建 runtime state、scheduler state、refresh state、job table、report table、review table、event table 或 artifact catalog。不得修改历史 migration。

## 12. Frontend Decision

Decision: **No frontend change**（不改前端）。

现有 `ValidationOperationsWorkbench` 已支持手动只读 refetch。GateT-6 不新增 auto-refresh、polling、scheduler status、start / stop / execute / trade button、review / acknowledge / approve / reject / escalate / closeout 写侧入口。

## 13. Python Decision

Decision: **No Python execution / no artifact file source**（不执行 Python / 不接 artifact 文件源）。

GateT-6 只阅读 Python evaluation artifact baseline 代码事实，不执行 `pytest`、`mypy`、`ruff`、Python 脚本、artifact reader、manifest reader 或 artifact file access。Python ML ready 仍为 `NO`；Python live execution ready 仍为 `NO`。

## 14. AI / DH Runtime Decision

Decision: **AI NOT STARTED / DH runtime NOT INTEGRATED**（AI 未启动 / DH runtime 未集成）。

GateT-6 不接 AI signal、AI runtime、AI Paper Trading、DH runtime、NQ-DH integration runtime、provider relay 或 external agent runtime。DH 不允许启动 Paper Run、不允许修改 NQ 交易状态、不允许访问 credential。

## 15. Testing Plan

Docs-only validation:

1. 本轮 docs-only，不运行 Maven / npm / Python，除非发现文档生成或格式检查要求。
2. 运行 Git / diff / forbidden-area checks，确认仅允许文档变更。
3. 运行 required `rg` safety scan，确认未把 runtime readiness 写成 LIVE ready、Shadow trading、AI started、DH integrated 或 trading authorization。

Future implementation validation, only if separately authorized:

1. 必须覆盖 no scheduler start。
2. 必须覆盖 no runner start。
3. 必须覆盖 no POST / PUT / PATCH / DELETE。
4. 必须覆盖 no report / event / run creation。
5. 必须覆盖 `notTradingAuthorization=true`。
6. 必须覆盖 `liveDisabled=true`。
7. 必须覆盖不读取 credential。
8. 必须覆盖不调用真实交易所。
9. 必须覆盖不执行 Python。
10. 必须覆盖不访问 artifact 文件。

## 16. Security / Credential Boundary

- 不读取 `.env`、credential、API key、exchange secret、tenant data、token、cookie、private key、passphrase。
- 不输出 credential 值。
- 不访问真实交易所 private endpoint。
- 不访问 artifact 文件、manifest 文件或用户目录。
- Readiness review 只能引用代码路径、文档路径、接口语义和测试计划。

## 17. LIVE / Real Provider Boundary

- LIVE: `DISABLED`（禁用）。
- RealClient: `NOT IMPLEMENTED`（未实现）。
- real provider: `NOT IMPLEMENTED`（未实现）。
- private trading adapter: `NOT IMPLEMENTED`（未实现）。
- real permission probe: `NOT IMPLEMENTED`（未实现）。
- Shadow trading: `NOT ENABLED`（未启用）。
- Runtime scheduling readiness 不等于 LIVE ready、trading ready、authorized for trading 或 can trade。

## 18. P0 / P1 / P2 / P3 Risk List

### P0

- 若把 existing scheduler / runner 复用为 GateT refresh，会创建 run / event / alert / recovery / schedule fire 或触发 adapter/reconcile，可能突破 no-side-effect 和交易安全边界。
- 若把 validation ready、checksum valid、consistency pass 或 operator recommendation 写成 trading authorization，会误导为可交易状态。

### P1

- 若未来新增 auto-refresh endpoint / job 时缺少 no-write tests，可能隐藏 POST / PUT / PATCH / DELETE 或持久化副作用。
- 若未来接入 Python artifact 文件源，可能读取未授权路径或把 artifact integrity 误写成策略有效。

### P2

- 现有 runtime / scheduler 事实源分散在 strategy、paper、shadow、scheduler、monitoring、recovery 模块；未来实现前必须再做 code-owner scoped review。
- GateT overview 当前依赖多个 GET-only endpoints；若未来做聚合 refresh，需防止错误传播和过度轮询。

### P3

- 当前仅形成文档 readiness matrix，未提供自动化 guard；后续实现任务需要补测试而不是沿用本轮 docs-only 结论。

## 19. Acceptance Criteria

- 新增 GateT-6 work order 文档，状态为 `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`。
- 明确选择 `Readiness-review only`。
- 明确 scheduler / runner / runtime / API / DTO / migration / frontend / Python / CI 均不实现。
- 明确 no-side-effect scheduling boundary 和 read-only refresh candidate matrix。
- 明确 LIVE、real provider、credential、AI / DH runtime、Python artifact source 禁止边界。
- 明确后续 implementation 的最小测试计划与 forbidden boundary。
- current docs 入口、状态、路线、验证、工作记录和事实源索引口径一致。

## 20. Next Concrete Action

提交本 docs-only work order。推荐 commit message：

```text
docs(gatet): define runtime scheduling readiness work order
```

后续如需进入 GateT-6 implementation，必须另起任务，并先确认是否仍选择 no implementation；默认不得直接实现 scheduler、runtime、API、migration、frontend 或 Python。
