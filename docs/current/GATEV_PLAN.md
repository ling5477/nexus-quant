# GateV 受控验证自动化与人工复核生命周期计划

> 状态：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateV-1、GateV-2、GateV-3A、GateV-3、GateV-4 均为 `ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
> 英文名：`Controlled Validation Automation & Durable Operator Review`。
> 本计划是 GateV 唯一 active plan；GateV-3 scheduler 与 GateV-4 已接受，GateV-FREEZE 为 `NOT STARTED`（未开始）；唯一下一动作是 GateV freeze closeout implementation。

## 1. Current Baseline

- Current authority：以 [STATUS.md](STATUS.md) 的 `nq-current-authority` 为唯一阶段事实源。
- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gateu-freeze`。
- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；不得写成 accepted、frozen 或 tagged。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `f7d71d5a80241ade049a83fa3f90b3ac6ce46806`，acceptance head `b3dd5f74f154d5ed9e2343bc18e451f48770814f`，CI run `29144345430` 为 `completed / success`。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit 与 acceptance head 均为 `99158738ec980f519637af8df75e4153dfa2869f`，CI run `29150549978` 为 `completed / success`。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit 与 acceptance head 均为 `45c7df9799c0534ddd3ee291dc9347076dec9ddd`，CI run `29152330658` 为 `completed / success`。
- GateV-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33`，acceptance head `b209c416e0daf402216140b62785726f5fd116b6`，CI run `29155396719` 为 `completed / success`。默认配置仍关闭，不表示生产启用。
- GateV-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `d7da91a662be1f0fc0bbf64df70ea57318773697`，acceptance head `fad9b20900b49fbb918288f8d32d09fc60976444`，exact-HEAD `NQ CI Baseline` run `29181214506` 为 `completed / success`。frontend review、API contract、权限、幂等与 E2E 已接受。
- GateV-FREEZE：`NOT STARTED`（未开始）；Python manifest preview 保持 No-file residual，GateV 尚未 freeze、archive 或 tag。
- LIVE `DISABLED`，Shadow trading `NOT ENABLED`，AI `NOT STARTED`，DH runtime `NOT INTEGRATED`，Integration runtime `NOT STARTED`，real provider / private trading `NOT IMPLEMENTED`。

## 2. GateU Freeze Evidence

- [GateU durable archive](../gates/gate-u/README.md) 已通过 strict manifest、remote tag 与 tagged-commit CI 检查。
- GateU 已冻结五来源 runtime evidence：`SHADOW_VALIDATION_WORKFLOW`、`SHADOW_RUNS`、`CONSISTENCY_EVIDENCE`、`INCIDENT_REPLAY_REVIEW`、`EVALUATION_ARTIFACT_PREVIEW`。
- Aggregate 仅做 request-time GET 聚合；全来源 `AVAILABLE` 才为 `AVAILABLE`，全来源 `AVAILABLE / FRESH` 才为 `FRESH`，其他情况 fail-closed。
- 固定安全语义为 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`；GateU 未实现 scheduler、operator action persistence 或 Python file reader。

## 3. GateV Objective

GateV 将 GateU 的只读证据能力推进为受控、可关闭的本地证据检查和 durable operator review lifecycle：建立可审计的 case/event 事实、`OPEN -> ACKNOWLEDGED / ESCALATED -> RESOLVED -> CLOSED` 非交易生命周期、有限本地写侧 API、默认关闭的只读 scheduler 边界，以及受控 Python manifest preview 的后续边界。所有状态只表达诊断与人工复核，不表达交易授权。

## 4. GateV Non-goals

- 不启用 LIVE 或 Shadow trading，不实现真实交易、private trading adapter、real provider、real permission probe、真实账户/余额读取、下单、撤单、转账或提现。
- 不修改 strategy publish、evaluation result、Paper/Shadow run、risk decision、account、order、ledger 或交易状态。
- 不接 AI、DH runtime 或 Integration runtime。
- 不执行 Python、不导入策略、不训练、不访问外网，不把 artifact 写成 trading-ready。
- 本轮不实现 migration、API、scheduler、frontend 或 Python；只形成计划和首切片选择。

## 5. Existing Capability Inventory

