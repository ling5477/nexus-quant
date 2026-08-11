# NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-FINAL-REVIEW — attempt-02

## 1. Task classification

- 归属：NQ-only。
- 类型：`INDEPENDENT_SECURITY_AND_CONSISTENCY_REVIEW` / `GUARDED_MATERIALIZATION_REVIEW` / `IDEMPOTENCY_CONCURRENCY_REVIEW` / `POSTGRESQL_RACE_REVIEW` / `FRONTEND_WRITE_BOUNDARY_REVIEW`。
- Review status：`PASS / RELEASE_TO_SHADOW_MATERIALIZATION_REVIEW_ACCEPTED / ADMISSION_MATERIALIZATION_FACT_TEAR_CLOSED / GUARDED_MATERIALIZATION_VERIFIED / READY_TO_COMMIT`（通过 / 审查已接受 / 事实撕裂已关闭 / 受 Guard 保护的物化已验证 / 可进入提交前复核）。
- 审查原则：不以 implementation evidence 单独判 PASS；结论来自 staged production code、V38、真实 PostgreSQL、backend/WebMvc/ArchUnit、frontend build 与 Playwright 的交叉证据。

## 2. Starting baseline

- Starting HEAD：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- `origin/dev` HEAD：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`；本轮开始时执行 `git fetch origin` 后仍相等。
- Branch：`dev`。
- Existing staged scope：50 paths，约 `6063 insertions / 127 deletions`；unstaged=0，untracked=0。
- Authority before：`GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；checker `errors=0`。
- Safety：LIVE=`DISABLED`，Shadow trading=`NOT_ENABLED`，AI=`NOT_STARTED`，DH runtime=`NOT_INTEGRATED`。

## 3. V38 dependency status

- V38 已作为既有 staged dependency 只读审查；本轮增量=0，未修改、未重写、未新增 V39。
- `strategy_release_admission_state` 以 `publish_record_id` 为唯一 state row，revision 单调递增，identity quartet 只允许全 NULL → 完整一次绑定，之后 immutable。
- source mutation triggers 覆盖 publish、evaluation/backtest、Paper、Shadow、consistency、strategy version 与 dataset；raw reverse-order writer 使用 `FOR UPDATE NOWAIT` fail-closed，application writer 使用 state-first coordinator。
- `shadow_runs` INSERT/UPDATE/DELETE 推进 revision；`shadow_run_events` 不重复推进。latest Shadow facts 以 `status <> 'CREATED'` 明确排除新建占位状态。

## 4. Production materialization chain

真实链路已逐层复核：

```text
POST /api/strategy-releases/{publishRecordId}/shadow-runs
→ StrategyReleaseShadowRunMaterializationController
→ StrategyReleaseShadowRunMaterializationService
→ StrategyReleaseAdmissionPreviewService
→ StrategyReleaseProductionService release/artifact verification
→ AdmissionGuard issuance
→ ReleaseToShadowAdmissionService
→ ShadowRunCreationPlan
→ ShadowRunMaterializationWriter
→ JdbcAdmissionMutationCoordinator
→ strategy_release_admission_state FOR UPDATE
→ current state/facts reload
→ canonical fingerprint + decision re-evaluation
→ JdbcShadowRunFactRepository idempotency/provenance check
→ shadow_runs INSERT
→ one CREATED event
→ V38 revision trigger
```

Controller 只接受 path `publishRecordId` 与标准 `Idempotency-Key`；actor/roles/trace 来自服务端 context。writer 的 production 依赖只有本地 repository、JSON mapper、state/facts/decision/fingerprinter、coordinator 与 clock。

## 5. AdmissionGuard / fingerprint / r0-r1

- Guard 固定包含 `guardSchemaVersion`、`admissionRevision`、`releaseArtifactDigest`、`manifestFingerprint`、`manifestSchemaVersion`、backtest/strategy/dataset/evaluation/window、validation/Paper/Shadow/consistency identities、authorization boundary、side-effect policy/version 与 `evaluatedAt`。
- fingerprint 使用 fixed-order typed binary encoding + SHA-256；NULL/presence、UUID、Instant、enum 与 policy 都进入 canonical encoding。
- issuance 顺序为 `read r0 → load/evaluate facts → issue candidate → read r1 → sameGeneration`；revision/schema/digest/manifest fingerprint/schema version 任一变化均抛 `ADMISSION_STALE`，没有 silent retry。
- PostgreSQL race 在 facts load 后提交 publish mutation，r0/r1 不同，未发出可消费 ELIGIBLE Guard。
- unknown guard schema fail-closed。

## 6. Writer atomic guard

- `JdbcAdmissionMutationCoordinator.withLockedAdmissionStates(...)` 在同一 Spring transaction 内按 publish ID 排序执行 `FOR UPDATE`。
- 锁内先比较 Guard/current state/plan，再从 DB 重新加载 current facts，并以原 Guard `evaluatedAt` 重算 canonical fingerprint。
- fingerprint mismatch → `ADMISSION_STALE`；current canonical decision 非 ELIGIBLE → `ADMISSION_BLOCKED`。
- idempotency/provenance compare 在 insert 之前/冲突后同事务完成；新建 run 与唯一 CREATED event 同事务提交。
- writer transaction 内 filesystem verification=0、network IO=0、manual admission revision update=0。

