# NQ-GATEX-3 Release-to-Shadow Admission Implementation — attempt-01

## 1. Task classification

- 任务：`NQ-GATEX-3-RELEASE-TO-SHADOW-ADMISSION-IMPLEMENTATION`。
- 归属：NQ-only。
- 类型：`BACKEND_IMPLEMENTATION / RELEASE_TO_SHADOW_ADMISSION / PURE_DECISION_SERVICE / PROVENANCE_ENFORCEMENT / REGRESSION_TEST / SELF_REVIEW / POST_CI_AUTHORITY_RECONCILIATION`。
- 主 skill：`java-backend-maintenance`；命中 Java application/domain production capability、fail-closed 业务编排、模块边界和 Maven 回归。
- 辅助 skill：`nq-dh-workflow-router` 用于 Gate/NQ-only/禁止范围校验；`nq-docs-writer` 用于 current authority、evidence 与 append-only ledger 同步。

## 2. Scope

### 2.1 目标

将 GateX preparation 中有价值的 Release-to-Shadow admission 语义提升为 production application capability：

```text
Strategy Release production facts
+ VERIFIED artifact
+ RELEASE_BOUND provenance
+ Shadow safety policy
        ↓
Release-to-Shadow Admission
        ↓
ShadowRunCreationPlan
```

固定边界：

```text
ShadowRunCreationPlan != ShadowRunCreated
Admission ELIGIBLE != trading authorization
Release bound != automatically admitted
```

### 2.2 允许范围

- `backend/nq-core` application/domain-facing immutable contract、纯决策 service 与 unit regression。
- GateX-3 implementation evidence。
- `STATUS.md`、`TESTING.md`、`WORKLOG.md` 最小同步；checker 要求下同步 root/current README 与 ROADMAP。

### 2.3 禁止范围

- Flyway、schema、SQL、repository write 或 Shadow Run 持久化。
- REST API、frontend、research、scheduler、runner runtime behavior。
- order/risk execution、adapter、exchange、credential、private endpoint、account/ledger/order/trade write。
- LIVE、真实交易、AI 或 DH runtime。

## 3. Baseline and authority

- repository：`E:\Project\nexus-quant`；附件中的 `F:\project\nexus-quant` 与实际 workspace 不一致，按当前用户工作区执行。
- branch：`dev`。
- starting `HEAD`：`894e76bf69dbcf1574be6c993f18ca7913033564`。
- `origin/dev`：`894e76bf69dbcf1574be6c993f18ca7913033564`。
- GateX-2 implementation/acceptance commit：`894e76bf69dbcf1574be6c993f18ca7913033564`。
- GateX-2 exact-head CI：run `31379536899 / completed / success`。
- GateX-2 acceptance：`ACCEPTED|CI_GREEN`。
- GateX-3 entering authority：`NOT_STARTED / NONE / NOT_RUN`；next action=`NQ-GATEX-3-RELEASE-TO-SHADOW-ADMISSION-IMPLEMENTATION`。
- LIVE：`DISABLED`。
- initial authority checker：`errors=0`。

## 4. Production capabilities inspected

- `StrategyRelease`：canonical `releaseAnchorId == publishRecordId` immutable production aggregate。
- `StrategyReleaseProductionService`：只读 provenance load + trusted-root artifact verification 的生产入口；admission 消费其结果，不复制 load/verifier。
- `StrategyArtifactVerificationResult`：复用 `VERIFIED/REJECTED` 事实与 artifact digest。
- `StrategyArtifactManifest`：复用 supported schema、strategy/dataset/evaluation/digest provenance。
- `StrategyValidationDecision`：复用 `APPROVED/NO_EVIDENCE/STALE_EVIDENCE/REJECTED/NEEDS_REVIEW/BLOCKED`。
- `ShadowRunReleaseBindingMode.derive(...)`：唯一 binding/digest validator；admission 不复制第二套 binding enum 或 digest regex。
- `ShadowRunAuthorizationBoundary`：仅允许 `DIAGNOSTIC_ONLY/REVIEW_ONLY`；`REPLAY_ONLY` fail-closed。
- `ShadowRun`：复用六项 production no-side-effect facts 的字段语义；不调用其 creation 或 repository path。
- production `src/main` 原先不存在等价 Release-to-Shadow admission；不存在 `EQUIVALENT_ADMISSION_IMPLEMENTATION_EXISTS` blocker。

## 5. Prototype admission inventory and promotion matrix

