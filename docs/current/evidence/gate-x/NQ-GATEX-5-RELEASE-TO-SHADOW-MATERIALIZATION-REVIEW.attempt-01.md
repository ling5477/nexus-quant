# NQ-GATEX-5 Release-to-Shadow Materialization Review — Attempt 01

> 任务：`NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`
>
> 归属：NQ-only
>
> 结论：`FAIL / REVIEW_REJECTED / ADMISSION_MATERIALIZATION_FACT_TEAR / REMEDIATION_REQUIRED`（失败 / 审查拒绝 / admission 与 materialization 事实撕裂 / 需要整改）
>
> 日期：2026-08-11
>
> Starting HEAD：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`
>
> `origin/dev`：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`

## 1. Review target 与基线

- Branch：`dev`。
- `HEAD == origin/dev`：是。
- 进入审查时：23 个 GateX-5 implementation 文件已 staged；unstaged=`0`；untracked=`0`。
- Authority before：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`；`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`next_action=NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`。
- `LIVE=DISABLED`；Shadow trading=`NOT_ENABLED`。
- `git diff --cached --check`：exit `0`。
- `scripts/docs/check-current-authority.ps1`：`errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。
- 本轮未启动 Shadow Run，未调用交易、凭证、private exchange、外部网络或 LIVE 路径。

## 2. Production materialization chain

已确认唯一新增 HTTP 写路径：

```text
POST /api/strategy-releases/{publishRecordId}/shadow-runs
→ StrategyReleaseShadowRunMaterializationController
→ StrategyReleaseShadowRunMaterializationService
→ StrategyReleaseAdmissionPreviewService.evaluate(...)
→ StrategyReleaseProductionService.verify(...)
→ ReleaseToShadowAdmissionService.admit(...)
→ ShadowRunCreationPlan
→ ShadowRunMaterializationWriter.materialize(...)
→ JdbcShadowRunFactRepository.create(...)
→ shadow_runs
→ JdbcShadowRunFactRepository.appendEvent(...)
→ shadow_run_events
```

证据：

- Controller 只接收 path `publishRecordId` 与 `Idempotency-Key`；actor/roles/trace 来自服务端 context。
- Application service 每次 POST 都调用 `admissionEvaluationSource.evaluate(...)`，不读取或复用历史 GET preview。
- `ShadowRunRunnerService.newRun` 仅由既有 `ShadowRunRunnerService.run` 内部使用；新增 Controller/service/writer 不依赖或调用 runner。
- production `ShadowRunFactRepository.create` 调用点只有既有 runner 与本轮 writer；新增 API 只到 writer，不到 runner。

## 3. Admission re-evaluation

结论：`PASS WITH P1 RACE`（重评估路径通过，但存在 P1 时间窗口）。

- POST 信任输入限定为 `publishRecordId`、`Idempotency-Key`、authenticated actor。
- `artifactDigest`、`strategyVersionId`、`datasetId`、`evaluationId`、validation decision、window、authorization boundary、side-effect policy 与 creation plan 均由服务端重新解析。
- `StrategyReleaseShadowRunMaterializationServiceTest.shouldRejectBlockedAdmissionWithoutWrite` 证明本次 evaluation 为 `BLOCKED` 时 writer invocation=`0`。
- 历史 GET preview 曾为 `ELIGIBLE` 不会被 POST 当作授权；POST 无 preview token/plan/digest request body。

阻断问题见第 8 节：重评估完成后，mutable facts 到 writer transaction 之间仍无原子保护。

## 4. CreationPlan provenance mapping

静态映射确认：

| CreationPlan fact | persistence / audit |
| --- | --- |
| `publishRecordId` / `releaseAnchorId` | `shadow_runs.publish_id`；二者在 plan 构造时要求相等 |
| `artifactDigest` | `shadow_runs.artifact_digest` |
| `strategyVersionId` | `shadow_runs.strategy_version_id` |
| `datasetId` | `shadow_runs.dataset_id` |
| `evaluationId` | `shadow_runs.evaluation_id` |
| `windowStart/windowEnd` | `shadow_runs.window_start/window_end` |
| `authorizationBoundary` | `shadow_runs.authorization_boundary` |
| 六项 side-effect policy | JSONB policy + 六个 boolean hard gates |
| `inputReference` / `provenanceReference` | `CREATED` event metadata |
| `manifestSchemaVersion` | `CREATED` event metadata |
| `traceId` | run/event `trace_id` |
| materialization identity | run `request_id/idempotency_key` + event request/metadata，均为 SHA-256，不是 raw header |

新 run 固定为 `RELEASE_BOUND / CREATED / startedAt=NULL / paperRunId=NULL`。`ShadowRunMaterializationWriter` 不创建 legacy binding，不推进状态。

## 5. Idempotency identity

结论：构造与当前 repository 冲突检测通过。

- GateX-3 base identity 与规范化 command identity 使用 length-prefixed UTF-8 fields 进入 SHA-256，字段边界无拼接歧义。
- raw `Idempotency-Key` 不持久化、不写日志、不返回 API；持久化/审计只使用 64 位 lowercase SHA-256 identity。
- 相同 base + 相同 command identity 得到相同 key；不同 command identity 得到不同 key。
- schema 唯一约束为 `shadow_runs(idempotency_key)`；不存在 `UNIQUE(publish_id, artifact_digest)`。
- `JdbcShadowRunFactRepository.requireSameReleaseProvenance` 对 publish/digest/strategy/dataset/evaluation/window/policy/authorization mismatch 抛 `ShadowRunIdempotencyConflictException`，不会覆盖既有行。

## 6. Serial / concurrency / conflict results

### Serial replay

`PASS`（通过）。同 plan 重放返回同一 `shadowRunId`；第一请求 `idempotentReplay=false`，第二请求为 `true`；CREATED event 只有 1 条。

### Concurrent same-command

`PASS`（通过）。真实 PostgreSQL 17、两个并发事务、同一 materialization identity：

- `shadow_runs` 物理行=`1`；
- `CREATED` event=`1`；
- 两个结果返回相同 `shadowRunId`；
- 恰好一个结果为 replay。

### Concurrent different-command

`NOT FULLY VERIFIED`（未完整验证）。现有 PostgreSQL 测试证明不同 command identity 可各自创建合法 run，但未用两个并发事务同时执行不同 identity。该强制场景缺少真实 PostgreSQL concurrency test。

### Provenance conflict

`PASS WITH COVERAGE GAP`（行为通过但覆盖不完整）。真实 PostgreSQL 测试对同 identity + 不同 window 得到 `ShadowRunIdempotencyConflictException`；实现同时比较 publish/digest/strategy/dataset/evaluation/policy/authorization。测试未逐项断言 publish/digest/provenance mismatch，且未显式复查冲突后所有既有列保持不变。

## 7. Transaction / audit atomicity

- `ShadowRunMaterializationWriter.materialize` 为 public `@Transactional` Spring service method。
- run `INSERT ... ON CONFLICT DO NOTHING`、existing-row provenance compare 与 `CREATED` append 位于同一 writer transaction。
- 真实 PostgreSQL 强制 `appendEvent` 抛错后，run identity count=`0`，证明 run insert 回滚。
- replay 通过 persisted/requested UUID 判定，不追加第二个 `CREATED` event。
- 测试未显式断言 forced failure 后 event count=`0`，但 event append 在抛错前未落行，且 FK 绑定同一 run；仍记录为覆盖缺口，不将缺失断言写成已验证。

Audit 内容包含 actor、timestamp、publishRecordId、shadowRunId 关联、release binding mode、input/provenance reference、traceId、hashed materialization identity 与 result reason；不含 raw `Idempotency-Key`、trusted root、filesystem path、manifest、artifact content、credential/token。核心 strategy/dataset/evaluation/window/digest 保存在同一 run 主事实中，可通过 `shadow_run_id` 追溯。

## 8. P1 — Admission-to-write fact tear

### Finding

`P1 / ADMISSION_MATERIALIZATION_FACT_TEAR`

### Evidence

1. `StrategyReleaseAdmissionPreviewService.evaluate` 在 `@Transactional(readOnly = true)` 中依次执行 release/provenance DB read、artifact filesystem verification、admission DB facts read 和纯决策，然后返回 plan。
2. `StrategyReleaseShadowRunMaterializationService` 在该 read-only transaction 已结束后调用 writer。
3. `ShadowRunMaterializationWriter.materialize` 开启另一写事务，但只消费 frozen `ShadowRunCreationPlan`；它不重新读取或 compare-and-lock admission facts。
4. release locator 的首次绑定后不可变由 V37 保护，但以下 admission facts存在合法 mutation path：
   - `backtest_publish_records` 的 publish/evaluation/version facts 可通过既有 upsert 更新；
   - `backtest_eval_reports.evaluation_status` 与 report facts可通过既有 upsert 更新；
   - latest Paper 状态、latest Shadow 状态与 consistency 状态是运行期可变事实；
   - filesystem artifact/manifest 在 verification 返回后到 DB insert 前没有跨介质 immutable lease。
5. CreationPlan 冻结 publish/digest/strategy/dataset/evaluation/window/policy/trace，但没有冻结 validation/admission fact version、paper evidence identity/status、evidence timestamp 或 filesystem object identity；writer 无法证明当前事实仍等于用于 admission 的事实。

### Trigger

请求在 `evaluate()` 得到 `ELIGIBLE` 后、writer transaction insert 前，另一事务将 evaluation/publish/Paper/Shadow/consistency 事实更新为会产生 `BLOCKED` 的状态，或 verified filesystem binding 被替换。

### Impact

以 facts A 通过 admission，却在 facts A′ 已失效时仍创建 `CREATED` Shadow Run。该结果违反“command-time admission 是创建依据”的 fail-closed contract，因此 review 不能接受。

### Minimal remediation boundary

不得用以下伪修复关闭：

- 再调用一次普通 `evaluate()`；第二次 read 到 insert 仍有窗口；
- 把 filesystem verification 直接包入长数据库事务；这会扩大事务并仍不能原子锁定 filesystem；
- 仅依赖 CreationPlan frozen values；它只能防止用 facts B 持久化字段，不能证明 facts A 在 commit 时仍有效。

后续整改应先冻结 admission snapshot/guard contract：区分真正 immutable release/artifact facts与必须在写事务内 compare-and-lock 的 mutable DB facts，定义合法 rerun 对 latest Shadow 变化的例外语义，再实现 atomic validate-and-insert。若需要新增 schema/version token，必须进入独立 schema review；本轮禁止新增 V38 或修改 V37。

## 9. State machine 与 side-effect firewall

- Created state：`CREATED`。
- Runner invocation：`0`。
- Scheduler invocation：`0`。
- Matching invocation：`0`。
- Order intent invocation：`0`。
- `OrderCommandService`：`0`。
- `TradingVenueGateway`：`0`。
- Risk/Ledger/Account write：`0`。
- Credential/private exchange/external network：`0`。
- 唯一允许副作用：本地 `shadow_runs` insert 与本地 `shadow_run_events` append。

上述结论来自 production dependency/call-site search与 writer dependency reflection test；未启动应用、runner、scheduler 或任何外部服务。

## 10. RBAC 与 API/error safety

### Verified

- Global security config：GET `/api/**` 只需认证，其他 `/api/**`（含本 POST）要求 `ADMIN` 或 `OPERATOR`。
- Application guard：从 server-side profile 构造 actor，再次只允许 `ADMIN/OPERATOR`。
- WebMvc：anonymous=`401`；VIEWER=`403`；OPERATOR=`200 / CREATED / RELEASE_BOUND`。
- 异常映射：malformed header=`400`；publish missing=`404`；idempotency provenance conflict=`409`；admission BLOCKED=`422`；异常 envelope 不返回 SQL、absolute path、storage key、raw manifest 或 creation plan。

### Coverage gaps

- ADMIN=`allowed` 没有本轮要求的真实 WebMvc test。
- 400/404/409/422 主要由静态 handler mapping和 unit/integration behavior支持，没有全部形成该 POST 的真实 WebMvc error-path tests。
- 这些缺口不改变 VIEWER 确实被双层阻断的事实，但不满足本任务的最低 RBAC/API 回归矩阵。

## 11. Validation evidence

### Baseline

- `git diff --cached --check`：PASS。
- `check-current-authority.ps1`：PASS，`errors=0`。
- 收口重跑 `check-current-authority.ps1`：PASS，authority 仍为 `GateX-5 / IMPLEMENTED|PENDING_REVIEW`。
- `check-doc-links.ps1 -Roots 'README.md','docs/current'`：PASS，`errors=0`、历史 ledger warning=`1`（`docs/current/TESTING.md` 的既有 `GATEJ_TEST_PLAN.md` 相对链接）；该 warning 不由本轮 evidence 引入。
- 收口 `git diff --check` 与 `git diff --cached --check`：均为 PASS。

### Focused core/WebMvc attempt

- Core GateX-5 tests：9 tests，0 failures/errors/skips。
- WebMvc：3 tests，0 failures/errors/skips。
- 同一 reactor 中 PostgreSQL 配置指向不存在的 `nexus_quant_test` database，3 tests 为 connection error；RCA 后改用同一 localhost PostgreSQL 17 的既有 `nexus_quant` database，并仅在随机 schema 中运行。

### PostgreSQL full-class rerun

- PostgreSQL 连接/Flyway：成功，V1..V37 applied/validated。
- Materialization 综合测试：通过。
- 整个测试类：`FAIL`（失败），3 tests 中 2 failures；两处旧断言仍期望 migration version `36`，实际为 `37`。
- 随机 `gatex2_*` schemas 在 `finally` 中清理；结束后残留 schema count=`0`。

### Focused PostgreSQL + ArchUnit

```text
ShadowRunProvenancePostgresIntegrationTest#materializationShouldBeAtomicIdempotentConcurrentAndProvenanceBound
ModuleBoundaryArchTest
PackageBoundaryArchTest
```

结果：17 tests，0 failures/errors/skips；23 modules `SUCCESS`；reactor `BUILD SUCCESS`。

### Full backend regression

`NOT RUN`（未运行）。本轮未修改 production code；review 已因 P1 阻断。不得把 implementation evidence 中先前记录的 full backend 结果写成本轮重跑结果。

## 12. Findings

### P0

- 无。未发现 LIVE、真实交易、credential/private exchange 越界或错误 release 被直接 materialize 的已执行路径。

### P1

1. `ADMISSION_MATERIALIZATION_FACT_TEAR`：mutable DB/filesystem admission facts 与 writer insert 不在可证明的 atomic snapshot/guard 下；command-time eligibility 可在 insert 前失效。

### P2

1. PostgreSQL regression drift：`ShadowRunProvenancePostgresIntegrationTest` 两处仍断言 latest migration=`36`，实际为 V37，导致强制全类回归失败。
2. Mandatory matrix 缺口：ADMIN WebMvc、different-command true concurrency、逐类 provenance conflict/unchanged-row、rollback event count、POST 400/404/409/422 WebMvc 未完整覆盖。
3. Frontend 未暴露 legitimate rerun UX；本轮按边界未修改 frontend。

### P3

1. PostgreSQL 测试类/方法注释仍以 GateX-2/V36 命名，已不能准确表达其同时承载 GateX-5/V37 回归的当前范围。

## 13. Boundary confirmation

- Migration impact=`0`；未修改 V37，未新增 V38。
- Frontend impact=`0`；未新增 create button 或 Playwright flow。
- LIVE impact=`0`；保持 `DISABLED`。
- Production code changes by review=`0`。
- Shadow Run start=`0`；runner/scheduler/trading/external side effects=`0`。
- Disposable PostgreSQL container=`0`（Docker Desktop 未运行）；复用 localhost PostgreSQL 17，随机 test schema 已全部删除。

## 14. Authority after 与 decision

- Authority after 保持：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`；`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`next_action=NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`。
- 未提升为 `REVIEW_ACCEPTED|READY_TO_COMMIT`。
- 未改为 `REVIEW_REJECTED|REMEDIATION_REQUIRED`：当前 governance contract 对该 token 要求已存在 commit/CI，并把 next action 限定到历史 GateW RC fix，不能合法表达未提交 GateX-5 review rejection；本轮按指令不修改 governance contract、不伪造 commit/CI。
- Review decision：`FAIL / RELEASE_TO_SHADOW_MATERIALIZATION_REVIEW_REJECTED / P1_REMEDIATION_REQUIRED / NOT_READY_TO_COMMIT`（失败 / release-to-shadow materialization 审查拒绝 / 需要关闭 P1 / 不可提交）。

## 15. Review fixes、rollback 与下一动作

- Review fixes：无 production/test fix；P1 不能在不先冻结 concurrency/snapshot contract 的情况下安全最小关闭。
- 本轮唯一新增文件为本 review evidence。
- Rollback：删除本 evidence 文件即可回滚本轮写操作；现有 23 个 staged implementation 文件保持不变。
- Commit recommendation：无；不得使用 `feat(shadow): materialize verified strategy releases` 提交当前 rejected baseline。
- 下一具体动作建议：`NQ-GATEX-5-ADMISSION-MATERIALIZATION-FACT-TEAR-REMEDIATION`。先做最小 contract/locking 设计并补真实 PostgreSQL race regression；如需 schema 变化，先进入 `SCHEMA_REVIEW_REQUIRED`，不得在本 review 中新增 migration。
