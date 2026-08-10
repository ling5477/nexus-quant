# NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-SECURITY-REVIEW — attempt-01

## Task classification

- 归属：NQ-only。
- 类型：`INDEPENDENT_SECURITY_REVIEW`，覆盖 filesystem trust boundary、path traversal、cross-release isolation、TOCTOU、sensitive output 与 configuration/assembly boundary。
- 等级：高风险独立安全审查；默认 review-only，仅允许在 GateX-4C 原范围内最小关闭明确 P0/P1。
- 主 skill：`java-backend-maintenance`；`nq-dh-workflow-router` 用于 Gate/禁止边界分类，`nq-docs-writer` 仅用于本 evidence 与 current authority 同步。
- 禁止范围：不恢复 GateX-4 API/UI，不创建或启动 Shadow Run，不改 migration/frontend/Python/部署，不接触 LIVE、交易、凭证、生产数据，不 commit/push。

## Review status

```text
PASS /
SECURITY_REVIEW_ACCEPTED /
SERVER_CONTROLLED_ARTIFACT_BOUNDARY_VERIFIED /
CROSS_RELEASE_ISOLATION_VERIFIED /
READY_TO_COMMIT
```

审查发现并在原范围内关闭 1 个 P1；修复后 P0=0、P1=0。保留 4 个 P2，P3=0，不阻断本批进入 commit 前复核。

## Starting HEAD

```text
92043c37dad96d984d5e55a1e5170c97d335d6d4
```

## origin/dev HEAD

```text
92043c37dad96d984d5e55a1e5170c97d335d6d4
```

分支为 `dev`。进入审查时 GateX-4C implementation 共 19 个 staged 路径，unstaged=0、untracked=0；未发现附件范围外文件。

## Authority before

```text
accepted_batch=GateX-4B
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-4C
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-SECURITY-REVIEW
live=DISABLED
```

