# GateY-1 Live Session Data Model Work Order

> 状态：`REVIEW ACCEPTED / READY TO COMMIT`（审查已接受 / 可进入提交前复核）。
> 任务：`NQ-GATEY-1-LIVE-SESSION-DATA-MODEL-WORK-ORDER-IMPLEMENTATION`。
> 定位：本文冻结 LIVE control-plane 的数据模型、状态机、事务、幂等、并发和 migration review 输入；它不是 Flyway migration，也不表示 schema、API、worker、真实交易或 LIVE 已实现。
> 安全边界：`LIVE=DISABLED`、Shadow trading=`NOT_ENABLED`、real provider/private trading=`NOT_IMPLEMENTED`；credential 访问、交易所调用与交易副作用均为 0。

## 1. 目标、非目标与交付状态

### 1.1 目标

- 冻结 `LiveSession`、`OperatorApproval`、`RiskLimitSet`、`ExecutionIntent`、`ExecutionReceipt` 五个核心模型。
- 冻结 LiveSession 状态机和所有 fail-closed 禁止迁移。
- 冻结 Control Plane DB transaction 与未来 exchange HTTP transaction 的边界。
- 为独立 Migration/Security Review 提供可逐字段审查的 PostgreSQL candidate schema。
- 明确复用既有 order、trade、position、ledger、audit、account、credential、release、reconciliation 与 kill-switch 事实，避免第二主账。

### 1.2 非目标

- 不创建或修改 Flyway migration，不修改历史 migration。
- 不实现 Java domain、Repository、Service、Controller、worker、API 或 UI。
- 不读取 credential material，不执行 permission probe，不连接 OKX。
- 不创建真实订单、撤单、成交、持仓、账务或对账事实。
- 不授权 micro-live，不启用 LIVE，不改变 kill switch。

### 1.3 交付状态

本文已经独立 migration/security review 接受，状态为 `REVIEW_ACCEPTED / READY_TO_COMMIT`。后续提交任务仍不得创建或 apply migration；下一 Flyway version 只能在独立 migration implementation 任务再次核对 branch 后确定。本文不构成 production migration deployment、micro-live 或任何真实交易授权。

## 2. 已审计基线

- 起始 Git baseline：`dev`，`HEAD == origin/dev == 21d3e457f749774800f2908d34e6e19a500c076e`，worktree clean、staged empty。
- exact-head CI：`NQ CI Baseline` run `31570833270`，`completed / success`，10 jobs / bad=0。
- current authority：GateY-PLAN=`ACCEPTED|CI_GREEN`；GateY-1=`NOT_STARTED / NONE / NOT_RUN`；进入本任务时 checker `errors=0`。
- 当前最高 Flyway version：`V38__gate_x5a_admission_materialization_guard.sql`。
- 当前状态：LIVE=`DISABLED`、Shadow trading=`NOT_ENABLED`、real provider/private trading=`NOT_IMPLEMENTED`、kill switch=`ENGAGED`。

## 3. Existing Fact Ownership Matrix

核心原则：**GateY 只增加 LIVE control-plane facts，不创建第二套 order、trade、position、ledger 或 audit 主事实。**

| Existing Fact | Current Owner | GateY Reuse | GateY New Reference | Forbidden Duplicate |
| --- | --- | --- | --- | --- |
| `orders` | trading domain；`orders` + `OrderCommandService` / `OrderRepository` | 继续作为订单生命周期与本地订单唯一主事实 | `execution_intents.local_order_id` 可空引用 `orders.order_id`；PLACE 在本地订单建立后绑定，CANCEL 必须引用既有订单 | `live_orders`、在 intent/receipt 重复保存订单状态、filled quantity 或订单生命周期 |
| `trades` | trading/ledger；`trades` | 继续作为成交唯一主事实，以 exchange trade identity 去重 | receipt 只保存外部回执 identity；fill 由后续 reconciliation 写入既有 `trades` | `live_trades`、在 receipt 保存 fill 明细或累计成交 |
| `positions` | ledger/trading；`positions` | 继续作为账户+symbol 当前持仓主事实 | risk/reconciliation 只读取并引用 account/symbol/as-of digest | `live_positions`、`live_position_snapshots` 作为第二持仓主账 |
| `ledger_entries` / `ledger_events` | `nq-ledger` | 继续承担资金与账务事实、idempotent posting 和事件投影 | 使用既有 order/trade/ref identity；session/intent 只作为可追踪引用 | `live_ledger_entries`、在 receipt/session 保存余额或账务分录 |
| `audit_logs` | audit port / infra repository | 继续承担跨域审计 | session/event/approval/intent/receipt 写入关联 audit domain/action；不复制完整 audit payload | `live_audit_logs` 或把 `live_session_events` 解释成通用审计替代品 |
| `risk_events` | `nq-risk` / risk repository | 继续记录运行期风险判定 | `risk_limit_set_id/digest` 与 `session_id/intent_id` 作为 scope/reference | 在 `risk_limit_sets` 保存运行期判定历史；新增第二风险事件表 |
| `event_store` | contracts/event publisher | 继续承载既有领域事件事实 | 可发布脱敏 session/intent lifecycle event；DB control-plane 表仍是对应 aggregate authority | 复制 event-store payload 到 receipt 或 session metadata |
| `exchange_accounts` | account domain；`exchange_accounts` | 复用 owner、venue、environment、account status | `live_sessions.exchange_account_id` FK；owner 与 venue 必须与账户事实一致 | `live_exchange_accounts` 或在 session 复制 external account detail |
| `exchange_account_credentials` | account/credential governance | 复用 credential lifecycle 与外部 secret reference | `live_sessions.credential_reference` FK 到精确 credential version；Control Plane 只见引用和 sanitized capability facts | 在 session/approval/intent/receipt 保存 encrypted/raw credential 或 permission 探活响应 |
| `credential_audit_logs` | credential governance | 继续作为 credential lifecycle/probe append-only 审计 | session credential gate 结果只追加脱敏 audit/reference | `live_credential_audit` 或复制 credential material |
| `backtest_publish_records` / Strategy Release | research publish + strategy release production view | 复用 `publish_record_id` 作为 release anchor，并绑定已验证 artifact digest | `live_sessions.strategy_release_id` FK；同时冻结 `release_digest` | 第二 release UUID、mutable filesystem path 作为 release identity、复制 manifest body |
| `paper_trading_*` | research/Paper domain | 只作离线/模拟历史和测试参考，不作为 LIVE source | 无 FK；GateY 不从 Paper facts materialize live facts | 将 Paper order/trade/position/run 改名或复用为 LIVE 主表 |
| `shadow_runs` / `shadow_run_events` | strategy Shadow domain | 只复用 release/admission provenance 的设计经验 | 无 FK；LiveSession 是独立控制面 aggregate | 把 Shadow Run 升级为 live session，或把 Shadow event 当真实执行 receipt |
| reconciliation facts | trading reconciliation ports、bounded read-only snapshots/comparator、既有 order/trade/position/ledger | GateY-3 在其上扩展 unknown/fill/account/position convergence | intent/receipt 提供 `client_order_id`、exchange ids 与 digest 供对账 | 在 GateY-1 新建第二 order/fill/position snapshot 主账 |
| `kill_switch_states` / `kill_switch_events` | durable global trading safety owner | 每次 start/resume/PLACE claim 必须读取；缺失/非法/读取失败按 UNKNOWN 阻断 | session event/audit 记录观察到的 scope/version，不复制 current state | `live_kill_switch`、session 内可写的 kill override、自动 disengage |

### 3.1 六表最小性决定

| Candidate | 决定 | 独立且不可替代的事实 | 明确不拥有 |
| --- | --- | --- | --- |
| `risk_limit_sets` | `NECESSARY` | 经版本化、审批可绑定且创建后不可变的 LIVE 风险规则定义 | 运行期 risk decision、余额、持仓、PnL |
| `live_sessions` | `NECESSARY` | LIVE control-plane aggregate、scope、window、状态与 optimistic version | order/trade/position/ledger/kill-switch 当前状态 |
| `live_session_events` | `NECESSARY` | session aggregate 的有序状态/命令历史 | 通用 `audit_logs`、订单事件或 provider receipt |
| `operator_approvals` | `NECESSARY` | exact scope/release/risk/expiry/approver 绑定的 immutable 人工 decision | venue permission、kill-switch disengage、LIVE authorization |
| `execution_intents` | `NECESSARY` | 一次 future mutating action 的幂等业务意图与 worker claim 状态 | 订单生命周期、成交、provider response |
| `execution_receipts` | `NECESSARY` | 每次网络 attempt 的脱敏、append-only 外部回执证据 | order/fill 主事实、raw request/response |

