# GateX-4 Minimal API/UI Closure Implementation（attempt-02）

## 1. Task classification 与结论

- 任务：`NQ-GATEX-4-MINIMAL-API-UI-CLOSURE-IMPLEMENTATION`。
- 分类：NQ-only、L 级 read-only backend API / release admission query orchestration / minimal frontend closure / self-review / regression。
- 结论：`IMPLEMENTED / GATEX_4_MINIMAL_API_UI_CLOSURE_COMPLETE / READ_ONLY_ADMISSION_PREVIEW_VERIFIED / READY_TO_COMMIT`。
- machine authority：普通 implementation 按 governance contract 使用 `IMPLEMENTED|SELF_REVIEWED`；该 contract 不包含用户示例 `IMPLEMENTED|READY_TO_COMMIT`，唯一下一动作仍为合法 `COMMIT_AND_PUSH` 类型。

## 2. Starting baseline 与 GateX-4 resume

- branch=`dev`；进入时 worktree clean、staged empty。
- starting `HEAD == origin/dev == b4e5406fbb9de5432f79f9ef8ef76c95002e0e56`。
- GateX-4C implementation/acceptance commit=`b4e5406fbb9de5432f79f9ef8ef76c95002e0e56`。
- exact-head GitHub Actions run=`31409595743 / completed / success / bad jobs=0`。
- GateX-4C 收口：`accepted_batch=GateX-4C / ACCEPTED|CI_GREEN`；随后 checker 接受 `GateX-4 / NOT_STARTED / NONE / NOT_RUN` 与 implementation action。
- LIVE=`DISABLED`，kill switch=`ENGAGED`，Shadow trading=`NOT_ENABLED`。

## 3. Admission fact-source audit

问题：仅凭 `publishRecordId`，服务端是否能够安全组装 admission preview 所需全部 production facts？

结论：是，GateX-4B/4C remediation 后没有 mandatory fact 缺口，也没有客户端 asserted fallback。

| Fact | Server-owned source | 处理 |
| --- | --- | --- |
| Release/provenance | `StrategyReleaseProductionService` + `StrategyReleaseProvenanceRepository` | exact publish、strategy version、dataset、evaluation、publish status。 |
| Artifact locator/root/manifest | V37 persistent opaque locator pair + `StrategyReleaseArtifactBindingResolver` + server typed trusted root | 客户端不提交 path/root/key/manifest。 |
| Artifact verification | `TrustedRootStrategyArtifactVerifier` | 复用既有 strict manifest/provenance/digest/file verification，不重算第二套规则。 |
| Validation | exact-publish SELECT projection + `StrategyValidationOverviewQueryService.evaluateDecision(...)` | 复用 canonical decision branches；缺失、stale、rejected、blocked 均 fail-closed。 |
| Window | publish 所属 immutable `backtest_runs.config_snapshot_json.startTime/endTime` | 缺失或非法时间进入 `SHADOW_WINDOW_*` blocker。 |
| Paper/Shadow/consistency | exact publish 的最新 bounded local facts | 仅 SELECT；同一 publish anchor；每个子查询 `LIMIT 1`。 |
| Authorization/no-side-effect | 既有 `DIAGNOSTIC_ONLY` 与六项 no-side-effect schema/domain invariant | 固定禁止 order、credential、private endpoint、ledger、account、external private IO。 |
| Trace | `TraceIdContext` | 只进入内存 creation-plan preview，不持久化、不执行。 |

Missing facts：无。Repository 缺行、解析异常或 runtime exception 返回 missing snapshot；canonical admission 最终为 `BLOCKED`，不会默认 `true` 或 `ELIGIBLE`。

## 4. Backend query orchestration 与 API

- `StrategyReleaseAdmissionPreviewService.preview(...)`：`publishRecordId → production release verify → exact-publish facts → canonical validation evaluator → GateX-3 admission → safe preview`。
- `@Transactional(readOnly=true)`；不注入 Shadow repository/runner/scheduler/trading/risk/ledger/credential/private client。
- GateX-3 的 `ShadowRunCreationPlan` 仅在 `ReleaseToShadowAdmissionDecision` 内存结果中形成；preview DTO 不返回也不执行它。
- Route：`GET /api/strategy-releases/{publishRecordId}/shadow-admission-preview`；Controller 没有 POST/PATCH/PUT/DELETE。
- HTTP：publish missing=`404`；legacy/unbound、artifact rejected、validation/policy blocked=`200 + BLOCKED`；全部通过=`200 + ELIGIBLE`。
- DTO：只含 publish/release/strategy/dataset/evaluation provenance、binding/release/artifact/validation/admission status、safe reason codes 与 digest。
- 响应不含 trusted root、absolute path、artifact/manifest storage key、raw manifest/content、creation plan、内部异常、credential/token/private endpoint。
- RBAC：复用全局 authenticated GET convention；unauthenticated=`401`，`VIEWER` 可读。读取权限不表示交易授权。

## 5. Fail-closed 与 side-effect audit

- Artifact finding 直接复用现有 `StrategyArtifactVerificationResult.FindingCode`；admission reason 直接复用 `ReleaseToShadowAdmissionDecision.ReasonCode`，未建立第二套 backend taxonomy。
- 覆盖 legacy unbound、root not configured、unsafe location/manifest/identity/verification rejection、validation not approved、release binding、window、authorization 与六项 side-effect policy blocker。
- 未知或事实加载异常不会暴露异常文本；通过 missing facts 导向 canonical BLOCKED reasons。
- JDBC 只有参数化 SELECT。Shadow 查询先由 selected publish 的 strategy anchor 利用既有 index 缩小范围，再校验相同 publish；无循环 DB/API、N+1、无界 collection 或 write SQL。
- DB writes、Shadow create/start、runner/scheduler、order/risk/ledger/account mutation、credential/private API、外部网络调用均为 0。