## 7. Race matrix

Guard issued at revision R 后，以下 mutation 均由真实 PostgreSQL 测试证明推进 revision；old Guard writer 返回 STALE，且 run/event/revision 额外副作用为 0：

| Race | Result |
| --- | --- |
| Validation/evaluation report mutation | `ADMISSION_STALE` |
| Paper insert/update/reorder/phantom | `ADMISSION_STALE` |
| Shadow evidence insert/transition | `ADMISSION_STALE` |
| Consistency append/update | `ADMISSION_STALE` |
| Publish mutation | `ADMISSION_STALE` |
| Evaluation mutation | `ADMISSION_STALE` |

blocked current facts 使用当前 state/facts 构造 Guard 后进入 writer，canonical re-evaluation 返回 BLOCKED；无 run/event/revision mutation。

## 8. Latest CREATED exclusion

- 新建 `CREATED` run 本身触发一次 revision bump。
- writer 追加 `CREATED` event 不产生第二次 bump。
- latest Shadow CTE 排除 `CREATED`，因此创建不会永久污染 validation latest evidence。
- `CREATED → PRECHECKING` 更新 `shadow_runs`，revision 再次变化并进入 evidence-bearing latest Shadow。

## 9. Idempotency / concurrency / legitimate rerun

- Same command retry：重新 evaluate + 同一 `Idempotency-Key` 派生同一不可逆 SHA-256 identity；返回相同 `shadowRunId`，`idempotentReplay=true`，CREATED event 总数=1。
- Same-command concurrent old Guard：state lock 串行化，结果为 one CREATE / one STALE；未为了双成功绕过 revision。
- Different-command concurrent old Guard：one CREATE / one STALE；没有 `UNIQUE(publish_id, artifact_digest)` 类约束。
- Legitimate rerun：loser 使用新 facts 重新 evaluate 后，以不同 command identity 创建第二个合法 run。
- 原始 `Idempotency-Key` 不进入 DB、audit metadata、response 或 log。

## 10. Provenance conflict matrix

`JdbcShadowRunFactRepository.requireSameReleaseProvenance(...)` 比较 publish、artifact digest、strategy version、dataset、evaluation、window、完整 side-effect policy JSON、六项 no-side-effect flags 与 authorization boundary。policy JSON 同时包含 policy version、input/provenance reference 与 manifest schema version。

在相同 materialization identity 下，publish、artifact、strategy、dataset、evaluation、window、authorization boundary、side-effect policy、input/provenance reference 任一变化均返回 `409 / IDEMPOTENCY_CONFLICT`；existing run/event 保持不变，不存在 last-write-wins。

## 11. Atomic rollback / revision / audit

- 使用 failing audit repository 强制 `appendEvent` 失败；显式 SQL 断言 `shadow_runs=0`、`shadow_run_events=0`、admission revision 与事务前相同。
- STALE、BLOCKED、IDEMPOTENCY_CONFLICT 均无新 run/event/revision。
- success 只产生一个 `CREATED / RELEASE_BOUND` run 与一个 CREATED event；event metadata 只含 actor ID、publish ID、binding mode、hashed idempotency identity、manifest schema、input/provenance reference 与明确的 no-start/no-trading flags。

## 12. RBAC / WebMvc / response safety

`StrategyReleaseShadowRunMaterializationSecurityWebMvcTest` 7/7：

| Case | Result |
| --- | --- |
| anonymous | 401 `UNAUTHORIZED` |
| VIEWER | 403 `FORBIDDEN` |
| OPERATOR | 200 + `CREATED / RELEASE_BOUND` |
| ADMIN | 200 + `CREATED / RELEASE_BOUND` |
| missing/malformed Idempotency-Key | 400 `BAD_REQUEST` |
| publish missing | 404 `RESOURCE_NOT_FOUND` |
| stale / idempotency conflict / blocked | 409 / 409 / 422 |

成功 DTO 只暴露 `shadowRunId`、publish、artifact digest、binding mode、status、createdAt 与 replay flag；稳定 error envelope 不暴露 revision、fingerprint、filesystem path、trusted root、storage key、manifest、SQL、internal exception 或 raw Idempotency-Key。

## 13. Frontend write boundary

- 仅 `ELIGIBLE + OPERATOR/ADMIN` 显示创建入口；BLOCKED/VIEWER 不显示。
- 二次确认明确“仅创建 CREATED Shadow Run；不启动 Runner/Scheduler；不下单；不访问交易凭证；不构成交易授权”。
- 一次人工确认/network retry 复用同一 command identity；显式“创建新的 Shadow Run”才生成新 key。
- TanStack mutation `retry=false`。
- `ADMISSION_STALE` 只 warning + refresh preview；POST count 保持 1，不自动 refresh→POST；必须人工再次确认。
- targeted Playwright 11/11 覆盖上述写侧与 fail-closed UI 状态。

