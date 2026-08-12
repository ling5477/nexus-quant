# GateY Implementation Plan

> 状态：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；当前实施批次为 GateY-2，但本计划本身不构成 LIVE 授权。
> 定位：GateY 是 `Single-Venue Micro-Live Gate`（单交易所微资金受控实盘阶段）的规划基线，不是 LIVE 授权。
> 安全边界：`LIVE=DISABLED`、`Shadow trading=NOT_ENABLED`、真实订单/撤单/转账/提现均为 0。未来能力使用 `CANDIDATE / NOT_IMPLEMENTED` 标记；不得据此修改当前 API 或 schema 事实。

## 1. Current Baseline

- GateX 已 `FROZEN / ACCEPTED / TAGGED`；freeze commit=`299ab30bd2e243314be2dc609cb244cd5388027b`，annotated tag=`nq-gatex-freeze`，tag object=`ef4deb25728601719d20b2c6c64af7905c73a92e`。
- GateX freeze exact-head CI run `31565353974`（completed / success）；最终 authority HEAD=`6413bc...`，authority-sync exact-head CI run `31565712836`（completed / success）。
- 当前 GateY=`IN_PROGRESS / NOT_FROZEN`；本计划只完成事实核对、目标、设计边界、批次与验收条件，不创建 GateY runtime capability。
- GateW 已接受的 168h OKX read-only soak 提供公共/受控私有只读、immutable release、systemd、失败终态、回滚和 incident evidence；它不证明交易权限、余额充分或真实下单能力。
- GateX 已接受的 release/admission/materialization 只允许受控创建 `CREATED / RELEASE_BOUND` Shadow fact；runner、scheduler、交易和外部网络调用为 0。
- GateX 保留 `PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 与 `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 两个 P2。二者均提升为 `FIRST_REAL_ORDER_HARD_GATE` blocker；关闭责任和 fail-closed 行为见第 6、20、21 节。

## 2. GateX Frozen Capability Matrix

分类只描述当前代码与已冻结证据，不表示 LIVE 授权。

| 能力 | 分类 | 当前事实与 GateY 差距 |
| --- | --- | --- |
| strategy release | `REAL_IMPLEMENTED` | release aggregate、manifest/provenance 与持久化已实现；仍需绑定精确 LiveSession 和 risk digest |
| release admission | `REAL_IMPLEMENTED` | fail-closed 纯决策与 immutable creation plan 已实现；未授权执行 |
| artifact verification | `REAL_IMPLEMENTED` | trusted-root checksum/verifier 已实现；stable-handle P2 未关闭 |
| V38 materialization guard | `REAL_IMPLEMENTED` | revision/identity/lock/race guard 已实现；production lock window P2 未测 |
| shadow session/run | `READ_ONLY_OR_SHADOW` | 可创建 `CREATED / RELEASE_BOUND` fact，runner 仅本地 skeleton；无真实执行 |
| portfolio risk summary | `PARTIAL_IMPLEMENTED` | Paper/diagnostic facts存在；无冻结的 LiveSession 风险集或真实账户聚合风险快照 |
| order preview | `READ_ONLY_OR_SHADOW` | LIMIT-only local preview；minimum notional、余额、fee、permission 仍可能 UNKNOWN |
| order state machine | `REAL_IMPLEMENTED` | 本地 order lifecycle 已实现；未与 GateY intent/receipt/session 状态机绑定 |
| order idempotency | `PARTIAL_IMPLEMENTED` | account/client-order 与本地 command 幂等存在；无跨 worker `intentId`/unknown-result 协议 |
| ledger | `REAL_IMPLEMENTED` | 现有 trade/position/ledger transactional posting 可复用；未验证真实 exchange receipt 绑定与收敛 |
| audit | `REAL_IMPLEMENTED` | append audit repository 与关键治理事件存在；需增加 GateY 事件类型和脱敏 contract |
| reconciliation | `PARTIAL_IMPLEMENTED` | bounded read-only order comparator 存在；缺 order/fill/account/position 全面收敛和 recovery case |
| kill switch | `PARTIAL_IMPLEMENTED` | durable、默认 `ENGAGED`、fail-closed read 与 engage 已实现；无安全 disengage 和 worker propagation 证据 |
| credential lifecycle | `REAL_IMPLEMENTED` | encrypted reference、rotate/revoke/disable/expire 与审计存在；GateY pilot scope/runtime visibility 未实现 |
| private endpoint capability | `READ_ONLY_OR_SHADOW` | OKX typed private read-only 在受控 profile 可用；mutating/funding endpoint 仍 default-deny |
| real provider | `NOT_IMPLEMENTED` | current authority 明确 `NOT_IMPLEMENTED`；adapter 类不构成获准 real provider |
| execution worker | `NOT_IMPLEMENTED` | 只有 GateW read-only soak worker；没有可接收 approved intent 的隔离执行 worker |
| deployment rollback | `REAL_IMPLEMENTED` | GateW immutable/root-owned release、canonical installer/verifier 与 rollback evidence 可复用 |
| backup / restore | `PARTIAL_IMPLEMENTED` | disposable DB restore drill 已有；未来 GateY schema 与 live facts 尚未纳入演练 |
| incident drill | `PARTIAL_IMPLEMENTED` | GateW 失败终态与 11 场景 drill 已有；缺真实执行特有 unknown/partial-fill/kill/reconcile drill |

