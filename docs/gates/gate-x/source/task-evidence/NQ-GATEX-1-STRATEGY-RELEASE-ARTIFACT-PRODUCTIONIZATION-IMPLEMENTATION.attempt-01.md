# NQ-GATEX-1 Strategy Release / Artifact productionization implementation — attempt-01

## 基线、authority 与范围

- Task classification：NQ-only、L 级 backend productionization / trusted-root security hardening / regression / self-review。
- Starting HEAD 与 `origin/dev`：`83e6161ed34da9a71f510680ad46b4584502cd82`；任务开始时 worktree clean、staged empty。
- Exact-head CI baseline：`NQ CI Baseline` run `31352595870`，`completed / success`，`headSha` 精确匹配 starting HEAD。
- Authority before：GateX=`IN_PROGRESS|NOT_FROZEN`；GateX-1=`NOT_STARTED`；next action=`NQ-GATEX-1-STRATEGY-RELEASE-ARTIFACT-PRODUCTIONIZATION-IMPLEMENTATION`；LIVE=`DISABLED`。
- 范围只包含 Strategy Release aggregate、manifest production contract、trusted-root verifier、只读 provenance service/JDBC adapter、回归测试和最小 current evidence sync。
- 明确不涉及 migration/schema/persistence write、API/frontend/Python/scheduler、Shadow admission/Shadow Run、credential/private endpoint、LIVE、订单/资金/风控写侧、AI 或 DH runtime。

## Prototype inventory 与 production facts

已逐项审查 `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/**` 的 15 个 prototype：

1. `StrategyReleaseAggregatePrototype.java`
2. `StrategyReleaseAggregateIdempotencyPrototypeTest.java`
3. `StrategyReleaseLifecyclePrototypeTest.java`
4. `StrategyReleaseManifestPrototypeTest.java`
5. `StrategyReleaseRepositoryPrototype.java`
6. `StrategyReleaseServicePrototype.java`
7. `TrustedRootArtifactVerifierPrototype.java`
8. `TrustedRootArtifactVerifierPrototypeTest.java`
9. `SensitiveFieldPolicyPrototypeTest.java`
10. `ShadowRunReleaseBindingPrototype.java`
11. `ShadowRunReleaseBindingPrototypeTest.java`
12. `ShadowRunCreationPlanPrototype.java`
13. `ShadowRunAdmissionPrototype.java`
14. `StrategyReleaseToShadowAdmissionServicePrototype.java`
15. `StrategyReleaseToShadowAdmissionPrototypeTest.java`

同时审查 `gatex/strategy-release-manifest.schema.json` 与 `gatex/strategy-release-manifest.golden.json`。production facts 来自 `backtest_publish_records`、其绑定的 backtest run、不可变 `dataset_snapshot_json.datasetId`、evaluation report、strategy version 和 dataset；当前 schema 已能通过一次 publish-record 主键有界 SELECT 形成验证事实，不需要新增 migration。

| Prototype 能力 | Production 处置 | 原因 |
| --- | --- | --- |
| release identity / aggregate invariants | `PROMOTED`（已提升） | 以既有 publish record 为唯一 canonical anchor |
| manifest core provenance + artifact descriptors | `PROMOTED` | 字段均有稳定 production 来源或由 artifact generator 提供 |
| canonical artifact-set digest | `PROMOTED` | 冻结排序、字段顺序、分隔符和 UTF-8/SHA-256 语义 |
| trusted-root verification / sensitive policy | `PROMOTED_AND_HARDENED`（已提升并加固） | 增加 path、symlink/reparse、资源、closed-set、TOCTOU mitigation 与安全 reason code |
| prototype params/data window/risk budget/signal summary | `NOT_PROMOTED`（未提升） | 当前没有稳定 production source，不制造伪 mandatory facts |
| prototype lifecycle/repository/idempotent persistence | `STILL_PROTOTYPE`（仍为原型） | GateX-1 不新增 persistence 或 lifecycle write model |
| Shadow binding/creation/admission | `STILL_PROTOTYPE` | 本批次明确禁止进入 Shadow admission/run |
| test schema/golden fixtures | `HISTORICAL_TEST_EVIDENCE`（历史测试证据） | 保留验证价值，不删除、不当作 production runtime source |

## Strategy Release identity、model 与状态

