# NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW — attempt-01

## 1. 结论

```text
FAIL /
GATEW_ATTEMPT_10_RC_REVIEW_REJECTED /
RELEASE_CANDIDATE_REMEDIATION_REQUIRED
```

固定 RC `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` 存在 4 个 P1 和 1 个 P2，不能进入生产部署验证。审查期间未修复 RC；Attempt-10 继续保持 `NOT_CREATED / NOT_AUTHORIZED`（未创建 / 未授权）。

唯一下一动作：

```text
NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
```

## 2. Task classification 与范围

- Task classification：`SECURITY_AUDIT`；辅助类型为 `CODE_ANALYSIS / CI_CD / DOCUMENTATION`。
- Repository：`E:\Project\nexus-quant`。
- Review source：固定 RC `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6`。
- Production diff：`3bfb4e5bcc2fa30db1d75e9162b1121ff6bf4b60..5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6`。
- Authority/evidence diff：`5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6..bcccf29e0f492220e5d04ec8bba4ff7eff70087b`。
- 允许写入：本 evidence、current authority/ledger/index，以及支持精确拒绝状态所需的最小 governance contract/test。
- 明确排除：`backend/**`、`scripts/gatew/**`、`frontend/**`、`research/**`、`deploy/**`、`.github/**`、migration、WIP 分支、`docs/gates/**`、历史 GateW evidence。

## 3. Starting authority 与前置检查

| 项目 | 结果 |
| --- | --- |
| branch | `dev` |
| worktree / staged | clean / empty |
| starting HEAD | `bcccf29e0f492220e5d04ec8bba4ff7eff70087b` |
| `origin/dev` | `bcccf29e0f492220e5d04ec8bba4ff7eff70087b` |
| starting exact-head CI | `30579834555 / completed / success / 10 of 10` |
| RC source commit | `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` |
| RC exact-head CI | `30576297678 / completed / success / 10 of 10` |
| current authority checker | PASS（通过） |
| Attempt-10 | `NOT_CREATED / NOT_AUTHORIZED` |

第一段 diff 仅包含 6 个 RC implementation/test/release-tooling 文件；第二段仅包含 current docs/evidence 与两个 governance 文件。`bcccf29e...` 不含 RC 后生产代码变化，因此：

```text
RC_SOURCE_DRIFT=0
```

明确废弃的 `f54cdc810cddad52e14048f72fe1ad32e6a22472` 保持：

```text
SUPERSEDED /
NOT_RELEASE_CANDIDATE /
NOT_DEPLOYABLE
```

## 4. Findings

### P0

无。

### P1-1 — release runtime contract 错误声明 Java 17

证据：

- `backend/pom.xml:15-16` 将编译 release 固定为 Java 21。
- `scripts/gatew/build-gatew-release-bundle.ps1:541-545` 将 manifest `requiredRuntime.javaMajor` 写为 `17`。
- `scripts/gatew/verify-gatew-release.ps1:363-369` 只接受 `javaMajor=17`。
- `scripts/gatew/verify-gatew-release.ps1:504-519` 在 Linux 仅检查 `/usr/bin/java` 存在，不校验实际 major。

影响：Java 17 环境可通过 release verifier，但无法可靠运行 Java 21 class；“Java 21 合同明确”未满足。该问题会把不兼容 runtime 错误判定为可部署，阻断 RC。

### P1-2 — Java launcher 缺少进程级有界 timeout

证据：

- `scripts/gatew/gatew-okx-readonly-soak-control.ps1:1380-1393` 直接同步调用 `/usr/bin/java` 并在返回后读取 `$LASTEXITCODE`。
- 实现不存在 `WaitForExit(timeout)`、job timeout、超时 kill 或超时 cleanup。
- 现有 timeout 只覆盖 JDBC/health probe，不能约束 JVM 本身卡死。

影响：canonical `precreate-prerequisite` 可在创建 Attempt 前无界阻塞；附件要求的“timeout 明确且有界”未满足。

### P1-3 — 固定 RC 无法重建声明的 canonical hashes

两个独立 detached worktree 分别使用 Windows PowerShell 5.1 与 PowerShell 7 构建。两次本轮构建彼此 bytes identical，但均无法重建声明基线：