任何 `REAL_IMPLEMENTED` 只说明该局部能力真实存在；`readiness`、`preview`、`shadow`、`diagnostic` 和 materialized admission 均不等于 `trading ready`。

## 3. GateY Objective

GateY 的唯一候选目标是 `OKX Spot Single-Venue Micro-Live`：单 venue、单 pilot account、单 owner、单 strategy release、单 execution window，symbol allowlist 仅 1～2 个高流动性现货交易对。每个 session 使用冻结的 release digest、risk-limit-set digest、credential reference 与人工批准事实。

资金和操作必须受以下硬边界约束：微量累计 pilot capital、单笔 notional cap、symbol position cap、每日 loss cap、LIMIT-only、显式启动、随时暂停、强制 kill switch、完整对账、可回滚。GateY-6 也不自动获得授权；只有全部 hard gate 通过且用户对具体 pilot 明确授权，才可进入第一笔真实订单任务。

## 4. GateY Non-goals

- 不支持 Binance 或任何第二 venue；不做跨 venue routing、套利或 failover trading。
- 不支持 margin、leverage、futures、options、borrow、market order、transfer、withdraw。
- 不做无人值守开盘、不做自动解锁 kill switch、不允许 KILLED/FAILED 后自动重下。
- 不新建第二套 order、trade、position、ledger、audit 或 reconciliation 事实。
- 不全面拆微服务；不改变交易核心状态机、策略核心算法或回测核心算法。
- 不接 AI/LLM trading、DH runtime、Integration runtime、online model serving、MLflow 或 DVC。
- 本计划不实现 Controller/Service/Repository/migration/worker/UI，不配置或读取 credential，不连接真实交易所。

## 5. Single-Venue Decision

GateY 唯一 venue 冻结为 **OKX Spot**，理由如下：

1. GateW 已对 OKX public/private read-only 边界完成 168h soak（656 条连续样本、hash chain 通过、forbidden/fallback/raw/secret=`0/0/0/0`），并验证 immutable release、systemd、stop/rollback 与 incident evidence。
2. 当前 capability matrix 对 OKX Spot 已有 typed public/read-only private operations、endpoint default-deny 与 LIMIT preview；复用面最小。
3. GateX 的 release/admission/artifact/materialization 链提供可绑定 strategy release 的 fail-closed 起点。

该决定只冻结设计范围，不授权 OKX TRADE。Binance 明确排除在 GateY 之外；现有 Binance adapter 不得在 GateY profile 装配或作为 fallback。

## 6. First Real Order Hard Gate

`FIRST_REAL_ORDER_HARD_GATE` 是逐项 `PASS` 的 AND gate。当前所有新增项初始为 `NOT_MET`；任一项未满足时必须保持 `LIVE=DISABLED`，Control Plane 不得创建可执行 intent，worker 不得调用 mutating endpoint。

