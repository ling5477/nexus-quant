# NQ-GATEY-1-LIVE-SESSION-DATA-MODEL-MIGRATION-SECURITY-REVIEW — attempt-01

## Task Classification

- ownership：NQ-only。
- type：`INDEPENDENT_MIGRATION_REVIEW + LIVE_SECURITY_REVIEW + SCHEMA_CONTRACT_REVIEW + STATE_MACHINE_REVIEW + CONCURRENCY_IDEMPOTENCY_REVIEW + DDL_LOCK_REVIEW`。
- level：L 级高风险 independent review。
- result：`PASS / GATEY_1_MIGRATION_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_MIGRATION_CREATED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

## Starting Baseline

- branch=`dev`；`HEAD == origin/dev == 21d3e457f749774800f2908d34e6e19a500c076e`；staged empty。
- 起始 dirty paths 精确为任务允许的 10 个 GateY-1 文档路径，无 mixed worktree。
- exact-head CI：`NQ CI Baseline` run `31570833270 / completed / success / 10 jobs / bad=0`，`headSha` 精确匹配 HEAD。
- authority before：GateY-PLAN=`ACCEPTED|CI_GREEN`；GateY-1=`IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；checker `errors=0`。
- safety：LIVE=`DISABLED`、Shadow trading=`NOT_ENABLED`、kill switch=`ENGAGED`、real provider/private trading=`NOT_IMPLEMENTED`。

## Files Inspected

- governance/current：`AGENTS.md`、`CLAUDE.md`、root/current README、`STATUS.md`、`ROADMAP.md`、`GATEY_PLAN.md`、GateY-1 work order/implementation evidence、`DB_SCHEMA.md`、`API.md`、`ARCHITECTURE.md`、`MODULES.md`、`TESTING.md`、`WORKLOG.md`。
- schema/migration：完整 migration inventory V1～V38；定向读取 V1、V10、V12、V19、V27、V29～V31、V35、V37、V38 及相关 COMMENT/FK/constraint。
- code facts：`OrderAggregate`、`OrderRecord`、`OrderRepository`、`OrderCommandService`、`OrderCommandWriteService`、`JdbcOrderRepository`，以及定向检索的 Strategy Release、account/credential、kill switch、risk/reconciliation owner。
- 排除：`node_modules`、`target`、`build`、`dist`、`.git`、`test-results`、`logs`、`secrets`、`credentials`；未读取 `.env`、credential material、key/certificate、private payload、日志或 dump。

## Six-table Minimality Review

| Table | Decision | Review result |
| --- | --- | --- |
| `risk_limit_sets` | `NECESSARY` | 只拥有 immutable LIVE risk rule definition，不复制 runtime `risk_events` |
| `live_sessions` | `NECESSARY` | 只拥有 LIVE control-plane aggregate，不复制 order/trade/position/ledger/kill facts |
| `live_session_events` | `NECESSARY` | 只拥有 session 有序 lifecycle，不替代通用 `audit_logs` |
| `operator_approvals` | `NECESSARY` | 只拥有 exact-scope human decision，不等于 venue/LIVE/kill authorization |
| `execution_intents` | `NECESSARY` | 只拥有 future external action intent/claim，不成为第二 order |
| `execution_receipts` | `NECESSARY` | 只拥有 sanitized network-attempt evidence，不成为 order/fill 主表 |

`live_position_snapshots`、`portfolio_risk_snapshots`、`cost_slippage_facts` 继续拒绝；`reconciliation_cases` 继续 DEFER。`DUPLICATE_SYSTEM_OF_RECORD` finding=0。

## FK / Type / Fact Ownership Review

