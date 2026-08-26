# NQ-GATEY-3 Execution Intent / Receipt / Fake Exchange Implementation — attempt-01

## Task classification

NQ-only、L 级 `BACKEND_IMPLEMENTATION + EXECUTION_INTENT_RUNTIME + EXECUTION_RECEIPT_RUNTIME + DETERMINISTIC_FAKE_EXCHANGE + IDEMPOTENCY + CLAIM_LEASE + CRASH_RECOVERY + LOCAL_RECONCILIATION + POSTGRESQL_CONCURRENCY_TEST + ARCHITECTURE_HYGIENE`。

## Scope

- 实现 V39 `execution_intents` / `execution_receipts` 的 domain、application port/service 与 JDBC/CAS runtime。
- fake exchange 只存在于测试 fixture，不注册 production Bean，不使用 HTTP、socket、DNS、SDK、credential 或 provider DTO。
- 复用既有 `orders`、`live_sessions`、`exchange_accounts` facts；不建立第二套 order/trade/position/ledger。
- V1～V39 diff 为 0；未创建 V40；未改 API、frontend、Python、部署或 CI。

## Implementation

- 状态矩阵固定为 V39 枚举：`CREATED → CLAIMED → SEND_STARTED`；`CLAIMED → CANCELLED`；`SEND_STARTED → SEND_SUCCEEDED / UNKNOWN / FAILED`；`UNKNOWN → RECONCILED`。禁止回到 `CREATED`、`UNKNOWN` reclaim、`FAILED` 自动重试与 `RECONCILED` 重发。
- canonical payload 使用 `execution-intent-payload.v1`、UTF-8、固定字段顺序、`Locale.ROOT` 与 scale=8 的 `BigDecimal`；stable clientOrderId 使用 `execution-client-order-id.v1`。Golden：`nq1-6d3a6706f72a51b2cd08d0672372d3720cf2c30a`；payload SHA-256=`d37fc1573db221c8212350c6f3e3c98b5a69423043f631ab7fc5351509081b78`。
- `createOrGet` 通过 intentId transaction advisory lock 与 payload hash 实现 same/same 返回、same/different `IDEMPOTENCY_CONFLICT`。
- PLACE 只接受 BUY/SELL LIMIT、正 quantity/price、既有 local order；`orders.client_order_id` 精确等于 stable clientOrderId。CANCEL 字段为空并必须绑定唯一原 PLACE 的 localOrderId/clientOrderId。
- identity bridge 精确验证 `live_sessions.exchange_account_id → exchange_accounts.legacy_account_id == orders.account_id`；不一致返回 `ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED`，不创建 intent。
- claim/reclaim 使用 row lock、DB authoritative time、state/version/claimToken CAS；只允许 `CREATED` 首次 claim，或 `CLAIMED + expired lease + send_started_at IS NULL` reclaim。`SEND_STARTED` 后禁止 reclaim。
- `markSendStarted` 在单独短事务提交后才调用 fake mutation；DB transaction 与 exchange call 分离。mutation 抛异常或 receipt 写失败均保留不可逆 `SEND_STARTED`，恢复只按 clientOrderId query。
- receipt 只保存 allowlisted normalized identity/error envelope 与 `execution-receipt-envelope.v1` digest；不保存 raw request/response、Authorization、Cookie、signature、key、secret 或 passphrase。intent row lock 内分配 `MAX(attempt_no)+1`，receipt insert 与 intent CAS transition 同事务回滚。
- ACK→`SEND_SUCCEEDED`，explicit reject→`FAILED`，timeout/transport/unknown→`UNKNOWN`；`SEND_STARTED`/`UNKNOWN` 恢复只 query，不 resend。partial-fill/cancel-race 只形成 normalized reconciliation receipt，不覆盖 trades/order facts。