进入时 `scripts/docs/check-current-authority.ps1` 返回 `errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。

## Production resolution chain

唯一 production 链路为：

```text
publishRecordId
→ JdbcStrategyReleaseProvenanceRepository
→ persisted artifact_storage_key / manifest_storage_key
→ StrategyReleaseArtifactBindingResolver
→ ServerControlledStrategyArtifactBindingResolver
→ configured trusted root
→ bounded manifest loader / StrategyArtifactManifest
→ TrustedRootStrategyArtifactVerifier
→ StrategyReleaseProductionService
```

`StrategyReleaseProductionService.verify(...)` 的 production 输入只包含 `publishRecordId`；repository 一次读取 release provenance 与 persisted locator pair，resolver 才能产生内部 `Path + manifest`。该 `Path` 不属于 API contract，也不由 caller 提供。

## Resolver bypass review

- main source 中只有 `StrategyReleaseProductionService` 消费 `StrategyReleaseArtifactBindingResolver`；未发现第二个 resolver 或直接把 caller `Path`/manifest 送入 production service 的重载。
- `nq-api` / `nq-contracts` 无 trusted root、path、manifest、artifact storage key 或 manifest storage key request surface。
- tests 中直接构造 `Path` 只用于 verifier/resolver regression，没有进入 Spring production scan。
- JDBC repository 是 release provenance 的 production 读取实现；locator 来自 V37 persisted facts，不从 digest、publish id、filename 或 request 猜测。

结论：未发现 client-controlled path、caller-supplied manifest 或 production fallback 旁路。

## Trusted-root configuration review

- 配置键固定为 `nq.strategy-release.artifacts.trusted-root`，通过 typed `@ConfigurationProperties(prefix = "nq.strategy-release.artifacts")` 绑定。
- properties 无默认值；missing/blank 时 resolver 返回 `ARTIFACT_ROOT_NOT_CONFIGURED`，不回退 cwd、`user.home` 或 temp。
- root 必须是 absolute directory；逐级读取 `NOFOLLOW_LINKS` attributes，并对 configured/real root 做 directory、link/special 与 identity 检查。
- main resources 未发现默认值、profile override 或环境模板填入宽泛 root；HTTP/request 不能覆盖该配置。
- composition root 只注册一套 resolver bean；缺失配置不阻断应用启动，但 capability 在每次 resolve 时 fail-closed，符合当前 optional capability 设计。
- 错误结果、exception 与日志不输出 root 字符串或绝对路径。

过宽 root 风险：当前没有拒绝 `/`、drive root、repository root 或 user home 的额外 breadth policy。该值属于 privileged server configuration、默认未配置且无 HTTP override；resolver 仍要求 direct-child pair、manifest identity 与 digest verifier 全部通过，因此未形成可由普通 caller 利用的 P1，但 operator 误配会扩大受信 filesystem 范围，保留 P2。

## Storage-key review

Java resolver 与 V37 contract 一致：

```regex
^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$
```

并额外拒绝包含 `..`。独立 regression 覆盖并拒绝：`.`、`../escape`、nested slash/backslash、drive prefix、URI、percent encoding、UNC、Unicode slash-like separators、前后空白、控制字符、129 字符与 half-null pair。由于允许字符集中没有 `/`、`\`、`:`、`%` 或非 ASCII，ADS、UNC、drive、percent 与 Unicode separator 无需额外 sanitizer 即被阻断。

Windows case-folding、trailing-dot 与 reserved-name alias 没有独立 canonical key contract；当前 release identity binding 防止仅凭 alias 跨 release 接受，但未来 filesystem storage provider 仍需冻结名称/编码语义，保留 P2。

## Path containment review

- storage key 先在 configured root 下 resolve/normalize，且 parent 必须精确等于 configured root，限制为 direct child。
- target 与 real target 都使用 `NOFOLLOW_LINKS` attributes，拒绝 symbolic link、reparse/other 与错误文件类型。
- real target 必须位于 real root 下，且 real parent 必须精确等于 real root。
- artifact target 必须是 directory，manifest target 必须是 regular file；missing 与 non-regular 均 fail-closed。
- manifest 读取前后检查 identity，读取完成后再次核对 configured→real mapping、root/artifact/manifest identity。

普通 direct child 正常解析通过；nested injection、missing target、non-regular manifest、symlink/junction escape、root replacement 与 artifact target replacement 均按安全 reason code 拒绝。

## Windows junction/reparse result

Windows `mklink /J` 实际构造的 junction escape 被拒绝；resolver 没有沿 junction 接受 root 外目标。该用例未 skip。

## Linux symlink result

Windows 普通 symlink 创建受当前权限限制，resolver 与 GateX-1 verifier 各有 1 个相关 skip。WSL 可见 Ubuntu 与 docker-desktop，但 Ubuntu 无 Java/Maven；Docker server 查询持续无响应后被终止。未安装 WSL Java、未拉镜像、未创建容器，因此没有 Linux JVM real-symlink proof。

结论：明确保留 `P2 / CROSS_PLATFORM_SYMLINK_PROOF_MISSING`，不得把 Windows privilege skip 写成 Linux 已验证。

## Root replacement result

test hook 在 manifest 读取后替换 configured root；post-read `toRealPath` 与 root identity 对比失败，返回 `ARTIFACT_LOCATION_UNSAFE`。真实 replacement hook 已执行，未仅做静态推导。

## Target replacement result

test hook 在 manifest 读取后 rename 原 artifact directory 并在同名位置建立 replacement；post-read configured→real/identity 对比失败，返回 `ARTIFACT_LOCATION_UNSAFE`。GateX-1 verifier 另有 artifact pre-read attributes 后替换回归。

## TOCTOU assessment

主要检查窗口为：

```text
validate root → resolve targets → pre-read stat → read manifest/artifact → post-read stat
```

现有 `NOFOLLOW_LINKS`、real-path containment、configured/real mapping、root/artifact/manifest pre/post identity 与 verifier snapshot 可以检测本轮实际构造的 root、artifact 与 reparse replacement。manifest channel 使用 `READ + NOFOLLOW_LINKS`，并在 channel 关闭后复核 identity/size/mtime/fileKey。

Java NIO 没有 POSIX `openat` 风格的目录 stable handle，无法把 root directory 与后续所有 open/stat 原子绑定；快速 rename/swap 的理论窗口不能数学消除。没有发现可稳定复现并跨过 post-read identity 的 P1 bypass，保留 `P2 / OS_ATOMIC_STABLE_HANDLE_NOT_AVAILABLE`。

## Cross-release isolation

mandatory identity 检查覆盖：

```text
strategyVersionId
datasetId
evaluationId
artifactDigest
```

- Release A + locator A：PASS。
- Release A + locator B：`REJECTED / ARTIFACT_RELEASE_IDENTITY_MISMATCH`。
- 相同 artifact bytes/digest、不同 release identity：仍拒绝，不会因为 digest 相同自动接受。
- artifact key A + manifest key B：拒绝。
- existing service regressions继续覆盖 strategy version、dataset、evaluation 与 aggregate digest mismatch。

结论：`CROSS_RELEASE_ISOLATION_VERIFIED`；resolver resolved 不等于 release/admission eligible。

## Mixed locator result

独立 test 在同一 trusted root 写入 A/B 两套 locator；使用 artifact A + manifest B 返回 `ARTIFACT_RELEASE_IDENTITY_MISMATCH`，未出现 mixed pair 被 VERIFIED。

## Manifest parser security

- bounded reader 在 stat 前与 streaming read 中双重限制 `<= 1 MiB`；增长越界立即返回 `ARTIFACT_MANIFEST_INVALID`。
- `FAIL_ON_UNKNOWN_PROPERTIES`：unknown field 拒绝。
- `FAIL_ON_TRAILING_TOKENS`：第二个 JSON value/trailing token 拒绝。
- `STRICT_DUPLICATE_DETECTION`：重复 identity key 拒绝。
- malformed JSON、malformed UTF-8、non-regular manifest 拒绝；不返回 raw bytes。
- `ObjectMapper` stream read nesting constraint 有界（当前 `maxNestingDepth <= 1000`），并叠加 1 MiB 总体上限。

Jackson byte parser 可识别其支持的标准 JSON encodings；当前 producer/storage provider 尚未接线，系统未冻结“raw manifest 必须 exact UTF-8”与跨平台 filename canonicalization contract，作为 provider 接线前的 P2 保留。

## Duplicate-key behavior

首次新增攻击回归时，原 reader 对重复 `strategyVersionId` 使用后值覆盖并静默解析：

```text
ServerControlledStrategyArtifactBindingResolverTest
Tests run: 9, Failures: 1
shouldRejectDuplicateManifestIdentityKey:
expected rejected=false but resolved=true
BUILD FAILURE
```

RCA：Jackson reader 已启用 unknown/trailing token strictness，但未启用 duplicate field detection。身份字段 shadowing 属于 manifest identity bypass，定级 P1。

最小修复是在 resolver 的既有 `ObjectReader` 增加：

```java
.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
```

修复后 duplicate identity key 稳定返回 `ARTIFACT_MANIFEST_INVALID`；未改变 manifest schema、API 或 verifier contract。

## Size/encoding result

- `> 1 MiB` manifest：拒绝。
- read 中动态增长到上限外：实现按累计 bytes 拒绝。
- malformed UTF-8：拒绝。
- unknown/trailing/duplicate/malformed JSON：拒绝。
- exact UTF-8 provider contract：尚未冻结，列为 P2，不写成已验证。

## Sensitive output review

- resolver 的 root/IO/security exception 全部转换为 typed safe reason code；不传播 `exception.getMessage()`。
- root 与内部 binding 使用 `<root>` / `<artifact-binding>` / `<manifest>` 等固定 identifier；parser failure 最多返回经过正则验证的 opaque manifest storage key。
- 未发现 `printStackTrace`、raw manifest/artifact content、absolute/real path、server username/home、credential/token/private payload 日志或响应。
- GateX-1 sensitive-marker regression 继续证明敏感 artifact value 不回显内容。

## Spring assembly review

- `StrategyReleaseArtifactConfiguration` 只有一个 `@Bean StrategyReleaseArtifactBindingResolver`，实现固定为 `ServerControlledStrategyArtifactBindingResolver`。
- typed properties 由 `@EnableConfigurationProperties` 注册；没有 `@Conditional*` unsafe fallback、profile-specific Noop VERIFIED resolver 或 test bean 进入 main scan。
- configuration regression 3/3：default unconfigured 不阻塞 application context、只绑定 explicit server-side root、只通过 internal port 暴露 resolver。
- missing root 的语义是 application 可启动但 capability resolve fail-closed，不是 fallback 到工作目录。

## Producer boundary

```text
PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED
```

普通 publish 仍走 unbound locator；没有 production `publishRecordId → generated storage key`、`digest → key`、Python caller path、HTTP path/key 或自动 filename layout。历史/普通记录可保持 `LEGACY_ARTIFACT_UNBOUND`，resolver 完成不等于 artifact pipeline fully operational。

## GateX-1 integration

resolver 只负责服务端 locator/path/manifest binding；最终 artifact set 仍由既有 `TrustedRootStrategyArtifactVerifier` 校验 manifest schema、relative paths、file count/size/type/hash、aggregate digest 与 file stability。resolver `RESOLVED` 不会绕过 GateX-1 verifier。

## GateX-3 boundary

```text
resolver VERIFIED != admission ELIGIBLE
admission ELIGIBLE != ShadowRunCreated
ShadowRunCreated != trading authorization
```

GateX-3 仍只生成 side-effect-free creation plan；本轮没有 Shadow create/start、scheduler、runner 或交易状态机变更。

## GateX-4 API/UI boundary

GateX-4 原 API/UI 继续为 `BLOCKED / WAITING FOR SERVER-CONTROLLED ARTIFACT BINDING`；本 review 不恢复 endpoint、DTO/query、frontend page 或 UI authority。LIVE=`DISABLED`，Shadow trading=`NOT ENABLED`。

## Files inspected

- `backend/nq-core/**/StrategyReleaseProductionService.java`
- `backend/nq-core/**/StrategyReleaseArtifactBindingResolver.java`
- `backend/nq-core/**/StrategyReleaseProvenanceFacts.java`
- `backend/nq-core/**/TrustedRootStrategyArtifactVerifier.java`
- `backend/nq-core/**/StrategyArtifactManifest.java`
- `backend/nq-infra/**/ServerControlledStrategyArtifactBindingResolver.java`
- `backend/nq-infra/**/JdbcStrategyReleaseProvenanceRepository.java`
- `backend/nq-app/**/StrategyReleaseArtifactConfiguration.java`
- `backend/nq-app/**/StrategyReleaseArtifactProperties.java`
- 对应 resolver/service/verifier/JDBC/config tests。
- `backend/nq-api`、`backend/nq-contracts`、main resources/profile 配置、GateX-1/3/4/4A/4B/4C evidence 与 current authority/docs。

## Files created

- `docs/current/evidence/gate-x/NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-SECURITY-REVIEW.attempt-01.md`

## Files changed

- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolver.java`：为 manifest reader 启用 strict duplicate detection。
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/artifact/ServerControlledStrategyArtifactBindingResolverTest.java`：新增 duplicate/parser/cross-release/mixed-locator/target-replacement regressions并扩展 key attack matrix。
- `README.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## Security tests

