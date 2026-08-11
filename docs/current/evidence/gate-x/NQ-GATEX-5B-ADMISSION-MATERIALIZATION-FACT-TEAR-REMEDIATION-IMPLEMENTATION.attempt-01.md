# NQ-GATEX-5B Admission Materialization Fact Tear Remediation Implementation（attempt-01）

## 1. Task classification 与最终结论

- 任务类型：`P1_REMEDIATION / CODE_CHANGE`，辅助范围为最小 `FRONTEND_UI` 与 `IMPLEMENTATION_REPORT`。
- 归属：NQ-only。
- 风险等级：L 级交易前置写侧一致性修复。
- 实施结论：

```text
IMPLEMENTED /
ADMISSION_MATERIALIZATION_FACT_TEAR_REMEDIATED /
ADMISSION_GUARD_ISSUANCE_VERIFIED /
ATOMIC_GUARDED_MATERIALIZATION_VERIFIED /
IDEMPOTENT_RERUN_SEMANTICS_PRESERVED /
PENDING_INDEPENDENT_REVIEW
```

本轮把 server-side verification、versioned `AdmissionGuard`、state-first lock、锁内 canonical
re-evaluation、幂等创建和 `CREATED` event 收敛为同一 fail-closed 写链。当前结论只表示 implementation
与本地回归完成，不表示独立 review、commit、CI 或 Gate 接受完成，也不授权启动 Shadow Run、交易或 LIVE。

## 2. Baseline、shared residual 与 authority