## Validation

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| `ExecutionIntentRuntimeTest` | PASS（通过） | 10 tests / 0 failures / 0 errors / 0 skipped；状态矩阵、golden、idempotency、canonical ambiguity、CANCEL matrix、全部 mutation/query scenario 与 crash matrix |
| `LiveSessionFactModelPostgresIntegrationTest` | PASS（通过） | PostgreSQL 17.7 disposable；1 test / 0 failures / 0 errors / 0 skipped；fresh V1→V38→V39、并发 create/claim、lease reclaim、SEND_STARTED no reclaim、CAS、bridge、CANCEL identity、receipt concurrency、attemptNo=1、rollback |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-adapter-api -am test` | BUILD SUCCESS | 16-module reactor；相关汇总 117 tests / 0 failures / 0 errors / 4 existing conditional skips |
| `mvn -f backend/pom.xml test`（本机默认 local DB） | FAIL（失败，环境基线） | 本机长期 DB 的 V38 checksum 为 `-977971896`，仓库当前 V38 为 `-1061979028`；未执行 Flyway repair、未写用户数据库 |
| `mvn -f backend/pom.xml test`（isolated PostgreSQL 17.7） | BUILD SUCCESS | fresh V1→V39；按仓库既有约定加入一次性 `SIM / ACTIVE` legacy account fixture；23/23 reactor SUCCESS；`nq-app` 270 tests / 0 failures / 0 errors / 27 existing conditional skips |
| ArchUnit / no-outbound focused tests | PASS（通过） | `ModuleBoundaryArchTest`、`PackageBoundaryArchTest`、`NoOutboundExchangeGuardTest`、OKX/Binance no-real endpoint tests 通过；1 条 CI-only guard 按既有条件 skip |
| Static boundary scan | PASS（通过） | execution 新代码无网络、credential、real provider、production fake Bean；migration diff=0、V40=0 |

已知 warning：既有 SLF4J no-provider、Mockito dynamic-agent、unchecked/deprecation 输出及 Windows LF→CRLF 提示；不影响本轮测试结论。CI=`NOT_RUN`。

## Crash recovery / NO BLIND RETRY proof

- crash-before-claim：mutation count=0。
- crash-after-claim-before-send：lease 到期可 reclaim，随后 mutation 总数=1。
- crash-after-SEND_STARTED-before-fake：恢复直接 query，mutation count=0。
- crash-after-fake-before-receipt：状态保留 `SEND_STARTED`，恢复 query 后 mutation 总数仍=1。
- receipt failure：receipt/state 同事务回滚，状态不伪造成成功；恢复 query，mutation 总数仍=1。
- reconciliation retry：query 可重试；mutation count 永不超过 1。

## Architecture and transaction findings

- intent/receipt 属于 livecontrol execution contract；domain 不依赖 JDBC/provider DTO，JDBC 不编排 worker、不调用 exchange。
- 同 session sequence 在 `FOR UPDATE OF ls,o` 锁定 session/order 后分配；同 session intent 创建串行化。
- receipt attempt 在 intent `FOR UPDATE` 锁内分配；receipt insert 与 state/version CAS 为同一短事务。
- CANCEL 原 PLACE identity 由 sessionId/localOrderId/clientOrderId 唯一匹配证明；不推导新 PLACE 权限。
- 未新增 `live_session_events` caller，因此 GateY-2 event residual 未扩大。

## Findings

- P0：0。
- P1：0；最终结论仍须独立 security/execution review。
- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`、`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 继续保留；`LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE` 具有真实 PostgreSQL fail-closed/positive-path 证据，作为候选 `CLOSED` 交由独立 review 确认。
- P3：0。

## Boundary confirmation

`LIVE=DISABLED`，kill switch=`ENGAGED`；real provider/private trading=`NOT_IMPLEMENTED`；credential material access、real exchange call、real permission probe、real PLACE、real CANCEL、transfer、withdraw 均为 0。production migration deployment、worker deployment、first real order 均未授权。fake mutation 不是 real exchange call。

## Final decision

`PASS / GATEY_3_FAKE_EXECUTION_RUNTIME_IMPLEMENTED / IDEMPOTENCY_ENFORCED / CLAIM_LEASE_ENFORCED / NO_BLIND_RETRY_PROVEN / UNKNOWN_RECONCILIATION_PROVEN / POSTGRESQL_GREEN / PENDING_INDEPENDENT_REVIEW / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED`。

Authority after：`GateY-3 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。

唯一下一动作：`NQ-GATEY-3-EXECUTION-INTENT-RECEIPT-FAKE-EXCHANGE-SECURITY-REVIEW`。

建议未来 commit：`feat(gatey): implement deterministic fake execution runtime`。
