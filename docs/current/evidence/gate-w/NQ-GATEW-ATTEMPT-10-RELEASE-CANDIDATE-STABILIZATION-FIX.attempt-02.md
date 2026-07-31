# NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX — attempt-02

## 1. 结论

```text
IMPLEMENTED /
JAR_INTEGRITY_BYPASS_CLOSED /
FULL_STREAM_AND_CRC_VERIFIED /
DISPOSABLE_LINUX_VALIDATION_PASSED /
NEW_RC_READY_FOR_REVIEW /
COMMITTED /
CI_GREEN /
PRODUCTION_DEPLOYMENT_NOT_STARTED /
ATTEMPT_10_NOT_AUTHORIZED
```

独立 review attempt-02 拒绝的 RC `ef803568ed56905cb9969477e1ad777d5a01faf6`
继续保持 `REVIEW_REJECTED / NOT_DEPLOYABLE`（审查已拒绝 / 不可部署）。本轮关闭
`UNSAFE_DUPLICATE_JAR_ENTRY` 的两个 fail-open 路径，并从新 source commit 生成唯一新 RC：

```text
5a7e824e7e3edc470c55614523a12a2a84286856
```

该 RC 只达到 `RC_READY_FOR_REVIEW / PRODUCTION_DEPLOYMENT_NOT_STARTED`
（RC 可进入审查 / 生产部署未开始）。它尚未被独立接受，不构成生产部署、Attempt-10、
LIVE、交易写侧或 freeze 授权。

## 2. Task classification 与范围

- Task classification：`RELEASE_VERIFIER_SECURITY_FIX / JAR_INTEGRITY_HARDENING /
  REPRODUCIBLE_RELEASE_BUILD`；NQ-only，L 级供应链整改。
- Repository：`E:\Project\nexus-quant`；branch=`dev`。
- 代码范围：release verifier、共享 release contract、builder receipt 与永久 release regression。
- Evidence/authority 范围：本 evidence、GateW evidence index、current authority/roadmap 与
  append-only testing/worklog。
- 明确排除：backend production code、frontend、research、deploy、migration、`.github`、
  `docs/gates`、历史 evidence、生产 SSH/deploy/systemd/DB、OKX、真实 credential、
  Attempt-10、RunId/clock/worker、LIVE、交易写侧、freeze/archive/tag。

## 3. Starting baseline、Commit A 与 CI

| 项目 | 结果 |
| --- | --- |
| starting HEAD / `origin/dev` | `9269e58c60b9228025fe5deebb16259ced43e0a2` |
| starting exact-head CI | `30630041855 / completed / success / 10 of 10` |
| authority before | `GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION / REVIEW_REJECTED|REMEDIATION_REQUIRED` |
| Commit A | `5a7e824e7e3edc470c55614523a12a2a84286856` |
| Commit A timestamp | `2026-07-31T21:03:27+08:00` |
| Commit A exact-head CI | `30632959743 / completed / success / 10 of 10` |
| Attempt-09 | `REJECTED` |
| Attempt-10 | `NOT_CREATED / NOT_AUTHORIZED` |

Commit A 精确包含 4 个允许文件：

- `scripts/gatew/verify-gatew-release.ps1`
- `scripts/gatew/gatew-release-contract.psm1`
- `scripts/gatew/build-gatew-release-bundle.ps1`
- `scripts/gatew/tests/run-gatew-release-reproducibility-regression.ps1`

未修改 backend production、frontend、research、deploy、migration、CI workflow 或交易主链。

## 4. Root cause 与目录 entry 合同

原 verifier 只枚举 `ZipArchive.Entries`，没有读取 entry stream，因此不会独立触发或验证
CRC、截断和解压流错误；同名路径以 `/` 结尾时又会直接按重复目录计数，未证明目录
metadata 和实际 payload 均为空。

修复后的目录合同为：

- 目录路径规范化后必须以 `/` 结尾，external attributes 也必须声明目录。
- central directory 声明长度必须为 0，固定 buffer 读取到 EOF 后实际长度也必须为 0。
- 仅原始名称与规范化名称完全相同的空目录 duplicate 允许、计数并去重。
- 非空重复目录、slashless directory metadata、目录/文件同路径、重复文件、大小写冲突、
  规范化冲突、`../`、绝对路径均立即 fail-closed。