六表均未建立第二主账；任何后续 migration 若加入订单状态、累计成交、持仓、余额、账务分录、通用审计 payload、运行期 risk decision、credential material、Paper/Shadow facts，均视为 `DUPLICATE_SYSTEM_OF_RECORD` 并拒绝。

## 4. 拒绝或延后的候选模型

| Candidate | GateY-1 决定 | 原因 |
| --- | --- | --- |
| `live_position_snapshots` | REJECTED（拒绝） | 容易复制 `positions`；GateY-1 没有证明独立不可替代的 fact ownership。后续对账应引用既有 position fact 与 digest。 |
| `portfolio_risk_snapshots` | REJECTED（拒绝） | 运行期判定应进入既有 `risk_events`；冻结规则属于 `risk_limit_sets`，两者不能混为第二风险主账。 |
| `cost_slippage_facts` | REJECTED（拒绝） | fee/price/trade identity 已由 `trades` 和 venue fact 承担；派生分析可后置且应可重建。 |
| `reconciliation_cases` | DEFERRED（后置） | 仅当 GateY-3 证明需要持久化“差异 + 人工处理状态”且不复制 order/fill/position facts 时再独立设计；不进入首版 migration。 |

## 5. Canonicalization 与 digest 合同

- digest algorithm 固定 `SHA-256`，输出固定 64 位 lowercase hex；数据库使用 `VARCHAR(64)` + regex CHECK。首版 schema version 分别为 `approval-scope.v1`、`risk-limit-set.v1`、`live-session-command.v1`、`execution-intent-payload.v1`、`execution-receipt-envelope.v1`，以独立 `*_schema_version` 列持久化并由 CHECK 固定；未知版本 fail-closed。
- canonical bytes 由专用 encoder 产生，不调用普通 JSON serializer：UTF-8、无 BOM、无空白、无尾随换行；object key 必须按下述合同顺序写出；string 使用 JSON escaping；整数用无前导零十进制；资金与 bps 数字作为固定 8 位小数的 JSON string；timestamp 使用 UTC `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`；NULL 不进入 canonical scope，因为所有风险/身份字段均 NOT NULL。
- symbol 在持久化前 trim + uppercase，按 Unicode code point 升序排序并去重；只允许 1～2 个 `BASE-QUOTE` ASCII symbol，首版 quote 必须与 `quote_currency='USDT'` 一致。order type 数组首版 canonical bytes 精确为 `["LIMIT"]`。
- `approval_scope_hash` key 顺序固定为：`schemaVersion,sessionId,ownerId,exchangeAccountId,venue,strategyReleaseId,releaseArtifactDigest,releaseAdmissionRevision,riskLimitSetId,riskLimitSetDigest,credentialReference,symbolAllowlist,capitalCap,executionWindowStart,executionWindowEnd`。
- `RiskLimitSet.canonicalDigest` key 顺序固定为：`schemaVersion,effectiveScope,version,quoteCurrency,capitalCap,maxOrderNotional,maxSymbolPositionNotional,maxDailyRealizedLoss,maxDailyTotalLoss,maxOpenOrders,maxIntradayOrders,symbolAllowlist,orderTypeAllowlist,maxSessionDurationSeconds,spreadLimitBps,slippageLimitBps,maxMarketDataAgeMs,minDataCoverageBps,requiredDataSource,dataQualityAction`。
- `ExecutionIntent.payloadHash` key 顺序固定为：`schemaVersion,sessionId,sequence,action,localOrderId,clientOrderId,symbol,side,orderType,quantity,limitPrice`；不覆盖 state、claim/lease、timestamps 或网络 attempt。CANCEL 的 nullable action fields用 JSON `null` 固定编码，不能省略。
- `ExecutionReceipt.payloadDigest` 只覆盖允许的 normalized envelope：`schemaVersion,intentId,attemptNo,outcome,exchangeRequestId,exchangeOrderId,errorCategory,errorCode,receivedAt`；nullable 字段固定编码 JSON `null`。encoder 输入不得包含 raw request、raw response、headers、signature 或 credential material。
- PostgreSQL integration/golden tests 必须证明：相同语义在字段插入顺序、BigDecimal scale 表达和 symbol 输入顺序变化下 digest 相同；任一 canonical scope 字段变化、未知版本或非 canonical persisted array 均被拒绝或产生不同 digest。

## 6. LiveSession

### 6.1 语义与字段

`LiveSession` 表示一次受控 micro-live session 的 control-plane aggregate，不表示 LIVE 已获授权，也不承载 order/trade/position/ledger facts。

| Field | 语义与约束 |
| --- | --- |
| `sessionId` | UUID 业务主键；一次 lifecycle 永不复用 |
| `ownerId` | FK `users.id`；必须等于 `exchange_accounts.owner_user_id` |
| `exchangeAccountId` | FK `exchange_accounts.exchange_account_id`；必须是 `trade_env='LIVE'` 候选账户，但账户存在不等于可交易 |
| `venue` | 首版固定 `OKX_SPOT`；必须与 account exchange/environment facts 一致 |
| `strategyReleaseId` | FK `strategy_release_admission_state.publish_record_id`；该 target 再以 RESTRICT FK 引用 `backtest_publish_records.publish_record_id`，不创建第二 release identity |
| `releaseDigest` / `releaseAdmissionRevision` | 必须在锁定 admission row 后绑定其 non-null `release_artifact_digest` 与当前正整数 revision；禁止 mutable path 作为 identity |
| `riskLimitSetId` / `riskLimitSetDigest` | 精确绑定冻结 risk set 及 digest |
| `credentialReference` | FK 到 `exchange_account_credentials.credential_id` 的精确 version；不含 material |
| `symbolAllowlist` | 1～2 个去重、排序后的 OKX Spot internal symbols；不能为空 |
| `capitalCap` | 正数、保守上限；必须等于或小于 risk set capital cap |
| `executionWindowStart/End` | UTC 闭开区间 `[start,end)`；end > start，且 duration 不超过 risk set |
| `state` | LiveSession 状态机枚举 |
| `version` | BIGINT optimistic-lock version，从 1 开始，每次合法状态/scope 变化递增 |
| `approvalScopeHash` | 当前 scope canonical hash；scope 变化必须生成新 hash并使旧 approval 不可用 |
| `nextEventSequence` | BIGINT 正整数；在锁定 session row 的同一短事务内分配 event sequence，禁止 `MAX+1` |
| `createdAt/updatedAt` | UTC；created immutable，updated 随 version 更新 |

### 6.2 并行、变更与终态

- 同一 `exchange_account_id + venue` 同时最多一个 non-terminal session。使用 partial unique index 覆盖 `APPROVAL_PENDING / APPROVED / LIVE_WARMUP / LIVE_ACTIVE / LIVE_PAUSED / LIVE_STOPPED / LIVE_RECONCILING / RECONCILIATION_BLOCKED`；`REJECTED / FAILED / KILLED / LIVE_RECONCILED` 为 terminal。
- scope 字段只允许在 `APPROVAL_PENDING` 内修改。修改必须以 `WHERE session_id=? AND version=? AND state='APPROVAL_PENDING'` CAS 更新，递增 version、重算 scope hash并追加 `SCOPE_CHANGED` event。
- 任何旧 `scopeHash` approval 自动失配；不更新、不删除旧 approval。
- execution window 到期后禁止新 PLACE。ACTIVE 到期应进入 PAUSED/STOPPED；未知时间源或 clock skew 超阈值时 fail-closed。
- terminal state 不允许恢复。`KILLED`、`FAILED` 或 `LIVE_RECONCILED` 后若需继续，只能新建 session、新 scope、新 approval。
- session 创建/批准/start 必须锁定 `strategy_release_admission_state`，要求 identity quartet 完整、`release_digest` 精确匹配且 observed revision 未变化；revision 变化后旧 approval/session start fail-closed。数据库只保存 publish anchor、digest 与 revision，不保存或信任 path、URL、symlink/reparse path。