| Prototype | Classification | 本轮处理 |
| --- | --- | --- |
| `StrategyReleaseToShadowAdmissionServicePrototype` | `PARTIALLY_PROMOTED` | 提升 fail-closed identity/provenance/validation/window/safety 与确定性 idempotency 语义；改为 production aggregate/result 类型 |
| `ShadowRunAdmissionPrototype` | `PARTIALLY_PROMOTED` | 将 prototype 的 `ADMITTED/BLOCKED/UNKNOWN` 收敛为 production `ELIGIBLE/BLOCKED`；UNKNOWN 一律 BLOCKED |
| `ShadowRunCreationPlanPrototype` | `PARTIALLY_PROMOTED` | 提升 immutable plan/input/trace/idempotency 语义；没有提升 actual Shadow creation |
| `ShadowRunReleaseBindingPrototype` | `OBSOLETE FOR PRODUCTION / EVIDENCE RETAINED` | production 已由 GateX-2 `ShadowRunReleaseBindingMode` 取代；历史 test evidence 保留 |
| test-only fixtures/tests | `STILL_PROTOTYPE` | 保留为历史 regression evidence，不删除、不作为 production facts |
| actual Shadow creation path | `STILL_PROTOTYPE / DEFERRED` | 本轮不 productionize；留给 GateX-4 或之后明确任务 |

## 6. Admission input contract

`ReleaseToShadowAdmissionRequest` 是允许空值进入决策层的 immutable snapshot，便于统一返回稳定 blocker，而不是在调用边界抛出或推测默认值。

| Fact | Source / rule |
| --- | --- |
| `release` | 必须是 production `StrategyRelease`；null 表示 publish record missing |
| `releaseAnchorId` / `publishRecordId` | 必须非空、彼此一致，并与 release canonical identity 一致 |
| `strategyVersionId` | 必须非空并与 release/manifest 一致 |
| `datasetId` | 必须存在并与 release/manifest 一致 |
| `evaluationId` | 必须非空并与 release/manifest 一致 |
| `artifactDigest` | 必须与 release/verification/manifest 一致，且经 `ShadowRunReleaseBindingMode.derive` 得到 `RELEASE_BOUND` |
| release / artifact status | release 必须 `VERIFIED`，artifact verification 必须 `VERIFIED` |
| `validationDecision` | 仅 `APPROVED` 可继续；missing/no-evidence/stale/other decision 全部阻断 |
| window | start/end 必须存在，且 end 严格晚于 start |
| authorization boundary | 仅 `DIAGNOSTIC_ONLY` 或 `REVIEW_ONLY` |
| side-effect policy | 六项 no-side-effect flags 必须全部为 true |
| `traceId` | 必须非空、无 control char、长度不超过 128；不进入幂等 key |

请求不包含 credential、账户余额、订单、private payload 或 LIVE authorization。

## 7. Admission decision model

### 7.1 Decision

- `ELIGIBLE`：只表示允许生成 `ShadowRunCreationPlan`。
- `BLOCKED`：至少一个稳定 reason code，creation plan 必须为空。
- 所有 decision 固定：`shadowRunCreated=false`、`shadowRunStarted=false`、`tradingAuthorized=false`、`orderSubmitted=false`。

### 7.2 Stable reason codes

- Eligible boundary：`ELIGIBLE_FOR_CREATION_PLAN_ONLY`。
- Publish/release identity：`PUBLISH_RECORD_MISSING`、`RELEASE_ANCHOR_MISSING`、`PUBLISH_ID_MISSING`、`RELEASE_IDENTITY_MISMATCH`、`PUBLISH_ID_MISMATCH`。
- Provenance：`STRATEGY_VERSION_*`、`DATASET_*`、`EVALUATION_*`、`MANIFEST_PROVENANCE_MISMATCH`、`MANIFEST_SCHEMA_UNSUPPORTED`。
- Verification/binding：`RELEASE_UNVERIFIED`、`RELEASE_REJECTED`、`ARTIFACT_NOT_VERIFIED`、`ARTIFACT_DIGEST_*`、`RELEASE_BINDING_REQUIRED`。
- Validation/window/boundary：`VALIDATION_*`、`SHADOW_WINDOW_*`、`AUTHORIZATION_BOUNDARY_*`。
- Safety/trace：`SIDE_EFFECT_POLICY_MISSING`、六个 `NO_*_REQUIRED`、`TRACE_REFERENCE_*`。

reason code 不携带路径、异常、digest 内容、credential 或 private payload。

## 8. Eligibility requirements and fail-closed matrix

