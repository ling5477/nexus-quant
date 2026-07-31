# NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW — attempt-03

## 1. 最终结论

```text
PASS /
RC_REVIEW_ACCEPTED /
JAR_INTEGRITY_BYPASS_CLOSED /
FULL_STREAM_AND_CRC_VERIFIED /
REPRODUCIBILITY_VERIFIED /
PRODUCTION_NOT_ACCESSED
```

固定 runtime RC source `5a7e824e7e3edc470c55614523a12a2a84286856` 的真实 release、
122 个 JAR、full-stream/CRC、duplicate/resource contract、tamper、Windows/Linux exact
build 与 focused Maven 全部通过。

审查首次 Windows PowerShell 5.1 运行永久 34-case regression 时发现 fixture timestamp
不确定性，初始结论为 `FAIL / NONDETERMINISTIC_RELEASE_REGRESSION_FIXTURE`。用户随后明确
授权本轮最小修复 P1，并要求完成 review。修复只修改不进入 release bundle 的 regression
fixture：所有合成 ZIP entry 使用固定 UTC `LastWriteTime`，path B 在 2 秒间隔后创建。修复后
Windows PowerShell 5.1、7 与 disposable Linux PowerShell 7 各连续 3 次 34/34，通过并在
各平台内得到稳定 hash。首次失败与 RCA 保留，不被后续通过覆盖；P1 经修复与重复回归关闭。

## 2. Task classification 与范围

- Task classification：`SECURITY_AUDIT`；辅助类型为 `CI_CD / REMEDIATION / DOCUMENTATION`。
- Repository：`E:\Project\nexus-quant`；NQ-only。
- Starting control HEAD：`32c58ba9cbf99d60e4316e1dd0e27f7317904404`。
- Runtime RC source：`5a7e824e7e3edc470c55614523a12a2a84286856`。
- Governance implementation：`b97d307d0c6abda313354dc3703fa73dafbcd964`。
- P1 remediation：仅 `scripts/gatew/tests/run-gatew-release-reproducibility-regression.ps1`。
- Docs/evidence：本 evidence、gate-w evidence index 与 5 个 current fact-source/ledger 文件。
- 明确排除：verifier、release contract、builder、`backend/**`、`frontend/**`、
  `research/**`、`deploy/**`、`.github/**`、migration、生产服务器、OKX、真实凭证、
  Attempt-10、RunId、worker、168h clock、LIVE、freeze/archive/tag。

附件指定的 `attempt-01.md` 与历史 `attempt-02.md` 已存在；为保护 append-only evidence，
本轮使用未占用的 `attempt-03.md`，没有覆盖历史记录。

## 3. Starting authority、ancestry 与 CI

| 项目 | 结果 |
| --- | --- |
| branch | `dev` |
| starting tracked worktree / staged | clean / empty |
| starting HEAD / `origin/dev` | `32c58ba9cbf99d60e4316e1dd0e27f7317904404` |
| RC source ancestry | `git merge-base --is-ancestor 5a7e824e... HEAD` exit `0` |
| RC source CI | `30632959743 / completed / success / 10 jobs / bad=0 / headSha=5a7e824e...` |
| governance CI | `30643903984 / completed / success / 10 jobs / bad=0 / headSha=b97d307d...` |
| control HEAD CI | `30644173342 / completed / success / 10 jobs / bad=0 / headSha=32c58ba9...` |
| current authority / next-action | PASS / PASS |
| Attempt-10 / production deployment | `NOT_CREATED / NOT_AUTHORIZED`；`NOT_STARTED` |

RC source 之后的提交只包含 RC evidence/current docs 与 governance contract/checker 变化；
runtime artifact source 未被替换。本轮 fixture 文件不在 131-artifact release manifest 中，
因此 remediation 不改变已审查的 runtime release bytes。

## 4. Release identity 与 JAR contract

| 检查项 | 独立结果 |
| --- | --- |
| manifest SHA-256 | `d82ae4fc453b3fbf8ed2d0e8ce3767c1d280a615d596f2bdf8f82eacb35a30c6` |
| bundle SHA-256 | `9feda6a825af58d45c61572a4fc590f7ad231b80c45243562cf68390fa68add0` |
| artifact / JAR / USTAR | `131 / 122 / 132` |
| closed set | missing / extra / undeclared=`0 / 0 / 0` |
| JAR full-stream | `122 / 122`；37,551 entries、133,989,252 bytes 读取至 EOF |
| duplicate contract | empty directory=`4`；file=`0`；非空目录、文件、目录/文件、大小写与规范化冲突均拒绝 |
| CRC / compression / truncation | 独立 CRC32、stale CRC、非法压缩流与截断流均 fail-closed |
| resource limits | 16,384 entries/JAR；268,435,456 bytes/entry；1,073,741,824 bytes/JAR |
| Windows exact build | PowerShell 5.1/7 detached builds 的 manifest、bundle、131 descriptors bytes identical |
| Linux exact build | Ubuntu 24.04.1 / pwsh 7.5.0 / Java 21.0.11 / Git 2.43.0 / Maven 3.9.12；与 Windows bytes identical |
| root/POSIX verifier | canonical USTAR 解包后 `posixVerified=true` |
| tamper | exit `2`；`BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH` |