- 当前最高 migration 精确为 V38；V39 不存在且未被本轮预占。
- 真实类型：`users.id BIGINT`、`exchange_accounts.exchange_account_id BIGINT`、`exchange_account_credentials.credential_id BIGINT`、`backtest_publish_records.publish_record_id VARCHAR(128)`、`strategy_release_admission_state.publish_record_id VARCHAR(128)`、`orders.order_id VARCHAR(64)`。
- LiveSession release anchor 改为直接 FK 到 `strategy_release_admission_state.publish_record_id`，并绑定 `release_artifact_digest + admission_revision`；target 自身再以 RESTRICT FK 指向 publish record，不创建第二 release identity。
- credential 只引用 exact `credential_id`；不保存 encrypted/raw payload、external secret value、path 或 provider response。
- 所有 FK 固定 `ON UPDATE RESTRICT ON DELETE RESTRICT`；audited facts 无 cascade delete。
- 发现 legacy identity split：`orders.account_id -> accounts.account_id`，而 session 使用 `exchange_accounts.exchange_account_id`。合同已冻结为 `exchange_accounts.legacy_account_id == orders.account_id` 的 locked fail-closed bridge；GateY-2/3 integration test 关闭前禁止 intent runtime，不修改现有 order owner/schema。

## LiveSession / Approval Review

- 单活 partial unique 精确覆盖 `APPROVAL_PENDING / APPROVED / LIVE_WARMUP / LIVE_ACTIVE / LIVE_PAUSED / LIVE_STOPPED / LIVE_RECONCILING / RECONCILIATION_BLOCKED`，排除 terminal `REJECTED / FAILED / KILLED / LIVE_RECONCILED`。
- execution window 固定 UTC `[start,end)`，`end > start`；version positive BIGINT；scope 仅 pending 可变且必须递增 version、生成新 hash、append event。
- terminal 不恢复；KILLED 无 session-local override；kill switch disengage 不等于交易授权。
- approval 固定 append-only，绑定 scope/release/risk digest、expiry、approver identity/role；creator≠approver authoritative enforcement 为 locked application transaction。PostgreSQL integration 必须永久验证 self-approval、stale scope/version 与 concurrent approval rejection。

## Risk / Digest Review

- 风险资金单位固定 USDT；资金、notional、position notional、loss 使用 PostgreSQL `NUMERIC(38,8)` / Java `BigDecimal`，scale > 8 以 `RoundingMode.UNNECESSARY` 拒绝；风险计算向 8 位使用 `CEILING`，不因向下舍入放行。
- NULL 不表示 unlimited；除 spread/slippage 可为 0 且表示零容忍外，全部阈值必须正数。
- schema hard maxima：capital 10000 USDT、order notional 1000 USDT、open orders 20、intraday orders 200、session 14400s、spread/slippage 1000 bps；它们不是默认 pilot 值或 LIVE 授权。
- data quality 选择结构化字段，不使用 arbitrary JSONB：market age、coverage、source、action 均有 CHECK/上限。
- canonical encoder 固定 UTF-8/字段顺序/number/timestamp/null/array contract，四个 schema version 与 SHA-256 algorithm 均持久化；普通 serializer 禁止作为 canonical authority。

## Execution Intent / Claim / Receipt Review

- PLACE 必须引用预先创建的既有 local order，要求 side、LIMIT、quantity、price、stable clientOrderId；CANCEL 必须引用同一 local order/clientOrderId，side/type/quantity/price 全 NULL，不创建第二订单。
- intent states 冻结为 `CREATED / CLAIMED / SEND_STARTED / SEND_SUCCEEDED / UNKNOWN / FAILED / CANCELLED / RECONCILED`，合法 transition 已逐项固定，terminal 不恢复。
- claim schema 固定 `claimed_by/claim_token/claimed_at/lease_expires_at/send_started_at`；claim quartet 成组；crash-before-send 仅在 `send_started_at IS NULL` 时 reclaim。
- SEND_STARTED 后 lease expiry 永不触发 resend；crash-after-send/timeout 进入 UNKNOWN/reconciliation；KILL/PAUSE/window expiry 抑制所有未 SEND_STARTED PLACE。
- receipt append-only、UNIQUE `(intent_id,attempt_no)`；只允许 sanitized ids/outcome/error/digest/time。digest 只覆盖 normalized allowlisted envelope，raw request/response/signature/header/Cookie/credential material 永不落库。