- 多盘 ZIP、ZIP64 sentinel、加密 entry 和未支持 compression method 均拒绝。

`archunit-1.3.0.jar` 的 4 个完全相同空目录 duplicate 继续稳定允许并计数；duplicate file=0。

## 5. Full-stream、CRC 与 compression validation

Verifier 对每个 JAR entry 打开 stream，使用固定 64 KiB buffer 循环读取到 EOF，并在
所有路径关闭 archive/file/entry stream。读取过程中独立流式计算 CRC32，并与 central
directory CRC 比较；任一读取、解压、长度或 CRC 异常立即阻断，不继续汇总为 PASS。

最终 receipt 新增并由 verifier/builder 同步输出：

```text
jarEntryCount
jarEntryBytesRead
duplicateDirectoryEntries
```

固定 release contract：

| 限制 | 值 | 失败分类 |
| --- | ---: | --- |
| buffer | `65,536` bytes | 固定流式读取，不整 entry 入内存 |
| max entries / JAR | `16,384` | `RELEASE_JAR_ENTRY_COUNT_LIMIT_EXCEEDED` |
| max single entry | `268,435,456` bytes | `RELEASE_JAR_ENTRY_SIZE_LIMIT_EXCEEDED` |
| max total / JAR | `1,073,741,824` bytes | `RELEASE_JAR_TOTAL_UNCOMPRESSED_LIMIT_EXCEEDED` |

## 6. 永久正负回归

Release reproducibility regression 从 23 个增至 34 个 cases。新增或强化的永久用例包括：

- 非空同名重复目录：拒绝。
- stale CRC：拒绝。
- truncated compressed data：拒绝。
- invalid compression stream：拒绝。
- duplicate file：拒绝。
- directory/file same path：拒绝。
- case-insensitive 与 normalized path collision：拒绝。
- `../` traversal 与 absolute path：拒绝。
- slashless directory metadata：拒绝。
- entry count、single entry size、JAR total uncompressed 三类资源上限：拒绝。
- 合法 duplicate-empty-directory：允许、计数、去重。
- 每个实际 entry 读到 EOF：通过 `jarEntryCount` 与 `jarEntryBytesRead` 断言。

Canonical artifact tamper 仍精确返回 exit `2 / BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH`。

## 7. Windows 验证

| 验证 | PowerShell 5.1 | PowerShell 7 |
| --- | --- | --- |
| AST | 12 files / 0 errors | 12 files / 0 errors |
| release regression | 34/34 PASS | 34/34 PASS |
| builder / installer | PASS / PASS | PASS / PASS |
| control / worker / fail-close | 70 / 59 / 8 PASS | 70 / 59 / 8 PASS |
| remediation / security | 32 / 12 PASS | 32 / 12 PASS |

Focused Maven：23 modules，50 tests，0 failures，0 errors，1 skipped；`BUILD SUCCESS`。
Canonical offline Maven `clean package` exit `0`。本地真实 candidate verifier 对
131 artifacts / 122 JAR 完成全量 entry read，未使用合成 JAR 代替真实 bundle 验证。

## 8. Windows A/B exact detached builds

两个干净 detached worktree 分别使用 PowerShell 5.1 与 PowerShell 7，从 Commit A 构建：

| 项目 | 值 |
| --- | --- |
| source commit | `5a7e824e7e3edc470c55614523a12a2a84286856` |
| manifest SHA-256 | `d82ae4fc453b3fbf8ed2d0e8ce3767c1d280a615d596f2bdf8f82eacb35a30c6` |
| bundle SHA-256 | `9feda6a825af58d45c61572a4fc590f7ad231b80c45243562cf68390fa68add0` |
| artifact / JAR / USTAR | `131 / 122 / 132` |
| JAR entries / bytes fully read | `37,551 / 133,989,252` |
| duplicate empty directories | `4` |
| manifest / bundle bytes | `identical=true / identical=true` |
| artifact descriptors | `identical=true` |
| missing / extra / undeclared | `0 / 0 / 0` |
| server Git references / sensitive artifacts | `0 / 0` |

两份 worktree 均由 builder 创建并删除；任务结束时两份 RC 临时目录已精确删除。

## 9. Disposable Linux

