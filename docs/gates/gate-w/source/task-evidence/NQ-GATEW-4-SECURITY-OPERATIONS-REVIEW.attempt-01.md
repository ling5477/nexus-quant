# NQ-GATEW-4 Security / Operations Review Attempt 01

## Review target

- Task：`NQ-GATEW-4-IMPLEMENTATION`
- Execution mode：`ROUND_5_COMBINED`
- Classification：`SECURITY_OPERATIONS_REVIEW` + conditional backend implementation
- Repository：`E:\Project\nexus-quant`
- Branch：`dev`
- Review date：2026-07-14
- Decision boundary：六项 hard gate 全部通过后才允许 implementation；任一 gate 失败即保持 GateW-4 `NOT_STARTED`。

## Preflight evidence

- Starting `HEAD`：`d006417442080b2851dde1f2e05faf05eb5fe028`
- `origin/dev`：`d006417442080b2851dde1f2e05faf05eb5fe028`
- Worktree：clean；staged：empty。
- Starting exact-head CI：`NQ CI Baseline / 29332758765 / completed / success`。
- CI jobs：10；bad jobs：0。
- Current authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- Authority：`accepted_batch=GateW-3 / ACCEPTED|CI_GREEN`；`work_batch=GateW-4 / NOT_STARTED`；`next_action=NQ-GATEW-4-IMPLEMENTATION`。
- 既有 immutable attempt evidence：31 份；已在写入前计算 SHA-256，结束时按原路径集合复核。

## Evidence checked

- `AGENTS.md`、`CLAUDE.md`、`README.md`。
- `docs/current/GATEW_PLAN.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`FACT_SOURCE_INDEX.md`、`evidence/gate-w/README.md`。
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchService.java`。
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchRiskRule.java`。
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/PreTradeRiskService.java`。
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/RiskRuleRegistry.java`。
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/trading/TradingRuntimeConfiguration.java`。
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPrivateReadonlyConfiguration.java`。
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeService.java`。
- GateW-3 risk preflight contract、validation review schema/repository、`scripts/backup-db.sh`，以及指定 backend/scripts/deploy 范围内的 startup、runner、scheduler、backup、restore、incident、soak 关键词与路径检索。

未读取 `.env`、credential、key、pem、secret、log、dump、backup 内容或生产数据库；未调用 OKX；未使用 API key。

## Hard-gate decisions

### 1. Security review

Decision：`FAIL / KILL_SWITCH_DURABILITY_NOT_PROVEN`。

1. 真实状态源是 `KillSwitchService.enabled` 的进程内 `AtomicBoolean`，没有 repository、配置快照、持久化 port、版本、`observedAt` 或 freshness contract。
2. 默认值是 `new AtomicBoolean(false)`；按当前语义 `false` 表示未熔断，因此默认启动不阻断。
3. 状态没有缺失、读取异常或存储失败模型，无法证明这些场景 fail-closed。
4. `KillSwitchRiskRule` 只在 `PreTradeRiskService` 的 registry 中读取状态；GateW private read probe 的 composition 与 service 均不依赖 `KillSwitchService`，因此现有 kill switch 不能证明会阻止 private probe。
5. 未发现 kill switch HTTP mapping；但 `KillSwitchService` 暴露 public `disable()`，其语义明确为“关闭熔断，恢复正常提交”。
6. `TradingRuntimeConfiguration` 在无 profile/condition 的普通 configuration 中直接 `new KillSwitchService()`；不存在生产安全默认值。
7. 单次布尔读写由 `AtomicBoolean` 保证线程安全，但这不解决传播、持久化、freshness 或跨组件一致性。
8. 重启会重新构造默认 `false`，因此不能保持安全阻断状态。
9. 本次检索未发现 kill switch 状态输出敏感内容；该项不是阻断点。
10. `PreTradeRiskService` 可能返回 ALLOW，但既有 `RiskDecisionResult` 测试明确该结果不授权 LIVE；该边界不能补救 kill switch 默认未阻断事实。

直接证据：

- `KillSwitchService.java:13`：`AtomicBoolean(false)`。
- `KillSwitchService.java:18-26`：public `enable()` / `disable()`。
- `TradingRuntimeConfiguration.java:42-50`：无条件创建 service 并仅接入 pre-trade registry。
- `GateWOkxPrivateReadonlyConfiguration.java:40-69`：private read transport/executor/probe 的 Bean 依赖中没有 kill switch。
- `OkxPrivateReadonlyProbeService.java:27-59`：probe 直接进入 account/credential executor，不读取 kill switch。
- `GATEW_PLAN.md:160-163`：GateW 要求默认 engaged/deny、未知/存储失败 fail-closed，并要求 durable human-review evidence 的独立 schema/security review。

### 2. Operations review

Decision：`FAIL / OPERATIONS_BOUNDARY_NOT_PROVEN`。

- GateW private configuration 的 Bean 创建本身不读取 credential、不自动 probe、不访问网络，也未注册 scheduler/runner；这部分为正向事实。
- 但 kill switch 没有接入 GateW private probe，无法证明 shutdown、restart、timeout、partial failure 或人工操作边界下的 stop propagation。
- 现有进程内状态在 graceful restart 后恢复为未熔断，无法证明安全状态不丢失。
- 因 Security gate 已失败，未继续构造或执行任何 private request、scheduler 或后台动作。

