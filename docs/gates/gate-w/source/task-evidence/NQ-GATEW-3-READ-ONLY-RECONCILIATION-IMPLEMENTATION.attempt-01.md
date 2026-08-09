# NQ-GATEW-3 Read-only Reconciliation Implementation — Attempt 01

## 结论

`IMPLEMENTED / PENDING_REVIEW`（已实现 / 待独立审查）。实现严格基于同任务 security/risk review 的 PASS 合同；未执行真实 OKX HTTP、未读取真实 credential。

## Architecture

1. `nq-core`：新增 immutable local/remote snapshot、bounded request、窄 read ports、taxonomy/finding/result，以及无 Spring 注解的 `ReadOnlyOrderReconciliationService` pure comparator。
2. `nq-adapter-api`：增加三项 private read capability；不增加 mutating capability。
3. `nq-adapter-okx`：扩展既有 GateW-1 guard / GateW-2 signer+transport，以三个 enum 固定 `GET` path 和 typed query schema读取 pending/history/fills；raw response 仍在 transport 内清零，不进入 normalized model。
4. `nq-infra`：新增 bounded SELECT local adapter；scoped credential callback 结果泛型化但 session 仍线程绑定且 callback 后立即失效；新增未装配为 bean 的 remote adapter，先 exact `READ_ONLY` permission 再发 typed reads。
5. `nq-app`：无变更。没有 controller、configuration bean、scheduler、runner、startup trigger 或 polling；default/CI 不能到达 credential/network。

## Bounds and matching

- `OKX SPOT`、`SIM|LIVE` environment 显式绑定、最多 3 symbols。
- `1 page` hard limit、每页最多 100，三类 operation 合计最大 `3 symbols * 3 operations * 100 = 900` normalized input rows，窗口最大 24h，无 retry。
- local SQL 同时限定 account/exchange/environment/symbol/window/LIMIT，成交数量只用 `SUM(trades.qty)` 只读聚合。
- identity 只使用 exchange order ID，然后 client order ID；duplicate/missing identity fail-closed。
- status mapping、BigDecimal canonical comparison、fixed Clock、5-minute stale/future/partial/scope checks 已实现。

## Test coverage

测试覆盖：全部 matched、local/remote only、status/price/original/filled quantity mismatch、duplicate local/remote ID、unknown status、missing identity、stale/future、partial/page/record/symbol/window/exchange/product/environment bounds、permission rejection、typed-path guard、mutating/unknown path rejection、sanitized parse、bounded local SELECT、zero real credential/network、deterministic result、BigDecimal canonical equality、`executionReadiness=BLOCKED`。

所有 transport 测试使用 fake `OkxPrivateHttpExchange`、synthetic sanitized payload 与 fixed `Clock`。没有 `.env`、真实 key、真实 private endpoint 或 private WebSocket。

提交前 hard gate：focused reconciliation/security regression `35/35` 通过；required targeted Maven 与 full `mvn -f backend/pom.xml test` 均为 23/23 reactor modules `SUCCESS`、`BUILD SUCCESS`、exit code 0。两次 Maven 均保持 `CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`，没有真实 OKX outbound。

## Boundary confirmation

`NO REAL OKX CALL / NO REAL CREDENTIAL / NO TRADE PERMISSION EXPANSION / NO WITHDRAW PERMISSION / NO ORDER WRITE / NO REPAIR / NO SCHEDULER / NO PERSISTENCE / NO MIGRATION / NO CONTROLLER / NO REST API / NO FRONTEND / NO LEDGER WRITE / NO AUDIT WRITE / NO EVENT WRITE / NO LIVE ENABLE / NO SHADOW ENABLE / NO DH / NO AI`。
