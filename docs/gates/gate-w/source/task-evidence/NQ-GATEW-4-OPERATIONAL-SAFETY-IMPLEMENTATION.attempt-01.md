# NQ-GATEW-4 Operational Safety Implementation Attempt 01

## Task classification

`CODE_CHANGE / INTERNAL_DIAGNOSTIC / OPERATIONAL_SAFETY / TESTS / DISPOSABLE_RESTORE_DRILL`。

## Scope

- Production：仅 `backend/nq-core/**/trading/application/safety/**` pure assessment contract。
- Tests：`nq-core` incident/human-review/soak；`nq-app` disposable PostgreSQL prepare integration。
- Script：`scripts/gatew/run-gatew4-disposable-restore-drill.ps1`。
- Docs：GateW current evidence、GATEW_PLAN execution override、TESTING/WORKLOG/index。
- 无 API、Controller、scheduler、runner、migration、adapter、frontend、research、deploy、CI/POM/package/lock diff。

## Implementation

- 新增 `GateW4OperationalSafetyAssessmentService`、request/result/status/finding/fact bundle。
- Service 为无状态纯函数，无 Spring component、field、repository、network、credential、order、ledger 或 mutable cache。
- 结果固定 `diagnosticOnly=true / readOnly=true / noSideEffect=true / orderSubmitted=false / tradingAuthorized=false / liveDisabled=true`。
- 状态封闭为 `PASS / BLOCKED / UNKNOWN / NOT_EVALUATED`；overall 优先级为 BLOCKED > UNKNOWN > NOT_EVALUATED > PASS。
- ENGAGED 始终 BLOCKED；kill-switch storage/missing 只产生 UNKNOWN；human evidence 缺失/过期/冲突均 BLOCKED。
- Human-review binder 从 V33 case/events 自动验证完整 event chain、version、tenant、state、retention 与 subject/reference，不表达交易授权。
- Restore drill 只操作随机 disposable local container，执行 V1→V35、dump/restore/verification/cleanup。

## Validation

- Focused assessment：16 tests / 0 failures / 0 errors / 0 skipped；包括 11 incident cases、human-review chain 与 10,000 次并发 soak。
- Required reactor：`mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra,nq-app -am test`；23/23 modules `SUCCESS`，`BUILD SUCCESS`。
- Full Maven：`mvn -f backend/pom.xml test`；23/23 modules `SUCCESS`，`BUILD SUCCESS`。
- Disposable restore：`PASS / GATEW4_DISPOSABLE_BACKUP_RESTORE_PROVEN`；V1→V35、35 migrations、恢复/constraints/data/cleanup 全部通过。
- Environment：`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。

## Boundary confirmation

- 未访问 OKX 或真实 credential；未执行真实 network/private endpoint。
- 无 order/cancel/transfer/withdraw、account/balance/position、ledger/audit/event 业务写入。
- V35 与所有历史 migration 无 diff；原 35 份 attempt evidence 不修改、不覆盖、不重命名、不删除。
- LIVE/Shadow/AI/DH/real provider/private trading 状态不变。

## Result

`IMPLEMENTED / LOCALLY_VALIDATED / PENDING_INDEPENDENT_REVIEW_AND_EXACT_HEAD_CI`（已实现 / 本地验证通过 / 等待独立复核与 exact-head CI）。

## Rollback

代码与脚本使用后续 revert commit 回滚；不删除或改写 V35。回滚 assessment 不改变 durable kill-switch state；LIVE 继续 disabled。
