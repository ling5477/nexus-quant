# NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW — attempt-02

## 1. 结论

```text
FAIL /
GATEW_ATTEMPT_10_RC_REVIEW_REJECTED /
RELEASE_CANDIDATE_REMEDIATION_REQUIRED
```

固定 RC `ef803568ed56905cb9969477e1ad777d5a01faf6` 存在 1 个 P1：
`UNSAFE_DUPLICATE_JAR_ENTRY`。release verifier 会把同名、带非空载荷的重复目录
entry 放行，且不读取 entry 数据以验证 CRC；因此不满足本任务规定的 JAR 完整性与
duplicate-entry fail-closed hard gate。不得进入生产部署验证。

本轮没有修复 RC；Attempt-10 继续保持 `NOT_CREATED / NOT_AUTHORIZED`（未创建 /
未授权）。唯一下一动作是：

```text
NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
```

## 2. Task classification 与范围

- Task classification：`SECURITY_AUDIT`；辅助类型为 `CODE_ANALYSIS / CI_CD /
  DOCUMENTATION`。
- Repository：`E:\Project\nexus-quant`；任务归属为 NQ-only。
- Review source：固定 RC `ef803568ed56905cb9969477e1ad777d5a01faf6`。
- Production diff：`295813a61b4f7facc78fd3cff6dfbd3d5a53b7fb..ef803568ed56905cb9969477e1ad777d5a01faf6`。
- Authority/evidence diff：`ef803568ed56905cb9969477e1ad777d5a01faf6..5aa0c70f7f0aa83412d6014be500b265443d4283`。
- 允许写入：本 evidence、`docs/current/evidence/gate-w/README.md`、`STATUS.md`、
  `ROADMAP.md`、`README.md`、`TESTING.md` 与 `WORKLOG.md`。
- 明确排除：`backend/**`、`scripts/gatew/**`、`frontend/**`、`research/**`、
  `deploy/**`、`.github/**`、migration、WIP 分支、`docs/gates/**`、历史 evidence，
  以及生产服务器、OKX 与真实凭证。

## 3. Starting authority 与前置检查

| 项目 | 结果 |
| --- | --- |
| branch | `dev` |
| worktree / staged | clean / empty |
| starting HEAD / `origin/dev` | `5aa0c70f7f0aa83412d6014be500b265443d4283` |
| starting exact-head CI | `30618511789 / completed / success / 10 of 10` |
| RC source commit | `ef803568ed56905cb9969477e1ad777d5a01faf6` |
| RC exact-head CI | `30616271884 / completed / success / 10 of 10` |
| current authority checker | `PASS / CURRENT_AUTHORITY_CONSISTENT`（通过 / 当前 authority 一致） |
| Attempt-10 | `NOT_CREATED / NOT_AUTHORIZED` |

第一段 diff 仅包含 7 个 RC implementation/test/release-tooling 文件；第二段仅包含
current docs、evidence 与治理同步。未发现 RC 后 backend production、frontend、research、
migration、deploy 或 CI workflow 漂移：

```text
RC_SOURCE_DRIFT=0
```

旧基线 `f54cdc810cddad52e14048f72fe1ad32e6a22472` 与
`5e7a9c4ef1f3f6f38bb4bd57c738bd53464a9ac6` 保持 historical rejected / not deployable，
未被重新引入。

## 4. Findings

### P0

无。

### P1-1 — `UNSAFE_DUPLICATE_JAR_ENTRY`：重复目录 entry 与 CRC 校验可 fail-open

证据：

- `scripts/gatew/verify-gatew-release.ps1:354-405` 的
  `Test-JarDuplicateEntryPolicy` 仅以 entry 名称末尾的 `/` 判断目录，并在两个
  原始名称相同的“目录” entry 出现时仅计数后继续。
- 同一函数在 `:373` 以 `ZipFile::OpenRead` 打开 JAR、在 `:382` 枚举
  `$archive.Entries`，但没有对 `$entry` 调用 `Open()` 或读取数据。CRC 因而不会在
  verifier 中被实际触发验证。
- 独立临时合成 probe 构造两个同名 `conflict/` entry，并分别写入不同的非空内容；
  更新该 artifact descriptor 与 canonical manifest 后，verifier exit `0` 并输出
  `PASS / IMMUTABLE_RELEASE_VERIFIED`。
- 第二个临时 probe 在保留 stale CRC 的前提下破坏 JAR entry 数据、更新 artifact
  SHA-256 与 canonical manifest；verifier 同样 exit `0` 并输出
  `PASS / IMMUTABLE_RELEASE_VERIFIED`。

影响：攻击者可把“目录”伪装为带载荷的重复 entry，或让 entry data 的 CRC 错误不被
release verifier 发现。任务要求的“完全相同的重复目录 entry 才允许、逐 JAR CRC /
entry structure 检查”没有实现，属于 release integrity 的直接 fail-closed 旁路。

修复前必须：仅允许 zero-length、内容与 metadata 等价的重复目录；读取每个 JAR entry
直到 EOF 并将 CRC/读取错误映射为稳定阻断码；增加非空重复目录与 stale-CRC 的回归用例。

### P2

无。`archunit-1.3.0.jar` 的 4 个重复目录 entry 本身不是本 finding 的降级理由；当前
实现无法证明这些 entry 是 zero-length / 内容等价，故已计入 P1。

### P3

无。

## 5. Java、process、mapping 与 clean-build 审查