| 能力 | 真实现状 | GateV 结论 |
| --- | --- | --- |
| Runtime evidence aggregate | 五来源 GET-only、request-time、fail-closed | 保留为只读来源；不得被 scheduler 提升为交易状态 |
| Shadow Validation Workflow / Shadow Run | 从本地表派生 operator item、overview、event/snapshot/consistency evidence | 只作为 evidence anchor，不修改其状态机 |
| Paper/Shadow consistency | SELECT-only overview/drilldown | 只作为 review source，不回写 report |
| Incident / Replay Review | 从 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records` 派生 recommendation | 当前明确 `DERIVED_REVIEW_ITEM_NOT_PERSISTED`，不能承载 durable lifecycle |
| Evaluation Artifact Preview | Java No-file baseline，`UNAVAILABLE / UNKNOWN`；Python 仅有离线 artifact schema/checksum 校验 | GateV 最多另做 allowlisted manifest preview；当前不读文件 |
| RBAC / identity | HTTP GET 需 authenticated；其他 `/api/**` 写侧仅 `ADMIN / OPERATOR`；用户/角色与 `owner_user_id` 约定已存在 | GateV 写侧必须进一步做 case-level owner boundary |
| Scheduler | 现有 `@Scheduled` 涉及 Paper matching、exchange reconcile/recovery、ledger reconcile | 全部禁止复用；GateV 需要独立、默认关闭的 no-exchange scheduler |
| Idempotency / locking / audit | Shadow Run 有 idempotency/version/event；`audit_logs` / `event_store` 可参考 | 复用约定，不复用交易/Shadow 事实表 |
| Frontend | `/strategies/validation` 已有 Validation Operations Workbench 与五来源只读 panel | 后续在原页面增加 review queue/detail/actions，不新增 route |

## 6. Durable Review State Decision

结论：**需要新增 durable operator review state，且不得复用现有 incident/replay/Shadow/event 表作为 case 主事实。**

原因：现有 Incident / Replay Review item 是 deterministic derived DTO，没有 owner、tenant scope、version、idempotency、actor、动作时间或 durable event stream；现有 incident/replay 表分别服务 Paper/Shadow/recovery 事实，复用会把人工复核生命周期反向耦合到交易或运行状态。GateV 新增独立 `validation_review_cases` 与 append-only `validation_review_events`，只保存脱敏本地 evidence anchor 和人工复核事实。

建议 case 字段：`id`、`tenant_key`、`owner_user_id`、`source_type`、`source_id`、`source_version`、`severity`、`state`、`summary`、`version`、`created_by`、`created_at`、`updated_by`、`updated_at`、`resolved_at`、`closed_at`、`retention_until`。`summary` 禁止 raw JSON、credential、账户余额、订单 payload 或 private provider material。

建议 event 字段：`id`、`case_id`、`tenant_key`、`owner_user_id`、`event_type`、`from_state`、`to_state`、`case_version`、`actor_user_id`、`actor_roles`、`idempotency_key`、`request_hash`、`request_id`、`trace_id`、`reason`、`metadata`、`occurred_at`。events 只追加，不更新、不删除。

Case 可由后续受权 lifecycle action 基于已验证的本地 evidence anchor 在同一事务内 materialize 为 `OPEN`，随后完成动作；不得由 GET、scheduler 或页面加载自动创建。GateV-1 只提供 domain factory/repository contract 与测试，不接 runtime 入口。

## 7. Review State Machine

```text
OPEN -> ACKNOWLEDGED
OPEN -> ESCALATED
ACKNOWLEDGED -> ESCALATED
ACKNOWLEDGED -> RESOLVED
ESCALATED -> RESOLVED
RESOLVED -> CLOSED
CLOSED -> terminal
```

- 禁止跳过复核直接 `OPEN -> RESOLVED/CLOSED`；禁止从 `CLOSED` reopen。
- 同一 idempotency key 的完全相同请求返回首次结果；新 key 的重复目标状态不是隐式成功，按非法流转处理。
- `ACKNOWLEDGED` 只表示操作员已看见；`ESCALATED` 只表示升级人工处理；`RESOLVED` 只表示复核问题已处理；`CLOSED` 只表示本地 case 关闭。任何状态均不是交易批准。
- 禁止出现 `APPROVED_FOR_TRADING`、`TRADE_AUTHORIZED`、`LIVE_READY`、`CAN_TRADE`、`ORDER_APPROVED`。

## 8. Operator API Plan

建议 base path：`/api/validation-review`。

只读：

- `GET /cases`：按当前 tenant/owner、state、severity、sourceType 分页查询。
- `GET /cases/{caseId}`：返回 case、version、evidence anchors 与固定安全边界。
- `GET /cases/{caseId}/events`：返回 append-only event stream。
- `GET /runtime-evidence`：代理本地 aggregate query service，不内部 HTTP fan-out。

有限写入：

- `POST /cases/{caseId}/acknowledge`
- `POST /cases/{caseId}/escalate`
- `POST /cases/{caseId}/resolve`
- `POST /cases/{caseId}/close`

写请求必须包含 `idempotencyKey`、`expectedVersion`、`requestId`、可选脱敏 `reason`；`traceId` 由 `TraceIdContext` 生成或承接可信 request context。不存在的 deterministic case 仅允许 `acknowledge` 或 `escalate` 基于服务端重新校验的本地 evidence anchor 原子 materialize；客户端不得提交任意 source payload。

错误语义：`401 UNAUTHORIZED`、`403 REVIEW_ACTION_FORBIDDEN`、`404 REVIEW_CASE_NOT_FOUND`（同时隐藏跨 owner 资源）、`409 REVIEW_CASE_VERSION_CONFLICT`、`409 REVIEW_STATE_TRANSITION_INVALID`、`409 IDEMPOTENCY_KEY_REUSED`、`422 REVIEW_EVIDENCE_UNAVAILABLE`。所有响应继续返回 `notTradingAuthorization=true` 与 `liveDisabled=true`。

## 9. RBAC / Tenant / Owner Boundary

- 复用现有 `ADMIN / OPERATOR`，不在 GateV 新增角色体系。GET 允许 authenticated `ADMIN / OPERATOR`；写侧仅 `ADMIN / OPERATOR`。
- `OPERATOR` 只能读取和变更 `owner_user_id` 等于当前认证用户的 case；`ADMIN` 可在同一 tenant 内跨 owner 复核，并记录 actor/owner 差异。
- 当前仓库没有通用 tenant entity/token claim；GateV 使用服务端固定 `tenant_key=NQ_LOCAL` 作为 fail-closed 单租户 scope，禁止接受客户端 tenant override。未来真实 multi-tenant 必须另起 contract/schema 任务。
- 所有 repository 查询和 mutation 必须同时带 `tenant_key + owner_user_id/case_id` 条件；跨 scope 统一返回 not found，禁止通过 ID 枚举。

## 10. Idempotency / Optimistic Locking / Audit

- `validation_review_cases.version` 从 0 起，每个 accepted transition `version + 1`；update 使用 `WHERE id=? AND tenant_key=? AND version=?`。
- `validation_review_events` 对 `(case_id, idempotency_key)` 建 unique constraint；保存 canonical request hash。相同 key+hash 返回首次结果，相同 key+不同 hash 返回 `IDEMPOTENCY_KEY_REUSED`。
- Case update 与 accepted event append 必须同事务提交；event 的 `case_version` 必须等于 transition 后版本。
- Accepted transition 进入 `validation_review_events`；鉴权拒绝、version conflict、非法流转和 scheduler failure 只写脱敏 operational audit，不得写伪造的状态 transition。
- `requestId` 用于业务请求去重/追踪，`traceId` 用于链路关联；两者均不可承载用户 payload 或敏感材料。

## 11. Scheduler Decision

GateV **允许一个新的受控只读 scheduler**，但必须后置到 durable facts 与 lifecycle API 之后，且不能复用现有 Paper、exchange reconcile/recovery、ledger 或交易维护 scheduler。

安全模型：默认 `enabled=false`；仅显式配置开启；CI profile 固定关闭；仅直接调用本进程本地 query ports；禁止 HTTP、adapter、credential、account、order、ledger、Paper/Shadow mutation；不得创建 review case；bounded batch、query timeout、run timeout、单实例 DB advisory lock 或独立 lock table、overlap prevention；每轮生成 requestId/traceId；成功/失败仅追加脱敏 operational audit/metrics；支持配置热切换或重启禁用。锁获取失败视为 safe skip，不并发补跑。

Lock prerequisite acceptance evidence：`SchedulerExecutionLock` contract 与 `SchedulerLockKey` / `SchedulerLockExecution` 保守结果模型位于 `nq-scheduler-contracts`；`nq-infra` 使用 `pg_try_advisory_xact_lock(int,int)`、SHA-256 UTF-8 big-endian 稳定 key mapping 及 `REQUIRES_NEW` read-only transaction；`nq-app` 只负责 Bean composition。Implementation commit 与 acceptance head 均为 `45c7df9799c0534ddd3ee291dc9347076dec9ddd`，`NQ CI Baseline` run `29152330658` 为 exact-head `completed / success`。GateV-3A 不包含 `@Scheduled`、GateU aggregate 调用、migration、API、业务 callback 或业务写侧，且不表示 scheduler 已启用。

已知 limitation：transaction timeout 可约束 JDBC/transaction 操作，callback 返回后也会按 elapsed time 返回 `TIMED_OUT`；但任意非 JDBC、无限阻塞且不响应 interrupt 的 callback 无法由该 primitive 主动终止。GateV-3 必须继续使用 bounded、可取消的只读 callback，并把 run timeout 与 safe failure audit 作为独立实现验收项。

## 12. Python Manifest Preview Boundary

GateV 只规划 manifest preview，不在首切片实现：

- 根目录必须来自显式 allowlist 配置，canonical path 必须仍位于 allowlisted root；拒绝绝对路径、`..`、symlink escape 和 path traversal。
- 只读固定 manifest 文件；限制单文件 size、item count 与总扫描量；校验 `schemaVersion`、artifact path、checksum、declared size 和实际 size。
- 不执行 Python、不 import strategy、不训练、不访问网络、不接受上传、不读取任意用户路径。
- manifest/artifact 缺失、schema 不支持、checksum/size/path 不一致均 fail-closed 为 `UNAVAILABLE / UNKNOWN`；checksum `VALID` 只表示完整性，不表示 trading-ready。
- GateV-4 选择 review workbench，因此 manifest preview 保留为 GateV freeze 可接受 residual；Java No-file baseline 不变。

## 13. DB / Migration Decision

需要新增一份 forward-only Flyway migration 创建 `validation_review_cases` 与 `validation_review_events`，包含中文 table/column COMMENT、state/event CHECK、owner FK、case/source/owner/state 索引、version 非负约束、event idempotency unique constraint 和 append-only 约定。不得修改历史 migration，不做交易表 backfill。

保留策略：case 在 `CLOSED` 后默认保留 180 天，events 与 case 同寿命；GateV 不实现自动删除，`retention_until` 只记录政策边界。任何 hard delete/归档 job 必须另起数据治理任务。

回滚：先回滚应用 wiring，使新表 dormant；Flyway 已应用后不执行 down migration、不编辑历史文件。若必须移除，另建经审查的 forward cleanup migration，并在删除前导出审计证据。GateV-1 必须在同一代码任务内执行独立 schema review、migration contract test 与 PostgreSQL/Testcontainers 验证；不另开 planning review。

## 14. GateV-4 Review Workbench Plan

- Status：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- Formal task ID：`NQ-GATEV-4-REVIEW-WORKBENCH-IMPLEMENTATION`。
- 目标：复用 GateV-2 已接受的本地 review case list/detail/events/lifecycle API，在既有 `/strategies/validation` Validation Operations Workbench 中增加 review queue、case detail、lifecycle event timeline，以及 acknowledge、escalate、resolve、close 四个有限动作；不新增 route。
- 默认前端范围：React/Vite/Ant Design、state/severity/owner 等既有查询条件、loading/empty/error/conflict/permission-denied 状态、optimistic version conflict、duplicate idempotency、危险操作确认、TanStack Query cache invalidation 与保守安全提示。
- 既有 API 依赖：`GET /api/validation-review-cases`、`GET /api/validation-review-cases/{caseId}`、`GET /api/validation-review-cases/{caseId}/events`，以及同一 case 路径下的 `POST .../acknowledge`、`POST .../escalate`、`POST .../resolve`、`POST .../close`。GateV-4 不新增或修改 backend endpoint；若最小闭环存在 API gap，implementation 必须先明确报告并 fail closed，不得自行扩大后端范围。
- 禁止范围：不新建 review case，不删除或 reopen，不增加 approve/authorize/execute/trade 或自动 lifecycle transition；不实现 Python manifest UI，不修改 GateV-3 scheduler，不新增 migration，不触碰 credential、账户、余额、订单、Ledger、LIVE、Shadow trading、AI、DH 或 Integration runtime。
- 测试方向：frontend build 与 targeted Playwright smoke，覆盖 queue/detail/events/actions、loading/empty/error、403/404/409、version conflict、duplicate idempotency、permission denied、危险操作确认和 cache invalidation；本 planning task 不运行这些实现期测试。
- 安全语义：所有 review 状态和操作只表达本地人工复核，不代表 trading authorization；页面必须持续展示 `LIVE DISABLED` 与 `not trading authorization`。
- 实现结果：已在既有 route 接入 module-separated types/API/query keys/hooks/queue/detail/events/actions；ADMIN 才显示 owner filter，OPERATOR 不传 ownerId；selection 使用 `reviewCaseId` URL 参数恢复；mutation 使用真实 `Idempotency-Key` 与 `expectedVersion/reason` body，不自动 retry，成功后刷新 queue/detail/events，409/422 fail-closed refetch。
- 专项 review：确认并最小关闭 4 个 P1，覆盖 conflict 后三类 query refresh、精确 mock endpoint 与网络断言、`reviewCaseId`/未知 state fail-closed、UUID 生成失败与确认 case 可见性；未扩大 GateV-4 原范围。
- 验收证据：implementation commit `d7da91a662be1f0fc0bbf64df70ea57318773697` 是 acceptance head `fad9b20900b49fbb918288f8d32d09fc60976444` 的 ancestor；该 acceptance head 对应 `NQ CI Baseline` run `29181214506` 为 exact-HEAD `completed / success`。frontend review、API contract、权限、幂等、targeted Playwright 与既有页面 E2E 已接受。

## 15. Test and CI Strategy

- GateV-1：migration contract、PostgreSQL/Testcontainers schema、domain state machine、repository create/read/transition/event append、optimistic conflict、tenant/owner isolation、idempotency replay/mismatch、sensitive-field guard。
- GateV-2：Controller/Service/Repository integration，401/403/404/409/422，case materialization、并发 transition、event/audit 原子性，禁止交易状态变化。
- GateV-3：默认关闭、CI 关闭、lock contention、overlap prevention、bounded batch、timeout、failure audit、no adapter/credential/order/Shadow invocation。
- GateV-4：frontend build 与 targeted Playwright，覆盖 queue/detail/actions/conflict/boundary；不新增 route。
- 每个 implementation batch 必须通过 scoped tests 与 `mvn -f backend/pom.xml test` 或有明确模块化理由；前端批次执行 build/E2E。Freeze 前执行全量 backend、frontend、docs checker 和 exact-HEAD CI。

## 16. Security / Credential / LIVE Boundary

- Review summary/reason/metadata 只允许脱敏文本和 allowlisted keys，长度受限；禁止 credential、token、cookie、private key、签名、headers、raw request/response、真实账户余额或订单 payload。
- Scheduler、API、repository 与 manifest reader 均不得依赖 credential service、private endpoint 或交易 adapter。
- `AVAILABLE`、`FRESH`、`ACKNOWLEDGED`、`RESOLVED`、`CLOSED`、checksum `VALID` 均不表示 LIVE readiness、交易放行或风险批准。
- NQ-only；不修改或声明 DH current authority，不启动 NQ-DH Integration runtime。

## 17. P0 / P1 / P2 / P3 Risks

- P0：无当前阻断；若实现出现交易授权字段、真实外联、credential read 或交易状态 mutation，立即阻断。
- P1：复用 incident/Shadow/交易 event 表会污染事实边界；缺 owner/version/idempotency 会造成越权或重复动作；migration 未经独立 schema review 不得提交。
- P2：scheduler overlap/timeout、case materialization 竞态、idempotency key hash 不一致、retention 未执行可能造成操作噪声或存储增长。
- P3：现有页面信息密度、No-file artifact residual、单租户 `NQ_LOCAL` 限制需要在 UI/文档中持续可见。

## 18. GateV Batch Plan

| Batch | 唯一交付 | 状态边界 |
| --- | --- | --- |
| GateV-0 | 本计划、架构决策、首切片选择 | `PLAN / NOT IMPLEMENTED` |
| GateV-1 | Durable Review Fact Model：两表 migration、domain state machine、repository、测试与同轮 schema review | `ACCEPTED / CI GREEN`；无 API、scheduler、frontend |
| GateV-2 | Operator Review Lifecycle API：GET、acknowledge/escalate/resolve/close、RBAC、owner scope、idempotency、audit | `ACCEPTED / CI GREEN`；仅本地 review 写侧，不改交易/运行事实 |
| GateV-3A | PostgreSQL Advisory Scheduler Lock Prerequisite：通用 contract、transaction-level try lock、稳定 key mapping、Spring composition、真实并发测试 | `ACCEPTED / CI GREEN`；无 `@Scheduled`、migration、业务 callback 或业务副作用 |
| GateV-3 | Controlled Read-only Scheduler：默认关闭、local query、lock/timeout/bounded batch、failure audit | `ACCEPTED / CI GREEN`；专项 review 无 P0/P1，exact-HEAD CI green；不创建 case、不外联、不改 Paper/Shadow/交易状态 |
| GateV-4 | Review Workbench：正式任务 `NQ-GATEV-4-REVIEW-WORKBENCH-IMPLEMENTATION`；既有页面 queue/detail/events/actions 与 targeted E2E | `ACCEPTED / CI GREEN`；implementation `d7da91a...`、acceptance head `fad9b209...`、CI `29181214506`，不实现 Python manifest preview，不新增 route |
| GateV-FREEZE | manifest 驱动归档、全量验证、exact-HEAD CI、tag handoff | `NOT STARTED`；不新增实现范围 |

## 19. Freeze Acceptance Criteria

- 两表 schema、state machine、owner/tenant、optimistic lock、idempotency 和 append-only audit 有真实测试证据。
- API 仅包含计划内 GET 与四类有限 lifecycle POST；非法流转、越权、重复请求与并发冲突 fail-closed。
- Scheduler 默认/CI 关闭、无 overlap、bounded/timeout、无 exchange/credential/order/Shadow side effect；不创建 case。
- Review workbench 完成 build 与 targeted E2E，并明确所有状态不是交易授权。
- Python manifest preview 可继续保持 No-file `UNAVAILABLE / UNKNOWN` 作为明示 residual；不得伪写为已实现。
- backend/frontend/docs checker 与 Gate archive manifest 通过，freeze commit exact-HEAD CI 为 `completed / success` 后才允许 tag。

## 20. Selected First Implementation Slice

选择 **方案 A：Review Case Local Fact Model**。

真实仓库没有可复用 durable review fact；现有 review DTO 明确为派生、未持久化的 recommendation。GateV-1 必须先建立独立事实基础，才能安全实现 lifecycle API 或 scheduler。首切片只包含 migration、domain/state machine、ports/repository 与测试；无 API、scheduler、frontend。migration 的高风险 schema review 在同一实现任务内完成，不产生新的 GateV planning/review 文档。

实现结果：`V33__gate_v_validation_review_fact_model.sql`、review domain/state machine、transaction application boundary、JDBC repository、状态机/migration/真实 PostgreSQL tests 已落地；专项 review 补齐并发幂等、list-order index 与 DB legal-transition CHECK 后通过。该接受只适用于 GateV-1，不得把 GateV 整体写为 accepted 或 frozen。

- 方案 B 后置：没有 durable case/version/event/idempotency 基线时直接做 API 会迫使复用错误表或产生无事实写侧。
- 方案 C 后置：automation 先于 durable lifecycle 会放大重复、并发与审计缺口；且现有 scheduler 都具有不适合 GateV 的 Paper/exchange/recovery/ledger 语义。

## 21. Next Concrete Task

下一轮唯一任务名：

```text
NQ-GATEV-FREEZE-CLOSEOUT-IMPLEMENTATION
```

GateV-4 已提升为 accepted baseline：implementation commit `d7da91a662be1f0fc0bbf64df70ea57318773697` 是 acceptance head `fad9b20900b49fbb918288f8d32d09fc60976444` 的 ancestor，acceptance head exact-HEAD `NQ CI Baseline` run `29181214506` 为 `completed / success`。GateV-FREEZE 已初始化为 `NOT STARTED`。

机器 authority 为 `accepted_batch=GateV-4 / ACCEPTED|CI_GREEN`，并初始化 `work_batch=GateV-FREEZE / NOT_STARTED / NONE / NOT_RUN`。下一轮只允许 GateV freeze closeout implementation；本计划不表示 freeze 已实现，不授权 trading authorization、scheduler 生产启用、LIVE 或 Shadow trading。