| Hard gate | 关闭证据 | 责任批次 | 未关闭时 fail-closed |
| --- | --- | --- | --- |
| GateX release/admission evidence 有效 | tag/commit/CI、archive verifier、release/admission replay | GateY-1/2 | session 不得进入 APPROVED |
| strategy release digest 稳定 | exact manifest/artifact digest 与 trusted handle 绑定 | GateY-2/4 | WARMUP 拒绝 |
| 单策略 risk limit set 冻结 | persisted version + canonical digest + approver binding | GateY-1/2 | intent creation 拒绝 |
| LiveSession 与 OperatorApproval 持久化 | PostgreSQL constraints、RBAC、expiry/replay regression | GateY-2 | 无 approval 不得 WARMUP |
| ExecutionIntent/Receipt 可追溯 | intent→clientOrderId→exchange receipt→local order 全链回放 | GateY-3 | worker 不得接单 |
| `intentId` 幂等 | same-payload replay/concurrency/unknown-result tests | GateY-3 | duplicate/conflict 均拒绝或返回原结果 |
| private endpoint allowlist 冻结 | exact method/path allowlist + negative endpoint suite | GateY-4 | endpoint guard default-deny |
| scoped pilot credential 最小 TRADE | permission probe、独立 key、scope digest、expiry | GateY-4/6 | credential gate BLOCKED |
| transfer/withdraw 关闭 | account permission proof + negative endpoint probe | GateY-4/6 | immediate KILL，pilot 不启动 |
| IP allowlist 已配置 | venue-side sanitized permission fact + operator attestation | GateY-6 | pilot 不启动 |
| kill switch 传播已验证 | DB→Control Plane→worker bounded-latency drill | GateY-4/5 | state UNKNOWN/ENGAGED，禁止 intent |
| order/fill/account/position 对账 | fake contract + private read-only dry-run convergence evidence | GateY-3/5 | RECONCILIATION_BLOCKED |
| unknown-order recovery | crash-after-send、query-by-clientOrderId、no-blind-retry drill | GateY-3/5 | PAUSED + RECONCILIATION_BLOCKED |
| partial fill/cancel/retry | deterministic fake exchange scenarios + ledger invariants | GateY-3/5 | 禁止新 PLACE intent |
| immutable release/rollback | exact build、root owner/mode、rollback drill | GateY-4/5 | worker 不启动 |
| backup/restore drill | 包含 GateY schema 的 disposable restore + hash/count checks | GateY-5 | micro-live authorization 拒绝 |
| incident drill | session/worker/network/receipt/reconcile/secret scenarios | GateY-5 | micro-live authorization 拒绝 |
| production lock window | production-like volume clone 的 lock duration、timeout、long-txn/rollback evidence | GateY-5（设计在 GateY-1） | migration/deploy 与 first order 均阻断 |
| filesystem stable handle | verified-open handle 或 sealed content-addressed workspace；replacement/symlink/reparse race tests | GateY-4 | artifact 不可消费，worker 不启动 |
| operator 明确人工授权 | 对 exact session/release/risk/account/window hash 的未过期 approval | GateY-6 | session 保持 APPROVAL_PENDING/REJECTED |

## 7. Live Session Control Plane

Java Control Plane 是唯一 session orchestration authority。它负责创建候选 session、校验 release/risk/credential/kill/reconciliation gate、记录人工批准、产生不可歧义的 execution intent、接收 worker receipt 并驱动状态机。它不得直接持有 raw credential 或绕过 worker 调用交易所。

- 每个 session 精确绑定 `venue=OKX_SPOT`、一个 account、owner、release、risk set、1～2 symbols、execution window、capital cap 和 immutable approval scope。
- `OperatorApproval` 必须绑定 canonical session draft hash；批准后任何 scope 变化都使批准失效并回到 `APPROVAL_PENDING`。
- 高风险动作要求 `LIVE_APPROVER`；session creator 与 approver 必须是不同 authenticated identity。`VIEWER` 只读，`OPERATOR` 可提出/暂停/停止，`LIVE_APPROVER` 可批准/拒绝，`ADMIN` 只可执行已定义 emergency kill，不得绕过 hard gate。
- Control Plane 只将已批准、risk/credential/kill gate 均通过的 immutable intent 交给 worker；worker 不能创建或改变策略意图。

## 8. Data Model Candidates

以下均为 **future schema candidates / NOT IMPLEMENTED**。GateY-1 必须先独立审查字段、约束、索引、COMMENT、锁和迁移窗口；审查通过前不得创建 migration。