| Condition | Decision / reason |
| --- | --- |
| verified release + verified artifact + complete matching production facts + `RELEASE_BOUND` + all safety flags | `ELIGIBLE / ELIGIBLE_FOR_CREATION_PLAN_ONLY` |
| publish record/release missing | `BLOCKED / PUBLISH_RECORD_MISSING` |
| release anchor 与 publish identity 不一致 | `BLOCKED / RELEASE_IDENTITY_MISMATCH` |
| requested publish 与 release 不一致 | `BLOCKED / PUBLISH_ID_MISMATCH` |
| strategy/dataset/evaluation 缺失或不一致 | `BLOCKED / *_MISSING` 或 `*_MISMATCH` |
| release `UNVERIFIED` / `REJECTED` | `BLOCKED / RELEASE_UNVERIFIED` 或 `RELEASE_REJECTED` |
| artifact verification 非 `VERIFIED` | `BLOCKED / ARTIFACT_NOT_VERIFIED` |
| digest 缺失、非法或不一致 | `BLOCKED / ARTIFACT_DIGEST_MISSING/INVALID/MISMATCH` |
| `LEGACY_UNBOUND` / `LEGACY_PUBLISH_ONLY` | `BLOCKED / RELEASE_BINDING_REQUIRED` |
| manifest schema 或 provenance 不一致 | `BLOCKED / MANIFEST_SCHEMA_UNSUPPORTED` 或 `MANIFEST_PROVENANCE_MISMATCH` |
| validation missing/no evidence/stale/non-approved | `BLOCKED / VALIDATION_*` |
| window/boundary missing 或非法 | `BLOCKED / SHADOW_WINDOW_*` 或 `AUTHORIZATION_BOUNDARY_*` |
| side-effect policy missing | `BLOCKED / SIDE_EFFECT_POLICY_MISSING` |
| 任一 no-side-effect flag=false | `BLOCKED /` 对应 `NO_*_REQUIRED` |
| trace 缺失、过长或含 control char | `BLOCKED / TRACE_REFERENCE_*` |

任何 missing、UNKNOWN、legacy 或非法输入都不能自动变为 `ELIGIBLE`。

## 9. ShadowRunCreationPlan contract

Immutable plan 包含：

- release/publish identity、artifact digest、strategy version、dataset、evaluation。
- window 与 `dataset:<datasetId>` input reference。
- authorization boundary 与完整六项 no-side-effect policy。
- manifest schema、`publish:<publishRecordId>` provenance reference、trace reference。
- deterministic `shadowRunIdempotencyKey`。

Plan 不包含：

- `ShadowRun` id/status 或已创建/已启动事实。
- exchange order id、真实账户余额、credential、private endpoint payload。
- LIVE authorization、trade approval、order command。

## 10. Determinism proof

- `ReleaseToShadowAdmissionService` 无 `Clock`、random、repository、file 或 network 依赖。
- reason code 使用固定校验顺序与 `LinkedHashSet` 去重，因此同输入顺序稳定。
- 同一 immutable input 重复调用得到等价 decision 与 creation plan。
- idempotency material 使用固定 schema marker、固定字段顺序、UTF-8 与四字节长度前缀，最后 SHA-256 输出 64 位小写 hex。
- trace 不参与 idempotency material；同一业务输入仅 trace 不同，key 保持一致，而 plan 仍保留各自 trace。

## 11. No-side-effect proof

### 11.1 Dependency proof

`ReleaseToShadowAdmissionService` 使用零参数构造器且无 instance field。production path 不依赖：

- `ShadowRunRepository` / JDBC / JPA / transaction。
- runner / scheduler / event / snapshot / report writer。
- `OrderCommandService` / `PlaceOrderCommand` / trading/risk execution chain。
- exchange adapter / HTTP client / credential service / private endpoint。

### 11.2 Behavior proof

- 唯一 public method 是纯 `admit(request)`。
- eligible 只 `new ShadowRunCreationPlan(...)`；blocked 只 `new ReleaseToShadowAdmissionDecision(...)`。
- decision constructor 拒绝任何 runtime/trading side-effect flag=true。
- unit reflection contract 验证 service 无 repository/order/adapter/credential/runner/scheduler field，decision/plan 无敏感或交易字段。
- source audit 中 forbidden-pattern 命中均为注释、no-credential boolean 或 `MessageDigest.update`；DB writes=0、external IO=0、trading writes=0。

## 12. Production integration

- Strategy Release：调用方先经 `StrategyReleaseProductionService.verify(...)` 得到 immutable `StrategyRelease`；admission 不读取 repository 或重新验证 artifact files。
- Artifact verifier：admission 只消费 production `StrategyArtifactVerificationResult`，同时交叉校验 release/verification/manifest digest。
- `RELEASE_BOUND`：统一调用 `ShadowRunReleaseBindingMode.derive(publishRecordId, artifactDigest)`；不复制 enum、regex 或数据库 binding 规则。

## 13. Files

### 13.1 Files inspected

