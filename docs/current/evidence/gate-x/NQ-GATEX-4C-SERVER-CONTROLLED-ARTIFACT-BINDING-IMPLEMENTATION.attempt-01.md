# NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-IMPLEMENTATION — attempt-01

## Task classification

- 归属：NQ-only。
- 类型：`CODE_CHANGE`，辅助类型为 `BACKEND_SECURITY_IMPLEMENTATION`、server-controlled artifact resolution、typed configuration、manifest loading 与 regression。
- 等级：L 级文件系统信任边界实现。
- 主 skill：`java-backend-maintenance`；`nq-dh-workflow-router` 负责 Gate/范围路由，`nq-docs-writer` 仅负责本 evidence 与 current authority 同步。

## Execution status

```text
IMPLEMENTED /
SERVER_CONTROLLED_ARTIFACT_BINDING_COMPLETE /
TRUSTED_ROOT_BOUNDARY_HARDENED /
PENDING_INDEPENDENT_SECURITY_REVIEW
```

## Starting HEAD / GateX-4B acceptance

- branch=`dev`。
- starting `HEAD == origin/dev == 92043c37dad96d984d5e55a1e5170c97d335d6d4`。
- GateX-4B commit=`92043c37dad96d984d5e55a1e5170c97d335d6d4`。
- exact-head CI run=`31403529376 / completed / success`。
- authority entering GateX-4C：`accepted_batch=GateX-4B / ACCEPTED|CI_GREEN`；`work_batch=GateX-4C / NOT_STARTED`；LIVE=`DISABLED`。

## Persistent locator facts / producer status

- `artifact_storage_key` 与 `manifest_storage_key` 继续来自 canonical `backtest_publish_records`，通过一次 bounded JDBC SELECT 回载到 core provenance facts。
- GateX-4A 已冻结 direct-child contract：artifact key 定位 artifact-set root/container，manifest key 独立定位 manifest object；本轮未猜 `root/<publishRecordId>`、digest path、suffix 或 `manifest.json`。
- Legacy `NULL/NULL` 稳定返回 `ARTIFACT_LOCATION_UNBOUND`，不扫描 filesystem。
- Producer status=`PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED`；普通 HTTP publish 继续产生 unbound release，本轮未接 producer、未造 fake key。

## Trusted-root configuration

- typed key：`nq.strategy-release.artifacts.trusted-root`。
- 配置仅由 `nq-app` composition root 注入；缺失/空白为 `ARTIFACT_ROOT_NOT_CONFIGURED`，relative/missing/non-directory/reparse root 为 `ARTIFACT_ROOT_INVALID`。
- 无默认 cwd、`user.home`、temp、HTTP override 或 credential；production default 保持 `UNCONFIGURED / DISABLED`。

## Resolver / manifest loader / release identity

- storage key 使用与 V37 相同的单段 ASCII opaque contract，并额外拒绝 `..`；只解析 configured root 的 direct child。
- configured root 每级组件与 direct child 均使用 `NOFOLLOW_LINKS`；检查 normalize/parent equality、real-path containment、directory/regular-file 类型与 symlink/reparse/special file。
- manifest 只由 `manifest_storage_key` 加载，最大 1 MiB；seekable channel 使用 `NOFOLLOW_LINKS`，前后 identity 一致后才 strict Jackson decode，拒绝 unknown fields 与 trailing tokens。
- production service 的公开验证入口只接收 `publishRecordId`；caller-supplied `Path + StrategyArtifactManifest` command 已删除。
- `schemaVersion` 由 GateX-1 manifest validator 检查；`strategyVersionId/datasetId/evaluationId` 与 publish/run/evaluation facts 比对；`artifactDigest` 与 canonical artifact metadata digest、逐文件 size/SHA-256 由 GateX-1 verifier 验证。任一不一致均 REJECTED。

## Legacy / cross-release isolation / filesystem security

- legacy unbound：`ARTIFACT_LOCATION_UNBOUND`。
- invalid/missing locator：`ARTIFACT_LOCATION_UNSAFE` / `ARTIFACT_LOCATION_NOT_FOUND` / `ARTIFACT_MANIFEST_NOT_FOUND`。
- invalid manifest：`ARTIFACT_MANIFEST_INVALID`；raw JSON、异常和绝对 path 不进入 result/log。
- release A 绑定 release B manifest 时，strategy/dataset/evaluation identity 返回 `ARTIFACT_RELEASE_IDENTITY_MISMATCH`；artifact/manifest cross-binding 不会进入 VERIFIED。
- Windows junction escape 已用真实 `mklink /J` 构造并拒绝；普通 symlink 用例因当前 Windows privilege 不可用而按 assumption skip，未伪报通过。GateX-1 verifier 的 symlink privilege 用例同样保持真实 skip。
- root replacement hook 实际移动原 root 并创建替代 root，前后 identity/real-path 检查返回 `ARTIFACT_LOCATION_UNSAFE`。
- non-regular manifest、oversized/invalid JSON、missing artifact root 与 deterministic repeat 均有回归。