| 检查项 | 本轮证据与结论 |
| --- | --- |
| Java contract | Windows exact-build 与 disposable Linux 的实际 Java 均为 major `21`；Java 17 mismatch、不可读版本与缺失 executable 的 fail-closed taxonomy 已由 builder/release regression 覆盖。 |
| process timeout | control self-test 验证 `30s / PROCESS_TREE_CLEANED` 与 async stdout/stderr；正常、non-zero、flood、hang/child cleanup 均通过。 |
| strict ResultSet mapping | focused Maven 覆盖 bounded `Byte/Short/Integer/Long` 与 NULL、负数、范围、错误类型、缺列的 `RESULT_MAPPING_FAILED` 路径；readiness 不继续。 |
| clean exact build | 两个全新 detached RC worktree 初始 `target=0`，分别由 Windows PowerShell 5.1 / 7 执行 canonical offline Maven clean/package；不读取主工作区 `backend/nq-app/target`。 |
| reproducibility | Windows 两份构建的 manifest、bundle 与 artifact descriptor bytes identical；manifest=`ba5f9c0536c3bc142ff6e44f194f12ab3ed29935e432b23e33ec55ed709752f5`，bundle=`75ef45cf0d61cd10be76992b981f6b4ebfb3418ad19e66931a286f79016a7c17`。 |
| counts | artifacts / JAR / USTAR / duplicate directory=`131 / 122 / 132 / 4`。 |

上述项目不能抵消 P1：release verifier 仍必须对所有 122 个 JAR 真正读取 entry 数据并
执行安全的重复目录判定。

## 6. Disposable Linux 独立重验

本轮使用 task-local Docker disposable environment：Ubuntu 24.04、PowerShell 7.5.0、
Java 21.0.9、独立 PostgreSQL 16；runner 使用 internal Docker network，测试命令不出网。

| 场景 | 结果 |
| --- | --- |
| PowerShell AST | `PASS / LINUX_PWSH_AST files=12 errors=0` |
| control self-test | `PASS / FORMAL_CONTROL_SELF_TEST`，71 cases；含 30s process-tree cleanup 与 async stdout/stderr |
| worker / fail-close / installer | PASS；`59 / 8 / installer PASS` |
| remediation / security regression | PASS；`32 / 12` |
| Java / PostgreSQL | Java 21.0.9 可执行；task-local PostgreSQL ready |
| Git-dependent builder / release regression | `NOT_RUN / LINUX_GIT_UNAVAILABLE` |
| Maven focused test / canonical Linux exact build | `NOT_RUN / LINUX_MAVEN_UNAVAILABLE` |

Linux image 中无 Git/Maven；未下载、安装或联网补齐，也没有把 Windows 产物当作 Linux
构建输入。该环境限制不会把未运行项写为通过；P1 已由独立静态与合成 tamper probe 确认，
足以拒绝 RC。所有 disposable container、network、volume、worktree 与测试 `target` 已
精确清理。

## 7. Tests rerun

| 验证 | 结果 |
| --- | --- |
| Windows PowerShell 5.1 / 7 AST | 12 files / 0 errors，PASS |
| Windows builder / installer / control / worker / fail-close | PASS；control 70、worker 59、fail-close 8 cases |
| Windows release reproducibility regression | PASS；23 cases |
| Windows remediation / security regression | PASS；32 / 12 cases |
| focused Maven | 23 modules `BUILD SUCCESS`；51 tests / 0 failures / 0 errors / 2 skipped |
| exact-commit Windows rebuild | PASS；两份 detached worktree、两种 PowerShell 与 expected hashes/counts 全部一致 |
| JAR security probes | BLOCKING FAIL；非空重复目录与 stale-CRC probe 均被错误放行 |
| Linux non-Git suite | PASS；AST、control、worker、fail-close、installer、32-case remediation、12-case security |
| current authority checker | PASS |

未运行：Linux Git/Maven-dependent builder、release reproducibility regression、focused Maven、
canonical exact build；生产 SSH/deploy/current switch/systemd、生产 DB read/write、OKX、
真实 credential、Attempt-10、RunId/clock/worker、LIVE、交易写侧、freeze/archive/tag。

## 8. Security / side-effect boundary

RC implementation added-lines 静态审查未发现 production external network / OKX、DB write SQL、
Attempt/clock 创建、worker/systemd mutation、order/cancel/transfer/withdraw、AI 或 DH runtime。
本轮实际副作用为 0：

```text
Production SSH=0
Production deployment=0
Server/current/systemd change=0
Attempt-10 / acceptance clock / worker=false
OKX calls=0
credential material access=0
production database reads/writes=0/0
LIVE enable=0
freeze/archive/tag=0
```

## 9. Authority after、rollback 与后续

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION
work_batch_status=REVIEW_REJECTED|REMEDIATION_REQUIRED
work_batch_commit=ef803568ed56905cb9969477e1ad777d5a01faf6
work_batch_ci_run=30616271884
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
```

P0=0；P1=1；P2=0；P3=0。GateW 保持 `IN_PROGRESS / NOT_FROZEN`；Attempt-09 保持
`REJECTED`；Attempt-10 保持 `NOT_CREATED / NOT_AUTHORIZED`。

本轮未修改 RC 代码或服务器。若必须回滚本次记录，只能对本 evidence/current-authority
文档提交执行 forward revert；不得改写 RC 历史，更不得把该 RC 重新视为 accepted。后续修复
任务必须保持最小范围，补充上述 P1 回归，再从新的固定 source commit 重新独立审查。
