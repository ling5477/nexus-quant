# C1 delivery CI remediation — attempt history and blocked closure

最新 Attempt-02 见第 8 节：本地交付门禁 PASS，远端验收待新 exact-head CI。第 1–7 节保留此前 remediation 与 Attempt-01 失败历史。

日期：2026-09-07。任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-DELIVERY-CI-COMPATIBILITY-REMEDIATION`。

结论：`BLOCKED / BACKUP_RESTORE_DIAGNOSIS_INCOMPLETE / ACTIVE_SCHEMA_FIXTURE_REGISTRY_SCOPE_BLOCKED`。

已完成两份 delivery fixture 的局部整改与本地验证；未暂存、commit、push 或触发新 CI。本文不接受 C1，不推进 C2，不改写 authority。远端 Docker 失败尚不能归入任务书要求的 A/B/C 唯一分类；同时仍有 9 处受禁止修改的 F009 registry 保护的 active current-schema 断言，未满足 stale active=0。

## 1. 固定 lineage 与 reviewed-byte 边界

- C1 implementation / failed delivery commit：`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd`。
- parent：`5ea72a0f44bdeb686f50d0e22a3a7f4f53f4de32`。
- failed exact-head CI：[34086018265](https://github.com/ling5477/nexus-quant/actions/runs/34086018265)，`completed / failure`，9 jobs：5 success、3 failure、1 skipped、0 cancelled。
- branch：`audit/post-gatey-agent-baseline`；本轮 fetched HEAD=origin=`41c3bbcb…`；初始 worktree CLEAN、staged=0。
- frozen `C1_REVIEWED_PATHS` 从上述 parent→implementation 的 Git diff 派生，共 12 paths。原始工作区 SHA256 与 committed blob 均保存于 `artifacts/20260907-c1-ci-remediation/C1_REVIEWED_PATHS.json`。
- remediation diff 与 frozen paths 的交集为 0；V47、4 个 C1 production Java、C1 correctness tests、reviewed evidence 均未修改。既有独立 correctness review 保持 VALID，不追加 review。
- `41c3bbcb… / 34086018265 = FAILED DELIVERY` 永久保留；本轮没有 acceptance head 或 green CI。

## 2. 三个 failed jobs 的精确诊断

| Job / step | Command / file | Expected / actual | Root cause / classification |
|---|---|---|---|
| Frontend build and critical E2E / Install and activate admitted canonical release | `Install-NqCanonicalReleaseCi.ps1 -SourceCommit <head>` → `observe-database` → `activate` | release requiredSchemaTarget=V47；fixture observed schema=V46 | `CURRENT_RELEASE_FIXTURE` stale hardpin；installer 严格相等检查正确拒绝 `BLOCKED / RELEASE_DATABASE_SCHEMA_INCOMPATIBLE` |
| Repository hygiene and governance / Validate canonical delivery workflow contract | `Test-CanonicalDeliveryWorkflow.ps1`、其 tests、Gitleaks execution tests、`Test-NqCanonicalRelease.Tests.ps1`；最后一项进入 installer | current release=V47；initial/concurrency database fixture=V46 | 同一 fixture pin；本地原样重现 installer line 378 的 schema 不兼容错误 |
| PostgreSQL and Flyway / Run current-schema backup and restore drill | `Invoke-NqCanonicalRestoreCi.ps1 -SourceCommit <head>` → `Invoke-NqCanonicalRestoreDrill.ps1 -ConfirmDisposable` | disposable Docker 子命令应 exit 0；实际 nonzero，wrapper line 145 抛 `FAIL / DISPOSABLE_DOCKER_COMMAND_FAILED` | `UNCLASSIFIED / DIAGNOSIS_INCOMPLETE`；wrapper 捕获但不输出底层命令和 stderr，现有 log 无法唯一确定 failed Docker argv |

远端 restore 已到 `RESTORE_AND_VALIDATE_CURRENT_SCHEMA`，此前 `MIGRATE_SOURCE_TO_CURRENT` 与 `CREATE_AND_VERIFY_BACKUP` 完成。失败区间包含 target startup、dump copy 和 pg_restore；不能只凭时间间隔指认其中某一个命令，也不能把 generic wrapper error 当成 V47 SQL 缺陷。原 job 没上传 restore dump/底层诊断，只上传了此前的 schema artifacts；runner 已结束。本轮没有重新触发诊断 CI、修改诊断 wrapper 或任意添加 retry。

三个原始 job logs 已在前一交付任务从 GitHub API 取得，本轮对同一 run 元数据重新核验并读取、按 SHA256 固定到 `artifacts/20260907-c1-ci-remediation/<job-id>-job.log`：101630098521、101630098687、101630098736。其失败事实不由本地 PASS 替代。

## 3. V46 引用盘点与未完成项

完整逐引用分类见 [machine inventory](GATEAUDIT_PHASE6_L4_C1_DELIVERY_CI_REMEDIATION_REFERENCES.json)，基于固定 implementation commit；每个已列引用只属于一个分类。检索覆盖 tracked repository 文本，含 `V46`、quoted `46`、schemaVersion/schema_version/migrationVersion/latestMigration/expectedSchema 与 compatibility error；排除生成物和敏感文件。

| Classification | Count | Before | After / reason |
|---|---:|---|---|
| CURRENT_RELEASE_FIXTURE | 3 | V46 | `$repositorySchema`，由既有 `Get-NqMigrationInventory.targetVersion` 导出 V47 |
| NEGATIVE_COMPATIBILITY_FIXTURE | 1 | 固定 V47 原本表示不兼容 | current+1=V48；另加 V46 activation rejection，避免原负例变成正例 |
| CURRENT_SCHEMA_HEAD | 9 | latest migration 后断言 46 | **未改，仍 stale**；见下文 scope blocker |
| IMMUTABLE_HISTORICAL_EVIDENCE | 137 | V46 / 46 | 不改历史文档、固定 migration/upgrade baseline、已接受 release 事实 |
| OLD_RELEASE_METADATA | 0 | 无单独归类项 | 不扫描或改写以前构建的 ignored release artifacts |
| UNRELATED_NUMBER | 0 | 该精确版本 inventory 无此项 | 非 Flyway protocol schemaVersion 不作为 V46 版本项 |

共 150 个版本相关引用记录，包含原负例 V47。新增 negative V46 明确属于不兼容输入，不是 current schema。Historical V46 facts modified=0，negative mismatch coverage lost=0；**active stale current-schema references after=9，验收未通过**。

9 处位于两份 active Java integration tests：`LiveSessionFactModelPostgresIntegrationTest.java` 原行 102/162/281/315/513/656，`OperatorPilotAuthorityPostgresIntegrationTest.java` 原行 72/189/656。源码实际使用无 target 上限的 Flyway latest，不能仅因 GateY 名称把它们归成非执行历史材料。

两文件均由 [F009 exception registry](../../../scripts/docs/stage-asset-exceptions.json) 的 `FIXTURE_IDENTITY` hash 绑定。只在 ignored artifacts 副本上替换 46→47，再使用原 checker 的 `inspect`，确认 semantic hash 必变；原文件未写入。诊断记录：`artifacts/20260907-c1-ci-remediation/protected-fixture-diagnostic.json`。要完成这部分 fixture 同步，需同步 registry hash，但任务书第 11 节明确禁止 F009 compatibility registry 修改。本轮因此保留这 9 处，不伪造 inventory closure，也不跳过 stage guard。

## 4. 已完成的最小 fixture patch

- [Install-NqCanonicalReleaseCi.ps1](../../../scripts/deployment/Install-NqCanonicalReleaseCi.ps1)：调用已有 canonical migration inventory helper，给 disposable database observer 传 repository-current schema；不再重复硬编码 V47。
- [Test-NqCanonicalRelease.Tests.ps1](../../../scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1)：initial/concurrency 正例同源导出 current；wrong-schema 由 current+1 构造；增加 old V46、wrong-major 拒绝。受影响负例核对确切 error token。
- `Install-NqCanonicalRelease.ps1`、release/schema compatibility enforcement、source identity、admission/provenance、SBOM、workflow、dependencies、scanner rules 与 registry 均未修改。
- 没有引入 `>=`、V46/V47 双接受、忽略 schema、跳过 job 或弱化 fail-closed。

## 5. 本地验证与 Docker 区分

- 原 canonical release tests：exit 1，重现 `RELEASE_DATABASE_SCHEMA_INCOMPATIBLE`。
- fixture patch 后 canonical release tests：69 cases PASS；确切错误码加强后再次 69 PASS。包含 install、activation、rollback、并发与异常路径。
- Docker：一次 `docker ps -a --filter name=^/nq-c1-occ-20260907$`，15 秒 timeout；`UNVERIFIED / DOCKER_API_UNAVAILABLE`。未删除容器，未改 daemon，仅结束本轮 query 子进程。
- exact **Docker** backup/restore：`NOT_RUN / DOCKER_API_UNAVAILABLE`。
- 额外执行原脚本已有 **WslPg16** 模式，复用缓存 PostgreSQL 16.15，无下载；Maven 离线、构建 skipTests，再执行 restore 专用 smoke，未重跑 1777 tests。临时 cluster 仅 loopback，正常 finally 清理。
- WSL：V47 migrate、custom-format dump/restore、Flyway validate、pending=0、schema/data canary、repository/app-context smoke 与 7 项 backup/restore 负例均 PASS。source/restored canary 同为 `75|465|276|47|V47|1`。
- `Test-NqCanonicalRestoreProof` 的 BACKUP_INTEGRITY 与 POST_RESTORE_VALIDATION 均 PASS。证明位于 `artifacts/20260907-c1-ci-remediation/restore-wsl/restore-proof.json`。
- **证明边界**：WSL 通过说明原 V47 在该 PG16 环境可完整恢复；不能恢复已经丢失的远端 stderr，不能据此声称 remote failure 已证明 TRANSIENT_RUNNER_DOCKER_FAILURE，也不声称已重现 Docker failure。
- 全量 Maven：NOT_REQUIRED / NOT_RUN；Java source modified=0。其余治理 admission 的命令、结果与日志记录于本轮 artifact report；没有将未执行项写成 PASS。

两个 delivery checker 曾并发占用同一个 `production-config-admission.log`，第二个启动 exit 1；根因是本轮命令调度产生文件锁，不是新的 repository defect。待首个完成后顺序重跑，未更改 checker 或日志路径来回避验证。

## 6. 边界、回滚与下一动作

P1-2=`REMEDIATED / INDEPENDENT_REVIEW_ACCEPTED / DELIVERY_CI_FAILED`；P1-3=`OPEN`；candidate canonical remaining=1。没有直接 evidence 证明需要重开 P1-2。Machine authority 保持 C1 未接受、C2 NOT_STARTED；LIVE DISABLED、kill ENGAGED、real_provider/private_trading NOT_IMPLEMENTED。

本轮工作区只保留两份 fixture patch、本 evidence 和 inventory；staged=0、commit=NONE、push=NONE、new CI=NOT_RUN。没有 production DB/server/provider、真实 PLACE/CANCEL/transfer/withdraw 或凭证访问。

回滚：仅对这两份 tracked fixture 应用本轮反向补丁，并移除两份本轮新增 evidence 文件；原 C1 commit/failed CI 保留。反向补丁只做 `git apply --check`，不执行回滚，不 reset/rebase/amend。

后续必须先解决远端 Docker 可观测诊断，以及 9 处 active fixture/F009 registry 的授权范围；两项均满足后才能继续 exact staging/follow-up commit/push/new CI。本轮不创建新任务、不追加整改循环，也不进入 `NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-POST-CI-AUTHORITY-TRANSITION-TO-C2`。

工具声明：Git、PowerShell 5.1/7、Python、rg、Maven/Java、Docker、WSL PG16、Gitleaks、GitHub CLI；primary Skill `nq-dh-workflow-router`，supporting `nq-docs-writer`（本文件事实记录）。无子代理。联网仅 origin fetch 与原 GitHub run 查询；无 push、CI dispatch 或生产调用。写入限局部 fixture/evidence、ignored artifacts/target 和自动清理的本地临时测试环境。


## 7. Delivery blocker closure attempt — 2026-09-07

本节是后续任务 `NQ-GATEAUDIT-PHASE6-L4-C1-DELIVERY-BLOCKER-CLOSURE` 的最终记录；第 1–6 节保持上一轮历史，不表示本轮验证结果。

结论：`BLOCKED / PHASE6_L4_C1_DELIVERY_BLOCKER_CLOSURE_NOT_ACCEPTED`。
原因：`F009_HASH_REFRESH_OUTSIDE_AUTHORIZED_FIXTURES / DIAGNOSTIC_RELEASE_HARNESS_REGRESSION`。
按任务书第 24 节，在本地硬门失败后停止技术修改和后续验证，仅固定失败证据；未执行自动第二轮整改、exact staging、commit、push、CI 或 authority transition。

### 7.1 基线、范围与 historical Docker disposition

初始 HEAD 与本地 origin 引用均为 `41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd`，branch=`audit/post-gatey-agent-baseline`，staged=0。本轮未联网刷新 origin。初始四路径与第 6 节及前一 remediation-report 完全一致；SHA256、12 个 reviewed paths、authority 原始哈希和 F009 完整 pre-state 保存于 `artifacts/20260907-c1-delivery-closure/baseline.json`，四路径哈希亦附于 machine inventory 的 closureAttempt。

失败历史 `41c3bbcb… / 34086018265` 不变；canonical historical disposition=`UNCLASSIFIABLE_WITH_RETAINED_EVIDENCE`。保留的远端日志只有 `DISPOSABLE_DOCKER_COMMAND_FAILED`，未保存确切 argv/stderr；出现 RESTORE 与 CLEANUP 阶段不证明 cleanup 是根因。本轮没有新证据可将它归为 TRANSIENT_CONFIRMED、V47_DEFECT_CONFIRMED 或 C1_DEFECT_CONFIRMED。

保留已有远端 PG16 Flyway V47、repository smoke、nq-app smoke PASS，以及前轮 WSL PG16 backup/restore/validate/smoke、7 项负例 PASS。这些是既有证据，未在本轮重跑；它们支持 `NO CURRENT EVIDENCE OF V47 MIGRATION FAILURE`，不能倒推出旧 Docker 命令。本轮没有新 exact-head CI，故不得声明 CURRENT_IMPLEMENTATION_PROVEN 或 NOT_REPRODUCED_AFTER_DIAGNOSTIC_HARDENING。

### 7.2 已写入但未获交付验收的修改

- 两个 Java fixtures 的 9 处 CURRENT_REPOSITORY_SCHEMA 断言已逐行核对后改为 Flyway 同一已解析 migration 集合的最高版本，覆盖 pending migration；未新增 `assertEquals("47", ...)`。原始行号继续保存在 inventory，6+3 处，active stale before=9、after=0。PowerShell `Get-NqMigrationInventory` 不能在 Java 中直接调用，本次 Java 表达式使用同一 canonical SQL 集合，不另设版本字面量；Java 编译与集成执行尚未验证。
- 原三处 delivery fixture schema 来源修复保留；V46 历史事实和 V46/V48/PG17/missing-schema 负例未删除。但本轮 release suite 提前退出，不能复用前轮 69/69 当作当前结果。
- Docker wrapper 增加 `operation / stage / exitCode / stderrAvailable / sanitizedStderr` 失败记录。16 个调用点有显式 operation，stage 跟随原阶段；未改调用参数、既有 AllowFailure probe、重试次数、workflow dependency 或 restore acceptance 条件。stderr 对本轮口令、传入环境值及 credential-bearing 行脱敏，输出有长度上限。诊断实现尚未通过完整 harness 验收，不宣称 gap 已闭合。
- 新增 focused native-stderr 诊断测试，并接入 canonical release tests。PS5.1 需要 UTF-8 BOM 正确解析新增中文注释；测试进程的 ExecutionPolicy Bypass 不修改系统策略。

### 7.3 F009 精确 delta 与授权 blocker

| 项目 | Before | After |
|---|---:|---:|
| compatibility contracts | 18 | 18 |
| contract members | 105 | 105 |
| authorized caller edges | 1559 | 1559 |
| protected exception paths | 178 | 178 |
| actual caller edges | 1559 | 1559 |

protected path set、完整 contract 内容及 actual caller topology 逐项比较不变。registry 唯一 delta 是两个已授权 Java fixture 的 sha256；非摘要 JSON delta=0。精确旧/新 hash 见 inventory closureAttempt 和 `artifacts/20260907-c1-delivery-closure/fixture-hash-refresh.json`。

canonical stage guard 实际结果：exit=1，scanned=1802，reviewed_exceptions=177，errors=2：

```text
STAGE_SEMANTICS: scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1
STALE_EXCEPTION: scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1
```

该诊断脚本本身是 registry 中的 GOVERNANCE_CONTRACT，原摘要 `7c5a4f8095e76eab37bf73e146d378d757c2281dfa377711545a9012966da866`。其 byte change 同样需要摘要同步，但本轮第 10 节只授权两个 Java fixture 的 hash。因此未修改第三项摘要。此问题是额外机械摘要授权范围不足，未证明需要新增 contract/member/caller、parser 或 enforcement change；不能冒称 F009 semantic change 已发生，也不能豁免 stage guard。已有 F009 acceptance authority 未修改，当前未交付 candidate 的 stage validation 为 FAIL。

### 7.4 实际验证及停止点

| 命令/检查 | 本轮结果 |
|---|---|
| standalone diagnostic test / PS7 | 早期候选 PASS；最终兼容性编辑后未单独复跑，不能当最终候选 PASS |
| standalone diagnostic test / PS5.1 -ExecutionPolicy Bypass | 最终候选 PASS；真实子进程 stderr、非零退出、operation/stage/exitCode、脱敏、无 stderr、成功输出与未捕获异常进程失败均执行 |
| `pwsh -NoProfile -File scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1` | FAIL / exit 1；内嵌 diagnostic 的 READ_PORT_MAPPING 成功 case 读到 exitCode=23，提前抛出 DISPOSABLE_DOCKER_COMMAND_FAILED，69 个 release cases 未完成 |
| `python scripts/docs/check-stage-assets.py` | FAIL / exit 1，2 个上述摘要错误；caller topology delta=0 |
| Docker version query | 15 秒 timeout，仅停止本轮 query 子进程，未改 daemon 或删除容器 |
| configured WSL PG16 pg_ctl executable probe | exit 1；仅说明既有配置路径当前无可执行文件，未扫描其他路径 |
| PG16 disposable backup/restore | NOT_RUN；Docker/既有 WSL 路径不可用，且已触发硬门停止 |
| 两个 Java integration classes / 135 delivery mutations / 其余 governance / Gitleaks | NOT_RUN；按硬门失败停止，不能将前轮结果冒充本轮 |
| authority PS5.1/PS7、next-action、lifecycle、stage regression、doc links | NOT_RUN；按硬门失败停止；authority bytes 已独立比对不变 |
| git diff --check / source scope and fingerprint check | PASS；staged=0，C1 reviewed bytes、authority bytes、C2、V1–V47、production trading correctness changes=0 |

日志位于 `artifacts/20260907-c1-delivery-closure/`：`docker-diagnostic-ps7.log`、`docker-diagnostic-ps51.log`、`release.log`、`stage.log`；boundary 和机器结果在 `boundary-final.json`。已保留失败命令，不做自动第二轮修复。执行准备曾出现 UTF-8 文本被 GBK 读取、PS5.1 ExecutionPolicy 拒绝和无 BOM 中文源码解析失败，均在正式硬门运行前处理；不把这些启动失败算作测试 PASS。

### 7.5 未交付状态、回滚与下一步

HEAD 不变；staged=0；commit=NONE、push=NONE、new CI=NOT_RUN。工作区为 6 tracked modifications + 3 untracked source/evidence files，均在任务目标范围内；初始 Install-NqCanonicalReleaseCi.ps1 原始 bytes 未再修改。P1-2 不接受，P1-3 仍 OPEN；不推进 C2，不变更 STATUS/ROADMAP/next_action/accepted_batch。

当前 candidate 不可提交。后续须先明确诊断脚本第三项机械摘要同步的授权范围，并在新一轮授权中修复 release harness 诊断测试失败及完成全部未执行验证。本次不追加独立 correctness review，不把交付失败当作 C1 correctness defect。

回滚仅针对本轮增量：`artifacts/20260907-c1-delivery-closure/this-turn.patch` 记录从四文件既有状态到本轮候选的增量，使用 `git apply --reverse --check` 预检；只有另行决定回滚时才执行 `git apply --reverse`。初始两份 evidence 原始 bytes 与所有本轮修改文件的 before 内容保存于同目录 `initial-files/`；不清除用户前轮 fixture/evidence，不 reset、amend 或改写 C1。

建议 commit message（仅在后续全部验收通过后）：`fix(ci): close C1 delivery schema and Docker diagnostics gaps`。本轮未提交。

工具声明：外部工具为 Git、PowerShell 5.1/7、Python、rg、Docker query、WSL executable probe；工具调用经 functions 执行，无 connector MCP、子代理或网络访问。Skills：primary nq-dh-workflow-router；supporting java-backend-regression-tests（Java expected-value 与执行边界）、nq-docs-writer（本证据续写）。写入限上述 9 个候选路径及 ignored artifacts；无生产、真实交易、凭证读取、provider、部署或远端写操作。


## 8. Attempt-02 — local delivery gates passed, exact-head CI pending

任务：`NQ-GATEAUDIT-PHASE6-L4-C1-DELIVERY-BLOCKER-CLOSURE-ATTEMPT-02`。本节为提交前证据，`PASS / LOCAL_DELIVERY_GATES / PENDING_EXACT_HEAD_CI`，不构成 C1 authority acceptance；Attempt-01 的两个 blocker 和全部失败历史保留在第 7 节。

### 8.1 基线及 B2 精确根因

HEAD、fetched origin=`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd`；branch=`audit/post-gatey-agent-baseline`，初始 staged=0。九个保留文件与 Attempt-01 报告及 `this-turn.patch` 反向预检一致，未 reset；逐文件原始备份、hash、F009 原始 JSON 和 caller graph 固定于 `artifacts/20260907-c1-delivery-attempt02/baseline.json` 与 `initial-files/`。

唯一分类：`Case A / TEST_FIXTURE_STATE_LEAK`。调用链是 release suite → diagnostic suite → AST 载入的真实 Invoke-Docker → test fake docker → 当前 PowerShell native 子进程。fake 写入的 `$script:LASTEXITCODE=23` 遮蔽随后 native 子进程更新的 `$global:LASTEXITCODE=0`；fake 的未限定 `$LASTEXITCODE` 又读回旧值 23，wrapper 因而正确拒绝收到的失败码。`nested-trace.log` 同时固定 `configured=0 / global=0 / script=23 / wrapper=23`，不是命令匹配问题，也未证明 production wrapper 将真实 0 改为 23。

最小修复仅在 Test-NqDockerDiagnostics.Tests.ps1：fake 将刚执行 native 子进程的 global 自动退出码传到 caller scope，不再创建可跨 case 泄漏的 script 副本。没有接受 23、忽略错误、扩大 stub 匹配、改变 release/schema compatibility 或跳过 cleanup。Attempt-02 production diagnostic script bytes 与 Attempt-01 完全相同。

### 8.2 诊断与 schema 证明

PS5.1 与 PS7 standalone 以及完整 release nested harness 均通过。真实子进程非零 23 仍抛 `DISPOSABLE_DOCKER_COMMAND_FAILED`；stage、operation、exitCode、stderrAvailable、sanitizedStderr 可见；known fixture values、credential URL 和 API key fixture 在输出中均不存在。empty stderr 有明确 false；负例之后正例仍为 `negative=23 / positive=0`，未捕获失败的外层进程 exit 非零，成功 case 无失败异常。PS5.1 使用进程级 ExecutionPolicy Bypass 和 UTF-8 BOM，不改变系统配置。

九处 CURRENT_REPOSITORY_SCHEMA 断言保持前轮已授权修改：预期值来自 Flyway 已解析 canonical SQL migration 集合最高版本，包含 pending，未重复硬编码 V47。current-schema stale before=9、after=0；历史 V46、固定旧迁移输入、V46/V48 incompatibility、missing-schema 和 PG17 wrong-major 负例均保留。两份 Java fixtures 在 PG16.15 实际执行，共 10 tests，failures=0、errors=0、skips=0。

### 8.3 三项机械 F009 摘要同步

只刷新 LiveSessionFactModelPostgresIntegrationTest.java、OperatorPilotAuthorityPostgresIntegrationTest.java 和 Invoke-NqCanonicalRestoreDrill.ps1 三项既有 sha256。两个 FIXTURE_IDENTITY 与一个 GOVERNANCE_CONTRACT 分类不变；精确 path、旧/新摘要见 machine inventory 的 closureAttempt02.digestRefresh。

| 项目 | Pre | Post |
|---|---:|---:|
| compatibility contracts | 18 | 18 |
| contract members | 105 | 105 |
| approved/actual caller edges | 1559 | 1559 |
| protected paths | 178 | 178 |

完整 registry 恢复三项旧摘要后与 committed baseline JSON 全等；contract/member、protected path set、caller topology、classification、exception scope 与 enforcement 的语义 delta=0。无 parser/checker 修改，无新/删 caller；F009 acceptance 不重开，不追加 review。canonical checker=`scanned=1802 / reviewed_exceptions=178 / errors=0`。

### 8.4 本地验证

| 范围与命令 | 实际结果 |
|---|---|
| `pwsh -NoProfile -File scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1` | 69/69 PASS，含嵌套诊断、V47 正例、V46/V48/missing schema/PG17 拒绝；无失败或跳过 |
| canonical Test-CanonicalDeliveryWorkflow.ps1 与对应 Tests.ps1 | real contract PASS；135/135 mutations REJECTED，0 accepted；mandatory production-config Maven tests 实际执行 |
| `mvn -o -f backend/pom.xml -pl nq-app -am test -Dtest=LiveSessionFactModelPostgresIntegrationTest,OperatorPilotAuthorityPostgresIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.postgres.smoke.required=true`，附本轮 loopback fixture 连接参数 | 6+4=10 PASS，failures/errors/skips=0；临时 cluster 完整清理，证据 java-results.json |
| check-stage-assets.py / stage regression | checker errors=0；Windows 47 PASS+2 既有 symlink 权限条件跳过，Linux 49/49 PASS、0 skip |
| check-current-authority.ps1 | PS5.1 与 PS7 errors=0 |
| next-action / agent workflow / lifecycle | failed=0；12/12；20/20 |
| check-doc-links.ps1 | checked=425，errors=0，123 既有历史 warnings |
| pinned Gitleaks 8.18.4 / execution negatives | findings=0；missing/finding/scanner-error 均拒绝；复用缓存 archive 并核对 canonical lock 与已缓存 official checksum，CI 配置未放宽 |
| git diff --check / frozen boundary | PASS；reviewed bytes、authority、C2、V1–V47、production trading delta 全部为 0 |

完整命令/日志位于 `artifacts/20260907-c1-delivery-attempt02/`，JSON 汇总为 `local-validation.json`。Maven 使用离线缓存；未运行全量 backend suite，不把 required module dependencies 编译称为 full backend regression。本轮测试统计辅助 PowerShell 命令曾出现 empty-pipe 解析错误，调整结果收集语法后取得真实 XML 属性；Java 测试本身未因此失败或重跑。B2 原始失败已通过独立 trace 留存。

### 8.5 Local restore 与历史 Docker 区分

Docker version query 15 秒 timeout，只终止 query 子进程；Docker mode=`NOT_RUN / LOCAL_DOCKER_API_UNAVAILABLE`。旧 `/tmp` runtime cache 不存在，但项目既有 `artifacts/20260907-l4-plan-gate-remediation/pg16-runtime` 可执行，实际版本 PG16.15。复用该缓存并显式传给原 `-ExecutionMode WslPg16 -WslPostgresqlRoot`，不使用本机 PG17，不下载或安装软件。

PG16 V47 backup、restore、Flyway validate、repository smoke、app smoke 和 7 项负例全部 PASS，pending=0；source/restored canary 同为 `75|465|276|47|V47|1`。两个 canonical restore-proof consumer PASS；drill exit=0、cleanup 正常完成，并独立确认本轮 `/tmp/nq-canonical-pg-<runId>` 已不存在。证据 `restore-wsl/restore-proof.json`。这只证明本地 WSL execution 和诊断负例，不替代远端 Docker CI。

`34086018265 / DISPOSABLE_DOCKER_COMMAND_FAILED` 永久保留为 `UNCLASSIFIABLE_WITH_RETAINED_EVIDENCE`。旧 stderr 丢失，不能回溯定位实际失败命令，也不改称 TRANSIENT/V47/C1 defect。已有远端 migration/repository/app smoke PASS 保留为历史事实；closure 取决于新的 exact-head CI，而非追认旧 run 成功。

### 8.6 交付与 authority 边界

本提交预期是 `41c3bbcb…` 的 follow-up child，禁止 amend。九个 exact candidate paths、三项摘要 delta 与 12 个 C1 reviewed path 的交集为 0。C1 independent correctness review 及 F009 acceptance 保持 VALID，additional independent review=NOT_REQUIRED；本轮不执行 authority transition，不实现 C2。

提交前本文不虚构自己的 SHA 或尚未执行的 CI。新 `C1_DELIVERY_ACCEPTANCE_HEAD`、push/head equality 与 exact-head CI 九项 job 结果保存在独立 `artifacts/20260907-c1-delivery-attempt02/delivery-receipt.json` 及交付回复，待下一 authority-transition 任务绑定；不为了把未来 CI 写回本文而 amend 已验证提交。只有九项全部 SUCCESS 后才能声称交付 blockers closed。若新 PostgreSQL/Flyway 失败，只按新诊断分类并停止，不自动再整改。

回滚：保留初始九文件原始副本；Attempt-02 增量 `attempt02.patch` 只在 `git apply --reverse --check` 预检后使用，不丢弃 Attempt-01 候选。整个 follow-up commit 如需回滚，使用后续反向提交，不改写原 C1 或远端历史。建议提交信息：`fix(ci): close C1 delivery schema and diagnostic gaps`。

工具声明：Git、PowerShell 5.1/7、Python、rg、Maven/Java、WSL PG16、Docker query、Gitleaks、GitHub CLI；primary nq-dh-workflow-router，supporting java-backend-regression-tests 与 nq-docs-writer。无子代理、connector MCP、production/real-provider/交易调用。网络限 GitHub fetch/read 和任务明确授权的后续 push/CI；写入限九个候选文件与 ignored artifacts/target、自动清理的 disposable fixture。