- Canonical identity：`releaseAnchorId == publishRecordId == backtest_publish_records.publish_record_id`。
- Model：`releaseAnchorId`、`publishRecordId`、`strategyVersionId`、`datasetId`、`evaluationId`、`artifactManifest`、`artifactDigest`、`releaseStatus`、`verificationResult`、`createdAt`、`publishedAt`。
- `UNVERIFIED`：只表示尚未形成接受结果；不授予 Shadow/LIVE/交易权限。
- `VERIFIED`：provenance、manifest 与 trusted-root artifact closed set 全部通过。
- `REJECTED`：任一 production fact、manifest 或 artifact verification fail closed。
- 状态不表达发布部署、Shadow admission、Paper Run、LIVE 或执行 readiness。

## Manifest production contract 与 digest

Schema：`strategy-release-manifest.v1`。

必填字段：

- `strategyVersionId`
- `datasetId`
- `evaluationId`
- `artifactFiles[]`
- `artifactDigest`
- `generatedAt`
- `generatorVersion`

每个 artifact descriptor 固定为 `logicalName`、`relativePath`、`sha256`、`sizeBytes`、`mediaType`。canonical serialization 按 `logicalName`、`relativePath` 升序；字段顺序固定为上述五项；字段分隔符为 U+001F，record 分隔符为 LF；UTF-8 bytes 经 SHA-256 生成 lowercase `artifactDigest`。digest 绑定完整声明集，不代表单文件内容校验的替代品；verifier 仍逐文件流式计算并比较 `sha256` 和 `sizeBytes`。

## Trusted-root verification

- Path normalization：只接受由 `[A-Za-z0-9._-]+` segment 组成的正斜杠相对路径；拒绝 absolute、drive prefix、backslash、空段、`.`、`..` 和 root escape。
- Symlink/reparse protection：trusted root 和逐级 path 使用 real path/`NOFOLLOW_LINKS`；拒绝 symbolic link、非 regular file、`isOther`/junction-like entry；验证 real target 仍位于 real trusted root 内。Windows 测试实际创建可用 symlink 并验证 escape 被拒绝，无 skip。
- Closed set：验证前后递归捕获目录快照，实际 regular-file set 必须与 manifest 声明 set 精确相等；missing/extra 均拒绝。
- TOCTOU：逐文件读取前后比较 fileKey/size/mtime，验证前后比较目录 type/fileKey、file set、regular-file identity 与总大小；测试 hook 在校验期间替换文件时必须拒绝。
- Resource limits：默认最多 64 files、单文件最多 `1,073,741,824` bytes、总计最多 `4,294,967,296` bytes；逐 manifest、目录 capture 与 stream read 三层执行上限。
- Sensitive-data protection：metadata 采用字符集/长度约束与敏感值模式；artifact 只允许明确 textual media type，并在跨 buffer overlap 的流式读取中扫描 key/token/secret/cookie/private-key/mnemonic 等 marker。binary/未知 media type fail closed。
- Failure contract：只返回固定 typed finding code 与安全 relative identifier；不返回 absolute path、文件内容、exception message、credential 或原始 payload。

## Production services 与 repository

- `StrategyReleaseProductionService`：先加载一次只读 provenance facts，验证 publish/run/strategy/dataset/evaluation/status/manifest 一致性，再调用 trusted-root verifier；repository exception 与缺失事实均 fail closed。
- `StrategyReleaseProvenanceRepository`：core read port，只暴露 `loadByPublishRecordId`。
- `JdbcStrategyReleaseProvenanceRepository`：`nq-infra` adapter，一次 publish-record 主键 SELECT 联结既有 publish/run/evaluation/version/dataset facts；无循环查询、无全表扫描、无写事务、无外部 API。
- Persistence impact：0；未创建 Strategy Release 表、migration、repository write path 或状态机持久化。

## 文件

Production code 新增：

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseProductionService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseProvenanceFacts.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseProvenanceRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactManifest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationPolicy.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationResult.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifier.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/domain/StrategyRelease.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/domain/StrategyReleaseStatus.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepository.java`

Tests 新增：

- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseProductionServiceTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifierTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseProvenanceRepositoryTest.java`

Current evidence 修改/新增：`STATUS.md`、`TESTING.md`、`WORKLOG.md` 与本文件。prototype 文件均未删除或改写。

## 验证与 RCA