1. Resolver suite：12 tests、0 failures、0 errors、1 Windows symlink privilege skip。
2. 五套 security-focused reactor：production service 17、GateX-1 verifier 2（1 skip）、resolver 12（1 skip）、JDBC provenance 1、typed configuration 3；合计 35 tests、0 failures、0 errors、2 skipped，23-module reactor `BUILD SUCCESS`。
3. Windows junction、root replacement、artifact target replacement、duplicate key、cross-release same-digest 与 mixed locator regression 均通过。

## Backend regression after fixes

```text
mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test
mvn -f backend/pom.xml test
```

两次均为 23-module reactor `BUILD SUCCESS`；focused/full 中 `nq-app` 为 250 tests、0 failures、0 errors、16 skipped。

本次 full run 在 00:21～00:22 生成 278 份 Surefire XML，精确汇总为：

```text
1345 tests / 0 failures / 0 errors / 25 skipped
```

`target` 中另有 7 份 2026-08-09 的陈旧旧包名报告，共 79 tests；它们不是本次 run 产物。直接汇总全部历史 XML 会误报 1424，本 evidence 不采用该错误口径，并据此纠正前序 implementation evidence 中“1420”汇总口径，不覆盖其历史文件。

## ArchUnit

- `ModuleBoundaryArchTest`：6/6。
- `PackageBoundaryArchTest`：6/6。
- 0 failures / 0 errors。