## 14. Side-effect audit

| Side effect | Result |
| --- | --- |
| Runner / Scheduler / Matching | 0 / 0 / 0 |
| OrderCommandService / TradingVenueGateway | 0 / 0 |
| Risk / Ledger / Account write | 0 / 0 / 0 |
| Credential access / private exchange | 0 / 0 |
| External network | 0 |
| LIVE / Shadow start | `DISABLED / NOT_ENABLED` |

静态 dependency/import review 只出现认证缺失异常类型与 no-credential/no-ledger policy 字段，不存在对应生产 service/client 依赖。后端回归使用 `CI=true`、`NQ_NO_OUTBOUND=true`、AI/DH/real-exchange disabled；Playwright 只使用本地 Vite 与 route fixtures。

## 15. Mandatory regression

| Validation | Result |
| --- | --- |
| PostgreSQL 17 mandatory matrix | PASS；17.10；3 suites / 12 tests / 0 failure / 0 error / 0 skip；23/23 reactor modules SUCCESS |
| Focused backend | PASS；最终 rerun exit 0 |
| Full backend | PASS；23 modules，exit 0 |
| WebMvc targeted | PASS；7/7 |
| `ModuleBoundaryArchTest` | PASS；canonical nq-app 6/6 |
| `PackageBoundaryArchTest` | PASS；canonical nq-app 6/6 |
| Frontend build | PASS；TypeScript + Vite exit 0，3910 modules |
| Targeted Playwright | PASS；11/11 Chromium |
| PostgreSQL cleanup | PASS；GateX test schema residual=0，disposable container removed |

失败/RCA 记录：

1. 第一次 mandatory Maven 命令的 JDBC `-D` 参数未整体引用，被 PowerShell 误解析为 plugin；lifecycle 未进入。引用修正后 12/12 通过。
2. focused 首轮未显式注入 datasource，Spring context 使用默认本地配置并失败。
3. datasource 修正后的空 `nqtest` 缺少 `ResearchBacktestHappyPathLocalTest` 明确要求的 legacy account fixture；仅在 disposable DB 写入一条 SIM fixture 后，focused 最终 rerun 与 full backend 均通过。
4. `postgres:17-alpine` 本机无镜像且 pull 无输出后终止；使用本机已有官方 `postgres:17`（server 17.10）完成验证。未知本机 `5432` 未连接、未修改。
5. doc-links 首次通过嵌套 `powershell` 调用时丢失 `-Roots` 数组边界，未开始扫描；改为当前 PowerShell 直接数组调用后 `212 checked / 0 errors / 1 existing warning`。

上述失败均未被写成产品通过；最终结论只采用修正后的成功 rerun。

## 16. Files created / changed / final staged scope

本轮新增唯一文件：

- `docs/current/evidence/gate-x/NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-FINAL-REVIEW.attempt-02.md`

本轮最小同步：

- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `README.md` 与 `docs/current/README.md` 的 machine-summary 兼容行；这是 authority checker 的必要跨文档同步。

代码、V38、其他 migration、governance contract、API contract 与 frontend production code 本轮增量均为 0。最终 staged scope 为既有 50 paths + 本轮 7 个 docs/current-summary paths，共 57 paths、`6324 insertions / 139 deletions`；`git diff --cached --check` 退出码 0。

## 17. Findings

### P0

- 0。

### P1

- 0。未发现 old Guard 可跨 revision 创建、r0/r1 silent retry、writer current-fact bypass、non-atomic run/event、provenance last-write-wins、RBAC 绕过、前端自动 POST 或交易/LIVE 触达。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：沿用 V38 独立 migration review residual；本地小规模 PostgreSQL race 不能代替生产表规模、长事务、lock wait 与 fan-out 容量评估。
- `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：沿用 GateX-4C 已接受 residual；artifact filesystem verification 在 writer transaction 外完成，writer 不新增 filesystem IO，未扩大该边界。

### P3

- 既有 Mockito dynamic-agent、SLF4J no-provider、Vite chunk-size 与 Ant Design v5/React 19 compatibility warning；非本轮引入，不阻断本 slice。

## 18. Final status / authority / decision

- `ADMISSION_MATERIALIZATION_FACT_TEAR` final status：`CLOSED`。
- Guarded materialization：`VERIFIED`。
- Authority after：`work_batch=GateX-5`；`work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT`；`work_batch_commit=UNCOMMITTED`；`work_batch_ci_run=NOT_RUN`；`next_action=NQ-GATEX-5-COMMIT-AND-PUSH`。
- Review decision：`PASS / RELEASE_TO_SHADOW_MATERIALIZATION_REVIEW_ACCEPTED / ADMISSION_MATERIALIZATION_FACT_TEAR_CLOSED / GUARDED_MATERIALIZATION_VERIFIED / READY_TO_COMMIT`。
- Commit recommendation：`fix(strategy-release): close guarded shadow materialization race`。
- 唯一下一动作：`NQ-GATEX-5-COMMIT-AND-PUSH`。
- Final decision：`READY_TO_COMMIT`。本任务未 commit、未 push、未运行 exact-head CI。