## TOCTOU assessment / sensitive-output review

- 已复用 GateX-1 `TrustedRootStrategyArtifactVerifier` 的 closed-set、path traversal、SHA-256、sensitive scan、resource limits 与 artifact 变更检测；未复制第二套内容 verifier。
- Resolver 只负责 location 与 manifest load，使用 NOFOLLOW、real-path、前后 root/artifact/manifest identity；Java NIO 不提供 openat 风格目录句柄绑定，仍保留 P2 TOCTOU residual，不能宣称 OS 级原子证明。
- 所有新增 failure result 仅携带固定 reason code 与已验证 opaque key/固定 placeholder；未发现 absolute path、server username、home、raw manifest、artifact content、credential/token 输出。

## GateX-1 verifier / GateX-3 admission boundary

- `StrategyReleaseProductionService` 继续调用唯一的 `TrustedRootStrategyArtifactVerifier` 与 `StrategyArtifactManifest`。
- VERIFIED 只表示 release artifact integrity/provenance；GateX-3 admission 仍是独立纯决策。本轮不创建 `ShadowRunCreationPlan`、不创建/启动 Shadow Run，不授权交易或 LIVE。

## Files created

- core binding resolver port/result。
- infra server-controlled resolver/loader 与安全回归。
- app typed properties/configuration 与 binding regression。
- 本 evidence。

## Files changed

- Strategy Release production service/provenance facts/finding codes。
- JDBC provenance SELECT/mapper 与测试。
- production service tests。
- `README.md`、`docs/current/README.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`。

## Validation

| Check | Result |
| --- | --- |
| focused five-suite reactor | PASS（通过）；29 executed、0 failure/error、2 privilege skips |
| actual resolver + production service happy path | PASS（通过） |
| Windows junction / root replacement | PASS（通过） |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test` | PASS（通过）；23 modules，`nq-app` 250/0/0/16 |
| `mvn -f backend/pom.xml test` | PASS（通过）；1420/0/0/25，23 modules |
| PackageBoundaryArchTest / ModuleBoundaryArchTest | PASS（通过）；6/6 + 6/6 |
| authority checker | PASS（通过）；`errors=0` |
| `git diff --check` | PASS（通过） |

初始 PowerShell `-D` 引用、test helper overload、non-null fileKey 与 missing-root reason mapping 的失败轮均已记录 RCA 并重跑通过，未将失败写为通过。

## Impact / findings

- Migration impact：0；V37 未修改，无新 migration。
- API/UI impact：0；无 endpoint/DTO/controller/frontend。
- Shadow impact：0；无 create/start/scheduler/runner。
- Trading/LIVE impact：0；LIVE 保持 `DISABLED`，无交易/凭证/外部调用。
- P0：0。
- P1：0。
- P2：1，Java NIO directory-handle/TOCTOU residual，需独立安全审查。
- P3：1，Windows symlink privilege test skip；junction 实测通过。工程 MCP 未暴露，按规范降级到 PowerShell、`rg`、Maven 与 Git，源码/测试事实可信度高。

## Authority after / staged scope

```text
accepted_batch=GateX-4B
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-4C
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-SECURITY-REVIEW
```

最终只精确暂存本任务 Java/config/tests 与允许的 current docs/evidence；不使用 `git add .`，不 commit/push。

## Independent security review / rollback / next action

- 必须独立复核 root component policy、junction/reparse、file identity、TOCTOU residual、manifest strict decode、reason-code/path redaction、cross-release isolation 与 GateX-1 reuse。
- 回滚：使用精确 inverse patch 移除 4C 新文件与 hunks，并把 current authority 恢复到 GateX-4C `NOT_STARTED`；不得修改/删除 V37 或 GateX-4B commit。
- 推荐未来 commit：`feat(strategy): resolve release artifacts from server-controlled storage`。
- 唯一下一动作：`NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-SECURITY-REVIEW`。

## Final decision

```text
IMPLEMENTED /
SERVER_CONTROLLED_ARTIFACT_BINDING_COMPLETE /
TRUSTED_ROOT_BOUNDARY_HARDENED /
PENDING_INDEPENDENT_SECURITY_REVIEW
```