## 7. OperatorApproval

### 7.1 语义与字段

| Field | 语义与约束 |
| --- | --- |
| `approvalId` | UUID PK，永不复用 |
| `sessionId` | FK `live_sessions.session_id` |
| `scopeHash` | 批准时精确 session scope hash |
| `releaseDigest` | 批准时 release digest，必须匹配 session |
| `riskLimitSetDigest` | 批准时 risk digest，必须匹配 session |
| `approverId` | FK `users.id` |
| `approverRole` | 首版只允许 `LIVE_APPROVER`；角色快照用于审计，授权仍必须由后端实时 RBAC 判定 |
| `decision` | `APPROVED / REJECTED` |
| `reason` | 非空脱敏理由；不得包含 credential/private payload |
| `approvedAt` | decision 发生时间；字段名保留，但对 REJECTED 表示 decision time |
| `expiresAt` | 必须大于 approvedAt，且不得超过 execution window end |

### 7.2 不可变与职责分离

- approval append-only/immutable：禁止 UPDATE/DELETE；修正只能追加新 decision。
- session creator/operator 与 approver 必须是不同 authenticated identity。authoritative enforcement 固定为 application transaction：`SELECT ... FOR UPDATE` 锁定 session，读取 immutable `created_by`，校验 `created_by <> approver_id`、expected version/scope/state 后才 append approval 并迁移 session。PostgreSQL CHECK 不承担跨表职责分离；GateY-2/3 必须永久保留 creator=self-approver rejection、concurrent approval、stale scope/version integration tests。
- 并发 approval 通过 session row lock + state/version CAS 串行化：只有 `APPROVAL_PENDING` 可追加 APPROVED 并迁移到 APPROVED，第二请求重读后返回原事实或 business conflict。不得对 `(session_id, scope_hash)` 建 APPROVED partial unique，因为它会阻止旧 approval 过期后在相同 scope 上追加新的 immutable approval。
- 使用 approval 时必须同时满足：decision approved、未过期、scope/release/risk digest 精确匹配、approver 在批准时具备角色、当前 session state 合法。
- approval **不表示** exchange permission 已验证，不表示 credential 可用，不表示 kill switch 已释放，也不表示 LIVE authorization。

## 8. RiskLimitSet

### 8.1 语义与字段

| Field | 语义与约束 |
| --- | --- |
| `riskLimitSetId` | UUID PK |
| `version` | 同一 effective scope 下正整数版本 |
| `effectiveScope` | 首版精确 `LIVE_SESSION_OKX_SPOT`，不可为空 |
| `quoteCurrency` | 首版精确 `USDT`；下列资金/损失/position notional 的业务单位 |
| `capitalCap` | USDT session 累计资本上限；`0 < value <= 10000.00000000` |
| `maxOrderNotional` | USDT 单笔 `price × quantity` 上限；`0 < value <= min(capitalCap,1000.00000000)` |
| `maxSymbolPosition` | 字段语义冻结为 `maxSymbolPositionNotional`：按 fail-closed mark price 计算的 USDT 单 symbol gross notional；`0 < value <= capitalCap` |
| `maxDailyRealizedLoss` | UTC calendar day 的 USDT realized loss 绝对值上限；`0 < value <= capitalCap` |
| `maxDailyTotalLoss` | 同一 UTC day 的 `realized loss + adverse unrealized PnL` 正数绝对值；`realized cap <= value <= capitalCap` |
| `maxOpenOrders` | 1..20 |
| `maxIntradayOrders` | `maxOpenOrders..200`，按 session execution window 内创建的 PLACE intent 计数 |
| `symbolAllowlist` | 1～2 个去重、排序 symbol |
| `orderTypeAllowlist` | 首版精确为单值 `LIMIT` |
| `maxSessionDuration` | 60..14400 秒；execution window 不得超过 |
| `spreadLimit` | `NUMERIC(18,8)` basis points，`0..1000.00000000`；0 表示只接受零 spread，不表示 unlimited |
| `slippageLimit` | `NUMERIC(18,8)` basis points，`0..1000.00000000`；0 表示不允许 adverse slippage，不表示 unlimited |
| data quality | 首版使用结构化字段：`maxMarketDataAgeMs=1..5000`、`minDataCoverageBps=1..10000`、`requiredDataSource='OKX_PRIMARY'`、`dataQualityAction='BLOCK'`；不使用 JSONB |
| `canonicalDigest` | 全字段 canonical SHA-256，UNIQUE |
| `createdBy` / `createdAt` | 创建者 FK 与 UTC 时间；immutable |

### 8.2 版本与使用规则

- risk set 创建后 immutable，禁止 UPDATE/DELETE；被 session/approval 引用后更不得原地覆盖。
- 放宽任何阈值必须创建新 `risk_limit_set_id/version/digest`、新 session scope 和新 approval；不能在 active session 上热更新。
- 运行时每次 PLACE intent 创建前与 worker claim 前均读取既有 positions/orders/ledger/risk facts进行 fail-closed 判定。余额、持仓、PnL、order count、data quality、spread 或 slippage 任一 UNKNOWN 都拒绝。
- `RiskLimitSet` 只定义规则，不保存判定结果；判定结果继续进入既有 `risk_events` 和 audit。

### 8.3 精度、NULL、0 与 Java 语义

- 五个 USDT 字段固定 PostgreSQL `NUMERIC(38,8)`；Java 只允许 `BigDecimal`，禁止 float/double。API/domain ingress 对 scale > 8 使用 `RoundingMode.UNNECESSARY` 并拒绝，不静默舍入；持久化与 canonical form 固定 `setScale(8)`。
- notional/exposure/loss 运行期计算先保留完整乘法精度，再对非负风险量使用 `RoundingMode.CEILING` 收敛到 8 位，保证不会因向下舍入放行；仅展示值可另行格式化，不能参与决策或 digest。
- 全部风险字段 `NOT NULL`；NULL 一律非法，不能解释为 unlimited。资金/损失/position/order/session/count/freshness/coverage 不允许 0；只有 spread/slippage 允许 0，语义如上。
- schema hard maximum 是安全上界，不是默认值或 micro-live 授权。未来 exact pilot 值必须更小或相等、由新 immutable risk set + approval 明确给出；禁止用 schema maximum 自动初始化。

## 9. ExecutionIntent

### 9.1 唯一业务事实与 PLACE/CANCEL

`ExecutionIntent` 是未来一次外部 mutating action 的唯一业务事实。PLACE 与 CANCEL 共用模型和幂等协议，但 action-specific CHECK 不同。

| Field | 语义与约束 |
| --- | --- |
| `intentId` | UUID PK；一次外部动作唯一业务键 |
| `sessionId` | FK `live_sessions.session_id` |
| `sequence` | session 内单调正整数，UNIQUE `(session_id, sequence)` |
| `action` | `PLACE / CANCEL` |
| `symbol` | allowlist 内 symbol；CANCEL 必须匹配 target order |
| `side` | PLACE 必填 `BUY/SELL`；CANCEL 必须为 NULL，目标 order side 只从既有 order 读取 |
| `orderType` | PLACE 首版必须 `LIMIT`；CANCEL 为 NULL |
| `quantity` | PLACE > 0；CANCEL 为 NULL |
| `limitPrice` | PLACE > 0；CANCEL 为 NULL |
| `payloadHash` / `payloadHashSchemaVersion` | immutable payload canonical digest；首版 schema version 精确 `execution-intent-payload.v1` |
| `clientOrderId` | PLACE 稳定派生；CANCEL 复用目标订单 clientOrderId，不生成第二订单号 |
| `localOrderId` | PLACE/CANCEL 均必须在 intent 创建时引用既有 `orders.order_id`；不允许 NULL→值后绑或改绑 |
| `state` | `CREATED / CLAIMED / SEND_STARTED / SEND_SUCCEEDED / UNKNOWN / FAILED / CANCELLED / RECONCILED` |
| `version` | optimistic-lock version |
| `claimedBy/claimToken/claimedAt/leaseExpiresAt` | bounded worker lease；四字段成组为空或非空，仅 claim metadata，可 CAS 更新，不属于 business payload |
| `sendStartedAt` | nullable；worker 在任何 network send 前以持有的 claim token 原子写入；一旦非空不可清空/改变，且永远禁止 lease-based resend |
| `createdAt` | immutable UTC 时间 |

