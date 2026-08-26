# NQ-GATEY-6F server read-only runtime composition and deployment contract implementation attempt-01

## 1. 结论

`PASS / GATEY_6F_SERVER_READONLY_RUNTIME_COMPOSITION_IMPLEMENTED / TRUSTED_OBSERVATION_RUNTIME_BOUND / REAL_MUTATION_RUNTIME_UNBOUND / STARTUP_CREDENTIAL_READ_0 / STARTUP_OKX_CALL_0 / IMMUTABLE_RELEASE_CONTRACT_READY / MIGRATION_CONTRACT_READY / ROLLBACK_HARD_GATE_READY / NO_SERVER_MUTATION / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / KILL_ENGAGED / PENDING_INDEPENDENT_SECURITY_REVIEW`（通过 / GateY-6F server 只读 runtime composition 已实现 / 真实 mutation runtime 未绑定 / 不可变 release、migration、rollback 合同已就绪 / 未发生服务器变更 / 等待独立安全审查）。

本结论只表示本地 implementation 与合同测试完成。当前 worktree 未提交，因此 exact-commit builder 正确返回 `BLOCKED / EXACT_COMMIT_WORKTREE_NOT_CLEAN`；未生成 deployable production release，未执行服务器部署、migration、activation、health probe 或真实 prerequisite collection。

## 2. Task classification 与 baseline

- classification：`CODE_CHANGE / SECURITY_SENSITIVE_RUNTIME_COMPOSITION / DEPLOYMENT_CONTRACT / GATEY_6F_BLOCKER_REMEDIATION / NO_SERVER_MUTATION`；NQ-only、L 级。
- branch/worktree：开始时 `dev`、clean、staged=`0`。
- exact baseline：`HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1`。
- CI：GitHub Actions run `32041844923`，attempt=`2`、`completed / success`、`headSha=2605a20e...`。
- authority：`GateY-6F=NOT_STARTED`、`LIVE=DISABLED`、kill switch=`ENGAGED`、first real order/micro-live 未授权、soak 未启动。

## 3. 审计范围与 existing runtime wiring

已审计 `NexusQuantApplication` 的全包 component scan、`PilotScopeControlPlaneController/Service`、default unavailable authority、OKX trusted authority、JIT credential executor、typed real transport、历史 trading adapter、`SpotExecutionProviderPort` implementation、worker launcher、startup recovery、全部 `@Scheduled` 入口、GateW release builder/verifier/installer primitive、GateY deployment boundary、profile 配置与 V1～V41 migration inventory。

现状结论：

1. `UnavailablePilotPrerequisiteObservationAuthority` 是默认 `@Component`；`OkxPilotPrerequisiteObservationAuthority` 无 Spring annotation，因此默认 runtime 无法采集 trusted OKX prerequisite。
2. IDEA call hierarchy 证明业务入口为 `PilotScopeControlPlaneController.materialize → PilotScopeControlPlaneService.materialize → PilotPrerequisiteObservationAuthority.resolveTrustedObservationSet`；该 Service 无 `ExecutionIntent/provider/worker` 依赖。
3. `JdbcOkxPrivateCredentialExecutor.CredentialSession` 的 typed surface 包含 PLACE/CANCEL，但 `OkxCredentialScopedSpotProviderTransport` 与 `OkxSpotProviderAdapter` 均无 Spring annotation；原始 qualification profile 若仅新增 authority，仍会因历史排除式 profile 条件装配 `TradingAdapter` 与 startup recovery，故必须显式排除。
4. `IsolatedFakeExecutionWorkerLauncher` 是 one-shot `main`，不是 Spring bean；真实 `SpotExecutionProviderPort` production bean 原为 0。

## 4. Chosen minimal composition 与 Spring bean graph

新增显式、default-off profile `gatey-readonly-qualification`，必须同时满足 feature flag=true、LIVE/real runtime=false、PLACE/CANCEL/transfer/withdraw=false、loopback bind、Java 21、`releaseId == sourceCommit == exact 40-hex commit`。

```text
PilotScopeControlPlaneController
  -> PilotScopeControlPlaneService
    -> @Primary GateYReadonlyQualificationObservationAuthority
      -> durable KillSwitchService.snapshot() == ENGAGED
        -> OkxPilotPrerequisiteObservationAuthority
          -> JdbcOkxPrivateCredentialExecutor exact-reference JIT callback
            -> JdkOkxPrivateReadTransport.observePrerequisites()
```