- branch：`dev`。
- starting `HEAD == origin/dev == ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- shared baseline 已包含 GateX-5 P1-open code，故本轮采用 forward-only remediation；未重写 shared history。
- 开始时既有 staged residual 为 24 paths，包含 V38、V38 implementation/review fix/evidence 与 GateX-5
  remediation evidence chain；本轮保留这些 residual，不执行 reset/rebase。
- `V38__gate_x5a_admission_materialization_guard.sql` 是已完成独立 migration review 的依赖，本轮
  `git diff -- <V38>` 为空，未修改 V38，也未新增 V39。
- machine authority 保持：GateX=`IN_PROGRESS|NOT_FROZEN`；GateX-5=`IMPLEMENTED|PENDING_REVIEW`；
  LIVE=`DISABLED`；next action=`NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`。
- 未修改 `STATUS.md`、`ROADMAP.md`、`WORKLOG.md`。

## 3. AdmissionGuard 与 canonical fingerprint

新增 immutable `AdmissionGuard`，字段覆盖本次 ELIGIBLE 所依赖的完整事实代际：

- `guardSchemaVersion / publishRecordId / admissionRevision`；
- `releaseArtifactDigest / manifestFingerprint / manifestSchemaVersion`；
- `backtestRunId / strategyVersionId / datasetId / evaluationId / windowStart / windowEnd`；
- strategy/evaluation/publish 状态；
- latest Paper、latest evidence-bearing Shadow、latest consistency identity，缺失时为明确 `NONE`；
- `authorizationBoundary / sideEffectPolicyVersion / sideEffectPolicy`；
- `admissionFingerprint / evaluatedAt`。

Guard 不包含 filesystem path、trusted root、raw manifest、raw `Idempotency-Key` 或客户端 decision；它不是
`ShadowRunCreationPlan`，也不是 command identity。

fingerprint schema 固定为 `strategy-release-admission-guard.v1`：固定字段顺序；每字段写入 UTF-8 tag、
type byte 与 presence byte；字符串使用 length-prefixed UTF-8；UUID 使用 lowercase canonical；enum 使用
uppercase canonical；boolean 使用 0/1；`Instant` 使用 epoch-second+nano；最终为 lowercase SHA-256 hex。
`NULL/NONE` 与 present empty structure 不等价。实现不依赖 JSON、Map iteration、locale 或 timezone。

`AdmissionGuardFingerprinterTest` 覆盖稳定性、revision/timestamp/policy/三类 evidence identity 敏感性，以及
NONE 与 present identity 的区分。新增 admission-sensitive fact 时必须提升 schema version，不能静默改变 v1。

## 4. First identity binding 与 r0/r1 issuance

正式 issuance 流程为：

1. `StrategyReleaseProductionService.verify(...)` 完成服务器受控 artifact/manifest verification；
2. 从 verified aggregate 计算 `VerifiedStrategyReleaseIdentity`，不接受 client digest/fingerprint；
3. admission state quartet 尚未绑定时，通过 V38 typed first-binding path 在 state-first lock 下绑定；
4. 绑定后重新读取最新 admission state 作为 r0；未绑定则 `ADMISSION_GUARD_UNINITIALIZED`；
5. 校验 supported guard schema 及 verified release identity 一致性；
6. 从 PostgreSQL 加载 exact admission facts，复用 canonical validation/admission evaluation；
7. 仅稳定 `ELIGIBLE` 构造 creation plan 与 Guard；
8. 再读 admission state 为 r1，比较 revision、schema、artifact digest、manifest fingerprint 与 manifest schema；
9. r0/r1 任一不一致立即 `ADMISSION_STALE`，不静默重试。

真实 PostgreSQL race 在 r0 read 与 r1 read 之间提交 publish mutation，issuance 返回
`AdmissionStaleException`，未签发 eligible Guard。verified aggregate 与 persisted identity mismatch、未知
`guard_schema_version` 同样 fail-closed。first-binding 完成后 revision 已由 V38 trigger 推进，Guard 基于绑定后的
最新 revision 签发。

## 5. Latest CREATED exclusion

`JdbcStrategyReleaseAdmissionPreviewFactsRepository` 的 latest Shadow CTE 明确使用：

```sql
WHERE sr.status <> 'CREATED'
```

因此 materialization command fact `CREATED` 不会被误当成 validation evidence；但 `shadow_runs` INSERT
仍由 V38 trigger 推进 `admissionRevision`。真实 PostgreSQL 断言创建后 revision 变化，而 latest Shadow
evidence identity 仍为 `NONE`；后续进入 `PRECHECKING/...` 的 evidence-bearing lifecycle 才参与 latest 查询。

## 6. Writer transaction、state lock 与 stale enforcement

`ShadowRunMaterializationWriter.materialize(...)` 在 transaction 内调用 `AdmissionMutationCoordinator`：

1. 按 canonical order 对 `strategy_release_admission_state` 执行 `SELECT ... FOR UPDATE`；
2. 锁内重读 current state；
3. 比较 Guard supported schema、revision、release digest、manifest fingerprint、manifest schema；
4. 比较 plan 与 Guard 的 publish/artifact/manifest/strategy/dataset/evaluation/window/authorization/policy；
5. 重载 current PostgreSQL admission facts；
6. 使用 Guard 的 `evaluatedAt` 重算 canonical fingerprint；
7. fingerprint 不同返回 `ADMISSION_STALE`；
8. 使用与 issuance 相同的 `AdmissionGuardDecisionService` 重算 current decision；非 ELIGIBLE 返回
   `ADMISSION_BLOCKED`；
9. 通过 repository 做 idempotency replay/conflict check；
10. winner 插入 `CREATED / RELEASE_BOUND` Shadow Run 并追加 exactly one `CREATED` event；
11. `shadow_runs` trigger 推进 revision，事务原子提交。

writer 不手工更新 `admission_revision`；V38 trigger 是 revision 唯一权威写方。writer dependency graph 仅含
本地 repository/coordinator、facts/state ports、canonical decision/fingerprinter、JSON mapper 与 clock；锁内不访问
filesystem verifier、manifest loader、trusted-root resolver、network、runner 或 scheduler。

稳定 API 语义：

| 场景 | HTTP / code | 写入结果 |
| --- | --- | --- |
| facts generation 或 Guard identity/schema/fingerprint 变化 | `409 / ADMISSION_STALE` | run=0、event=0 |
| current canonical decision 非 ELIGIBLE | `422 / ADMISSION_BLOCKED` | run=0、event=0 |
| 同 identity 但 provenance 不同 | `409 / IDEMPOTENCY_CONFLICT` | existing row/event 不变 |
| publish 不存在 | `404 / RESOURCE_NOT_FOUND` | 无写入 |
| 缺失或 malformed `Idempotency-Key` | `400 / BAD_REQUEST` | 无写入 |

响应不输出 current revision、fingerprint material、filesystem path、storage key、SQL 或内部 exception message。

## 7. Race、replay、rerun 与 provenance matrix

真实 PostgreSQL 覆盖：

| 场景 | 结果 |
| --- | --- |
| validation report mutation | old Guard → `ADMISSION_STALE`，run/event/revision 不发生额外变化 |
| Paper phantom insert | old Guard → `ADMISSION_STALE`，零写入 |
| evidence-bearing Shadow insert/status fact | old Guard → `ADMISSION_STALE`，零写入 |
| consistency append/update | old Guard → `ADMISSION_STALE`，零写入 |
| publish mutation | old Guard → `ADMISSION_STALE`，零写入 |
| evaluation mutation | old Guard → `ADMISSION_STALE`，零写入 |
| 两个相同 command 持同一旧 Guard | 一个 create；另一个 `ADMISSION_STALE` |
| stale loser 使用相同 key 重新 evaluate | 返回原 `shadowRunId`，`idempotentReplay=true`，CREATED event 仍为 1 |
| 两个不同 command 持同一旧 Guard | 一个 create；另一个 `ADMISSION_STALE` |
| different-command loser 重新 evaluate | 合法创建第二个不同 `shadowRunId` |

没有新增 `UNIQUE(publish_id, artifact_digest)`；同一 release 的显式 legitimate rerun 保留。

full provenance conflict matrix 对相同 materialization identity 分别改变以下事实：

- `publishRecordId`；
- `artifactDigest`；
- `strategyVersionId`；
- `datasetId`；
- `evaluationId`；
- `windowStart`；
- `windowEnd`；
- side-effect policy/version；
- `inputReference`；
- `provenanceReference`；
- authorization boundary。

每项均抛出 `ShadowRunIdempotencyConflictException`，既有 row 与唯一 `CREATED` event 保持不变，不存在
last-write-wins。

## 8. Atomic rollback

真实 PostgreSQL 通过 failing audit repository 强制制造“run insert 成功、event append 失败”。显式 SQL 断言：

- `shadow_runs` 新增行=0；
- `shadow_run_events` 新增行=0；
- admission revision 与事务前相同。

stale、blocked、idempotency conflict 同样断言无新 run、无新 event、无额外 revision mutation。创建 winner
固定只有一个 `CREATED` event；revision 只在事务成功提交时由 trigger 推进。

## 9. RBAC 与 WebMvc matrix

`StrategyReleaseShadowRunMaterializationSecurityWebMvcTest` 共 7 tests、0 failures/errors：

- anonymous → 401；
- VIEWER → 403；
- OPERATOR → 200 + `CREATED`；
- ADMIN → 200 + `CREATED`；
- missing/malformed `Idempotency-Key` → 400；
- missing publish → 404；
- `ADMISSION_STALE` → 409；
- `IDEMPOTENCY_CONFLICT` → 409；
- `ADMISSION_BLOCKED` → 422。

Controller/global security 与 application role guard 双层存在；VIEWER 不可触达写 service。

## 10. Frontend materialization closure

Strategy Validation 的既有 admission preview 附近新增最小创建闭环：

- 仅 `admissionDecision=ELIGIBLE` 且当前角色为 OPERATOR/ADMIN 时显示写入口；BLOCKED 与 VIEWER 均无按钮；
- 二次确认明确“仅创建 `CREATED` Shadow Run；不会启动 Runner/Scheduler；不会下单；不会访问交易凭证；
  不构成交易授权”；
- mutation 禁止自动 retry；一次用户确认生成并保存一个 command identity；
- “重试同一创建命令”复用相同 `Idempotency-Key`；只有用户明确选择“创建新的 Shadow Run”才生成新 key；
- `ADMISSION_STALE` 只显示 warning 并刷新 preview，POST 次数保持 1，不自动 re-evaluate + POST；用户必须重新确认；
- 成功结果显示 `CREATED`、`shadowRunId` 与 replay 状态，不提供启动、执行或下单入口。

targeted Playwright 11/11 通过，覆盖 loading/no-write、404、legacy unbound、artifact rejected、blocked、
eligible/create confirmation、same-command retry、explicit rerun、stale refresh/no auto POST、BLOCKED/VIEWER
无入口及 request failure fail-closed。

## 11. Validation evidence

| 验证项 | 命令 / 环境 | 结果 |
| --- | --- | --- |
| Mandatory PostgreSQL 17 | `mvn -f backend/pom.xml -pl nq-app -am test`，显式选择 3 个 PostgreSQL suites，`required=true`，`127.0.0.1:55439/nqfocused` | PASS（通过）；PostgreSQL 17.10；12 tests、0 failures、0 errors、0 skipped；23/23 modules `SUCCESS` |
| Focused backend reactor | `mvn -f backend/pom.xml -pl nq-core,nq-research,nq-infra,nq-api,nq-app -am test` | PASS（通过）；23/23 modules `SUCCESS` |
| Full backend final | `mvn -f backend/pom.xml test`，no-outbound flags，disposable `nqtest` database | PASS（通过）；23/23 modules `SUCCESS`，`BUILD SUCCESS`，exit 0；PostgreSQL-required suites在本命令内按设计 skip，已由上一行单独强制执行 |
| WebMvc | full backend 中 `StrategyReleaseShadowRunMaterializationSecurityWebMvcTest` | PASS（通过）；7 tests、0 failures/errors |
| ArchUnit | full backend 中 `ModuleBoundaryArchTest` / `PackageBoundaryArchTest` | PASS（通过）；6/6 + 6/6 |
| Frontend build | `npm run build` | PASS（通过）；TypeScript + Vite exit 0 |
| Targeted Playwright | `npx playwright test ... --project=chromium --grep <GateX-5B cases>` | PASS（通过）；11 passed，0 failed |

已知非阻断 warning：Maven/Mockito 动态 agent 与 SLF4J no-provider；Vite chunk 超过 500 kB；Ant Design v5
对 React 19 的既有 compatibility warning。没有把 warning 写成失败，也没有为本任务扩大依赖或构建配置范围。

## 12. Side-effect proof

| Side effect | 结果 | 证据 |
| --- | --- | --- |
| Shadow initial state | `CREATED / RELEASE_BOUND` | writer 与 PostgreSQL/WebMvc assertions |
| Runner invocation | 0 | writer dependency graph 无 runner；UI 无启动入口 |
| Scheduler invocation | 0 | writer dependency graph 无 scheduler |
| Matching / orders | 0 / 0 | 无 matching/order service 或 adapter dependency |
| Risk / ledger / account write | 0 / 0 / 0 | 无对应 write dependency；六项 no-side-effect flags=true |
| Credential access | 0 | 无 credential dependency；确认文案与 policy 均 fail-closed |
| Private exchange | 0 | 无 private client/endpoint dependency |
| External network | 0 | backend final 使用 no-outbound flags；Playwright 仅本地 Vite + route fixtures |
| LIVE | `DISABLED` | `STATUS.md` machine authority 未变；本轮无 LIVE 路径 |

## 13. Files created / changed

新增：

- `backend/nq-core/.../AdmissionGuard.java`
- `backend/nq-core/.../AdmissionGuardDecisionService.java`
- `backend/nq-core/.../AdmissionGuardFingerprinter.java`
- `backend/nq-core/.../AdmissionGuardUninitializedException.java`
- `backend/nq-core/.../AdmissionStaleException.java`
- `backend/nq-core/.../AdmissionGuardFingerprinterTest.java`
- `backend/nq-app/.../AdmissionGuardedMaterializationPostgresIntegrationTest.java`
- 本 evidence。

修改：

- core admission preview/decision/creation plan/materialization service/writer/state ports、幂等 conflict exception 及单元测试；
- infra admission state/facts JDBC adapters、Shadow provenance repository tests；
- API exception handler、materialization controller 与 WebMvc security/error matrix；
- frontend strategy-release API/types/query hook、admission preview panel 与 targeted Playwright spec。

V38 以及 24 个既有 staged residual 均保留，V38 本轮增量为 0。未修改 Python、deployment、CI、DH、AI、
Runner、Scheduler、order、risk、ledger、account 或 credential production path。

## 14. Findings

### P0

- 0。未发现 wrong release materialized、LIVE/交易/private exchange 触发或 shared migration 重写。

### P1

- 0。r0/r1、old Guard stale、state-first lock、fingerprint/decision re-evaluation、phantom races、
  run/event/revision atomicity、idempotency conflict、VIEWER deny 与 no-auto-start 均有 production code 和回归证据。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：沿用 V38 独立 review residual；部署前仍需按目标表规模、长事务、
  lock wait 与 fan-out 做只读容量评估。
- 前端更完整的 session/risk summary 仍为后续 UX；本轮只实现合法 rerun 的最小闭环。
- filesystem stable-handle limitation 继承 GateX-4C verifier contract；本轮未扩张 PostgreSQL write transaction
  到 filesystem IO，也未重新定义该安全模型。

### P3

- 既有 Maven/Mockito/SLF4J、Vite chunk-size、Ant Design React 19 compatibility warnings；不阻断本 slice，
  未以无关依赖升级处理。

## 15. Closure、rollback 与 independent review

`ADMISSION_MATERIALIZATION_FACT_TEAR` 在 implementation/self-verification 层满足 closure 门槛：

- r0/r1 issuance guard verified；
- writer `FOR UPDATE` verified；
- revision/schema/release identity/fingerprint mismatch 均 stale；
- current canonical decision 在锁内重算；
- validation/Paper/Shadow/consistency/publish/evaluation races fail-closed；
- run/event/revision atomic；
- same-command replay 与 different-command rerun 语义保留；
- P0=0、P1=0。

因此状态为 `CLOSED / IMPLEMENTATION_SELF_VERIFIED / PENDING_INDEPENDENT_REVIEW`，不得写成 review accepted。

回滚未执行。若独立 review 拒绝，应仅对本 evidence 中列出的 GateX-5B task-scoped 增量应用精确反向 patch；
不得 reset shared history、不得覆盖既有 24-path staged chain、不得修改已独立审查的 V38。无生产部署、
无生产 DB 写入、无数据回填、无外部资源，因此没有生产数据回滚步骤。

唯一下一动作：

```text
NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW
```
