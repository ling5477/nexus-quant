# NQ-PRE-GATEX-ARTIFACT-VERIFICATION-SECURITY-PROTOTYPE-ATTEMPT-01

## 1. Task identity

- Task ID：`NQ-PRE-GATEX-ARTIFACT-VERIFICATION-SECURITY-PROTOTYPE-ATTEMPT-01`
- Attempt：`01`
- Classification：`TEST_ONLY_SECURITY_PROTOTYPE`
- 状态：`PREPARED / SELF_REVIEWED / READY_TO_COMMIT_ON_PREP_BRANCH`
- 日期：`2026-07-23`
- Branch：`prep/gatex-research-to-shadow`
- Starting HEAD：`4493ccb1171c9c90397684e07d2e6fb0557c325c`
- `origin/dev`：`557980eaf5e6302d9a46d718b124f0f530aa74f1`
- Remote preparation SHA：`4493ccb1171c9c90397684e07d2e6fb0557c325c`

## 2. Baseline verification

- preparation worktree 起步 clean，staged empty；
- `origin/dev` 是 preparation HEAD 的祖先；
- `origin/dev...HEAD = 0 behind / 1 ahead`；
- 本地和远端 preparation SHA 相同；
- 主工作区 `dev` clean，HEAD 等于 `origin/dev`；
- authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT`，errors 0；
- GateW：`IN_PROGRESS / NOT_FROZEN`；
- Attempt-09：`RUNNING / PENDING_168H`；
- 未访问 soak 服务器。

## 3. Scope

仅新增四个 allowlist 文件：

```text
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/TrustedRootArtifactVerifierPrototype.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/TrustedRootArtifactVerifierPrototypeTest.java
docs/drafts/pre-gatex/ARTIFACT_VERIFICATION_SECURITY_PROTOTYPE.md
docs/drafts/pre-gatex/NQ-PRE-GATEX-ARTIFACT-VERIFICATION-SECURITY-PROTOTYPE-ATTEMPT-01.md
```

未修改 production、migration、POM、frontend、Python、`docs/current`、GateW runtime/evidence、deploy 或
`.github`。

## 4. Files inspected

- `README.md`
- `docs/current/STATUS.md`
- `docs/current/README.md`
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/drafts/pre-gatex/RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md`
- `docs/drafts/pre-gatex/STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql`
- `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json`
- `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json`
- `StrategyReleaseManifestPrototypeTest.java`
- `SensitiveFieldPolicyPrototypeTest.java`

## 5. Implementation

`TrustedRootArtifactVerifierPrototype` 只存在于 `src/test`，提供：

- `VERIFIED / REJECTED / UNKNOWN` 三态结果；
- 完整 finding taxonomy；
- trusted root、Path containment、逐级 `NOFOLLOW_LINKS`、real path 边界；
- symbolic link、`isOther()` 和非普通文件拒绝；
- 8192-byte buffer 流式 SHA-256 与最大读取限制；
- size 与小写 SHA-256 校验，constant-time digest compare；
- pre/post attributes 与 package-private TOCTOU hook；
- 安全 path identity hash；
- 既有 canonicalization 的 aggregate digest；
- 固定脱敏原因，不传播原始本地异常。

## 6. Tests

成功执行：

```powershell
mvn -pl nq-core -am "-Dtest=TrustedRootArtifactVerifierPrototypeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：22 tests，0 failures，0 errors，3 skipped。

```powershell
mvn -pl nq-core -am "-Dtest=*PrototypeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：35 tests，0 failures，0 errors，3 skipped。

```powershell
mvn -pl nq-core -am test
```

结果：381 tests，0 failures，0 errors，3 skipped。

首次定向命令因 PowerShell 将未加引号的
`-Dsurefire.failIfNoSpecifiedTests=false` 误传为 Maven lifecycle phase 而退出 1，未进入编译。给该参数加引号后
按相同测试范围重跑并通过；这是命令行解析错误，不是测试失败。

## 7. Skipped tests

三个 symbolic link 测试状态：

```text
NOT_RUN / SYMLINK_PRIVILEGE_UNAVAILABLE
```

当前 Windows 权限不允许创建真实 symbolic link。未把 skipped 写成 passed，未据此声称 Windows reparse protection 完成。

## 8. Findings

### P0

无。

### P1

无。

### P2

- Java NIO/provider 无法保证识别全部 Windows junction/reparse point；
- 双重 stat 不是稳定文件描述符级完整 TOCTOU 防护；
- 当前环境未执行真实 symbolic link、junction、SMB/NFS 或恶意并发压力测试。

上述 P2 是 production GateX verifier 前的阻断前置，不阻断本 test-only prototype。

### P3

- 根 `README.md` 的 GateW 简述仍有陈旧文字；该文件明确不决定 current authority，且本任务禁止修改。
  `docs/current/STATUS.md`、`docs/current/README.md` 与 authority checker 当前一致。

## 9. Security boundary

- 未读取 credential、API key、token、cookie、私钥或真实账户数据；
- 未读取真实 artifact 目录；
- Maven 测试只使用 `@TempDir` 虚构文件；
- 未访问交易所、private endpoint 或 soak 服务器；
- 失败结果不包含 trusted root、文件内容、stack trace、原始异常 message 或实际 digest；
- Git 远端访问仅用于任务开始前核验 preparation SHA，不属于 artifact 测试网络访问。

## 10. Trading boundary

- GateW remains `IN_PROGRESS / NOT_FROZEN`；
- GateX remains `NOT_STARTED`；
- preparation branch remains `UNMERGED`；
- dev remains unchanged；
- production artifact verifier remains `NOT IMPLEMENTED`；
- LIVE remains `DISABLED`；
- 未新增下单、撤单、转账、提现、Shadow Run、AI 或 DH runtime 路径。

## 11. Self-review

- 仅新增 allowlist 四文件；
- 未使用 `Files.readAllBytes`；
- 未进行字符串 path prefix 判断；
- 没有无界读取、无界集合或额外依赖；
- 摘要输入、排序、UTF-8、U+001F/LF 与既有合同一致；
- 失败结果仅保留安全字段；
- IDE Java problems 检查为 0；
- production code 和 migration diff 为 0。

## 12. Commit recommendation

推荐 commit：

```text
test(gatex): prototype trusted-root artifact verification
```

不得自动 commit 或 push。提交前只允许精确暂存本文件第 3 节列出的四个文件。

## 13. Next action

```text
PREPARATION_BRANCH_HOLD / NO_DEV_MERGE
```

GateW 接受且 current authority 明确允许 GateX 前，不得合入 dev，不得创建 production verifier。