- default context：trusted real authority=`0`，只有 unavailable authority。
- qualification context：guarded trusted authority=`1`；unavailable authority保留但非 primary。
- `SpotExecutionProviderPort` bean=`0`；`TradingAdapter` bean=`0`；worker deployment bean=`0`。
- `OkxRecoveryService`、OKX/Binance reconcile、ledger reconcile、paper matching、validation evidence scheduler bean=`0`；即使外部把 validation scheduler flag 覆盖为true，profile hard exclusion仍保持0。
- 没有新增 Controller、API、debug/raw provider 入口、migration 或 Maven dependency。

## 5. Mutation reachability 与 credential lifecycle

- 唯一真实 provider 调用由既有 `materialize()` 业务入口触发，先校验 authenticated actor、stored authority/risk、exact scope hash，再进入 guarded observation authority。
- durable kill 不是启动期读取；每次 collection 调用期先读 snapshot，非 `ENGAGED`（包括 UNKNOWN/DB failure）在 credential JIT 前返回固定脱敏错误。
- executor 只在 callback 内 exact-reference SELECT/decrypt，credential context `try-with-resources` 关闭，session 同线程/同 callback 有效，char arrays finally 清理；本变更不新增第二 credential SoR。
- Spring test 使用实际 `JdbcOkxPrivateCredentialExecutor` 与 `JdkOkxPrivateReadTransport`，计数 DataSource 和 kill repository 在 context refresh 后均为 0；startup OKX GET/POST=0。
- 没有 `ExecutionIntent → SpotExecutionProviderPort → PLACE/CANCEL` Spring binding；历史 `TradingAdapter`、private WS、startup recovery 与 scheduler 全部被 qualification profile 排除。

## 6. Immutable release contract

新增独立 GateY contract，不修改 GateW frozen files：

- builder 只接受 clean committed exact HEAD；dirty worktree hard-block。
- builder 在 operator workstation 以 source commit UTC seconds 作为 `project.build.outputTimestamp`，执行本地 Maven clean/repackage；server build 永久禁止。
- fat JAR 必须包含 `NexusQuantApplication`、qualification profile，并逐项验证 JAR 内 Flyway migration bytes 与源码 inventory SHA-256 相同；不能用 stale/arbitrary JAR 冒充 exact source。
- canonical manifest 绑定 source commit/timestamp policy、Java 21、application artifact identity、profile identity、V1～target migration inventory/hash、artifact path/size/SHA-256/POSIX mode/role、root owner/service user、immutable releases root 与 atomic current pointer。
- 自有 canonical serializer 使用 ordinal key/path ordering、InvariantCulture 与 UTC seconds；PowerShell 5.1/7 对相同 fixture 产出完全相同 manifest SHA-256。
- verifier拒绝 non-canonical manifest、缺失/额外 artifact、size/hash tamper 与路径越界。

本轮未运行正常 builder，因为 implementation worktree 必然 dirty；这证明未把 uncommitted build 伪装为 deployable release。

## 7. Migration 与 rollback strategy

- actual repository inventory：V1～V41 连续、count=`41`、target=`V41`、inventory SHA-256=`2b6847457a91423f0cbbaed49c3e018f28846a5b94615a169fc5bee67802488b`。
- deployment contract 必须消费服务器实际 `flyway_schema_history` 的 applied versions，验证其为 manifest inventory 的连续前缀，再派生 pending migrations；不硬编码 server current version。
- 顺序固定为 release verify → Flyway history inspect → pending derivation → backup → backup verification → rollback contract → forward-only migrate → target verify → atomic activation → health verify。
- `CODE_ROLLBACK` 与 `DATABASE_RECOVERY` 分离。只有已证明 previous release 可读 target schema 时允许直接 code rollback；否则必须同时具备 verified backup 与 verified restore procedure，缺任一即 `BLOCKED / ROLLBACK_CONTRACT_UNPROVEN`。
- 本轮 production backup/restore/migration=`0`；没有把 Flyway production migration描述为简单可逆。

## 8. Activation 与 health contract

- immutable release path：`/opt/nexus-quant/releases/<release-id>`；current pointer：`/opt/nexus-quant/current`；禁止 server build、覆盖旧 release 或直接写 current 内容。
- pre-deployment script只验证本地 release与operator/server evidence JSON并输出 decision；不含 SSH、systemctl、symlink、psql、Flyway执行或网络调用。
- health AND gate：process、exact release、Java 21、DB、Flyway target、Spring context、exact profile、127.0.0.1、LIVE disabled、kill engaged、历史 GateW units inactive，以及 provider/worker/startup credential/GET/POST/intent/receipt/exchange mutation全部为0。
- deployment health不得调用真实 OKX；真实 read-only prerequisite collection仍属于后续 exact pilot attempt。

## 9. Tests 与 validation