## Authority checker

最终 `scripts/docs/check-current-authority.ps1` 必须返回：

```text
AUTHORITY_CHECK errors=0
PASS / CURRENT_AUTHORITY_CONSISTENT
```

## Diff checks

最终必须满足 `git diff --check` 与 `git diff --cached --check` 均 exit 0；staged scope 只包含原 GateX-4C implementation、两处 review fix 与必要 evidence/current docs，不允许 migration/frontend/Python/deploy/generated output/credential。

## P0

无。

## P1

无未关闭项。已关闭 1 项：`DUPLICATE_MANIFEST_IDENTITY_KEY_SHADOWING`，首次 regression 真实失败后通过 `STRICT_DUPLICATE_DETECTION` 最小修复并完成 focused/full regression。

## P2

1. `OS_ATOMIC_STABLE_HANDLE_NOT_AVAILABLE`：Java NIO 无 `openat` 风格目录句柄；现有 NOFOLLOW/real-path/identity/snapshot 只能缩小而不能数学消除 TOCTOU 窗口。
2. `CROSS_PLATFORM_SYMLINK_PROOF_MISSING`：当前无可用 Linux Java/Maven runtime，Windows 普通 symlink 因 privilege skip；Windows junction 已实测拒绝。
3. `TRUSTED_ROOT_BREADTH_POLICY_NOT_ENFORCED`：server-controlled absolute root 未额外禁止 filesystem root/repository root/user home；默认未配置且 caller 无覆盖，但 operator 误配会扩大受信范围。
4. `STORAGE_PROVIDER_PLATFORM_CONTRACT_NOT_FROZEN`：Windows case-folding/trailing-dot/reserved-name alias 与 raw manifest exact encoding 尚未由 producer/provider contract 冻结；当前 key charset 与 release identity binding 阻断已知 path/cross-release acceptance。