最终验证环境：Ubuntu 24.04.1、PowerShell 7.5.0、Temurin Java/Javac 21.0.11、
Git 2.43.0、Maven 3.9.12、systemd 255。官方基础镜像为
`eclipse-temurin:21-jdk-noble@sha256:35685c7e23352983a48882d97cd9875f5284c228db71d1e2476e5e6c1bab1080`。
镜像拉取是本轮唯一外部网络用途；最终验证容器使用
`--network none --init --cap-drop ALL`，`/proc/net/route` 的 default route 始终为 0。

| 验证 | 结果 |
| --- | --- |
| Linux PowerShell AST | 12 files / 0 errors |
| canonical exact build | PASS；hash/count/entry bytes 与 Windows A/B 完全一致 |
| USTAR extract 后 root/POSIX verifier | PASS；`posixVerified=true` |
| release regression | 34/34 PASS |
| builder / installer | PASS / PASS |
| control / worker / fail-close | 71 / 59 / 8 PASS |
| remediation / security | 32 / 12 PASS |
| focused Maven | 50 tests / 0 failures / 0 errors / 1 skipped；BUILD SUCCESS |
| final boundary | route=0；`WindowStyle` patch=0；dirty=0 |

两次编排级 RCA 如实保留：首个候选镜像仅有 JRE、无 `javac`，canonical Maven 因而失败；
完整 JDK 环境重建后通过。直接验证 builder staging root 又因 host umask 不满足正式 POSIX
合同而拒绝；按正式合同解包 canonical USTAR 后执行 root/POSIX verifier 通过。两者均未修改
source、放宽 hard gate 或把未执行项写成通过。

Linux 任务目录、两份 Windows RC 临时目录与专用 Docker volume
`nqgw-a10-java-5a7e824e` 已精确删除；未删除其他 volume 或镜像。

## 10. Governance 与文档验证

- `test-governance-workflow-lifecycle.ps1`：PASS；含 `TASK_EVIDENCE_POLICY_VALID`。
- `test-current-authority-next-action.ps1`：PASS。
- `check-current-authority.ps1`：PASS。
- `check-doc-links.ps1 -Roots docs/current`：PASS；145 links，0 errors，1 个既有
  historical warning（`TESTING.md -> GATEJ_TEST_PLAN.md`）。
- `git diff --check`：PASS。

`check-doc-links.ps1` 首次遗漏 mandatory `-Roots` 参数，exit `1`；RCA 后使用
`-Roots docs/current` 重跑通过。该调用错误不是 link finding，未隐藏或改写为首轮通过。

## 11. Findings 与已知限制

- P0：无。
- P1：无；非空 duplicate directory 与未读取 entry stream 两个穿透路径均关闭。
- P2：无；duplicate/path/resource policy 已进入固定 contract 和永久 regression。
- P3：无。
- 已知限制：当前显式拒绝 multi-disk、ZIP64 sentinel、encrypted 和非 allowlist compression
  method；若未来确需支持，必须独立扩展 contract 与负向回归，不能静默放宽。

Java 21 测试输出包含 Mockito/Byte Buddy 动态 test agent 的未来兼容性 warning；不影响本轮
50 个 focused tests 的结果，且不属于 release runtime artifact。

## 12. Security / side-effect boundary

```text
Production SSH=0
Production deployment=0
Server/current/systemd change=0
Production database reads/writes=0/0
OKX calls=0
Credential material access=0
Attempt-10 created=false
RunId / acceptance clock / worker=false
LIVE enable=0
Trading write path=0
AI/DH runtime=0
Freeze/archive/tag=0
```

## 13. Authority after 与下一动作

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
work_batch_status=IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING
work_batch_commit=5a7e824e7e3edc470c55614523a12a2a84286856
work_batch_ci_run=30632959743
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

Attempt-09 保持 `REJECTED`；Attempt-10 保持 `NOT_CREATED / NOT_AUTHORIZED`；GateW 保持
`IN_PROGRESS / NOT_FROZEN`。唯一下一动作是新 RC 的独立 review；不授权生产部署、
Attempt-10、LIVE、交易写侧或 freeze/archive/tag。

## 14. Rollback

- 代码回滚：对 Commit A 执行 forward revert；不得重写历史或恢复旧 rejected RC 的资格。
- Authority/evidence 回滚：对本次 docs commit 执行 forward revert。
- 新 RC 未部署，运行时回滚不适用；服务器 current 未变化。
- Disposable 资源已删除，不是运行时或部署回滚点。
