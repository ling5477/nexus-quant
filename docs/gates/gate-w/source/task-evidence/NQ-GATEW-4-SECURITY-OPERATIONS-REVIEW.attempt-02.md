# NQ-GATEW-4 Security / Operations Review Attempt 02

## Review target

- Task：`NQ-GATEW-4-IMPLEMENTATION`。
- Execution mode：`ROUND_5_RESUME / ATTEMPT_02`。
- 类型：`SECURITY_OPERATIONS_REVIEW / PERSISTENCE_RETENTION_REVIEW / BACKUP_RESTORE_DRILL / INCIDENT_DRILL / READONLY_SOAK`。
- 决策边界：先复核 Blocker-1 与六项 hard gate；全部通过后才允许 internal-only assessment implementation。

## Preflight evidence

- Repository：`E:\Project\nexus-quant`；branch `dev`；worktree clean；staged empty。
- Starting `HEAD == origin/dev == 89cf600d39923d1b59427f92227febc043797417`。
- Starting exact-head CI：`NQ CI Baseline / 29336417826 / completed / success / 10 jobs / bad=0`。
- Authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT`；GateW-3 `ACCEPTED|CI_GREEN`，GateW-4 `NOT_STARTED`，next action `NQ-GATEW-4-IMPLEMENTATION`。
- 起始 immutable attempt evidence：35 份；聚合 SHA-256 `9d10ede0baaa5f940a721d01fa6acebbe73881f678324813383b343e8c25e09f`。

## Blocker-1 regression check

结论：`PASS / KILL_SWITCH_REMEDIATION_BASELINE_INTACT`（通过 / kill-switch 修复基线完整）。

- `kill_switch_states/events` 与 V35 存在，V35 无 diff；`GLOBAL_TRADING` seed 为 `ENGAGED`。
- missing/read error/unknown/非法或未来时间均 fail-closed；`KillSwitchRiskRule` 使用 durable snapshot。
- private probe 在 account/credential/request/transport 前检查 kill switch。
- production 仅装配 durable repository，无 in-memory fallback；无 release/disengage/reset/clear surface。
- restart、repository failure、credential zero-call、transport zero-call 回归继续通过。

## Hard-gate decisions

### Operations

`PASS / OPERATIONS_BOUNDARY_PROVEN`（通过 / operations 边界已证明）。

- Bean 创建不执行 probe、credential 或 network；无 `@PostConstruct`、scheduler、runner。
- restart 后 ENGAGED durable state 保留；repository failure 转为 UNKNOWN/BLOCKED。
- stop check 在 private probe timeout/redirect/retry path 之前；assessment 无 IO、线程池、连接或临时文件依赖。
- 无 order/cancel/account/balance/position/ledger/audit/event 业务写入。

### Persistence / retention

`PASS / EXISTING_SCHEMA_REUSE_ACCEPTED / NO_MIGRATION_REQUIRED`（通过 / 接受复用既有 schema / 无需 migration）。

- V33 `validation_review_cases` 提供 case id、version、evidence type/source/JSON anchor、lifecycle、`retention_until` 与 optimistic version。
- `validation_review_events` 为 append-only accepted transition，tenant/case FK `ON DELETE RESTRICT`；stable createdAt/id 顺序可重建 event chain。
- V35 kill-switch current/event 事实保持 optimistic/current + append-only event；无 cascade delete。
- 本轮无新表、无新 migration、无 durable approval state、无敏感字段或自动清理 job。
- 增长边界继续使用 bounded event query；归档/自动删除未实现，属于 Freeze readiness 的 retention residual。

### Human-review evidence binding

`PASS / HUMAN_REVIEW_EVIDENCE_BINDING_PROVEN`（通过 / 人工复核证据绑定已证明）。

绑定固定为：`reviewCaseId / caseVersion / evidenceType / evidenceSubject / evidenceReference / lifecycleState / ordered event-chain completeness / retentionUntil / observedAt`。代码从 tenant-scoped case 与稳定升序 events 推导完整性，不信任调用方布尔声明。

只允许：`HUMAN_REVIEW_EVIDENCE_PRESENT / MISSING / STALE / CONFLICT`。任何缺链、过期、subject/type 不匹配或 lifecycle 非 `RESOLVED|CLOSED` 均 BLOCKED；该证据不表达 `TRADE_AUTHORIZED / LIVE_APPROVED / ORDER_APPROVED / CAN_TRADE`。

### Backup / restore

`PASS / GATEW4_DISPOSABLE_BACKUP_RESTORE_PROVEN`（通过 / disposable 备份恢复已证明）。

- Windows PowerShell protected drill 创建两个随机、loopback、无 volume PostgreSQL 16 Alpine 容器。
- Fresh Flyway V1→V35：35 migrations；写入无敏感 review fixture；custom dump；销毁源容器；restore 到第二容器。
- 恢复后：Flyway version 35 / migrations 35；`GLOBAL_TRADING=ENGAGED`；kill-switch event=1；review fixture=1；关键 constraints=3。
- dump、两个容器和 `artifacts/gatew4-restore-tmp-*` 残留均为 0。
- 脚本要求 `-ConfirmDisposable`，拒绝 production profile、非 pinned image、非 loopback/非 disposable DB 和非随机前缀容器；密码运行时随机且不回显。

两次最初执行在进入有效 drill 前失败并完成 RCA：Windows PowerShell 5 UTF-8 注释解析；未引号 Maven `-D` 参数与不存在容器 cleanup。最小修复后最终 drill exit 0；失败不得记为通过，最终成功证据来自第三次完整执行。

### Incident drill

`PASS / INCIDENT_DRILL_COMPLETE`（通过 / incident drill 完整）。

JUnit parameterized matrix 覆盖 11 项：`KILL_SWITCH_ENGAGED`、`KILL_SWITCH_UNKNOWN`、`KILL_SWITCH_STORAGE_FAILURE`、`DATABASE_UNAVAILABLE`、`RECONCILIATION_STALE`、`RECONCILIATION_PARTIAL`、`PRIVATE_PROBE_FAILURE`、`CREDENTIAL_UNAVAILABLE`、`CREDENTIAL_CONFLICT`、`RESTORE_FAILURE`、`MARKETDATA_STALE`。

每项结果只为 BLOCKED 或 UNKNOWN；reason code 明确。Assessment service 没有 credential/transport/repository/order/ledger port 或 fields，因此 credential/network/business-write 调用为 0；`orderSubmitted=false / tradingAuthorized=false / liveDisabled=true`。

### Local no-egress soak

`PASS / LOCAL_SOAK_PROVEN`（通过 / 本地 soak 已证明）。

- fixed input/clock，8 个 bounded worker × 1,250 evaluations = 10,000 次。
- 所有结果与 golden result 完全相等；ENGAGED 始终 BLOCKED；UNKNOWN 不升级为 PASS。
- executor 在 10 秒内终止；service declared fields=0，无 cache/connection/temp file/scheduler/runner/network/credential/order command。

真实 OKX read-only soak：`NOT_RUN / CREDENTIAL_REQUIRED`。未要求或读取 credential，未用 mock 冒充真实联通；按 Round-5 指令不阻断 GateW-4 acceptance，必须由 `NQ-GATEW-FREEZE-READINESS-REVIEW` 判断是否阻断 freeze。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- Root `README.md` 的 GateW 短摘要仍停留在早期 planning/GateW-1 未初始化口径；该文件不覆盖 `STATUS.md`，且不在本任务 allowlist。记录为既有 out-of-scope drift，留给 Freeze readiness/current-entry sync。

### P3

- 既有 SLF4J NOP 与 Mockito dynamic-agent/JDK future warning；不由本 diff 引入。

## Decision

`PASS / ALL_HARD_GATES_PROVEN / OPERATIONAL_SAFETY_IMPLEMENTATION_AUTHORIZED`（通过 / 全部 hard gate 已证明 / 授权 internal-only operational safety implementation）。