## 6. Frontend minimal closure

- 集成点：既有 `StrategyValidationWorkspace`，没有新增 route 或巨型 page/workspace。
- Query key：`strategyReleaseQueryKeys.admissionPreview(publishRecordId)`，按 publish ID 隔离缓存，无 raw ad-hoc key。
- Client/hook：唯一 encoded publish ID GET；TanStack Query 仅在 publish ID 存在时启用，失败不自动重试为成功。
- UI：canonical `StatusTag` + Ant Design；用户可见中文，无 Gate/authority engineering token。
- 状态：未输入、loading、404、legacy unbound、verification rejected、admission blocked + reasons、eligible、request failure。
- 交互：只有刷新、复制/查看 provenance；无 Create/Start/Execute/Order/Rebind/Upload/Storage Key/Directory 动作。
- Eligible 文案明确：仅允许形成内存创建计划，`ELIGIBLE != ShadowRunCreated`，`ELIGIBLE != TradingAuthorized`。
- 未改变 CN_STOCK 红涨绿跌语义；系统状态继续使用 canonical status tone。

## 7. Validation 与 retry evidence

| Command / check | Final result |
| --- | --- |
| Backend focused four suites | PASS；10 core + 1 infra + 3 API + 2 security = 16 tests，0 failures/errors。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-research,nq-infra,nq-app -am test` | PASS；23 modules `SUCCESS`，`BUILD SUCCESS`。 |
| `mvn -f backend/pom.xml test`（最终代码） | PASS；23 modules `SUCCESS`；`nq-app` 252 tests、0 failures/errors、16 skipped；`BUILD SUCCESS`。 |
| `ModuleBoundaryArchTest` / `PackageBoundaryArchTest` | PASS；6/6 + 6/6。 |
| `npm run build` | PASS；3910 modules；仅既有 chunk-size warning。 |
| targeted Playwright | PASS；最终完整 9/9。 |
| current authority checker | PASS；`errors=0 / CURRENT_AUTHORITY_CONSISTENT`。 |
| current doc links | PASS；210 checked、0 errors、1 个既有 `GATEJ_TEST_PLAN.md` warning。 |

Retry / RCA：

1. 首个 focused Maven 命令仅因 PowerShell 逗号参数未引用而 parse failure；修正命令后进入测试。
2. nq-core 新 test 初版使用 Mockito，但该模块无 Mockito；改为 in-memory fakes，没有新增依赖。
3. Controller test 混用 raw/matcher 导致 Mockito matcher error；统一 matcher 后通过。
4. loading E2E 首轮 mock 延迟不足、随后断言 Ant Button `disabled` 与真实 loading DOM 不符；延长受控 mock 并断言 `.ant-btn-loading` 后单测通过。
5. 最终 9 条完整重跑首次在 webServer 启动前因 Windows reserved port 覆盖固定 `51888` 返回 `EACCES`；没有终止未知进程，使用项目已有 `E2E_EXTERNAL_DEV_SERVER` contract 和未保留本地端口 `52340` 后 9/9，临时 Vite 随即关闭。
6. 自审发现 Shadow exact-publish CTE 可能因缺少 publish-only index 扫表；改为 selected publish + indexed strategy anchor + exact publish join，repository regression 与最终 full backend 重新通过。
7. doc-links 首次通过嵌套 `powershell` 调用时丢失 `-Roots` array 边界，脚本未开始扫描；改用当前 PowerShell 显式 array invocation 后完成 210-link 扫描并通过。

## 8. Findings、影响与边界

- P0：0。
- P1：0。
- P2：0；本轮发现的 query anchor 性能点已最小修复并回归。
- P3：1；既有 Maven settings tag warning、Mockito future agent warning、Ant Design React 19 compatibility warning、Vite chunk warning，均非本轮引入，不阻断。
- Migration/V37 impact：0；未新增或修改 migration。
- Artifact producer/locator API impact：0；producer 仍为 `PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED`。
- Shadow impact：只读 preview；create/start/persist/runner/scheduler=0。
- Trading/LIVE impact：order/risk/ledger/account write、private exchange API、credential、LIVE=0；LIVE 保持 `DISABLED`。
- AI/DH/Integration impact：0；NQ-only。

## 9. Historical attempt-01、authority、rollback

- attempt-01 是 `BLOCKED / SAFE_ARTIFACT_ROOT_BINDING_MISSING` 的历史证据，未覆盖或改写。
- attempt-02 是 GateX-4B persistent locator 与 GateX-4C server-controlled trust boundary accepted 后的正式 retry；它关闭 attempt-01 的 P1，不声称 artifact producer 已接线。

```text
accepted_batch=GateX-4C
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=b4e5406fbb9de5432f79f9ef8ef76c95002e0e56
accepted_batch_acceptance_head=b4e5406fbb9de5432f79f9ef8ef76c95002e0e56
accepted_batch_ci_run=31409595743
work_batch=GateX-4
work_batch_status=IMPLEMENTED|SELF_REVIEWED
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4-COMMIT-AND-PUSH
```

- Commit recommendation：`feat(strategy): expose release-to-shadow admission preview`。
- Rollback：仅反向恢复本 attempt-02 列出的 code/test/current-doc diff；无 migration、数据或外部状态需要回滚；禁止 `git reset --hard`。
- Next action：`NQ-GATEX-4-COMMIT-AND-PUSH`。
- Final decision：`IMPLEMENTED / GATEX_4_MINIMAL_API_UI_CLOSURE_COMPLETE / READ_ONLY_ADMISSION_PREVIEW_VERIFIED / READY_TO_COMMIT`。
