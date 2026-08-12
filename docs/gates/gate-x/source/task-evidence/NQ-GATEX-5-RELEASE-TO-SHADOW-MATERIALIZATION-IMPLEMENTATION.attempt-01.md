# NQ GateX-5 Release-to-Shadow Materialization Implementation — Attempt 01

## 结论

`IMPLEMENTED / RELEASE_TO_SHADOW_MATERIALIZATION_COMPLETE / IDEMPOTENT_PROVENANCE_BOUND_SHADOW_CREATE_VERIFIED / NO_SHADOW_START / PENDING_INDEPENDENT_REVIEW`

本轮只新增受控 Shadow Run materialization 写侧：从唯一客户端业务事实 `publishRecordId` 重新执行 server-owned release/artifact/validation/admission 链，并在本次结果为 `ELIGIBLE` 且存在 `ShadowRunCreationPlan` 时，原子创建一个 `CREATED / RELEASE_BOUND` Shadow Run。未启动 runner/scheduler，未触达交易、凭证、private exchange、外部网络或 LIVE。

## GateX-4 acceptance 与 authority

- branch：`dev`。
- starting `HEAD == origin/dev == 7aaf6027644b2ba6cd7dc588536784be50ff1eff`；进入时 worktree clean、staged empty。
- GateX-4 exact-head GitHub Actions：`NQ CI Baseline` run `31467397459 / completed / success / 10 jobs / bad=0`。
- `accepted_batch=GateX-4`、`accepted_batch_status=ACCEPTED|CI_GREEN`。
- GateX-5 初始化：`NOT_STARTED / NONE / NOT_RUN`；实现完成后：`IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- LIVE 始终为 `DISABLED`；Shadow trading 始终为 `NOT_ENABLED`。

## 既有 Shadow write path 审计

- `EXISTING_CREATE_PATH = ShadowRunRunnerService.newRun → ShadowRunFactRepository.create → JdbcShadowRunFactRepository`。
- `EXISTING_START_PATH = ShadowRunRunnerService.newRun` 在创建后继续执行 `CREATED → PRECHECKING → READY → RUNNING`；本轮不依赖、不调用该 service。
- `EXISTING_STATE_MACHINE = CREATED` 是当前真实代码中的安全未启动初态；`startedAt` 与 `paperRunId` 均保持 `null`。
- `EXISTING_IDEMPOTENCY = shadow_runs.idempotency_key UNIQUE + INSERT ... ON CONFLICT DO NOTHING + existing fact reload`；本轮复用该行为，不新增第二套 aggregate 或 UNIQUE(publish_id, artifact_digest)。
- `EXISTING_AUDIT = shadow_run_events`；本轮复用 `CREATED` event，不新增平行账本或 migration。

## Admission re-evaluation 与 creation-plan mapping

- `StrategyReleaseAdmissionPreviewService.evaluate(...)` 成为 GET preview 与 POST materialization 共用的只读 orchestration，返回安全 preview 与完整 `ReleaseToShadowAdmissionDecision`；未复制 GateX-1/GateX-3 规则。
- POST 请求体为空。客户端不能提交 digest、strategy/dataset/evaluation、validation、policy、plan、path、storage key 或 manifest。
- 写入唯一输入为本次重新评估得到的 `ShadowRunCreationPlan`：
  - `publishRecordId → shadow_runs.publish_id`
  - `artifactDigest → shadow_runs.artifact_digest`
  - `strategyVersionId/datasetId/evaluationId/window →` 既有同名事实
  - 六项 `SideEffectPolicy →` 既有 policy JSON 与六个布尔 hard gates
  - `authorizationBoundary →` 既有 authorization boundary
  - `inputReference/provenanceReference/manifestSchemaVersion →` 受控 `CREATED` audit metadata
  - `traceId →` run/event trace
  - binding mode 由 publish+digest 派生为 `RELEASE_BOUND`
- `JdbcShadowRunFactRepository` 的 collision guard 从 publish/digest 扩展到所有已持久化 immutable plan provenance，包括 strategy/dataset/evaluation/window/policy/六项 flags/authorization boundary。

## 事务、幂等与并发

- artifact filesystem verification、validation 与 admission 运行在 DB 写事务外；只有 `shadow_runs` insert 与 `shadow_run_events` append 位于 `@Transactional` writer 内，避免长事务包围 filesystem verification。
- GateX-3 base key 表达 immutable admission facts；GateX-5 使用现有标准 `Idempotency-Key` 作为 materialization command identity，与 base key 进行 length-prefixed SHA-256 派生。原始 header 不入库、不写日志、不进入响应。
- 相同 command identity 重放得到同一 derived key 与同一 Shadow Run；不同 command identity 形成不同 key，允许同一 release 的合法 rerun。
- 单次创建仍可生成候选 UUID，但 UNIQUE idempotency key 决定物理唯一性；它不是每次 POST 无条件创建新 run 的随机幂等模型。
- 真实 PostgreSQL 两线程同 key 并发结果：一个物理 run、同一 `shadowRunId`、一个 create + 一个 deterministic replay、一个 `CREATED` event。
- 相同 key 但 window provenance 不同：`ShadowRunIdempotencyConflictException`，fail-closed，不吞冲突。
- 强制 audit append 失败：外层真实 PostgreSQL 事务回滚，run 计数为 0。

## API、RBAC、audit 与 side-effect firewall

- API：`POST /api/strategy-releases/{publishRecordId}/shadow-runs`；空 body；标准 `Idempotency-Key` header。
- 最小响应：`shadowRunId/publishRecordId/artifactDigest/bindingMode/status/createdAt/idempotentReplay`；不返回 root/path/storage key/manifest/plan/internal exception。
- RBAC：anonymous=`401`；VIEWER=`403`；既有 write contract 中 OPERATOR/ADMIN 可调用；application service 仍从 server-side profile 再校验 role。
- Audit：只在物理创建 winner 上追加一个既有 `CREATED` event；包含 actorId、when、publishRecordId、shadowRunId、release binding、traceId、不可逆 command identity、materialization result flags。无 path、manifest、artifact content 或 credential。
- `ShadowRunMaterializationWriter` 依赖图只有 `ShadowRunFactRepository/ObjectMapper/Clock`；runner、scheduler、TradingVenueGateway、OrderCommandService、risk/ledger/account write、credential provider、private client 与 network dependency 均为 0。
- 创建结果固定 `CREATED / RELEASE_BOUND`，`startedAt=null`、`paperRunId=null`、六项 no-side-effect flags=`true`；runner invocation=`0`，scheduler invocation=`0`，交易副作用=`0`，外部网络副作用=`0`。

## 验证证据

| 验证项 | 结果 | 摘要 |
| --- | --- | --- |
| Core focused | PASS（通过） | materialization service/writer 与既有 admission focused suites 合计 30 tests，0 failures / 0 errors |
| Security WebMvc | PASS（通过） | 3 tests：anonymous 401、VIEWER 403、OPERATOR 200 + `CREATED` |
| Disposable PostgreSQL | PASS（通过） | 官方 `postgres:17` 一次性容器；V1..V37 migration；single/replay/concurrent/conflict/rollback 1 个综合测试通过；无 volume，测试后容器删除 |
| Focused reactor | PASS（通过） | `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-api,nq-app -am test`；23 modules `SUCCESS`；`nq-app` 256 tests、0 failures、0 errors、17 skipped |
| Full backend | PASS（通过） | `mvn -f backend/pom.xml test`；23 modules `SUCCESS`；285 份 Surefire XML 汇总 1374 tests、0 failures、0 errors、21 skipped |
| ArchUnit | PASS（通过） | `ModuleBoundaryArchTest` + `PackageBoundaryArchTest` 合计 16 tests，0 failures / 0 errors |
| Frontend | NOT RUN（未运行） | frontend 变更为 0，按任务边界不做形式化 Playwright |
| Authority/docs/diff | PASS（通过） | authority checker=`errors=0`；doc links=`211 checked / 0 errors / 1 existing warning`；tracked/cached diff checks 在精确暂存后复核 |

RCA：首次 Security WebMvc 因 slice 缺少 `AuthUserRepository` mock 失败，补齐既有 security dependency mock 后 3/3 通过。首次 focused reactor 暴露新增 service 双构造器导致 Spring 无法选择构造器，给 production 构造器增加 `@Autowired` 后，定向 6/6、focused reactor 与 full backend 全部重跑通过。尝试拉取 `postgres:17-alpine` 时 registry 下载无进展并被终止，改用本机已有官方 `postgres:17` 镜像完成真实 disposable PostgreSQL 验证；未依赖 mock DB。`check-doc-links.ps1` 首次遗漏 mandatory `-Roots`，扫描未开始；修正为 `-Roots @('README.md','docs/current')` 后通过，不把 CLI 失败记录为首轮通过。

## Findings 与边界

- P0=0。
- P1=0。admission BLOCKED 零写入、provenance freeze、retry/concurrency dedupe、VIEWER 禁止、create/start 分离、audit 与 idempotency identity 均已验证。
- P2=2：未来 legitimate rerun 的前端 UX 尚未暴露；更完整 session/risk summary 留待 GateX 后续 slice。两项均为任务明确允许的后续项，不阻断 backend review。
- P3=1：外部 Maven `settings.xml` 存在既有 unrecognized profile warning；不影响本轮构建和测试结果，且未修改该外部配置。
- Migration impact=`0`；未修改 V37、未新增 V38。
- Frontend impact=`0`；未新增创建按钮、API client 或 E2E。
- Trading/LIVE impact=`0`；未启动 Shadow、未调 runner/scheduler、未调用交易或 private endpoint，LIVE=`DISABLED`。
- AI/DH/Python/runtime bridge 影响=`0`。

## 下一动作与回滚

- 唯一下一动作：`NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`。
- 必须独立审查；当前实现不得直接标记 accepted、commit 或 push。
- 回滚：在未 commit 状态下，仅撤销本证据列出的 GateX-5 staged files；未新增 migration/数据回填/外部资源，故无 schema 或生产数据回滚步骤。一次性 PostgreSQL 容器已删除。