### 3. Persistence / retention review

Decision：`BLOCKED / NOT_EVALUATED`。

- kill switch 当前没有 durable state，已触发 fail-fast。
- `validation_review_cases/events` 具备 durable lifecycle、version、`retention_until` 与 append-only event 事实，可作为后续窄化只读证据候选。
- 但 `GATEW_PLAN.md` 明确要求复用前进行独立 schema/security review，并要求证据绑定具体 snapshot/reconciliation/preview/soak version；本轮尚无该 GateW-4 绑定事实，不能把现有表的“存在”判为 human review evidence `PASS`。
- 本轮未新增 migration、table 或 durable approval state。

### 4. Backup / restore review

Decision：`NOT_RUN / RESTORE_DRILL_NOT_PROVEN`。

- 仓库存在 `scripts/backup-db.sh`，但在允许检索范围内未找到对应的安全 disposable restore-drill 脚本。
- 因 Security gate 已 fail-fast，未启动 disposable PostgreSQL、未生成或读取 dump、未执行 restore、未验证 schema version/Maven PostgreSQL smoke。
- “存在 backup 脚本”不构成 restore `PASS`。

### 5. Incident-drill review

Decision：`NOT_RUN / INCIDENT_DRILL_INCOMPLETE`。

- 现有 monitoring incident replay read models 不等于 GateW-4 指定的十场景 operational incident drill。
- 未执行 `KILL_SWITCH_ACTIVE`、`KILL_SWITCH_STATE_UNKNOWN`、`DATABASE_UNAVAILABLE`、`RECONCILIATION_STALE`、`RECONCILIATION_PARTIAL`、`PRIVATE_PROBE_FAILURE`、`CREDENTIAL_UNAVAILABLE`、`CREDENTIAL_CONFLICT`、`RESTORE_FAILURE`、`MARKETDATA_STALE` 演练。
- 未以 mock/fixture 成功结果冒充真实演练。

### 6. Soak design review

Decision：`NOT_RUN / SOAK_DESIGN_NOT_PROVEN`。

- 允许范围内未找到 GateW-4 local no-egress soak 脚本或 runner。
- 未执行有界 deterministic soak，因而未证明内存、线程、连接、临时文件无持续泄漏，也未证明 repeated UNKNOWN 始终不会升级为 PASS。
- `REAL_READONLY_SOAK=NOT_RUN / CREDENTIAL_REQUIRED`；`API_KEY=NOT_REQUIRED_FOR_REVIEW`。

## Findings

### P0

- 无。

### P1

1. `KillSwitchService` 默认未熔断、仅进程内保存且重启丢失；不满足 default engaged、durable/restart-safe 与 fail-closed 要求。
2. GateW private read probe 未接入 kill switch，无法证明 stop propagation 或不存在 private diagnostic bypass。

### P2

1. GateW-4 human-review evidence 尚未证明与具体 evidence version 绑定；现有 validation review lifecycle 只能作为后续复用候选。
2. 只有 backup helper，没有已验证的 disposable restore path；incident 与 local soak 也没有本轮可接受证据。

### P3

- 无。

## Validation

- 未运行 Maven：hard gate 在 implementation 前失败，本轮没有 production/test code diff。
- 未运行 restore/incident/soak：不得把 Security fail-fast 后未执行的验证写成通过。
- 写入本 evidence 后只运行 docs/governance、scope diff 与 immutable evidence hash 复核。

## Boundary confirmation

- 未修改 production code、test code、script、migration、API、scheduler、frontend、deploy、CI 或 historical evidence。
- 未新增 Controller、REST endpoint、runner、background worker、真实 provider、order/cancel/transfer/withdraw 或 kill switch release API。
- `LIVE=DISABLED`；`SHADOW_TRADING=NOT_ENABLED`；`REAL_ORDER_SUBMISSION=DISABLED`；`TRANSFER_WITHDRAW=DISABLED`；`AI=NOT_STARTED`；`DH_RUNTIME=NOT_INTEGRATED`。
- Authority 保持 GateW-4 `NOT_STARTED`，不初始化 GateW-FREEZE。

## Final decision

```text
BLOCKED /
KILL_SWITCH_DURABILITY_NOT_PROVEN
```

同时存在：`BLOCKED / OPERATIONS_BOUNDARY_NOT_PROVEN`。因此六项 hard gate 未全部通过，不授权 GateW-4 implementation、Commit A/B、push、CI acceptance 或 authority sync。

## Follow-up

- 先由治理方提供正式 canonical task ID，授权独立的 kill-switch safety remediation review/implementation；本 review 不现场发明任务 ID。
- 该任务至少需要冻结：default engaged/deny、read failure/stale/unknown fail-closed、restart-safe 状态策略、只读 snapshot contract、private probe/diagnostic stop propagation、public release/mutation 边界，以及是否需要 schema/migration review。
- remediation 获得 review、实现、测试与 exact-head CI 证据后，再重试 current authority 中的 `NQ-GATEW-4-IMPLEMENTATION`。

## Commit recommendation

无。按 blocked 分支规则，本 evidence 保持未提交；不得 commit/push。