| Command / check | Result |
| --- | --- |
| focused Maven（首次） | command quoting error；Maven exit=`1`、未进入编译；`-Dsurefire.failIfNoSpecifiedTests=false` 被 PowerShell 误解析为 lifecycle phase |
| focused Maven（修正） | 23 modules `BUILD SUCCESS`；新增/相关 `2 + 8 = 10` tests，failures/errors/skipped=`0/0/0` |
| final `mvn -f backend/pom.xml test` | 23 modules、319 reports、1546 tests、failures/errors/skipped=`0/0/48`、48.858s、`BUILD SUCCESS` |
| GateY contract regression / PowerShell 5.1 | 14/14 PASS；含 synthetic fat-JAR V1～V41 byte binding/tamper rejection；manifest SHA-256=`c8b986ee55deadca4b13671871dd545e87052b70eb82e88c827d8b0c0aad8c01` |
| GateY contract regression / PowerShell 7 | 14/14 PASS；与 PS5.1 manifest bytes/hash 完全一致 |
| GateW release reproducibility regression | 34/34 PASS；GateW manifest/bundle、locale/timezone、tamper、JAR path/size/CRC 与 dirty exact commit regression全绿 |
| dirty exact-commit GateY builder | expected block：`BLOCKED / EXACT_COMMIT_WORKTREE_NOT_CLEAN`；deployable release未生成 |
| actual migration inventory | V1～V41，41 files，连续；target=`V41` |
| IDEA problems / ArchUnit | 新增文件 errors=`0`；全量 ArchUnit PASS。`BinanceRestReconcileService` 有一个不在本轮 diff 的既有 Javadoc param inspection，Maven compilation PASS |
| credential/secret/side-effect scan | 新增范围未发现硬编码 credential；deployment contract无 SSH/systemctl/symlink/HTTP/psql/Flyway执行 |
| authority / doc links / diff | authority=`errors=0 / PASS`；links=`375 checked / 14 historical warnings / 0 errors`；tracked `git diff --check`通过，untracked检查仅发现本 evidence 尾部空行并已修正；LF→CRLF 为既有非阻断提示 |

48 个 skipped 为仓库既有 conditional/manual integration；未将 skipped 记为 passed。全量 Maven 中 local integration 访问本机开发 PostgreSQL 并确认 schema V41，属于项目既有测试行为；未访问 production server/DB。

## 10. Findings

### P0

- 无。

### P1

- 无。自审发现“caller-supplied arbitrary JAR 可冒充 exact source”的草案 P1，已在本轮关闭：builder 改为 exact-source local build，并验证 JAR 内 profile/application/migration bytes。

### P2

- deployable exact release 尚未构建；必须在 commit + independent security review + exact-head CI 后单独执行。
- server Flyway history、backup integrity、restore procedure、old release + V41 compatibility 与 activation health 尚未在服务器验证；全部由后续 deployment hard gate 阻断，不影响本轮 contract implementation verdict。

### P3

- IDEA 对 `BinanceRestReconcileService` 报一个既有 Javadoc `@param eventStoreAppender` 无法解析；相关行不在本轮 diff且编译通过，按范围控制记录、不修复。

## 11. Changed files

- runtime：`GateYReadonlyQualificationConfiguration.java`、`GateYReadonlyQualificationRuntimeIdentity.java`、`GateYReadonlyQualificationObservationAuthority.java`、`application-gatey-readonly-qualification.yml`。
- isolation：`ExchangeAdapterConfiguration.java`、5 个 scheduler/recovery class 与 validation scheduler configuration 的 qualification profile exclusion。
- tests：两个新增 qualification tests、`ExchangeAdapterConfigurationReadinessTest.java` regression。
- release/deployment：`gatey-readonly-release-contract.psm1`、`build-gatey-readonly-release.ps1`、`invoke-gatey-readonly-deployment-contract.ps1`、13-case regression。
- docs：本 evidence、`TESTING.md`、`WORKLOG.md`、GateY evidence index。

## 12. Authority、side-effect counters 与 next action

- authority：`GateY-6F=NOT_STARTED` 保持不变；exact PilotScope/approval/preflight 未物化/未执行。
- server SSH/write/deployment/migration/symlink/systemd=`0/0/0/0/0/0`。
- credential metadata/material read=`0/0`；OKX GET/POST=`0/0`。
- PLACE/CANCEL/transfer/withdraw=`0/0/0/0`；ExecutionIntent/ExecutionReceipt delta=`0/0`。
- LIVE enable=`0`；kill disengage=`0`；LIVE 保持 `DISABLED`，kill 保持 `ENGAGED`。
- Git stage/commit/push/tag=`0/0/0/0`。
- next：`NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-SECURITY-REVIEW`。