## P3

无。

## Review fixes applied

- production fix：在既有 manifest `ObjectReader` 增加 `StreamReadFeature.STRICT_DUPLICATE_DETECTION`。
- test evidence：新增 duplicate identity、unknown/trailing/malformed encoding、same-digest cross-release、mixed locator、artifact replacement，并扩展 storage-key 攻击矩阵。
- 未修改 API、schema、provider、verifier public contract 或交易相关代码。

## Residual security limitations

- 没有 OS 原子 directory handle 证明。
- 没有 Linux JVM symlink regression。
- trusted root breadth 与未来 provider platform/encoding contract 需要在 producer 接线前冻结。
- 这些限制不允许被解释为 artifact producer、GateX-4 API/UI、Shadow Run 或 trading authorization 已就绪。

## Evidence file

本文件是 GateX-4C security review attempt-01；implementation evidence 保留在 `NQ-GATEX-4C-SERVER-CONTROLLED-ARTIFACT-BINDING-IMPLEMENTATION.attempt-01.md`，未覆盖。

## Authority after

```text
work_batch=GateX-4C
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4C-COMMIT-AND-PUSH
live=DISABLED
```

## Staged scope

最终只允许原 19 个 GateX-4C implementation 路径，加本 review evidence；两处 review fix 与 current docs 精确叠加在这些路径中。禁止使用 `git add .`，不得混入 migration/frontend/Python/deploy/generated output/credential。

## Review decision

```text
SECURITY_REVIEW_ACCEPTED
SERVER_CONTROLLED_ARTIFACT_BOUNDARY_VERIFIED
CROSS_RELEASE_ISOLATION_VERIFIED
```

## Commit recommendation

```text
feat(strategy): resolve release artifacts from server-controlled storage
```

本任务不执行 commit/push。

## Rollback

- 当前尚未 commit/deploy；回滚 review fix 应使用精确 inverse patch 移除 duplicate-detection import/reader option 与本轮新增测试。
- 回滚 review docs 应只删除本 evidence 与本轮 current authority/TESTING/WORKLOG hunks。
- 不得对整个 staged 文件粗粒度 restore，因为相同文件包含前序 GateX-4C implementation；无数据库、部署、LIVE 或外部副作用需要回滚。

## Next action

唯一下一动作：

```text
NQ-GATEX-4C-COMMIT-AND-PUSH
```

不得恢复 GateX-4 API/UI。

## Final decision

```text
PASS /
SECURITY_REVIEW_ACCEPTED /
SERVER_CONTROLLED_ARTIFACT_BOUNDARY_VERIFIED /
CROSS_RELEASE_ISOLATION_VERIFIED /
READY_TO_COMMIT
```