| Candidate | 最小字段/约束 | 复用与边界 |
| --- | --- | --- |
| `live_sessions` | `session_id UUID PK`、owner/account/release/risk/credential refs、venue/symbols/window/capital、state/version、scope/digests、timestamps；单活跃 account+venue partial uniqueness 候选 | 只做控制面 aggregate，不复制 orders/trades |
| `live_session_events` | monotonic event id、session id/sequence、from/to、command、actor/request/trace、reason、sanitized metadata、created_at；append-only | 状态历史和审计关联，禁止 UPDATE/DELETE |
| `operator_approvals` | approval id、session id、scope hash、release/risk digests、approver/role、decision、expires_at、reason；immutable unique active approval | 不保存 credential material |
| `execution_intents` | `intent_id UUID/ULID PK`、session sequence、action、canonical payload hash、clientOrderId、local order ref、status/version/lease、created_at | intent body immutable；只引用既有 order lifecycle |
| `execution_receipts` | receipt id、intent id、attempt no、sanitized exchange request/order ids、outcome、HTTP/category/error code、received_at、payload digest | append-only；raw private response/signature 禁止落库 |
| `live_position_snapshots` | session/account/symbol/as_of、qty/cost/mark/source、digest | 与既有 `positions` 对账，不成为第二主账 |
| `cost_slippage_facts` | intent/order/trade refs、fee/fee currency、expected/actual price、slippage/units、source/as_of | 从既有 trades/venue facts派生，可重建 |
| `portfolio_risk_snapshots` | session/as_of、capital/exposure/PnL/loss/open-order/data-quality/spread/slippage、risk digest、decision | append-only风险判定证据，不覆盖 ledger |
| `reconciliation_cases` | case id、session、scope、local/remote snapshot digests、status/severity、unknown refs、resolution、actor/timestamps | 聚合差异与人工闭环；底层 facts 仍复用现有表 |

所有表必须含中文 `COMMENT`，状态字段有 CHECK，分页/retention 有界，tenant/account 访问边界在后端校验。`ExecutionIntent` body 与 approval scope 不可修改；状态更新采用 optimistic version/合法迁移 guard。不得在数据库事务中等待 OKX。

事务边界：session command、state event 与 audit 在同一短事务；approval 与状态迁移同事务；intent 创建与可领取状态同事务；worker 短事务 claim 后释放连接，再执行外部调用，最后在新事务中追加 receipt、更新 intent outcome 和 audit。若外部调用后进程崩溃，intent 标为/恢复为 `UNKNOWN`，只能按 `clientOrderId` 查询并对账，禁止盲重试。

## 9. API Candidates

以下 API 均为 **CANDIDATE / NOT IMPLEMENTED**，不得写入 `API.md` 作为当前能力：

- `POST /api/live-sessions`：以 `Idempotency-Key` 创建 `APPROVAL_PENDING`，仅接受 release/risk/account/symbol/window 引用，不接受 credential material。
- `GET /api/live-sessions`、`GET /api/live-sessions/{sessionId}`、`GET .../events|intents|receipts|risk-snapshots|reconciliation-cases`：分页只读，强制 account/role scope 与 max page size。
- `POST /api/live-sessions/{sessionId}/approvals`：`LIVE_APPROVER` 对 exact scope hash 批准/拒绝；过期或 scope mismatch fail-closed。
- `POST /api/live-sessions/{sessionId}/start|pause|resume|stop|kill`：command-specific idempotency，合法状态/RBAC/risk/credential/kill gate，危险动作需 reason/确认。
- `POST /api/live-sessions/{sessionId}/manual-cancel-intents`：仅生成可审计 CANCEL intent，不直接调用 venue。
- `POST /api/live-sessions/{sessionId}/reconciliation-cases/{caseId}/resolve`：只允许证据齐备的人工闭环，不改写历史 receipt。

不得暴露通用“任意订单提交”、transfer 或 withdraw API。策略执行模块只能经内部 application port 申请 intent；公共 API 不能绕过 approval/risk gate。响应使用稳定 DTO、错误码与 `traceId`，不返回 Entity、内部 SQL、异常栈或 private payload。

## 10. State Machines

主路径：`APPROVAL_PENDING → APPROVED → LIVE_WARMUP → LIVE_ACTIVE ↔ LIVE_PAUSED → LIVE_STOPPED → LIVE_RECONCILING → LIVE_RECONCILED`。异常终态/阻断态：`REJECTED`、`FAILED`、`KILLED`、`RECONCILIATION_BLOCKED`。

