# NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX — attempt-01

## 1. 结论

```text
IMPLEMENTED /
ALL_RC_FINDINGS_CLOSED /
DISPOSABLE_LINUX_VALIDATION_PASSED /
NEW_RC_READY_FOR_REVIEW /
COMMITTED /
CI_GREEN /
PRODUCTION_DEPLOYMENT_NOT_STARTED /
ATTEMPT_10_NOT_AUTHORIZED
```

旧 RC `5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` 继续保持
`REVIEW_REJECTED / NOT_DEPLOYABLE`（审查已拒绝 / 不可部署），废弃提交
`f54cdc810cddad52e14048f72fe1ad32e6a22472` 继续保持
`SUPERSEDED / INVALID_JAR / NOT_DEPLOYABLE`（已替代 / JAR 无效 / 不可部署）。

唯一新 RC source 为：

```text
ef803568ed56905cb9969477e1ad777d5a01faf6
```

该 RC 只达到 `RC_READY_FOR_REVIEW / PRODUCTION_DEPLOYMENT_NOT_STARTED`
（RC 可进入审查 / 生产部署未开始），尚未被独立接受，也不构成生产部署或 Attempt-10 授权。

## 2. Task classification 与范围

- Task classification：`RELEASE_CANDIDATE_REMEDIATION`；辅助类型为
  `CODE_CHANGE / CI_CD / DOCUMENTATION`。
- Repository：`E:\Project\nexus-quant`。
- 代码范围：GateW launcher/readback、builder/verifier/installer contract 及对应测试。
- Evidence/authority 范围：本 evidence、current authority/ledger/index、governance
  exact-triple contract 与 regression。
- 明确排除：production SSH/deploy/current、LIVE、OKX、真实 credential、生产 DB、
  Attempt-10、acceptance clock、worker、freeze/archive/tag、frontend、research、migration、
  `.github` 与 `docs/gates`。

## 3. Starting baseline、代码提交与 CI

| 项目 | 结果 |
| --- | --- |
| starting HEAD | `295813a61b4f7facc78fd3cff6dfbd3d5a53b7fb` |
| starting exact-head CI | `30596185610 / completed / success / 10 of 10` |
| Commit A | `8db984f3369cec7b6f66d613daa13651e211b682` |
| Commit A exact-head CI | `30607922128 / completed / success / 10 of 10` |
| Linux follow-up commit | `ef803568ed56905cb9969477e1ad777d5a01faf6` |
| follow-up exact-head CI | `30616271884 / completed / success / 10 of 10` |
| final RC source timestamp | `2026-07-31T08:25:26Z` |

Commit A 关闭原独立 review 的 4 个 P1 与 1 个 P2。Disposable Linux canonical build
随后暴露 Windows `core.autocrlf=true` 会把 Git LF blob 转成 CRLF，使 35 个 migration
与 3 个 CSV resource 跨 OS 漂移；follow-up commit 仅在 canonical detached worktree
创建时固定 `core.autocrlf=false / core.eol=lf`，未修改 migration 或业务逻辑。

## 4. P1-1 Java 21 runtime contract

- Builder、manifest、verifier 与 launcher 的 `requiredJavaMajor` 统一为 `21`。
- Builder 与 verifier 均实际执行 Java 并解析 major，不再只检查 executable 存在。
- Linux Java 21.0.11：`PASS / IMMUTABLE_RELEASE_VERIFIED`。
- Linux 真实 Java 17.0.19：exit `2`，
  `BLOCKED / JAVA_MAJOR_VERSION_MISMATCH`；随后已恢复 Java 21。
- Java 17/20/21/22 与无法解析版本由 builder/release regression 覆盖。
- 失败 taxonomy 保持：
  `JAVA_RUNTIME_NOT_FOUND / JAVA_VERSION_UNREADABLE / JAVA_MAJOR_VERSION_MISMATCH`。

## 5. P1-2 Java process timeout 与清理