### 9.2 幂等与 clientOrderId

固定协议：

```text
same intentId + same payloadHash      -> 返回原 ExecutionIntent
same intentId + different payloadHash -> CONFLICT / INTENT_PAYLOAD_MISMATCH
```

- PLACE `clientOrderId` 从 versioned namespace + session UUID + intent UUID 的 canonical bytes 派生，使用固定长度、venue 可接受字符集和 checksum；相同 intent 永远得到相同值，算法版本不得静默改变。
- CANCEL 以新 `intentId` 表示一次独立外部撤单动作，但必须引用同一 `localOrderId/clientOrderId`；不得创建第二 order。
- PLACE 必须先在既有 `orders` owner 中创建本地 order，再在同一短 transaction 创建 intent；CANCEL 必须锁定既有 order 并复用其 `order_id/client_order_id`。`local_order_id` 从 intent insert 起 immutable，避免额外一次性 binding 状态和 orphan intent。
- 现有 `orders.account_id` 引用 legacy `accounts.account_id`，LiveSession 引用 `exchange_accounts.exchange_account_id`。intent 创建必须锁定 session/account/order，并要求 `exchange_accounts.legacy_account_id IS NOT NULL AND orders.account_id = exchange_accounts.legacy_account_id`，同时校验 owner、venue、symbol、clientOrderId；任一缺失或不一致返回 `ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED` 并拒绝。GateY-2/3 必须以独立实现与 PostgreSQL integration test 验证该 bridge；本轮不得修改既有 order owner/schema。
- unknown result 不生成新 intent/clientOrderId，不盲重试；先按 clientOrderId/exchangeOrderId 进行 read/reconciliation。

### 9.3 Intent 状态与合法转换

| From | To | 条件与语义 |
| --- | --- | --- |
| `CREATED` | `CLAIMED` | session/risk/credential/kill/window gates 重检 PASS；CAS 写完整 claim quartet |
| `CREATED` | `CANCELLED` | KILL/PAUSE/window expiry 在 send 前发生；表示 intent 被本地抑制，不表示订单已撤销 |
| `CLAIMED` | `CLAIMED` | 仅旧 lease 已过期且 `send_started_at IS NULL`；CAS 更换 claim token，用于 crash-before-send 安全 reclaim |
| `CLAIMED` | `SEND_STARTED` | 当前 token 匹配、lease 未过期、最后一刻 gate PASS；先持久化 `send_started_at` 再在 transaction 外发请求 |
| `CLAIMED` | `CANCELLED` | 尚未 SEND_STARTED 且 KILL/PAUSE/window expiry；禁止发送 |
| `SEND_STARTED` | `SEND_SUCCEEDED` | 收到 definitive acknowledged receipt；只表示外部 action 已被明确接受，不替代 order state |
| `SEND_STARTED` | `FAILED` | 收到 definitive rejection/local bounded failure；必须有 receipt/error category，不允许自动 resend |
| `SEND_STARTED` | `UNKNOWN` | timeout、连接中断、crash-after-send 或 receipt persistence uncertainty；立即进入 reconciliation，禁止 resend |
| `UNKNOWN` | `RECONCILED` | read/query 证据确认 remote outcome 并与既有 order/trade/ledger 收敛；不回到可发送状态 |

`SEND_SUCCEEDED / FAILED / CANCELLED / RECONCILED` 为 terminal。CHECK 固定上述枚举；DB transition trigger + application CAS 固定合法转换，任何 terminal 恢复、`SEND_STARTED` 后回 `CLAIMED/CREATED`、UNKNOWN 自动 resend 均以 SQLSTATE `23514` 拒绝。首版即使 query 得到 `QUERY_NOT_FOUND`，也不重发原 intent；新的外部动作必须在 reconciliation 闭合后由新的业务决策、新 intent 与新 approval/gate 产生。

### 9.4 worker lease 决定

首版 schema **必须包含** claim/lease 字段。原因：只靠内存 claim 无法在 duplicate worker、进程重启和 crash-before/after-send 场景中证明唯一领取者。

- 首次 claim 使用短事务 `UPDATE ... WHERE state='CREATED' AND send_started_at IS NULL AND version=? RETURNING ...`；expired reclaim 使用 `state='CLAIMED' AND lease_expires_at < now() AND send_started_at IS NULL AND version=?`。两者均写完整 claim quartet；PLACE 必须重检 session=`LIVE_ACTIVE` 及 window/risk/credential/kill gates，CANCEL 只能在 approved `cancel-if-safe` policy 下对既有 order 领取，可在 PAUSED/KILLED 收尾路径执行但不得推导任何新 PLACE 权限。
- lease 有配置化短上限，不得无限；worker 需要稳定 `claim_token` 才能推进 SEND_STARTED。
- 一旦写入 `SEND_STARTED`，lease 到期也不得自动回到 CREATED/CLAIMED 或重新发送；只能按 receipt 进入终态或 UNKNOWN/reconciliation。
- claim owner/token 是运行元数据，可以受控 CAS 更新；intent business payload、payload hash、action 字段不可变。

## 10. ExecutionReceipt

`ExecutionReceipt` 只表示每次网络 attempt 的 sanitized 外部执行回执，不成为第二订单或成交主表。

| Field | 语义与约束 |
| --- | --- |
| `receiptId` | UUID PK |
| `intentId` | FK `execution_intents.intent_id` |
| `attemptNo` | 从 1 开始；UNIQUE `(intent_id, attempt_no)` |
| `outcome` | `ACKNOWLEDGED / REJECTED / TIMEOUT / TRANSPORT_ERROR / UNKNOWN / QUERY_CONFIRMED / QUERY_NOT_FOUND` |
| `exchangeRequestId` | 可空、脱敏、长度受限；非 credential |
| `exchangeOrderId` | 可空；只保存 identity，不保存订单主状态 |
| `errorCategory` | 可空内部分类，不直接暴露 provider raw error |
| `errorCode` | 可空 sanitized code，长度受限 |
| `receivedAt` | UTC receipt time |
| `payloadDigest` | sanitized normalized receipt envelope digest；不保存 raw payload |
| `payloadDigestSchemaVersion` | 首版精确 `execution-receipt-envelope.v1`；未知版本拒绝 |

- receipt append-only：禁止 UPDATE/DELETE，不能用后续成功覆盖早期 timeout/unknown。
- 多次 attempt 使用递增 attemptNo。只有存在可信 negative proof 且 policy 明确允许时才可产生后续 mutating attempt；unknown 默认只允许 read/query attempt receipt。
- 严禁持久化 raw private response、signature、authorization metadata、Cookie、credential material、完整 request 或未经脱敏 headers/body。
- `ACKNOWLEDGED` receipt 只提供外部回执证据；订单状态仍由既有 `orders` owner 按合法状态机推进，fill 仍进入既有 `trades`。

## 11. LiveSession 状态机

### 11.1 状态分类

- non-terminal：`APPROVAL_PENDING / APPROVED / LIVE_WARMUP / LIVE_ACTIVE / LIVE_PAUSED / LIVE_STOPPED / LIVE_RECONCILING / RECONCILIATION_BLOCKED`。
- terminal：`REJECTED / FAILED / KILLED / LIVE_RECONCILED`。
- `RECONCILIATION_BLOCKED` 是交易阻断态，只能经证据闭合进入 `LIVE_RECONCILED`，不能返回 ACTIVE。

### 11.2 Transition Matrix