- `AGENTS.md`、root/current README、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`。
- GateX-1 Strategy Release aggregate/service/verifier/manifest。
- GateX-2 `ShadowRun` provenance 与 `ShadowRunReleaseBindingMode`。
- `strategyrelease/preparation/**` admission/binding/creation-plan prototypes 与 tests。
- Shadow authorization/no-side-effect production model、existing ArchUnit suites、CI legacy fixture。

### 13.2 Files created

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ReleaseToShadowAdmissionRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ReleaseToShadowAdmissionDecision.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunCreationPlan.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ReleaseToShadowAdmissionService.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ReleaseToShadowAdmissionServiceTest.java`
- 本 evidence 文件。

### 13.3 Files changed

- `README.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## 14. Validation

| Validation | Result |
| --- | --- |
| focused `ReleaseToShadowAdmissionServiceTest` | 11 tests，0 failures / 0 errors / 0 skipped |
| `mvn -f backend/pom.xml -pl nq-core -am test` | 450 tests，0 failures / 0 errors / 4 skipped；BUILD SUCCESS |
| initial full backend without PostgreSQL | FAIL：3 个既有 local Spring context tests connection refused |
| disposable PostgreSQL first full backend | FAIL：仅缺 CI legacy account fixture |
| disposable PostgreSQL + exact CI fixture rerun | 23 modules；1324 tests，0 failures / 0 errors / 21 skipped；BUILD SUCCESS |
| explicit `PackageBoundaryArchTest,ModuleBoundaryArchTest` | BUILD SUCCESS；canonical reports 各 6 tests，0 failures / 0 errors / 0 skipped |
| IDE reformat | PASS |
| IDE problems | 0 errors；4 个非阻断 warning/weak warning |
| authority checker | `AUTHORITY_CHECK errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT` |
| current/root Markdown link checker | 首次遗漏 mandatory `Roots` 参数而未执行；显式传入 `README.md` 与 `docs/current` 后重跑：199 links、0 errors、1 个既有 warning、PASS |
| `git diff --check` | PASS；tracked diff whitespace errors=0，只有 Git LF→CRLF working-copy warning |

环境：Windows、Java 21、Maven、本机 PostgreSQL 17 disposable cluster。Windows PostgreSQL service 因无控制权限未启动；Docker engine 未运行，未使用 container。cluster 仅监听 `127.0.0.1:5432`，完成测试后已停止并删除；本机 service 保持 `Stopped/Manual`。

Known warnings：既有 SLF4J no-provider、Mockito dynamic-agent/JDK warning、Maven/IDE 非阻断 warning 与 existing/opt-in skips。未运行 frontend、Python、migration 专项或远端 CI，因为对应范围未修改，远端 CI 需要 commit/push 后 exact-head 运行。

## 15. Boundary and impact

- Persistence impact：0；无 repository、SQL、schema、migration 或数据库写入代码。
- API impact：0；无 endpoint/DTO/controller。
- Frontend impact：0。
- Trading/LIVE impact：0；`tradingAuthorized=false`、`orderSubmitted=false`，LIVE 保持 `DISABLED`。
- Credential/private endpoint impact：0；请求/结果/plan 不携带 credential/private payload，service 不依赖相关组件。
- Shadow runtime impact：0；`shadowRunCreated=false`、`shadowRunStarted=false`，无 runner/scheduler。
- NQ/DH boundary：NQ-only；未修改或声明 DH/Integration runtime。

## 16. Findings P0–P3

### P0

- 无。

### P1

- 无。

### P2

- GateX-4 或之后的实际创建侧必须把 `ShadowRunCreationPlan` 作为不可变 input，重新保持 release-bound provenance、idempotency 与六项 no-side-effect facts；不得把 `ELIGIBLE` 解释为交易或 LIVE 授权。本轮未实现该创建边界，因此不是 GateX-3 blocker。

### P3

- 既有 Maven SLF4J/Mockito/JDK warnings、existing/opt-in skipped tests 与 IDE data-flow/duplicate weak warnings；未由本轮引入功能缺陷，不阻断 commit。

## 17. Authority after and final decision

收尾 checker 已通过，authority 已同步为：

```text
work_batch=GateX-3
work_batch_status=IMPLEMENTED|SELF_REVIEWED
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-3-COMMIT-AND-PUSH
```

- Review requirement：未出现 migration、DB write、交易主链、credential/private endpoint、P0/P1 或非平凡失败修复；按任务无需独立 review。
- Rollback：在 commit 前从精确 staged allowlist 移除并删除 5 个新增 Java 文件与本 evidence，恢复 6 个 current-control/ledger 文件的本轮 diff；不得用 `git reset --hard` 或覆盖其他改动。
- Commit recommendation：`feat(shadow): productionize release-to-shadow admission`。
- Next action：唯一 `NQ-GATEX-3-COMMIT-AND-PUSH`。
- Final decision：`IMPLEMENTED / SELF_REVIEWED / RELEASE_TO_SHADOW_ADMISSION_PRODUCTIONIZED / NO_SIDE_EFFECTS_VERIFIED / BACKEND_REGRESSION_GREEN / READY_TO_COMMIT`。