- Timeout：固定有界 `30s`。
- stdout/stderr：独立异步读取，避免 pipe deadlock。
- 非零退出、timeout、output read failure 与 termination failure 使用不同 closed taxonomy。
- timeout 后终止进程树并等待清理；正式 Linux 容器使用 Docker `--init`，避免 PID 1
  不回收已终止 orphan 而形成 zombie。
- Linux control self-test：`71/71 PASS`；
  `javaProcessTimeout=30s / PROCESS_TREE_CLEANED`，
  `javaProcessOutput=PASS / ASYNC_STDOUT_STDERR`。

## 6. P1-3 clean exact-commit build

Canonical Maven command：

```text
mvn --offline --quiet -f backend/pom.xml -pl nq-app -am -DskipTests clean package
```

- 仅接受 clean exact commit。
- 每次创建 fresh detached worktree；目标模块 `target` 预存在时 fail-closed。
- 只打包本次 `clean package` 的 `target/classes` 与 dependency closed set。
- 预置伪造 classes、旧 JAR、build 后额外 class、dirty worktree、错误 source commit
  均由 builder/reproducibility regression 拒绝。
- Windows PowerShell 5.1、Windows PowerShell 7 与 Linux PowerShell 7 三次 exact build
  的 manifest bytes、bundle bytes 与全部 131 个 artifact descriptors 精确相同。
- 三次 builder 均报告 detached worktree `CREATED_AND_REMOVED`。

Linux 初次 builder 的 `RELEASE_BUILD_COMMAND_FAILED` 根因是 disposable Maven cache
缺少 plugin-prefix metadata 和 2.8 artifact provenance marker，不是 source build failure。
只补齐 Maven 公共 metadata/artifact cache 并把 mirror marker 规范化为正式 offline
repository id 后，原 canonical 命令在 no-egress 环境通过；未放宽 builder hard gate。

## 7. P1-4 strict ResultSet mapping

- 仅接受 `Byte / Short / Integer / Long`。
- 允许范围：`0..Integer.MAX_VALUE`。
- `NULL`、列缺失、负数、`Long.MAX_VALUE`、`BigDecimal` 整数/非整数、`BigInteger`、
  `String`、`Boolean` 与不支持对象均拒绝。
- 任一 count mapping 异常统一进入
  `RESULT_MAPPING / RESULT_MAPPING_FAILED`，不得继续 readiness 计算。
- Focused mapping test：`2/2 PASS`；support test 中 mapping closed taxonomy 同时覆盖。

## 8. P2 JAR duplicate-entry policy

- 重复文件 entry：`RELEASE_JAR_DUPLICATE_FILE_ENTRY`。
- 大小写归一冲突：`RELEASE_JAR_CASE_COLLISION`。
- 路径规范化冲突：`RELEASE_JAR_NORMALIZED_PATH_COLLISION`。
- 绝对路径、路径穿越与非法路径：`RELEASE_JAR_ENTRY_PATH_INVALID`。
- 仅完全相同的重复目录 entry 允许并计数。
- 新 RC：122 JAR 全部完整；duplicate file=`0`，
  duplicate directory=`4`，对应 `archunit-1.3.0.jar` 的合法目录重复。

## 9. Disposable Linux

| 项目 | 结果 |
| --- | --- |
| Ubuntu | `24.04.4` |
| PowerShell | `7.5.2` |
| Java | `21.0.11`；真实负例 `17.0.19` |
| PostgreSQL | PG17 internal sidecar；focused test 使用同容器 loopback PG16 |
| Network | Docker internal network，`internal=true`，正式容器无 default route |
| AST | `12 files / 0 errors` |
| control / worker / fail-close | `71 / 59 / 8`，全部 PASS |
| builder / installer | PASS / PASS |
| remediation / security / release | `32 / 12 / 23`，全部 PASS |
| focused Maven | `51 tests / 0 failures / 0 errors / 1 skipped` |
| DB rollback | `exchange_accounts=0`；`exchange_account_credentials=0` |
| Java 21 verifier | `PASS / IMMUTABLE_RELEASE_VERIFIED / posixVerified=true` |
| Java 17 verifier | `BLOCKED / JAVA_MAJOR_VERSION_MISMATCH` |