| 状态 | 进入条件 | 允许命令 / 权限 | 禁止、gate 与恢复 |
| --- | --- | --- | --- |
| APPROVAL_PENDING | draft/scopes persisted，尚无有效 approval | revise/submit/reject；OPERATOR/LIVE_APPROVER | 禁止 start/intent；新 scope hash 需新 approval |
| APPROVED | distinct LIVE_APPROVER、scope/digests/expiry 全匹配 | start/reject；OPERATOR start | start 前重跑 release/risk/credential/kill gates；失败退回 pending 或 FAILED |
| LIVE_WARMUP | start command，同事务记录 event；无外部订单 | pause/stop/kill、read-only probes；OPERATOR/ADMIN kill | 未完成 preflight/reconcile 不得 ACTIVE |
| LIVE_ACTIVE | 所有 hard runtime gates PASS 且在 window 内 | pause/stop/manual cancel/kill；approved strategy 可申请 intent | 每个 intent 重跑 risk/kill/credential；越限立即 pause/kill |
| LIVE_PAUSED | 人工、risk、data quality、network 或 recovery pause | reconcile/resume/stop/cancel/kill | 禁止新 PLACE intent；resume 必须重新 gate 且原 approval 未失效 |
| LIVE_STOPPED | stop 后无新 PLACE、outstanding intents 已封口 | reconcile/kill | 不可恢复 ACTIVE；只能进入 RECONCILING |
| LIVE_RECONCILING | stop/failed/kill 后生成 snapshots/cases | reconcile/case operations | 禁止任何新交易；差异未闭合转 BLOCKED |
| LIVE_RECONCILED | orders/fills/account/position/ledger 收敛 | 只读/archive | terminal；新运行创建新 session |
| REJECTED | approver 拒绝/approval 过期 | clone new draft/read | terminal；禁止 start |
| FAILED | non-recoverable local/worker/control failure | pause/kill/reconcile | 禁止自动重下；只可人工终止、对账、新建 session |
| KILLED | kill switch、越权、P0/P1 safety event | cancel-if-safe/reconcile/read | 不得自动恢复；解除 kill 也必须新 approval/new session |
| RECONCILIATION_BLOCKED | unknown/差异未收敛 | read-only diagnose/kill/case resolve | 禁止交易；证据闭合后只进入 RECONCILED，不回 ACTIVE |

每个命令都以 `sessionId + command + idempotencyKey` 唯一化，先校验 state/version/RBAC，再在短事务写 state/event/audit。所有拒绝也记录可追踪的 risk/credential/kill decision；不得记录 secret。

## 11. Idempotency and Audit

- `sessionId`：一次 pilot lifecycle 的全局稳定标识。
- `intentId`：一次外部动作（PLACE 或 CANCEL）的唯一业务键；相同 intent+相同 payload 返回原结果，相同 intent+不同 payload 返回 conflict。
- `requestId`：每个入站调用；`idempotencyKey`：调用方 command replay key，按 actor+resource+command scope 唯一；`traceId`：跨 Control Plane/worker/DB 日志关联。
- `clientOrderId`：由 session/intent 的稳定编码派生，重试不得改变；`exchangeOrderId`、`exchangeRequestId` 只能来自脱敏 receipt，可为空但不可伪造。
- 并发重复由数据库 unique constraint、version/row lock 和 canonical payload hash 处理；不依赖内存锁。未知结果不得重新生成新 clientOrderId，必须先远端查询和 reconciliation。

审计覆盖批准、拒绝、启动、暂停、恢复、停止、kill、人工撤单、override、credential scope decision、risk decision、order submission、exchange receipt、reconciliation result、rollback。每条包含存在的 `traceId/requestId/accountId/orderId/sessionId/intentId`、actor、decision、reason/error code、elapsed；禁止 secret、signature、Cookie、raw headers/private response、credential material。

## 12. Risk Limits

GateY-1/2 必须将 session 级 risk set 持久化、版本化并计算 canonical digest；批准绑定 digest，运行期不可就地放宽。阈值必须由受控配置/审批明确给出，默认缺失即 BLOCKED，不沿用现有“empty allowlist means allow all”或宽松 default。

硬限制：total pilot capital cap、single-order notional cap、per-symbol position cap、daily realized loss cap、daily total loss cap、max open orders、max intraday orders、1～2 symbol allowlist、`LIMIT`-only order type、UTC execution window、max session duration、market/data-quality freshness、spread/slippage cap、durable kill switch。

每个 PLACE intent 创建前和 worker 领取前均检查；余额/position/PnL/order count/data quality 任一 UNKNOWN 都拒绝。达到 loss/capital/position/open-order cap 时禁止新 PLACE 并进入 PAUSED 或 KILLED；规则只能收紧，放宽需要新 risk version、重新批准和新 session。

## 13. Credential Boundary

- 使用独立 OKX pilot key，仅最小 `TRADE` scope；funding/transfer/withdraw 必须关闭，IP allowlist 必须启用。
- 记录 external secret reference/key alias、permission-scope digest、有效期、轮换/吊销状态与审计；不把 API Key、Secret、Passphrase 放入计划、API、日志、audit metadata 或前端。
- Control Plane 只看 credential reference 和 sanitized capability fact，不解密 material。isolated worker 在受限进程内 just-in-time 解密，只看到当前 session/venue/account 所需引用；不得列举或管理其他 credentials。
- rotation/revoke/expire/permission mismatch 立即使 session PAUSED/KILLED，并阻止新 intent；worker 必须 bounded refresh，不能缓存到 session 之外。
- 真实联调仅在 GateY-6 显式授权后提醒用户使用本地安全凭证路径配置；永远不要求在对话中粘贴凭证。