| 产物 | 声明 SHA-256 | 本轮重建 SHA-256 |
| --- | --- | --- |
| manifest | `cbc2c0c49ec1bce7b0bf7211535b2face2e4c37471d5d4d645cfe52ec4dbce7b` | `5d9464079e869ff0f868b303adffcf9bdd25f782443473201b160284ba27740c` |
| bundle | `84446b2d9631780df1e921b57ec62a56658e9a2998914e2a05b66cddf5e952d3` | `475a7037ab527fcdf9ed396495ba3a3f988c032f7facfa5948d9a34715d15d3a` |

使用声明 manifest hash 验证本轮 exact-commit build，精确返回：

```text
BLOCKED / RELEASE_MANIFEST_HASH_MISMATCH
```

补充代码证据：

- `scripts/gatew/build-gatew-release-bundle.ps1:318-322` 执行 `mvn ... install`，未执行 `clean install`。
- `scripts/gatew/build-gatew-release-bundle.ps1:338-350` 随后对各模块现存 `target/classes` 整体打包。

影响：fixed source commit 与声明 release identity 之间不存在本轮可重建的确定映射，构建输入未完全闭合，违反 reproducible release hard gate。

### P1-4 — null / 类型错误未进入 mapping failure

证据：

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java:1015-1017` 的 `longValue` 对 `null` 或非 `Number` 静默返回 `0L`。
- 现有测试覆盖 `Long.MAX_VALUE` overflow，但未覆盖 null/错误类型必须进入 `RESULT_MAPPING_FAILED`。

影响：错误类型被投影为业务值 `0`，虽然会形成 blocker 而非直接 fail-open，但没有按 closed taxonomy 识别 mapping failure；附件要求“null、类型错误和 mapping failure fail-closed”未完整满足。

### P2-1 — JAR duplicate-entry policy 未形成显式 hard gate

- 122/122 JAR 均可读取且 CRC/结构检查通过。
- `archunit-1.3.0.jar` 存在 4 个重复目录 entry；未发现重复文件 entry。
- 当前 release verifier 未定义或执行 duplicate-entry policy。

影响：本轮未发现可执行 class/resource 覆盖，但附件要求的 `duplicate entry policy PASS` 不能由当前 verifier 证明。

### P3

无。

## 5. Launcher / JDBC / sanitized schema 审查

| 检查项 | 结论 |
| --- | --- |
| Java executable | 固定 `/usr/bin/java`，但 major contract 错误，FAIL（失败） |
| Java version | source=21，release manifest/verifier=17，FAIL |
| classpath | 来自 release closed set；但 builder 输入未完全闭合 |
| main class | 固定 `GateWOkxReadonlySoakCycleTest$PrerequisiteMain` |
| arguments | 数组构造；未把 credential/数据库参数拼入 shell command |
| JVM exit | 返回后读取并传播；无进程 timeout |
| stdout/stderr | 分离；stdout closed JSON contract 与 stderr marker 回归通过 |
| PowerShell JSON parse | fail-closed taxonomy 回归通过 |
| JDBC query | single bounded aggregate SELECT；production/tooling 增量无 write SQL |
| ResultSet mapping | overflow 有覆盖；null/type mapping 不满足明确 taxonomy |
| unknown readiness | 未发现 unknown fallback 为 ready |
| sanitized schema | allowlisted closed schema；敏感字段/原始异常运行时回归通过 |
| raw exception exposure | 未发现 |

## 6. Cross-platform 与 disposable Linux

| 环境 / 场景 | 本轮结果 |
| --- | --- |
| Windows PowerShell 5.1 | PASS |
| PowerShell 7 on Windows | PASS |
| PowerShell 7 on Linux | `NOT_RERUN / EVIDENCE_REVIEW_ONLY` |
| WSL/disposable evidence review | Ubuntu、Java 21.0.12、PowerShell 7.5.2、loopback PostgreSQL 17；证据可读 |
| disposable positive | 历史固定 RC evidence 中 `readyForAttemptCreation=true` |
| disposable negative | account/credential/count/type/status/permission/trade/withdraw/IP/management/PostgreSQL/query 等 15 场景 |

本轮 WSL Ubuntu 中 `pwsh` 已被历史任务清理，复跑命令 exit `127`。未擅自安装系统包。该项不覆盖 Windows 本轮回归结果，也不把历史 disposable evidence 写成本轮重跑；由于已有独立 P1，RC 已拒绝。

`-WindowStyle Hidden` 的 RC 修正仅保留在 Windows path；Linux path 不依赖部署时临时删除参数。本轮未发现按 OS 放宽安全检查。

## 7. Release / JAR / tamper 结果

| 项目 | 结果 |
| --- | --- |
| artifact count | `131` |
| USTAR entries | `132` |
| missing / extra / undeclared | `0 / 0 / 0` |
| cross-engine bytes | 本轮两次重建彼此 identical |
| declared hashes | 无法重建，P1 |
| JAR CRC/structure | `122/122 PASS` |
| duplicate file entry | `0` |
| duplicate directory entry | `4`（`archunit-1.3.0.jar`） |
| tamper rejection | `BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH` |
| server Git references | `0` |
| sensitive artifacts | `0` |

临时资源清理：

- detached worktree `w51` / `w7` 已执行精确 `git worktree remove --force`。
- `git worktree prune` 后注册匹配数为 `0`。
- `E:\Project\nq-rc-review-019fb59e`（含 `o51`、`o7`、`tamper`）已删除，`exists=false`。

## 8. Tests rerun

| 验证 | 结果 |
| --- | --- |
| PowerShell 5.1 / 7 AST（9 files） | PASS / PASS |
| builder self-test | PASS / PASS |
| installer / control / worker / fail-close | PASS；`66 / 59 / 8` cases |
| release reproducibility regression | PS5.1/PS7 各 `16/16 PASS` |
| remediation regression | `32/32 PASS` |
| security regression on Windows | PS5.1/PS7 各 `12/12 PASS` |
| focused Maven | `49 tests / 0 failures / 0 errors / 2 skipped`；23 modules BUILD SUCCESS |
| JAR CRC/structure | `122/122 PASS` |
| governance next-action / lifecycle / task-evidence | PASS |
| current authority checker | PASS |
| docs/current link checker | PASS；1 条既有 warning，0 error |
| `git diff --check` | PASS |

执行编排中保留的失败：

- 首次 AST 命令双层 quoting 错误，测试未启动；修正后同范围重跑通过。
- 首次长路径 exact build 返回 `BLOCKED / RELEASE_ARCHIVE_PATH_INVALID`；改用短路径后两次构建成功。
- 首次 doc checker 遗漏 `-Roots`；补参后通过。
- 首次 WSL CRC bash quoting 失败；随后逐 JAR `unzip -tqq` 重跑 `122/122 PASS`。

## 9. Security / side-effect boundary

RC production/tooling added-lines 静态扫描结果：

```text
external network / OKX=0
DB write SQL=0
Attempt or clock creation=0
worker or systemd mutation=0
order / cancel / transfer / withdrawal=0
AI / DH runtime=0
```

测试增量中的 `INSERT / UPDATE / DELETE` 仅用于 disposable PostgreSQL fixture，不属于 production readback implementation。

本轮实际副作用：

```text
Production SSH=0
Production deployment=0
Server changes=0
current switch=0
Attempt-10 created=false
acceptance clock created=false
worker started=false
OKX calls=0
credential material access=0
production database reads/writes=0/0
LIVE enable=0
AI/DH runtime=0
freeze/archive/tag=0
```

## 10. Git / CI 与 authority after

Review evidence commit：`9705fdf9e03643cc1cd34d8bc6eb3dd5dc9eb17f`。

Review exact-head CI：`30596025850 / completed / success / 10 of 10`，`headSha=9705fdf9e03643cc1cd34d8bc6eb3dd5dc9eb17f`。

Authority after：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION
work_batch_status=REVIEW_REJECTED|REMEDIATION_REQUIRED
work_batch_commit=5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6
work_batch_ci_run=30576297678
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
```

Attempt-09 保持 `REJECTED`；Attempt-10 保持 `NOT_CREATED / NOT_AUTHORIZED`。GateW 保持 `IN_PROGRESS / NOT_FROZEN`，不得进入生产部署、Attempt-10、freeze/archive/tag。

## 11. Rollback

本轮未修改 RC 生产代码或服务器。文档/治理变更的回滚方式是对 review evidence/current authority/governance commit 执行 forward revert；不得回退、覆盖或改写 RC 历史。回滚后仍不得把 RC 视为通过，除非独立修复任务完成并重新审查。