Manual private launcher test 保持 1 个 skipped；未启用真实 provider、OKX 或 credential。
Focused DB 正例只使用 disposable loopback DB 与事务 fixture。

## 10. 新 RC identity 与 reproducibility

| 项目 | 值 |
| --- | --- |
| source commit | `ef803568ed56905cb9969477e1ad777d5a01faf6` |
| source exact-head CI | `30616271884 / completed / success / 10 of 10` |
| required / actual Java | `21 / 21` |
| source timestamp | `2026-07-31T08:25:26Z` |
| manifest SHA-256 | `ba5f9c0536c3bc142ff6e44f194f12ab3ed29935e432b23e33ec55ed709752f5` |
| bundle SHA-256 | `75ef45cf0d61cd10be76992b981f6b4ebfb3418ad19e66931a286f79016a7c17` |
| artifact / JAR / USTAR | `131 / 122 / 132` |
| duplicate directory entries | `4` |
| A/B/Linux manifest bytes | `identical=true` |
| A/B/Linux bundle bytes | `identical=true` |
| A/B/Linux descriptors | `identical=true` |
| missing / extra / undeclared | `0 / 0 / 0` |
| server Git references / sensitive artifacts | `0 / 0` |
| tamper | exit `2 / BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH` |

## 11. Tests executed

- PowerShell 5.1/7 Windows builder self-test：PASS。
- Linux PowerShell AST：12/0。
- Linux builder/control/worker/fail-close/installer：全部 PASS。
- Linux remediation/security/reproducibility：32/12/23，全部 PASS。
- Linux focused Maven：51 tests，0 failure，0 error，1 skipped。
- Commit A exact-head CI：10/10。
- Follow-up exact-head CI：10/10。
- Linux canonical tar 解包后 POSIX verifier：PASS。
- Java 17 mismatch 与 canonical tamper：均按稳定 taxonomy 拒绝。

非阻断 warning：Mockito/Byte Buddy 在 Java 21 动态加载 test agent；不影响测试结果。

## 12. Security / side-effect boundary

```text
Production SSH=0
Production deployment=0
Server changes=0
Production current switch=0
Production database reads/writes=0/0
OKX calls=0
Credential material access=0
Attempt-10 created=false
Acceptance clock created=false
Worker started=false
LIVE enable=0
Trading write path=0
AI/DH runtime=0
Freeze/archive/tag=0
```

Disposable fixture SQL 只作用于任务专用 loopback PostgreSQL，事务回滚后账户与 credential
metadata 行均为 0；不属于生产 readback implementation。

## 13. Findings

- P0：无。
- P1：无；原 4 个 P1 与 follow-up 暴露的跨 OS EOL residual 均已关闭。
- P2：无；duplicate-entry policy 已成为 verifier hard gate。
- P3：无。

## 14. Authority after 与下一动作

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
work_batch_status=IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING
work_batch_commit=ef803568ed56905cb9969477e1ad777d5a01faf6
work_batch_ci_run=30616271884
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

Attempt-09 保持 `REJECTED`；Attempt-10 保持 `NOT_CREATED / NOT_AUTHORIZED`。
GateW 保持 `IN_PROGRESS / NOT_FROZEN`。唯一下一动作是对新 RC 做独立 review；
不授权生产部署、Attempt-10、freeze/archive/tag。

## 15. Rollback

- 代码回滚：对 Commit A 与 follow-up commit 执行 forward revert；不得回退或改写历史。
- Authority/evidence 回滚：对本 task 的 docs/governance commit 执行 forward revert。
- Disposable 资源在任务结束时删除；它们不是运行时或部署回滚点。
- 回滚后旧 RC 仍保持 rejected，不得因此恢复部署资格。