## 14. Reconciliation

复用现有 `orders/trades/positions/ledger/audit`，新增 case/snapshot 只表达差异和闭环：

1. session start 前对 account balance、positions、open orders 和 clientOrderId namespace 做基线快照；任何 unknown 或非零遗留冲突阻断。
2. receipt 后以 order/fill read、account/position snapshots 与本地 order/trade/ledger 做有界对账；费用与滑点来自可追溯 venue fact。
3. crash/timeout 后先按 clientOrderId 查询：存在则补 receipt/本地状态，不存在且有可信 negative proof 才允许受控 retry；无法证明即 `RECONCILIATION_BLOCKED`。
4. partial fill、cancel race、duplicate receipt、out-of-order fill、late fill、restart replay 必须在 fake contract 与 dry-run 中覆盖。
5. stop/kill 后必须完成 final order/fill/account/position/ledger convergence；人工 override 只关闭 case，不改写历史 fact，必须有 distinct actor/reason/evidence digest。

## 15. Deployment and Worker Isolation

GateY 保持 `Java Control Plane + minimal Isolated Execution Worker + PostgreSQL audit/fact store + Python Offline Artifact Builder`。不进行全面微服务化。

- worker 只领取已批准的 immutable execution intent；不拥有策略准出权、风险规则定义权、LiveSession 状态决定权或 credential 管理权。
- worker endpoint allowlist 仅 OKX Spot 必需的 place/cancel/order/fill/account/position read；transfer/withdraw/funding 永久 default-deny。worker 不接受任意 URL、method 或 raw credential 参数。
- Control Plane 与 worker 通过 PostgreSQL durable intent/receipt boundary 协作；claim 使用有界 lease/并发控制，不在 DB 事务中调用外部 API。receipt 必须持久化后才推进控制面。
- 独立进程、最小 OS identity、资源/timeout/rate limits、health/heartbeat、no shared writable release、fail-closed config；worker 故障不得拖垮主应用，主应用故障后 worker 不得继续领取新 intent。
- 复用 GateW reproducible immutable release、root ownership/POSIX/systemd verifier、canonical rollback；GateY-4 必须关闭 stable-handle 风险，GateY-5 必须执行 worker restart/release rollback/DB restore drill。

## 16. CI and Test Strategy

1. 默认 no-egress CI：secret scan、endpoint negative tests、architecture/RBAC、backend/frontend/Python regression；无 real credential。
2. fake exchange contract：place/cancel/order/fill/account/position、partial fill、timeout、rate limit、late/out-of-order/duplicate facts。
3. idempotency/replay/concurrency：same/different payload、parallel claim、crash-before/after-send、unknown recovery、session command replay。
4. PostgreSQL/Flyway：真实 disposable PostgreSQL、constraints/triggers/index/rollback/lock-timeout/retention；migration 仅在 GateY-2 获批后实现。
5. private read-only manual smoke：显式受控 profile，只验证 permission/account/order/fill reads，不交易。
6. execution dry-run：isolated worker + fake endpoint，完整 intent/receipt/restart/reconcile，无真实 mutating request。
7. explicit micro-live pilot：仅 GateY-6、全部 hard gate PASS、用户显式授权、受控 credential/local environment；不进入普通 CI。

每批必须有 normal/failure/boundary/RBAC/idempotency/concurrency 回归。真实交易所测试不得成为 CI、单测或默认启动的一部分。

## 17. Micro-Live Soak

GateY-6 候选为 120 小时 OKX Spot micro-live soak，仅单账户、单 owner、单 release、单 window、1～2 symbols、冻结 risk limits。必须生成连续 session/intents/receipts/risk/reconciliation/audit evidence，并完成：pause/resume、process restart、worker restart、network failure、unknown order recovery、reconciliation、kill switch、rollback release、backup/restore 和 incident evidence drills。

以下任一事件立即 `KILL/PAUSE + RECONCILIATION_BLOCKED`，停止新订单并拒绝 acceptance：重复下单；intent/receipt 无法关联；本地/交易所 order 状态无法收敛；balance/position/fill 对账不一致；未审批外部动作；kill propagation 失败；credential 越权；命中 transfer/withdraw endpoint；fee/slippage 事实失真；secret 暴露；无法回滚。失败 attempt 必须终态化并保留，不得清洗后续跑结果覆盖。

