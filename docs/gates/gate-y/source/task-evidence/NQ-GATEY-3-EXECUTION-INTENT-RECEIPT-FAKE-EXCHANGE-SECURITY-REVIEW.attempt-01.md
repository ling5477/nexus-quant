# NQ-GATEY-3 Execution Intent / Receipt / Fake Exchange Security Review — attempt-01

## Review target

本轮为 NQ-only、L 级独立 execution/security review。审查对象是当前未提交的 GateY-3 fake/local execution runtime、V39 compatibility、状态机、幂等、claim/lease、crash recovery、receipt 原子性、legacy account bridge、fake adapter 隔离与 PostgreSQL 并发证据。

允许在当前 GateY-3 文件内关闭 P0/P1；禁止修改 V39、创建 V40、实现真实 provider/credential/private endpoint、production worker、真实 PLACE/CANCEL、LIVE 或 micro-live，也禁止修改 frontend、research、scripts、deploy、`.github`、`docs/gates`、`docs/archive`。

## Starting baseline

- branch=`dev`；`HEAD == origin/dev == a6c390b4f8e8c852c4b6516a4bc3fdd90aa14d9c`；staged empty。
- accepted batch=`GateY-2 / ACCEPTED|CI_GREEN`；work batch=`GateY-3 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，real provider/private trading=`NOT_IMPLEMENTED`。

## Evidence checked

- `livecontrol/execution/domain/**`、`application/**`、`application/port/**`、JDBC repository、runtime unit tests 与 PostgreSQL integration tests。
- V39 execution intent/receipt schema、trigger、状态矩阵、唯一约束、append-only/immutable contract；V39 diff=0，V40=0。
- 既有 `orders`、`trades`、`positions`、ledger、`exchange_accounts`、`live_sessions`、durable global kill switch 与 no-real/no-outbound guards。
- module/package ArchUnit、OKX/Binance no-real hardening 与依赖 diff。

## P0/P1 findings and corrections

审查过程中发现并在当前 GateY-3 allowlist 内关闭以下 P1；这些不是“初始实现无问题”的追认：

1. PLACE writer 仅检查 `LIVE_ACTIVE`，未绑定 durable global kill switch。修复为 create 与 `SEND_STARTED` 前均要求 `GLOBAL_TRADING=DISENGAGED`，缺失/重复/非 DISENGAGED 一律 fail-closed。
2. legacy bridge 未验证 `exchange_accounts.owner_user_id == live_sessions.created_by`，且 missing order/NULL/mismatch taxonomy 不统一。修复为统一 `ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED`，并证明不创建 intent。
3. 原 PLACE=`SEND_STARTED/UNKNOWN` 时仍可创建 CANCEL。修复为只接受唯一原 PLACE 的 `SEND_SUCCEEDED`，或 `RECONCILED + latest QUERY_CONFIRMED`；其余返回 `CANCEL_PLACE_RECONCILIATION_REQUIRED`。
4. public draft 只校验 digest 形状，repository 未重新 canonicalize。修复为 intent/receipt 入库前重算 canonical envelope，并拒绝 forged digest。
5. canonical 字符串允许 CR/LF，receipt null 与字面 `null` 有歧义。修复为 intent 拒绝 canonical delimiter，receipt nullable 文本使用 `null` 或 UTF-8 Base64 明确编码，并固定 `Locale.ROOT`。
6. adapter exception/thread interruption 缺 mutation counter 证据。补齐 deterministic recovery tests，证明 durable `SEND_STARTED` 后 duplicate/recovery 只 query，mutation 总数保持 1。
7. receipt record 默认 `toString()` 会输出 normalized identity/error。改为 `normalizedEnvelope=REDACTED`，并对 allowlist 字段做 bounded single-line 校验。
8. same intentId 的既有记录仅比较 payload hash。修复为同时精确比较 schema、session、action、symbol、side/type、quantity/price、clientOrderId 与 localOrderId；真实 PostgreSQL 证明“相同 hash 但字段不一致”返回 `IDEMPOTENCY_CONFLICT`。

最终 P0=0、P1=0。

## Review conclusions

- Intent 状态机：与 V39 一致；禁止 `SEND_STARTED/UNKNOWN/FAILED/RECONCILED` 回到 mutation 路径。
- `SEND_STARTED` hard boundary：先在独立短事务提交，再调用 fake mutation；exchange call 不持有 intent/session/order transaction。
- NO BLIND RETRY：timeout、transport error、service exception、thread interruption、receipt rollback、duplicate invocation 与 recovery 均不重发；mutation count≤1 有 counter 证据。
- Claim/lease：authority 为 PostgreSQL row lock + state/version/claimToken CAS + DB time；仅未发送的 expired CLAIMED 可 reclaim。
- Receipt：intent row lock 内分配 attemptNo；receipt insert 与 state/version CAS 同一事务，失败整体回滚。
- PLACE/CANCEL identity：PLACE 复用唯一既有 order；CANCEL 不自带 quantity/price，不猜 remote identity，UNKNOWN 必须先 reconciliation。
- Legacy bridge：正向与 `legacy NULL / mismatch / owner mismatch / missing order` 反向均通过真实 PostgreSQL，`LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE=CLOSED`。
- Fake isolation：fake 只在 test fixture；production execution source 无 HTTP/socket/DNS/WebClient/RestClient/OkHttp/SDK/credential/env lookup/fake success fallback/production Bean。
- 事实所有权：未新增或写入第二套 orders/trades/positions/ledger，也未新增 `live_session_events` caller。
- Sensitive data：receipt 只保存 normalized allowlist envelope/digest；无 raw request/response、header、signature 或 credential material。

## Validation

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| `ExecutionIntentRuntimeTest` | PASS（通过） | 10 tests / 0 failures / 0 errors / 0 skipped；含 golden、canonical ambiguity、exception/interruption recovery 与 mutation counter |
| `LiveSessionFactModelPostgresIntegrationTest` | PASS（通过） | disposable PostgreSQL 17.7；fresh V1→V38→V39；1/0/0/0；含 kill switch、bridge 五类正反、forged digest、same-hash field mismatch、UNKNOWN/CANCEL 与 receipt 原子性 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-adapter-api -am test` | BUILD SUCCESS | 16/16 reactor；0 failures/errors；4 个既有条件性 skip |
| `mvn -f backend/pom.xml test` | BUILD SUCCESS | fresh disposable PostgreSQL 17.7 + 唯一 PAPER/ACTIVE fixture；23/23 reactor；`nq-app` 270 tests / 0 failures / 0 errors / 27 existing conditional skips |
| ArchUnit / no-real focused reactor | BUILD SUCCESS | module/package boundary、no-real credential、OKX/Binance hardening；19 tests / 0 failures / 0 errors / 1 条既有 env 条件 skip |
| Static boundary scan | PASS（通过） | V39 diff=0、V40=0、dependency diff=0、无 production outbound/credential/fake Bean、无第二事实写入 |

已知 warning：既有 SLF4J no-provider、Mockito dynamic-agent、unchecked/deprecation 与 Windows LF→CRLF 提示。首次指定不存在的 `postgres:17.7-alpine` 镜像时 Docker create 超时，未产生测试结论；改用本机已有 `postgres:17.7` 后完成全部 disposable PostgreSQL 验证并清理容器。CI=`NOT_RUN`。

## Residuals and boundary

- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`、`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 保留，继续阻断 production migration deployment、production worker 与 first real order。
- P3：0。
- 未验证且未授权：production lock window、production migration、production worker、真实 provider、credential decrypt、private endpoint、真实 PLACE/CANCEL、remote permission、micro-live、LIVE、transfer、withdraw。
- 本轮测试只在 disposable PostgreSQL 使用 test-only `kill_switch_states` 更新和 fake account/order facts；未写用户长期数据库或生产环境。

## Decision

`PASS / GATEY_3_FAKE_EXECUTION_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_BLIND_RETRY_VERIFIED / FAKE_PROVIDER_ISOLATED / POSTGRESQL_CONCURRENCY_VERIFIED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

Authority after：`GateY-3 / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。

唯一下一动作：`NQ-GATEY-3-EXECUTION-INTENT-RECEIPT-FAKE-EXCHANGE-COMMIT-AND-PUSH`。

建议 commit：`feat(gatey): implement deterministic fake execution runtime`。
