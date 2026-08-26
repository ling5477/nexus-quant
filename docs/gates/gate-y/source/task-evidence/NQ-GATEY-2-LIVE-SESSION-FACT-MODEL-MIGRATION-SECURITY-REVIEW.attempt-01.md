# NQ-GATEY-2 LIVE Session Fact Model Migration / Security Review — attempt-01

## 审查对象与结论

- 任务：`NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-MIGRATION-SECURITY-REVIEW`。
- 基线：`dev`，`HEAD == origin/dev == 2217d28ff184d3ca38a1b76bea194fa462586599`；baseline CI run `31583487794 / completed / success / 10 jobs`；staged empty。
- 对象：未提交的 V39 六表 migration、`livecontrol` domain/application/JDBC、domain/migration/PostgreSQL tests。
- 结论：P0=0、P1=0；V39 与 Java control-plane fact model 接受为 local/disposable baseline，进入 `READY TO COMMIT`（可进入提交前复核）。
- 不构成：production migration、micro-LIVE、真实 provider、真实 PLACE/CANCEL、credential material 访问或任何交易副作用授权。

## 审查范围与证据

- Codex Security diff scan：`scanId=7fd476ec-9854-42d7-9170-b2c07120866b`；23 个 source-like 文件形成 23/23 全文 receipt；最终 reportable findings=0。
- 六表：`risk_limit_sets`、`live_sessions`、`live_session_events`、`operator_approvals`、`execution_intents`、`execution_receipts` 均为 `NECESSARY`；未形成第二套 order、trade、position、ledger、audit、risk decision、credential、strategy release 或 RBAC 主事实。
- FK/identity：FK 类型与真实 schema 对齐，使用 `RESTRICT / NO ACTION`；credential 只保存 reference。creator/owner 必须等于 authenticated actor，审批瞬间重新锁定 enabled user、`LIVE_APPROVER` role 与 account/release/risk/credential references。
- Canonical digest：固定 schema version、UTF-8、字段顺序、symbols canonical order、8 位 decimal、UTC 六位微秒、null/boolean 表示和 `Locale.ROOT`；session persistence 前重新计算 approval scope hash。
- DB enforcement：append-only/immutable trigger、initial state/version/sequence、session scope immutable、intent payload immutable、claim/reclaim lease、`SEND_STARTED`、receipt/reconciliation outcome、array dimensionality/null/canonical ordering均已 direct SQL 验证。
- 事务与并发：application service 负责短事务；无 HTTP、credential decrypt、exchange call、sleep/retry/scheduler。approval/session/event/sequence 通过 row lock/CAS/rollback；approve-vs-approve、approve-vs-reject 与 concurrent event sequence 已验证。

## 审查中最小修复

- 收紧 V39 session/intent 初始状态、scope mutation、claim/reclaim/lease、receipt result 与 canonical symbol array 约束。
- 增加 authenticated creator/owner binding、created-event exact semantics、canonical scope hash、approval-time DB clock、live RBAC/reference revalidation。
- 修正 canonical timestamp/locale 编码与 Spring `@Service` 构造器装配。
- 扩展 domain/contract/PostgreSQL tests，覆盖 digest 字段、时间/locale、direct SQL bypass、RBAC revoke/disable、并发与事务回滚。
- 只在 GateY-2 原始六表与 control-plane 范围内做最小修复；未引入 GateY-3 worker、API、provider 或交易路径。

## 验证

| Command / 环境 | 结果 |
| --- | --- |
| focused `LiveSessionFactModelPostgresIntegrationTest` / PostgreSQL 17.7 / disposable port 55439 | `PASS`（通过）；1 test / failures=0 / errors=0 / skipped=0；fresh V1→V38、V39、Flyway validate、direct SQL、concurrency、rollback、cleanup |
| `mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra -am test` | `PASS`（通过）；117 tests / failures=0 / errors=0 / skipped=4 |
| `mvn -f backend/pom.xml test` / 默认 local 5432 | `FAIL`（失败，环境基线）；既有本地 DB V38 checksum mismatch，未执行 Flyway repair |
| `mvn -f backend/pom.xml test` / isolated disposable PostgreSQL database + one minimal legacy account fixture | `PASS`（通过）；270 tests / failures=0 / errors=0 / skipped=27 |
| Codex Security finalization | `PASS`（通过）；canonical manifest/findings/coverage/report/SARIF 已封存；P0=0、P1=0 |

已知非阻断 warning：SLF4J provider、Mockito dynamic agent、少量 compiler deprecation/unchecked；均为既有 warning。CI 对本未提交工作树为 `NOT_RUN`（未运行）。

## Findings

- P0：无。
- P1：无。
- P2：
  - `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：本地 migration 成功不代表生产锁窗口已验证。
  - `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：继承既有文件系统稳定句柄边界，未由 GateY-2 扩大。
  - `LEGACY_ORDER_ACCOUNT_IDENTITY_BRIDGE`：GateY-3 第一笔真实动作前 hard gate；当前无 intent writer、worker 或 dispatch，因此不可达。
- P3：无。

数据库未独立绑定每个 event 的 command/from/to 与 session state 属于 defense-in-depth residual；当前生产调用只能经事务化 Service 且 created-event exact semantics 已强制，不构成本轮 reportable vulnerability。GateY-3 增加新 event caller 时必须重新证明该绑定。

## 安全与交易边界

- `LIVE=DISABLED`，kill switch=`ENGAGED`。
- real provider/private trading=`NOT_IMPLEMENTED`。
- credential material access、exchange call、permission probe、order/cancel/transfer/withdraw、intent dispatch=`0`。
- production database/migration、stage、commit、push、PR、tag 均未执行。

## Authority transition

```text
accepted_batch=GateY-1
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-2
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-COMMIT-AND-PUSH
```

## 最终状态

```text
PASS /
GATEY_2_MIGRATION_SECURITY_REVIEW_ACCEPTED /
P0_0 /
P1_0 /
V39_ACCEPTED_FOR_LOCAL_BASELINE /
NO_PRODUCTION_MIGRATION_AUTHORIZATION /
MICRO_LIVE_NOT_AUTHORIZED /
LIVE_DISABLED /
READY_TO_COMMIT
```

建议 commit message：`feat(gatey): add live session control-plane fact model`