## 18. Rollback and Incident Response

- 首先 engage kill、停止 worker 领取、禁止新 PLACE、允许受控 cancel-if-safe，然后对 unknown/outstanding intents 建 case 并保存所有 receipt/audit。
- 应用/worker 只回滚到已验证 immutable release；回滚不得移动 tag 或覆盖历史 artifact。schema 采用 forward-compatible migration；不得为匹配旧代码删除外部交易事实。
- DB restore 只用于独立灾难恢复流程和经过审查的恢复点；真实 venue 已发生事实必须通过 reconciliation 重建，不能用数据库回滚假装撤销订单/成交。
- incident evidence 包含 timeline、session/release/risk/credential-scope digests、sanitized request/receipt ids、kill latency、reconcile result、rollback verifier；不含 secret/private raw payload。
- KILLED/FAILED session 不恢复交易。修复、reconcile 与独立复核完成后，只能创建新 session 和新 approval。

## 19. Python Boundary

Python 继续是 offline research domain。允许规划 walk-forward validation、受约束 parameter search、execution-quality/fee/slippage attribution、strategy drift、reconciliation analytics 和 anomaly report。现有 dataset/experiment/evaluation metadata 与 checksum/release artifact contract继续复用。

可在独立评估后引入 Optuna，但仅用于离线、受约束参数搜索；参数空间、窗口、样本 hash、seed、指标和结果全部固化进 release artifact。禁止 runtime tuning、直接发单、访问 credential、控制 LiveSession。GateY 不引入 MLflow、DVC、online serving、LLM 自动交易或 DH runtime execution。

## 20. P0/P1/P2/P3 Risks

### P0

- 当前 P0=0，因为本计划未开启 LIVE、未接 real provider、未读取 credential、未产生交易副作用。未来若出现未审批外部动作、credential/secret 泄露、transfer/withdraw、kill 失效或不可回滚，立即按 P0 阻断。

### P1