## Append-only / Ordering / DDL Decision

- classification：sessions/intents=`MUTABLE_AGGREGATE`；risk sets=`IMMUTABLE`；approvals/events/receipts=`APPEND_ONLY`。
- high-value immutable/append-only facts 统一使用 DB trigger 拒绝 UPDATE/DELETE；session/intent 另有 DB transition/immutable-column trigger；repository/application guard 为第二层。下一 migration regression 必须执行 direct SQL negative tests。
- event order 使用 session row `next_event_sequence`；锁定 session、读取/递增 counter、append event 同一短 transaction；禁止无锁 `MAX+1`。
- 六表为空创建、无 backfill、无现有大表 rewrite；只创建普通新表 index；migration candidate 为单 transaction，固定 `SET LOCAL lock_timeout='5s'`、`statement_timeout='60s'`。
- deployment abort threshold 已冻结；`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 未关闭前禁止 production migration deployment。migration implementation 与 production deployment 明确分离。

## Security / Retention Boundary

- plain mutable path、user-controlled path、symlink/reparse path 不进入 schema identity；只绑定 admission fact、digest、revision。`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 继续阻断 artifact consumption、worker start 与 first real order。
- GateY pilot 固定 `NO AUTOMATIC HARD DELETE`；六表在 pilot/freeze 前禁止自动清理或 hard delete，后续 retention/archive 独立设计。
- credential access、exchange call、permission probe、order/cancel/transfer/withdraw、trading side effect 均为 0。

## Findings

- P0：无。
- P1：无；初审合同缺口均在不改变六表/owner/transaction architecture 的前提下完成最小文档收口。
- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`；`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`；`LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE` runtime implementation/test；`reconciliation_cases` necessity deferred。
- P3：无。

## Corrections Applied

- 修正 Strategy Release FK/identity anchor 与 admission revision binding。
- 冻结 risk units/precision/rounding/null/zero/max/data quality 与 canonical digest contract。
- 冻结 intent field matrix、完整状态机、claim timestamps/send-start crash semantics。
- 冻结 append-only/immutable DB trigger、session event counter 与 command idempotency index。
- 冻结 DDL timeout/abort、retention 与 legacy account bridge fail-closed contract。
- 同步 root/current authority、roadmap、testing/worklog 与 evidence index；未修改业务代码或 migration。

## Validation

- `check-current-authority.ps1`：PASS，errors=0；canonical after authority/next action 匹配。
- `check-doc-links.ps1 -Roots @('README.md','docs/current')`：PASS WITH WARNINGS，242 checked / 14 historical warnings / 0 errors；warnings 均为 append-only GateJ/GateX 历史路径。
- migration inventory：V1～V38 连续 38 个，missing/above38=`0/0`。
- `git diff --check`：PASS，whitespace errors=0；只有 Windows LF→CRLF 提示。
- final worktree allowlist：11 paths，unexpected/missing=`0/0`；staged empty。
- forbidden diff：backend/frontend/research/scripts/deploy/.github/migration/docs/gates/docs/archive=`0`。
- 产品测试：NOT RUN；本轮只修改文档，业务代码、migration、CI workflow diff=0，沿用 exact-head CI baseline，不伪造本地产品验证。

## Authority After

```text
accepted_batch=GateY-PLAN
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-1
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-1-LIVE-SESSION-DATA-MODEL-COMMIT-AND-PUSH
```

LIVE=`DISABLED`、Shadow trading=`NOT_ENABLED`、kill switch=`ENGAGED`、real provider/private trading=`NOT_IMPLEMENTED`。未 stage、commit、push、PR 或 tag。

## Next Action

唯一下一动作：`NQ-GATEY-1-LIVE-SESSION-DATA-MODEL-COMMIT-AND-PUSH`。

推荐 commit：`docs(gatey): accept GateY-1 live session data model`。