| from | command | to | requiredFacts | requiredRole | riskGate | credentialGate | killSwitchRequirement | auditEvent | recoverySemantics |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| APPROVAL_PENDING | APPROVE | APPROVED | exact scope/release/risk digest；未过期 window；creator≠approver | LIVE_APPROVER | frozen set structurally valid | reference only；不代表 permission | ENGAGED 可记录 approval，但不得 start | `LIVE_SESSION_APPROVED` | scope 变化后旧 approval 失效并回 pending |
| APPROVAL_PENDING | REJECT | REJECTED | exact scope、reason | LIVE_APPROVER | N/A | N/A | 任意安全状态 | `LIVE_SESSION_REJECTED` | terminal；新尝试建新 session |
| APPROVAL_PENDING | CHANGE_SCOPE | APPROVAL_PENDING | expected version、仅 draft fields | creator OPERATOR | 新 risk digest required | reference must exist | 任意 | `LIVE_SESSION_SCOPE_CHANGED` | version/hash 变化，旧 approval 不可用 |
| APPROVED | APPROVAL_EXPIRED | APPROVAL_PENDING | 当前时间 >= expiresAt、尚未 start、version | system | 重新评估前保持 BLOCKED | 重新评估前保持 BLOCKED | 任意安全状态 | `LIVE_SESSION_APPROVAL_EXPIRED` | 不修改旧 approval；相同 scope 可追加新 approval |
| APPROVED | START | LIVE_WARMUP | valid approval、release handle/digest、window、baseline reconcile | OPERATOR | preflight PASS | lifecycle/scope/IP/funding facts PASS；当前仍未实现 | 必须 DISENGAGED 且 version 可追踪；否则拒绝 | `LIVE_SESSION_WARMUP_STARTED` | 失败进入 FAILED/PAUSED，不直接 ACTIVE |
| LIVE_WARMUP | ACTIVATE | LIVE_ACTIVE | warmup、account/order/position baseline、clock/data quality全 PASS | OPERATOR | PASS | PASS | DISENGAGED；UNKNOWN/ENGAGED 拒绝 | `LIVE_SESSION_ACTIVATED` | 任何 unknown 立即 pause/kill |
| LIVE_WARMUP | PAUSE | LIVE_PAUSED | reason、version | OPERATOR | 可为 BLOCKED | 可为 BLOCKED | 任意；ENGAGED 强制 pause/kill | `LIVE_SESSION_PAUSED` | 禁止 PLACE；可诊断/对账 |
| LIVE_ACTIVE | PAUSE | LIVE_PAUSED | reason、version | OPERATOR 或 safety system | threshold/data/network 可触发 | mismatch 可触发 | ENGAGED/UNKNOWN 强制 | `LIVE_SESSION_PAUSED` | 禁止新 PLACE；outstanding action 对账 |
| LIVE_PAUSED | RESUME | LIVE_ACTIVE | 原 approval 仍有效、window 未过、无 unknown intent、version | OPERATOR | 全量重检 PASS | 全量重检 PASS | DISENGAGED 且传播验证 PASS | `LIVE_SESSION_RESUMED` | 任一 gate 非 PASS 保持 PAUSED |
| LIVE_ACTIVE | STOP | LIVE_STOPPED | reason、version、停止新 PLACE | OPERATOR | 记录当前结果 | 只允许必要 read；cancel-if-safe 另建 intent | ENGAGED 不阻止安全 stop | `LIVE_SESSION_STOPPED` | 不得恢复 ACTIVE；进入 reconcile |
| LIVE_PAUSED | STOP | LIVE_STOPPED | reason、version | OPERATOR | N/A | N/A | 任意 | `LIVE_SESSION_STOPPED` | 不得恢复 ACTIVE |
| LIVE_STOPPED | BEGIN_RECONCILE | LIVE_RECONCILING | outstanding intent set sealed、snapshot digest | OPERATOR/system | read-only reconcile policy | read-only capability；无 mutating permission推导 | 任意，建议 ENGAGED | `LIVE_RECONCILIATION_STARTED` | 只读收敛 |
| LIVE_RECONCILING | RECONCILE_PASS | LIVE_RECONCILED | order/fill/account/position/ledger convergence | OPERATOR + evidence | PASS/closed | no credential material persisted | ENGAGED 或已停止 worker | `LIVE_RECONCILIATION_COMPLETED` | terminal |
| LIVE_RECONCILING | RECONCILE_BLOCK | RECONCILIATION_BLOCKED | unknown/divergence case digest | system/OPERATOR | BLOCKED | UNKNOWN/FAILED 可触发 | 必须阻断新执行 | `LIVE_RECONCILIATION_BLOCKED` | 只允许诊断、read、case resolution |
| RECONCILIATION_BLOCKED | RESOLVE_AND_CLOSE | LIVE_RECONCILED | 独立证据闭合、actor/reason/evidence digest | OPERATOR + reviewer | closed | no trading gate | worker 无新 claim | `LIVE_RECONCILIATION_RESOLVED` | 不回 ACTIVE；terminal |
| 任一 non-terminal | KILL | KILLED | reason、kill switch event/version | ADMIN emergency 或 safety system | immediate block | access revoke/stop claim | ENGAGE 必须持久化并传播 | `LIVE_SESSION_KILLED` | terminal；cancel-if-safe/reconcile 另行受控，不自动恢复 |
| APPROVED/WARMUP/ACTIVE/PAUSED | FAIL | FAILED | non-recoverable error、reason/evidence | system | blocked | blocked/unknown | engage 或保持 fail-closed | `LIVE_SESSION_FAILED` | 禁止自动重下；只允许收尾对账后新 session |

固定禁止：未批准→ACTIVE、PAUSED→新 PLACE、KILLED→自动恢复、FAILED→自动重下、RECONCILIATION_BLOCKED→交易，全部必须返回稳定错误并追加拒绝审计。

## 12. Transaction Boundary

固定原则：`Control Plane DB transaction != external exchange HTTP transaction`。

未来建议流程：

1. 短 DB transaction 锁定/校验 session version、approval、risk/credential/kill facts，原子创建 immutable approved intent、event 与 audit。
2. commit，释放数据库连接与锁。
3. worker 使用短 transaction CAS claim intent，写 bounded lease；commit。
4. worker 在 transaction 外执行 future exchange call；所有 timeout/rate limit/retry 均受配置上限控制。
5. 新短 transaction append receipt，并以 version/state guard 推进 intent；receipt append 失败时保留 UNKNOWN/reconciliation 路径。
6. reconciliation 读取外部事实与既有 orders/trades/positions/ledger，追加必要 receipt/risk/audit，再推进本地 order/session。

禁止 `BEGIN → call OKX → wait → COMMIT`。外部调用不得持有 session/order/intent DB row lock，也不得处于账本 transaction 中。

## 13. Idempotency Model

| Operation | Idempotency key | Window / storage | Duplicate semantics | Concurrent handling |
| --- | --- | --- | --- | --- |
| create session | caller key + owner + account + venue | durable session lifetime；DB | same payload 返回原 session；different payload conflict | unique constraint + payload/scope hash |
| session command | session + command + caller key | durable event/audit | 已完成的相同 command 返回原 transition；payload 不同 conflict | version CAS + unique expression index `(session,command,COALESCE(actor_id,0),idempotency_key)` + payload hash compare |
| approval | approvalId；session + scope hash + decision | permanent append-only | same approvalId/same fact返回原 approval；same id/different fact conflict；过期后可追加新 id | session row lock + state/version CAS；不使用阻断续批的partial unique |
| create intent | intentId + payloadHash | permanent | same/same 返回原 fact；same/different conflict | PK + payload hash compare；不依赖内存锁 |
| worker claim | intent + claim token | bounded lease | same token 返回当前 claim；其他 token被拒绝 | CAS state/version/lease |
| receipt append | receiptId；intent + attemptNo | permanent append-only | same normalized receipt 返回原 fact；different payload conflict | PK + unique `(intent,attemptNo)` |
| ledger posting | 复用既有 ledger idempotency key | 既有 durable contract | 不新增 GateY ledger protocol | 复用既有 repository/unique index |

## 14. Concurrency、Crash 与 Race Semantics

| Scenario | 必须行为 |
| --- | --- |
| concurrent approval | 同一 scope 最多一个有效 APPROVED fact；第二请求返回原事实或 conflict。批准前在短事务校验 session version/scope 与职责分离。 |
| pause / kill race | KILL 优先且 terminal；pause CAS 失败后重读 KILLED。任何 worker claim 在 kill version变化后失败；已 SEND_STARTED 的 intent进入 unknown/reconcile，不假装未发送。 |
| duplicate worker claim | 单条 CAS + bounded lease + unique claim token；只有持有当前 token 的 worker可写 SEND_STARTED。 |
| crash-before-send | 若只 CLAIMED 且无 SEND_STARTED/receipt，lease 到期后可受控重新 claim；必须证明尚未开始发送。 |
| crash-after-send-before-receipt | intent 进入或恢复为 UNKNOWN；按稳定 clientOrderId query/reconcile，禁止 blind retry。 |
| timeout / unknown result | append TIMEOUT/UNKNOWN receipt；session 至少 PAUSED，intent=`UNKNOWN` 并进入 reconciliation；原 intent 永不重发。 |
| PLACE / CANCEL race | CANCEL 必须引用既有 local order；若 PLACE 尚 UNKNOWN，cancel 先进入受控等待/reconcile，不能假定订单存在或不存在。 |
| partial fill | fills 进入既有 `trades` 并推进既有 order；CANCEL receipt 不覆盖 late fill。最终以 order/fill/account/position/ledger convergence为准。 |
| optimistic locking | session/intent mutable state字段均使用 positive BIGINT version 和 `WHERE version=?`；0-row update 返回 conflict，不做无条件覆盖。 |