- `FIRST_REAL_ORDER_HARD_GATE` 当前不是全绿：LiveSession/approval、intent/receipt、session risk set、isolated worker、mutating private endpoint、scoped credential proof、full reconciliation 均未实现或未验证。
- 在上述能力关闭前，`LIVE=DISABLED`、real provider/private trading=`NOT_IMPLEMENTED`；任何真实订单任务必须拒绝。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：属于 first-order blocker。GateY-1 定义测量/超时/回滚 contract，GateY-5 在 production-like volume clone 关闭；未关闭时 migration/deploy/first order 全部 fail-closed。
- `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：属于 first-order blocker，归 GateY-4。必须用 verified-open stable handle 或可信 sealed content-addressed workspace，并通过 replace/symlink/reparse race tests；未关闭时 artifact consumption 与 worker start 拒绝。
- 当前 reconciliation、kill propagation、backup/restore 与 incident evidence 只部分覆盖 GateY execution 场景，分别由 GateY-3～5 关闭。

### P3

- Operator dashboard、术语、风险/差异可视化与 evidence 查询需在 GateY-5 收口；不得用 UI 隐藏 BLOCKED/UNKNOWN/KILLED/RECONCILIATION_BLOCKED。

## 21. GateY Batch Plan

| Batch | 范围 | 交付与验收 | LIVE 边界 |
| --- | --- | --- | --- |
| GateY-0 | 当前 Plan / Fact Reconciliation | 本文、evidence、authority self-review；无代码 | DISABLED |
| GateY-1 | LiveSession、OperatorApproval、ExecutionIntent/Receipt 数据模型独立审查 | schema/API/state/locking/retention/threat/lock-window review；只审查，不建 migration | DISABLED |
| GateY-2 | LiveSession control-plane Fact Model、Approval State Machine、immutable RiskLimitSet、append-only event | Flyway migration + domain/repository/JDBC + PostgreSQL constraint/migration tests；不含真实 exchange execution | DISABLED |
| GateY-3 | Intent/Receipt Ledger、Fake Exchange Contract、Idempotency、Unknown-order/Reconciliation | no-egress fake contract、crash/replay/concurrency、full reconciliation evidence | DISABLED |
| GateY-4 | Scoped Credential Runtime、Private Read-only Probe、Kill Switch、Deployment Boundary | credential scope/endpoint negatives、kill propagation、stable-handle closure、immutable worker packaging | DISABLED |
| GateY-5 | Worker Dry-run、Approval Dashboard、Restart/Rollback/Restore Drill | fake-only isolated worker、risk-visible UI、production-like lock/restore/incident drills；结束仍不得真实下单 | DISABLED |
| GateY-6 | Explicit Micro-Live Authorization、OKX Spot 单账户微资金 pilot、120h soak | 全部 hard gate PASS + 用户显式授权 + exact evidence；失败即终态/冻结 | 只在显式受控窗口候选启用，非自动授权 |
| GateY-FREEZE | frozen release closeout | 120h acceptance、strict archive、exact-head CI、annotated tag、post-tag authority sync | 不扩大已接受范围 |

GateY-1～5 不能以“为后续准备”为由配置 real credential 或发送 mutating request。每批为可审查、可回滚的小步；docs 默认不改，测试基线按项目 docs budget 记录，freeze 才同步阶段总状态。

## 22. Acceptance and Freeze Criteria

GateY 只有同时满足以下条件才可进入 freeze closeout：GateY-1～6 均经独立 review 与 exact-head CI 接受；全部 `FIRST_REAL_ORDER_HARD_GATE` 有可复验证据；120h soak 完成且无立即冻结事件；order/fill/account/position/ledger 最终收敛；kill/rollback/restore/incident drills 通过；P0=0、P1=0，P2 有明确关闭或冻结范围不受影响的证据；strict archive manifest 完整；LIVE 范围仍严格限定单 OKX Spot pilot。

Freeze/tag 前必须运行 archive、current-authority、doc-link 与 release exact-head checks。失败 attempt、unknown receipt、reconciliation case 和 safety incident 必须保留。GateY freeze 不授权 GateZ、第二 venue、更大资金或无人值守执行。

## 23. GateY-1 Acceptance 与 GateY-2 Initialization

GateY-PLAN 已由 exact-head green CI 接受，GateY-1 正式 work order 已形成、通过独立 migration/security review，并由 exact-head CI 正式接受：

- [GATEY_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md](GATEY_1_LIVE_SESSION_DATA_MODEL_WORK_ORDER.md)
- 状态：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`76ef325f7b8a3d3325df63af2cb1b979309bd141`，CI run=`31581317959 / completed / success / 10 jobs / bad=0`。
- 范围：五个核心模型、六表 candidate schema、事实所有权、状态机、事务、幂等、并发、append-only、migration lock-window 与 stable-handle 安全合同。
- 边界：migration、Controller/Service/Repository、worker、credential 配置、真实交易调用与 LIVE side effect 均为 0。

独立审查已确认 P0=0、P1=0，并冻结六表最小性、真实 FK/type、risk/canonical digest、intent claim/crash、append-only trigger、event ordering、DDL/retention 合同。该接受不表示 Flyway migration、Java domain、Repository、LiveSession runtime、execution worker 或 OKX TRADE 已实现。

GateY-2 初始化为 `NOT STARTED`（未开始），只允许实现以下 control-plane facts：

1. GateY LIVE control-plane Flyway fact model。
2. `LiveSession` aggregate/domain 与 approval state machine。
3. immutable/versioned/digest-bound `RiskLimitSet` facts。
4. append-only `live_session_events`。
5. Repository/JDBC baseline。
6. PostgreSQL constraints 与 migration tests。

GateY-2 不实现真实 exchange execution。future exchange worker、real PLACE/CANCEL transport、`ExecutionIntent` external dispatch、`ExecutionReceipt` real-provider binding、unknown-order exchange reconciliation 与 partial-fill real exchange handling 全部后置 GateY-3。

GateY-2 implementation 启动时必须重新扫描 `backend/nq-infra/src/main/resources/db/migration` 的最高 Flyway version。若最高仍为 V38，候选为 `V39__gate_y2_live_session_fact_model.sql`；否则使用 current highest + 1。不得抢号、预先写死版本事实或修改历史 migration。精确下一动作只读取 [STATUS.md](STATUS.md)。

## 24. Do-Not-Build List

- 第二 venue/Binance fallback、smart order router、cross-venue arbitrage。
- margin/leverage/futures/options/borrow、market order、transfer/withdraw/funding API。
- 第二套 orders/trades/positions/ledger/audit/reconciliation 数据源。
- worker 内 strategy admission、risk rule authoring、credential lifecycle management 或 session authorization。
- secret/raw private response/signature/cookie/header 持久化或前端展示。
- 自动 kill release、KILLED/FAILED 自动恢复、unknown order 盲重试、RECONCILIATION_BLOCKED 继续交易。
- runtime parameter tuning、Optuna runtime control、MLflow、DVC、online serving、LLM/DH 自动交易。
- 全面微服务化、无必要依赖升级、修改 GateX frozen baseline/tag、提前创建 GateY tag。