| 验证 | 结果 |
| --- | --- |
| focused production suites | 19 tests；0 failures / 0 errors / 0 skipped |
| `mvn -f backend/pom.xml -pl nq-core -am test` | 437 tests；0 failures / 0 errors / 0 skipped；BUILD SUCCESS |
| `mvn -f backend/pom.xml test` | 23 modules；1307 tests；0 failures / 0 errors / 15 existing skipped；BUILD SUCCESS |
| `PackageBoundaryArchTest` | 6/6 PASS |
| `ModuleBoundaryArchTest` | 6/6 PASS |
| authority checker（实现前） | `errors=0` |
| authority checker（实现后） | 首次因三个入口仍复制旧 next action 返回 5 errors；最小同步 root/current README 与 ROADMAP 后 `errors=0` |
| current documentation link checker | 196 checked / 0 errors / 1 个既有 historical-ledger warning |

失败轮次透明记录：

1. 首次 compile 使用不存在的 `IOException` 四参数构造器；改为无消息构造，避免泄漏底层异常后重跑通过。
2. 首次 focused PowerShell 命令未引用 `-Dsurefire.failIfNoSpecifiedTests`，Maven lifecycle 未开始；正确引用后执行并通过。
3. 首次 module full 有 1 个 verifier happy-path failure：Windows 目录 size/mtime 不稳定，被误作为 directory identity。最小修复为目录只比较 type/fileKey，regular file 继续比较 fileKey/size/mtime，并保留 real path、closed set、逐文件双重 stat 和前后目录快照；focused、module 与 full backend 均重跑通过。
4. 首次 post-sync authority checker 返回 5 个 root/current README 与 ROADMAP next-action mismatch；按 checker 明确要求只同步入口摘要和唯一治理动作，未修改 checker/contract，重跑 `errors=0`。

## Architecture、影响面与安全自审

- Architecture boundary：core 只定义 domain/application/read port；JDBC 位于 `nq-infra`；`nq-api` 无 SQL；两个 ArchUnit suites 共 12/12 通过。
- API impact：0；无 Controller、endpoint、request/response DTO 或 JSON contract 变更。
- DB/migration impact：0；只读 adapter 使用既有表/字段，无 migration/schema/write。
- Shadow impact：0；未提升 binding/creation/admission prototype，未创建 Shadow Run。
- Trading/LIVE impact：0；无下单/撤单/转账/提现/风控状态机/账户上下文变更，LIVE=`DISABLED`。
- 性能：单次有界 SELECT；artifact verification 为 O(文件数 + 总字节数)，有 file-count、single-file、total-size 三类上限；无循环 DB/API、无无界缓存/队列/线程。
- 日志与凭证：未新增敏感日志；verification result 不携带 absolute path/content/secret；未读取 credential/private endpoint。
- 事务/幂等：只读，无写事务；canonical publish identity 和纯验证结果可重复计算，本批次无需写侧幂等记录。
- 资源释放：目录 stream 与文件 stream 均使用 try-with-resources。

## Findings 与 residual verifier limitations

### P0

- 无。

### P1

- 无。

### P2

- Java NIO 跨平台 API 不提供本实现可依赖的 OS 原子 stable file handle；当前以 real path、`NOFOLLOW_LINKS`、fileKey/size/mtime 双检、closed-set 双快照和替换回归降低 TOCTOU，但不能证明在所有文件系统/provider 上完全消除竞态。后续若 artifact 来源跨越不受信任的本机写者，应在隔离 staging/只读 mount 或平台原生 handle 层继续加固。

### P3

- Maven settings 存在既有 profile warning；未影响任何 compile/test 结果，本任务不扩大到本地 Maven 配置修复。

## Authority after、回滚与下一动作

- Authority after：GateX=`IN_PROGRESS|NOT_FROZEN`；GateX-1=`IMPLEMENTED|SELF_REVIEWED`；commit=`UNCOMMITTED`；CI=`NOT_RUN`；next action=`NQ-GATEX-1-COMMIT-AND-PUSH`；LIVE=`DISABLED`。
- Staged scope：仅上述 13 个 code/test 文件和 4 个 current evidence 文件；无 commit、push、migration、frontend、research、deploy 或 generated artifact。
- 回滚：当前无 commit/push；可先取消这 17 个精确路径的 staged 状态，再删除本任务新增 code/test/evidence 文件并恢复 `STATUS.md`、`TESTING.md`、`WORKLOG.md` 的本轮最小变更。无需 DB、API、运行时或生产环境回滚。
- 推荐 commit：`feat(strategy): 生产化策略发布制品验证`。
- 唯一下一动作：`NQ-GATEX-1-COMMIT-AND-PUSH`。

最终结论：`IMPLEMENTED / SELF_REVIEWED / STRATEGY_RELEASE_ARTIFACT_PRODUCTIONIZED / BACKEND_REGRESSION_GREEN / READY_TO_COMMIT`。