## 15. Migration Work Order

### 15.1 通用规则

- 当前最高 migration 为 V38；下一版本号只在独立 review 检查 branch 最新状态后确定，本文不预占 `V39`。
- 仅允许新增 6 张表：`risk_limit_sets`、`live_sessions`、`live_session_events`、`operator_approvals`、`execution_intents`、`execution_receipts`。依赖顺序按此排列。
- 所有表/字段/constraint 必须有中文 COMMENT；敏感字段注释必须明确禁入 material/private payload。
- 新表均无 historical backfill；不得从 Paper/Shadow/现有 orders 推断 LIVE session、approval、risk 或 intent。
- 外键默认 `ON UPDATE RESTRICT ON DELETE RESTRICT`；不级联删除事实。
- migration 采用 forward-only remediation。生产已应用后不依赖 down migration 删除外部动作证据；失败在 deploy 前停止，已提交后用新 migration修复。

### 15.2 `risk_limit_sets`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `risk_limit_set_id` | UUID | PK |
| `digest_schema_version` | VARCHAR(64) | NOT NULL，CHECK `risk-limit-set.v1` |
| `version` | INTEGER | NOT NULL, > 0 |
| `effective_scope` | VARCHAR(64) | NOT NULL，首版 `LIVE_SESSION_OKX_SPOT` |
| `quote_currency` | VARCHAR(16) | NOT NULL，CHECK `USDT` |
| `capital_cap` | NUMERIC(38,8) | NOT NULL，`0 < value <= 10000` |
| `max_order_notional` | NUMERIC(38,8) | NOT NULL，`0 < value <= capital_cap AND value <= 1000` |
| `max_symbol_position_notional` | NUMERIC(38,8) | NOT NULL，`0 < value <= capital_cap` |
| `max_daily_realized_loss` | NUMERIC(38,8) | NOT NULL，`0 < value <= capital_cap` |
| `max_daily_total_loss` | NUMERIC(38,8) | NOT NULL，`realized <= value <= capital_cap` |
| `max_open_orders` | INTEGER | NOT NULL，1..20 |
| `max_intraday_orders` | INTEGER | NOT NULL，`max_open_orders..200` |
| `symbol_allowlist` | TEXT[] | NOT NULL，cardinality 1..2；trigger 校验 uppercase/sorted/unique/USDT quote |
| `order_type_allowlist` | TEXT[] | NOT NULL，首版精确 `ARRAY['LIMIT']` |
| `max_session_duration_seconds` | INTEGER | NOT NULL，60..14400 |
| `spread_limit_bps` | NUMERIC(18,8) | NOT NULL，0..1000 |
| `slippage_limit_bps` | NUMERIC(18,8) | NOT NULL，0..1000 |
| `max_market_data_age_ms` | INTEGER | NOT NULL，1..5000 |
| `min_data_coverage_bps` | INTEGER | NOT NULL，1..10000 |
| `required_data_source` | VARCHAR(32) | NOT NULL，CHECK `OKX_PRIMARY` |
| `data_quality_action` | VARCHAR(16) | NOT NULL，CHECK `BLOCK` |
| `canonical_digest` | VARCHAR(64) | NOT NULL, lowercase SHA-256, UNIQUE |
| `created_by` | BIGINT | NOT NULL FK `users.id` |
| `created_at` | TIMESTAMPTZ | NOT NULL default now |

- INDEX：`(effective_scope, version DESC)`；UNIQUE 候选 `(effective_scope, version)` 与 `canonical_digest`。
- immutable：共享 DB trigger 拒绝 UPDATE/DELETE（SQLSTATE `23514`）；repository 只暴露 insert/read。GateY-2 PostgreSQL regression 必须验证 direct SQL rejection。
- lock：insert-only，无应用 row update lock。
- retention：永久保留至少覆盖关联 session/audit/法务策略；存在引用时不得 purge。
- COMMENT：说明规则定义而非风险判定结果，不保存 credential、行情 raw payload或账户余额。

### 15.3 `live_sessions`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `session_id` | UUID | PK |
| `owner_id` | BIGINT | NOT NULL FK `users.id` |
| `exchange_account_id` | BIGINT | NOT NULL FK `exchange_accounts.exchange_account_id` |
| `venue` | VARCHAR(32) | NOT NULL，CHECK `OKX_SPOT` |
| `strategy_release_id` | VARCHAR(128) | NOT NULL FK `strategy_release_admission_state.publish_record_id` |
| `release_digest` | VARCHAR(64) | NOT NULL SHA-256 |
| `release_admission_revision` | BIGINT | NOT NULL, > 0；创建/批准/start 时锁定并匹配已绑定 identity 的 admission state |
| `risk_limit_set_id` | UUID | NOT NULL FK `risk_limit_sets` |
| `risk_limit_set_digest` | VARCHAR(64) | NOT NULL SHA-256 |
| `credential_reference` | BIGINT | NOT NULL FK `exchange_account_credentials.credential_id` |
| `symbol_allowlist` | TEXT[] | NOT NULL，cardinality 1..2 |
| `capital_cap` | NUMERIC(38,8) | NOT NULL, > 0 |
| `execution_window_start` | TIMESTAMPTZ | NOT NULL |
| `execution_window_end` | TIMESTAMPTZ | NOT NULL, > start |
| `state` | VARCHAR(32) | NOT NULL，CHECK 全部状态 |
| `version` | BIGINT | NOT NULL default 1, > 0 |
| `approval_scope_hash` | VARCHAR(64) | NOT NULL SHA-256 |
| `approval_scope_schema_version` | VARCHAR(64) | NOT NULL，CHECK `approval-scope.v1` |
| `next_event_sequence` | BIGINT | NOT NULL default 1, > 0 |
| `created_by` | BIGINT | NOT NULL FK `users.id`，用于职责分离 |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL |

- partial unique：`(exchange_account_id, venue) WHERE state IN (non-terminal states)`，阻止同账户/venue并行 active session。
- INDEX：`(owner_id, created_at DESC)`、`(exchange_account_id, state, updated_at DESC)`、`(strategy_release_id)`、`(risk_limit_set_id)`。
- CHECK：digest格式、window、capital、array cardinality/state/schema versions；DB trigger 拒绝 terminal 恢复、非 `APPROVAL_PENDING` scope mutation、scope mutation 未改变 hash/version。跨表 owner/account/release/risk digest一致性由锁定相关 rows 的短事务 Service authoritative enforcement。
- lock：state/scope command 使用 version CAS；approval/start 可 `SELECT ... FOR UPDATE` 单 session 短锁。
- retention：terminal session 永久/长期审计保留；禁止 hard delete，后续只允许 archive policy。
- initialization：不 backfill；第一行只能由未来显式 command 创建。
- COMMENT：credential_reference 只指向 credential record，不得保存 material；session 不代表交易授权。

### 15.4 `live_session_events`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `event_id` | UUID | PK |
| `session_id` | UUID | NOT NULL FK `live_sessions` |
| `sequence_no` | BIGINT | NOT NULL, > 0 |
| `from_state` | VARCHAR(32) | nullable only for CREATED |
| `to_state` | VARCHAR(32) | NOT NULL |
| `command` | VARCHAR(64) | NOT NULL |
| `actor_id` | BIGINT | nullable for bounded system actor，FK when present |
| `request_id` / `trace_id` | VARCHAR(128) | NOT NULL, nonblank |
| `reason_code` | VARCHAR(128) | NOT NULL, nonblank |
| `idempotency_key` | VARCHAR(128) | NOT NULL, nonblank；raw key 不得进入日志/响应 |
| `command_payload_hash` | VARCHAR(64) | NOT NULL，canonical command payload SHA-256 |
| `command_payload_schema_version` | VARCHAR(64) | NOT NULL，CHECK `live-session-command.v1` |
| `metadata` | JSONB | NOT NULL default `{}`，脱敏、bounded |
| `created_at` | TIMESTAMPTZ | NOT NULL |