Linux exact-build runner 使用 `--network none --cap-drop ALL`，default route=`0`。真实 RC 的
manifest/bundle/artifact descriptors 跨 Windows/Linux bytes identical。

## 5. P1 初始失败、RCA 与 remediation

初次 Windows PowerShell 5.1 结果：

```text
exit=2
passed=1
MANIFEST_HASH_CHANGED_ACROSS_PATHS
```

随后三次未经修复的 34/34 重跑仍分别产生 `b4afd03b...`、`a7c8a9cc...`、
`9ecc42aa...`，证明 fixture 不确定，不能以偶然通过接受 review。

RCA：`scripts/gatew/tests/run-gatew-release-reproducibility-regression.ps1` 原
`Write-TestJar` 使用 `$archive.CreateEntry(...)`，未固定 `LastWriteTime`；path A/B 在 ZIP
DOS timestamp 的 2 秒边界可能得到不同 entry bytes。

授权后的最小修复：

1. 固定所有合成 ZIP entry 的 UTC timestamp 为 `2020-01-02T03:04:06Z`；选择偶数秒以符合
   ZIP DOS timestamp 粒度。
2. 将 path B release 移到原有 `Start-Sleep -Seconds 2` 之后创建，使既有
   `manifest-bytes-and-hash-identical-across-paths` case 同时证明跨时间边界确定性。
3. 不修改 verifier、builder、release contract、runtime artifacts 或业务代码；34-case 数量
   与全部 negative fail-closed contract 不变。

## 6. Remediation 后永久回归

| 环境 | 连续运行 | cases | 平台内稳定 synthetic manifest / bundle |
| --- | ---: | ---: | --- |
| Windows PowerShell 5.1 | 3/3 PASS | 34/34 each | `d79bfaa8358106596fa97f4c343d5cc5e342fcba8f95b2c0caf4d51f988daed7` / `4eccc42c422f80f0aad8717136b343a557ad637a3c7cd02562c31fdf02cfad74` |
| Windows PowerShell 7 | 3/3 PASS | 34/34 each | 同上 |
| disposable Linux PowerShell 7 | 3/3 PASS | 34/34 each | `1c556eb734e6bf3f4532e2bebf6ffcfcdb8c8775a832c2a7c0bf1a810778d68c` / `b71a8f8f805d1a9af06cb4135b57a55f81eb4f4cfa15546ee5fd51889124d1f2` |

Linux 使用 `--network none --cap-drop ALL`，每次 `networkCalled=false`。Synthetic ZIP 因
平台压缩实现不同不要求 Windows/Linux hash 相同；hard gate 是各平台内跨路径/时间稳定。
正式 RC 的跨平台 bytes identity 由第 4 节 exact builds 单独证明。

## 7. 其他验证

| 验证 | 结果 |
| --- | --- |
| Windows/Linux AST | 12 files / 0 errors |
| builder / verifier / control / worker / fail-close | PASS；Linux control/worker/fail-close=`71 / 59 / 8` |
| remediation / security | `32 / 12`，Windows 与 Linux PASS |
| focused Maven | 23 modules；50 tests / 0 failures / 0 errors / 1 skipped，Windows 与 Linux PASS |
| canonical offline Maven package | PASS |
| governance lifecycle / task evidence | PASS；含 `TASK_EVIDENCE_POLICY_VALID` |
| next-action / authority | PASS |
| docs/current links | 148 checked / 0 errors / 1 个既有 GateJ warning |
| `git diff --check` | PASS（修复前审查基线；写后将重跑） |

最终 findings：P0=0；P1=0（1 个初始 P1 已修复并关闭）；P2=0；P3=0。

## 8. Production boundary

```text
Production SSH=0
Production deployment=0
Server/current/systemd change=0
Attempt-10 / RunId / acceptance clock / worker=false
OKX calls=0
credential material access=0
production database reads/writes=0/0
LIVE enable=0
freeze/archive/tag=0
```

Attempt-09 保持 `REJECTED`；Attempt-10 保持 `NOT_CREATED / NOT_AUTHORIZED`。本轮没有修改
交易状态机、策略/回测算法、LIVE、AI、DH、real provider 或 private trading 边界。

## 9. Authority transition、rollback 与下一动作

Review accepted 阶段按 governance schema `1.3.0`：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-COMMIT-AND-PUSH
```

Review commit exact-head CI 成功前不得转换为 `DEPLOYMENT_AUTHORIZED`。成功后再单独执行
authority-sync，并保持 runtime artifact source=`5a7e824e...`、production deployment
`NOT_STARTED`、Attempt-10 `NOT_CREATED`、LIVE `DISABLED`。本轮绝不执行后续生产
`PREPARATION-AND-START`。

若需回滚，forward revert 本轮 review/remediation commit；固定 RC runtime release bytes
保持不变。不得改写 attempt-01/02、RC source 或首次失败证据。