- UNIQUE `(session_id, sequence_no)`；UNIQUE expression index `(session_id,command,COALESCE(actor_id,0),idempotency_key)`；INDEX `(session_id, sequence_no)`、`(trace_id)`；timeline 不再依赖 timestamp tie-break。相同 idempotency scope 的 payload hash 相同则返回原 event，hash 不同则 conflict。
- append-only：共享 DB trigger 拒绝 UPDATE/DELETE；metadata 禁止 credential、raw request/response、headers/signature/private payload。
- lock：先 `SELECT ... FOR UPDATE` 或 version CAS 锁定 session row，读取 `next_event_sequence`，在同一 transaction 将其递增并以旧值 append event；失败整体 rollback。禁止 `SELECT MAX(sequence_no)+1`。GateY-2 concurrency integration test 必须证明并发 allocation 无 duplicate/gap（失败 transaction 不计已提交序列）。
- retention：至少与 session 同寿命；不得级联删除。

### 15.5 `operator_approvals`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `approval_id` | UUID | PK |
| `session_id` | UUID | NOT NULL FK `live_sessions` |
| `scope_hash` / `release_digest` / `risk_limit_set_digest` | VARCHAR(64) | NOT NULL SHA-256 |
| `approver_id` | BIGINT | NOT NULL FK `users.id` |
| `approver_role` | VARCHAR(64) | NOT NULL，CHECK `LIVE_APPROVER` |
| `decision` | VARCHAR(16) | NOT NULL，`APPROVED/REJECTED` |
| `reason` | TEXT | NOT NULL, bounded at API/service layer |
| `approved_at` / `expires_at` | TIMESTAMPTZ | NOT NULL，expires > approved |

- partial unique：无。相同 scope 必须允许旧 approval 过期后追加新 immutable approval；并发由 session row lock、`APPROVAL_PENDING` state 和 version CAS 防护。
- INDEX：`(session_id, approved_at DESC)`、`(approver_id, approved_at DESC)`、`(expires_at) WHERE decision='APPROVED'`。
- append-only：共享 DB trigger 拒绝 UPDATE/DELETE；过期是查询判定，不回写 mutable status。
- lock：approval transaction 锁定 session，校验 version/scope/creator≠approver后 append并迁移 state。
- retention：永久保留；不级联删除。
- COMMENT：明确 approval 不等于 exchange permission、LIVE authorization 或 kill-switch release。

### 15.6 `execution_intents`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `intent_id` | UUID | PK |
| `session_id` | UUID | NOT NULL FK `live_sessions` |
| `sequence` | BIGINT | NOT NULL, > 0 |
| `action` | VARCHAR(16) | NOT NULL `PLACE/CANCEL` |
| `symbol` | VARCHAR(64) | NOT NULL |
| `side` | VARCHAR(8) | PLACE required `BUY/SELL`；CANCEL nullable |
| `order_type` | VARCHAR(16) | PLACE `LIMIT`；CANCEL nullable |
| `quantity` / `limit_price` | NUMERIC(38,8) | PLACE > 0；CANCEL nullable |
| `payload_hash_schema_version` | VARCHAR(64) | NOT NULL，CHECK `execution-intent-payload.v1` |
| `payload_hash` | VARCHAR(64) | NOT NULL SHA-256 |
| `client_order_id` | VARCHAR(128) | NOT NULL |
| `local_order_id` | VARCHAR(64) | NOT NULL FK `orders.order_id`；PLACE/CANCEL 创建时均已绑定 |
| `state` | VARCHAR(32) | NOT NULL，CHECK intent states |
| `version` | BIGINT | NOT NULL default 1, > 0 |
| `claimed_by` / `claim_token` | VARCHAR(128) / UUID | nullable pair |
| `claimed_at` / `lease_expires_at` | TIMESTAMPTZ | nullable pair；claim 时 required，lease > claimed |
| `send_started_at` | TIMESTAMPTZ | nullable；首次发送前原子写入，之后不可清空/改变 |
| `created_at` | TIMESTAMPTZ | NOT NULL |

- UNIQUE `(session_id, sequence)`；partial unique `(session_id, client_order_id) WHERE action='PLACE'` 为 defense-in-depth，既有 orders 仍以 `(account_id,client_order_id)` 为订单幂等 owner。
- CHECK：PLACE 必须 `side IN ('BUY','SELL') / order_type='LIMIT' / quantity>0 / limit_price>0`；CANCEL 必须 `side/order_type/quantity/limit_price IS NULL` 且引用 existing `local_order_id/client_order_id`；claim quartet 全空或全非空且 `lease_expires_at > claimed_at`；CREATED/CANCELLED 尚未领取时 quartet 为空，CLAIMED 及所有 send 后状态 quartet 非空；`state='SEND_STARTED' OR send 后状态` 时 `send_started_at IS NOT NULL`；digest/state/version/schema version 合法。
- INDEX：bounded claim partial `(state, lease_expires_at, created_at) WHERE state IN ('CREATED','CLAIMED') AND send_started_at IS NULL`；`(session_id,state,created_at)`；`(local_order_id)`；`(client_order_id)`。
- mutable aggregate：DB trigger 固定 intent 合法 transition、保护 immutable business columns/local order binding、`send_started_at` 单向 first-bind 与 SEND_STARTED 后 no-reclaim；application 使用 state/version/token CAS。GateY-3 必须覆盖 direct SQL immutable/illegal-transition rejection。
- lock：claim用单 row CAS/`RETURNING`，禁止长事务和全表 scan；worker query 必须 bounded batch。
- retention：永久或至少覆盖 order/trade/ledger/reconciliation审计期；UNKNOWN 永不自动 purge。

### 15.7 `execution_receipts`

| Column | PostgreSQL type | Null / rule |
| --- | --- | --- |
| `receipt_id` | UUID | PK |
| `intent_id` | UUID | NOT NULL FK `execution_intents` |
| `attempt_no` | INTEGER | NOT NULL, > 0 |
| `outcome` | VARCHAR(32) | NOT NULL，CHECK receipt outcomes |
| `exchange_request_id` | VARCHAR(128) | nullable |
| `exchange_order_id` | VARCHAR(128) | nullable |
| `error_category` / `error_code` | VARCHAR(64) / VARCHAR(128) | nullable sanitized values |
| `received_at` | TIMESTAMPTZ | NOT NULL |
| `payload_digest` | VARCHAR(64) | NOT NULL SHA-256 |
| `payload_digest_schema_version` | VARCHAR(64) | NOT NULL，CHECK `execution-receipt-envelope.v1` |

- UNIQUE `(intent_id, attempt_no)`；INDEX `(intent_id, received_at, receipt_id)`、`(exchange_order_id) WHERE exchange_order_id IS NOT NULL`、`(outcome, received_at DESC)`。
- append-only：共享 DB trigger 拒绝 UPDATE/DELETE；不保存 raw payload。
- lock：append receipt 与 intent CAS 可同一短 transaction；不得持有外部 HTTP transaction。
- retention：永久或跟随 intent审计期；unknown/error receipt不得清洗覆盖。

### 15.8 Mutable / immutable / append-only enforcement 决定

| Table | 分类 | 数据库 enforcement | application/repository enforcement |
| --- | --- | --- | --- |
| `risk_limit_sets` | `IMMUTABLE` | shared BEFORE UPDATE OR DELETE trigger，SQLSTATE `23514` | repository 只暴露 insert/read |
| `live_sessions` | `MUTABLE_AGGREGATE` | CHECK + transition/scope/terminal trigger；partial unique 单活 | state/version CAS；scope 仅 pending |
| `live_session_events` | `APPEND_ONLY` | shared BEFORE UPDATE OR DELETE trigger | repository 只 append/read；同 session lock 分配 sequence |
| `operator_approvals` | `APPEND_ONLY` | shared BEFORE UPDATE OR DELETE trigger | locked session transaction append；不原地 expire |
| `execution_intents` | `MUTABLE_AGGREGATE` | immutable-column、合法 transition、send-start monotonic trigger | state/version/claim-token CAS；bounded claim batch |
| `execution_receipts` | `APPEND_ONLY` | shared BEFORE UPDATE OR DELETE trigger | repository 只 append/read；attempt unique |

高价值 facts 不依赖注释或单一 repository guard。下一轮 migration 必须创建 trigger/function 中文 COMMENT，并以 disposable PostgreSQL direct SQL regression 覆盖每个 immutable/append-only 表的 UPDATE/DELETE rejection、intent immutable field/illegal transition、session terminal/scope guard；DB role privilege 收紧可作为 defense-in-depth，但不能替代 trigger。

## 16. Migration Initialization、Lock 与 Rollback Review

### 16.1 Initialization

- 6 张表全部为空创建，无 historical backfill、无从现有 Paper/Shadow/order facts推断的 seed。
- 不修改现有大表字段；只有新表 FK metadata/validation可能短暂访问 referenced tables。
- 本工单不允许对既有表增加字段、constraint 或 trigger；所有新 constraint/trigger 只落在六张新表及其新 helper functions。需要改变既有表时必须拆分为独立 review，不能暗含在首版 migration。

### 16.2 `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`

当前 branch migration inventory 精确为 V1～V38，最高为 `V38__gate_x5a_admission_materialization_guard.sql`，不存在 V39；本文不预占下一版本。GateY-1 只冻结验证合同，实际测量在 GateY-5：

- 使用 production-like row volume 与相同 PostgreSQL major/version/config clone。
- 保持代表性 concurrent traffic：orders/trades/ledger/account/release read/write，不触发真实交易。
- 记录每条 DDL 的 lock mode、wait time、execution duration、blocked sessions和最大业务延迟。
- migration candidate 使用 PostgreSQL/Flyway 单 transaction，transaction 开头固定 `SET LOCAL lock_timeout='5s'`、`SET LOCAL statement_timeout='60s'`；只对空新表使用普通 `CREATE INDEX`，不使用 `CONCURRENTLY`。
- 六表为空创建，无 backfill、无既有大表 rewrite。新表 FK 在 transaction 中对 `users`、`exchange_accounts`、`strategy_release_admission_state`、`exchange_account_credentials`、`orders` 建引用；部署演练必须按 `SHARE ROW EXCLUSIVE` 级别的保守上界评估 referenced-table lock contention，不以 metadata-only 推断零锁风险。
- 注入 long transaction，验证 timeout 后无 partial schema、连接可恢复、Flyway history一致。
- 验证 transaction rollback behavior；若 PostgreSQL/Flyway DDL rollback失败或超阈值，停止 deployment。
- production deployment abort threshold 冻结为：preflight 发现任一目标 referenced table 上存在 age > 30s 的 open transaction 即不启动；任一 lock wait 达 5s、任一 statement 达 60s、任一业务请求被阻塞 > 2s、出现任一 migration/Flyway error 或任一业务请求错误即 rollback/abort；整体 rehearsal 超过 120s 也判失败。GateY-5 可收紧，不能放宽而不重审。

这些阈值只用于 disposable/production-like rehearsal 合同；`migration implementation != production migration deployment`。GateY-2 后续可实现并在 disposable PostgreSQL 验证，未经独立部署任务与明确授权不得触达生产。`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 在 GateY-5 实测关闭前继续阻断 production migration deployment 和第一笔真实订单。

### 16.3 `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`

- plain mutable filesystem path 不能作为可信 release identity，也不能单独进入 approval scope。
- session 只保存 immutable release digest + verified handle/reference；`strategy_release_id` 指向现有 release fact，不能替代 digest。
- GateY-4 必须实现 verified-open stable handle 或 sealed content-addressed workspace，并覆盖 replace/symlink/reparse race。
- 未关闭前，artifact consumption 和 worker start fail-closed；该 P2 阻断第一笔真实订单。

### 16.4 Forward remediation

- review/部署前失败：不提交 migration，修正文档/SQL candidate后重审。
- transactional migration 执行失败：验证 PostgreSQL rollback、Flyway history无错误记录、应用仍可使用旧 schema。
- migration 已成功但发现设计问题：不删除 live facts、不修改历史 migration；创建新的 forward remediation migration，保持旧应用兼容或停止发布。
- application rollback 与 schema rollback分离；真实 venue 已发生的外部事实不能用 DB rollback假装撤销。

## 17. Security、Logging 与 Retention Boundary

- Control Plane 只持有 credential reference和sanitized capability decision；未来 isolated worker才可在受限进程内 just-in-time使用精确 credential version。
- transfer/withdraw/funding endpoint 永久 default-deny；GateY-1～5 不配置可交易 credential，不调用 mutating endpoint。
- logs/audit 使用存在的 `traceId/requestId/sessionId/intentId/accountId/orderId`、actor、decision、reason/error code与elapsed；不存在的字段不编造。
- 禁止日志/COMMENT/metadata/receipt 保存 credential material、signature、Cookie、raw header、raw private request/response或明文 payload。
- GateY pilot retention 固定为 `NO AUTOMATIC HARD DELETE`：risk sets、sessions、approvals、events、intents、receipts 在 GateY pilot/freeze 前全部禁止自动清理或 hard delete。后续 archive/retention policy 必须独立设计、审查并证明不破坏监管、审计、unknown recovery与账务收敛；不能为控制表大小提前删除。

## 18. Independent Migration/Security Review Checklist

独立 review 必须逐项回答：

1. 六表是否仍是最小集合，是否有任何字段复制 orders/trades/positions/ledger/audit主事实。
2. FK type是否与 V38 baseline完全一致；下一 migration version是否仍可用。
3. state/action/outcome CHECK 是否完整，PLACE/CANCEL field matrix是否能在 DB fail-closed。
4. partial unique active-session predicate是否覆盖所有 non-terminal state，是否存在并发窗口。
5. approval职责分离、immutability、expiry和scope invalidation是否有数据库/Service双层保护。
6. risk canonicalization、numeric units/rounding、arrays排序/去重与 digest schema version是否精确。
7. intent immutable columns、一次性 local order binding、claim lease、SEND_STARTED后no-reclaim是否可证明。
8. unknown-result、cancel/place race、partial fill和late receipt是否不会触发blind retry或第二主账。
9. append-only策略选用trigger、privilege还是repository guard；是否有直接 SQL regression。
10. indexes是否支持bounded claim、session timeline、receipt lookup且不引入高写放大。
11. DDL lock/statement timeout、long transaction、rollback/abort threshold合同是否可在GateY-5实测。
12. plain path是否完全排除出release identity，stable-handle blocker是否保持fail-closed。
13. COMMENT/metadata/logging是否存在敏感信息泄露或raw provider payload入口。
14. migration是否无需backfill，是否保持旧应用forward compatibility与明确remediation路径。

## 19. 独立审查 Findings 与最终决定

### P0

- 无。

### P1

- 无。独立审查发现的合同缺口已在本文内以最小设计收口：真实 FK/identity anchor、legacy account bridge、risk单位/精度/上限、versioned canonical digest、intent 状态/field matrix/claim crash semantics、append-only triggers 与 event sequence allocation 均已冻结。
- `FIRST_REAL_ORDER_HARD_GATE` 中尚未实现/验证的 runtime 能力是未来批次的 pre-live blockers，不是本 migration work-order review 的未关闭 finding；它们继续阻断 migration deployment、worker start 和真实首单。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：合同已冻结，GateY-5实测前保持 blocker。
- `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：identity合同已冻结，GateY-4实现/验证前保持 blocker。
- `reconciliation_cases` 的独立必要性尚未证明，后置 GateY-3；不得在首版 migration顺手加入。
- `LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE`：现有 `orders.account_id` 与 `exchange_accounts.exchange_account_id` 属于不同 identity space；本工单已冻结 fail-closed mapping contract，GateY-2/3 实现与 PostgreSQL integration test 关闭前阻断 intent runtime，不阻断六张新表的本地 migration implementation。

### P3

- 无。

最终决定：`PASS / GATEY_1_MIGRATION_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_MIGRATION_CREATED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

唯一下一动作：`NQ-GATEY-1-LIVE-SESSION-DATA-MODEL-COMMIT-AND-PUSH`。
