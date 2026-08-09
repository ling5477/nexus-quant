# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 07

## 1. Task classification

- 执行模式：`ATTEMPT_07 / START_ONLY_AFTER_SANITIZER_REMEDIATION`。
- 类型：`REAL_OKX_READONLY_SOAK_START / FIXED_RUNTIME_VERIFICATION / EXISTING_PERMISSION_METADATA_REUSE / SUPERVISOR_DETACHMENT / EVIDENCE_CHAIN_VERIFICATION`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；仅允许真实 `GET /api/v5/account/config` 与 `GET /api/v5/account/balance`。
- 执行日期：`2026-07-17`。
- 最终结论：`BLOCKED / REAL_OKX_READONLY_PERMISSION_METADATA_REUSED / FIRST_REAL_SAMPLE_VERIFIED / SUPERVISOR_DETACHMENT_FAILED / REAL_OKX_READONLY_SOAK_NOT_STARTED`（阻断 / 复用既有 OKX 只读权限 metadata / 首条真实只读样本已验证 / supervisor 脱离失败 / 真实 soak 未启动）。

本 attempt 已证明 sanitizer remediation 后首条 `gatew-soak-evidence-v2` 真实 config+balance 只读样本可以安全写入并通过 hash-chain 验证；但 Linux PowerShell 在 supervisor detached 路径执行 `Start-Process -WindowStyle Hidden` 时抛出 `NotSupportedException`，未生成 `supervisor.json`，随后按合同执行 `failure-stop` 并恢复 kill switch `ENGAGED`。该样本不得启动或继续 168 小时 acceptance clock，也不得宣称 soak 已启动、GateW 已冻结、七天验收通过、LIVE ready 或 trading authorized。

## 2. Fixed baseline, CI and authority

| Item | Verified result |
| --- | --- |
| Repository / branch | `E:\Project\nexus-quant` / `dev` |
| Local `HEAD` / `origin/dev` | `8bfc23615852eab43de7dfa67ceff518c7af683e` / exact match |
| Fixed runtime commit | `8bfc23615852eab43de7dfa67ceff518c7af683e` |
| Fixed runtime CI | `NQ CI Baseline` run `29517106026` |
| CI result | `completed / success`；`headSha` exact match；10 jobs；bad=0 |
| Current authority | GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED` |
| Authority next action | `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` |
| LIVE | `DISABLED` |

`docs/current/STATUS.md` 继续作为唯一 current authority。本 attempt 不修改 `STATUS.md` 或 `ROADMAP.md`，不把一条有效真实样本写成 seven-day acceptance、freeze candidate、release 或 tag 事实。

## 3. SSH, server isolation and fixed artifacts

- Server：`root@47.251.74.35:22`；hostname=`iZrj9gpab986sm4d0bb6agZ`；public-key/BatchMode SSH=`PASS`。
- NTP：`NTPSynchronized=yes`；40 GiB root disk、约 26 GiB available；2 GiB swap、used=0；资源 hard gate通过。
- 公网 listener：仅 `22/tcp`；本机 resolver `127/8:53` 不属于公网 listener。
- Management app：仅 `127.0.0.1:18889`；health/readiness=`UP`。
- PostgreSQL：仅 `127.0.0.1:55432`；database=`nq_gatew_okx_readonly_soak`。
- 旧 `sub2api*`、`nq-freeze-*`、`sing-box` workloads保持 stopped/retained；`iperf3` 保持 inactive/disabled。
- `nqgatew` GitHub auth与 server-side exact-head CI复核均为 `PASS`。
- Server checkout：detached fixed commit；tracked dirty=0。
- JAR SHA-256：`02bd70fb7127747a1e729bca934d616ca4218dd9f84034f640bd1939cc6ff761`。
- Supervisor SHA-256：`f7c3e80927f5f8eeffc5e76ef7bcb41d6549b6f85a78cdc6e7c813a8bc936159`。

Server isolation：`PASS / SERVER_ISOLATED`。Artifact：`PASS / RUNTIME_ARTIFACT_MATCHED`。

## 4. Old blocked run immutability

旧 run `gatew-soak-20260716T145410Z-230ae5be` 继续保持：

| File / state | Verified result |
| --- | --- |
| `failures.jsonl` SHA-256 | `eab0a6a85eb3477f661eb8c4a7a8e121136dd0b2ad7e9766afde1048d984e65b` |
| `samples.jsonl` SHA-256 | `eab0a6a85eb3477f661eb8c4a7a8e121136dd0b2ad7e9766afde1048d984e65b` |
| `heartbeat.json` SHA-256 | `ae534d7e3a5b495fb84222ddcef45cd914d9c27d8e138c3a0c573e627bf40768` |
| `manifest.json` SHA-256 | `ff3c5c868a65cc61d86064f98eff82ab5cfad0884b76e276fbdbebb7648c7996` |
| Schema / state | `gatew-soak-evidence-v1 / BLOCKED / SOAK_LAUNCHER_FAILED` |
| Valid real PASS / supervisor / final summary | `0 / absent / absent` |
| Acceptance clock | `NOT_STARTED` |

Self-test、启动前 final gate、启动失败后复核三次均确认这些 hash 不变。未 resume、append、rewrite、delete 或生成 `final-summary.json`。

## 5. Credential and permission metadata reuse

只执行聚合、布尔和引用一致性核验；未输出 credential ID、owner/account ID、encrypted payload、master key、audit 原文或 provider 响应。

| Item | Verified result |
| --- | --- |
| Credential rows / active / encrypted | `1 / 1 / 1` |
| Active conflict | `0` |
| Key version | `1` |
| Credential reference changed | `false` |
| Permission probe status | `SUCCEEDED` |
| Permission scope | `READ_ONLY` |
| Trade / withdraw | `false / false` |
| IP allowlist required / status | `true / PASSED` |
| Expected IP | `47.251.74.35` |
| Residual `IN_PROGRESS` | `0` |
| Last probe | present；仓库未定义额外数值 TTL，未自行制定阈值 |
| Subsequent permission/lifecycle events | `0 / 0` |
| Audit forbidden secret shape | `0` |

Permission metadata：`PASS / REAL_OKX_READONLY_PERMISSION_METADATA_REUSED`。本轮未重跑独立 permission hard gate，未重新录入、轮换、展示或解密 credential。

## 6. Runtime composition and automatic-call proof

- 实际 management profile来源为 `NQ_PROFILE=gatew-okx-readonly-soak`；`SPRING_PROFILES_ACTIVE` 未出现在该 management PID 环境中，但 `application.yml` 明确由 `NQ_PROFILE` 激活 profile，因此不构成配置错误。
- Real read-only与 permission probe direct flags为 true；`CI/no-outbound/LIVE/order/transfer/withdraw/AI/DH/real provider/client/exchange` 均为 false。
- Expected IP、isolated DB、credential master-key引用、owner/account正整数引用均通过 owner-only布尔核验；direct OKX credential env count=0。
- Exact-head互斥 composition code与实际输入共同证明选择 `OkxRealReadonlyPermissionProbePort`，而不是 `NoRealExchangeCredentialPermissionProbePort`。
- 当前 management PID启动后，`last_used_at`、`last_permission_probe_at` 均未晚于进程启动；credential audit、permission audit、access audit增量均为 0。

Runtime composition：`PASS / REAL_PERMISSION_PROBE_COMPOSED`。Application bootstrap：`credentialAccessed=false / networkCalled=false / tradingAuthorized=false / liveDisabled=true`。

## 7. Supervisor CLI and offline self-test

实际参数 introspection：PowerShell `7.6.3`；actions=`start/status/resume/stop/failure-stop/evidence-verify/cleanup/run-loop/self-test`；`DurationHours` 下限 168；`CadenceSeconds` 支持 900；`StartingCiRun`存在。

第一次从 `/root` 以 `nqgatew` 调用 self-test时，子进程因不可访问的当前工作目录而无法启动 `/usr/bin/git`；该 invocation 在 self-test逻辑前失败。切换到 fixed checkout作为工作目录后，实际结果为：

```text
PASS / SUPERVISOR_SELF_TEST
cases=36
unsafeFixtureRejections=15
hashChain=PASS
tamperDetection=PASS
appendOnlySequence=PASS
fallbackExcludedFromValidPass=PASS
legacyV1HashVerification=PASS
legacyBlockedRunImmutable=PASS
terminalRunResumeRejected=PASS
terminalRunLoopRejected=PASS
uploadedArtifactSha256=PASS
finalSummaryNotGenerated=true
cleanupReleasedTemporaryDirectory=true
noPrivateNetworkCalled=true
```

Self-test前后 persistent run directories/evidence files/final-summary=`1/5/0`，旧 run hash、credential metadata、kill switch均不变；supervisor count=0。

## 8. Final start hard gate

启动前 final hard gate全部通过：

- fixed commit与 CI exact match；artifact hash match；detached tracked-clean checkout；
- 公网非 SSH listener=0；management/PostgreSQL loopback；
- owner-only runtime files owner/group/mode正确；runtime flags与 DB固定值匹配；
- credential/permission=`1/1/1 / SUCCEEDED / READ_ONLY / withdraw=false / IP PASSED`；
- Flyway V35 success=1、failed migration=0；
- kill switch=`ENGAGED / version=3`；
- 旧 run不可变；新 run count=0；supervisor=0；final-summary=0。

Final gate：`PASS / START_FINAL_HARD_GATE`。

## 9. Single start attempt and first real sample

本 attempt只发起一次 start；未指定旧或自定义 RunId：

```text
DurationHours=168
CadenceSeconds=900
StartingCiRun=29517106026
NQ_GATEW_SOAK_CURRENCIES=USDT
```

首个 SSH仅创建 owner-only attempt sentinel并把 start wrapper交给 `nohup`；随后所有状态读取均来自新的独立 SSH。新 run：

| Field | Verified result |
| --- | --- |
| RunId | `gatew-soak-20260717T122834Z-eb5ef11c` |
| Evidence schema | `gatew-soak-evidence-v2` |
| Manifest startedAt | `2026-07-17T12:29:53.3225655Z` |
| Manifest plannedEndAt | `2026-07-24T12:29:53.3225655Z` |
| Cadence | `900s` |
| First sample observedAt | `2026-07-17T12:31:14.0630196Z` |
| First sample status | `PASSED_READ_ONLY / READ_ONLY_SAMPLE_ACCEPTED / SUCCESS_2XX` |
| Permission classification | `READ_ONLY_WITH_IP_ALLOWLIST` |
| Credential accessed / network called | `true / true` |
| Endpoint category | `ACCOUNT_CONFIG_AND_BALANCE_READ` |
| Config / balance probe | `SUCCEEDED / SUCCEEDED` |
| Real outcome proven | `true` |
| First record hash | `87cb4fa2b4a2f293cac6829aa70c026a59678d0ddab330e60f56ad1ab3f07448` |
| Failure / fallback / raw response / secret exposure | `0 / 0 / 0 / 0` |

这证明 sanitizer remediation关闭了 Attempt-06的真实 sample持久化 blocker。但首样本之后 detached supervisor没有建立，因此该 observedAt不得登记为持续验收的 `acceptanceStartAt`；本 run的 acceptance clock为 `NOT_STARTED / INVALIDATED_BY_DETACHMENT_FAILURE`。

## 10. Detachment failure, fail-close and RCA

Start wrapper最终返回：

```text
FAIL / SUPERVISOR_INTERNAL_ERROR
```

此时 `samples.jsonl`已有一条真实 PASS，`failures.jsonl`为空，但 `supervisor.json`不存在，heartbeat仍为 `PREPARING / HARD_GATES_PENDING`。未重发 start、未手工启动 `run-loop`、未 resume。

按合同立即执行：

```text
failure-stop / gatew-soak-20260717T122834Z-eb5ef11c
```

结果：

| Item | Verified result |
| --- | --- |
| Stop result | `STOP_REQUESTED / kind=failure` |
| Heartbeat | `FAILURE_STOPPED / OPERATOR_FAILURE_STOP` |
| Supervisor PID / process | `0 / false` |
| Sample / valid real PASS | `1 / 1` |
| Hash chain | `PASS / HASH_CHAIN_VERIFIED` |
| Fallback / raw response / secret exposure | `0 / 0 / 0` |
| `final-summary.json` | absent |
| Kill switch | `ENGAGED / version=5` |
| Public non-SSH listener | `0` |

RCA使用 `/bin/true` 复现同一平台调用形态，未启动 NQ或访问网络：

```text
PASS / DETACHMENT_PLATFORM_RCA
exceptionType=NotSupportedException
errorId=NotSupportedException,Microsoft.PowerShell.Commands.StartProcessCommand
windowStyleUnsupported=true
```

Exact-head `Start-LoopProcess` 在 Linux使用 `Start-Process ... -WindowStyle Hidden -PassThru`。`-WindowStyle` 在该平台不受支持，因此 detached process未创建，`ownershipTransferred`保持 false，start finally重新 ENGAGE kill switch并返回脱敏 internal error。

## 11. Findings

- P0：无。
- P1：Linux supervisor detached start不可用；`Start-Process -WindowStyle Hidden` 抛出 `NotSupportedException`，导致真实首样本之后无法建立长期 `run-loop`，168 小时 soak未启动。
- P1：现有 36-case self-test覆盖 detached branch/blob/artifact语义，但未在 Linux执行真实 `Start-LoopProcess` smoke，因此未能在真实 OKX start前发现平台不兼容。
- P2：start错误合同只返回 `FAIL / SUPERVISOR_INTERNAL_ERROR`，没有独立的安全 reason code区分 detachment平台错误；RCA需额外无副作用复现。
- P3：self-test必须从 `nqgatew`可访问的 checkout工作目录调用；从 `/root`降权启动会在 Git子进程前失败。正确调用已 36/36 PASS，不影响最终 P1判断。

## 12. Boundary, rollback and next action

- 未修改 backend/frontend/research/scripts/deploy/.github/migration、supervisor、evidence schema、endpoint allowlist、V31/V35、credential、permission metadata、Gate archive、`STATUS.md`或`ROADMAP.md`。
- 未重新录入、轮换、展示或解密 credential；未重跑 permission hard gate。
- 真实 OKX只出现首 sample合同允许的 config+balance typed reads；没有 POST/PUT/PATCH/DELETE、order/cancel/transfer/withdraw或其他 endpoint。
- LIVE/order submission/transfer/withdraw/AI/DH/real provider/client/exchange写侧均保持 disabled。
- 新 run已 terminal fail-close；禁止 resume、手工启动 run-loop、追加/编辑 evidence或生成 PASS final summary。

运行状态命令：

```text
pwsh -NoProfile -File scripts/gatew/gatew-okx-readonly-soak.ps1 -Action status -RunId gatew-soak-20260717T122834Z-eb5ef11c
```

Evidence验证命令：

```text
pwsh -NoProfile -File scripts/gatew/gatew-okx-readonly-soak.ps1 -Action evidence-verify -RunId gatew-soak-20260717T122834Z-eb5ef11c
```

Stop状态：`ALREADY_FAILURE_STOPPED / PID_0 / KILL_SWITCH_ENGAGED`；不得重复 stop或 start。

本地文档回滚：删除本 attempt文件，移除 evidence index对应行，并回退本轮 `TESTING.md` / `WORKLOG.md` append；不得使用 `git reset --hard`。服务器运行证据不得删除或重写；owner-only attempt sentinel/result/pid文件作为本 attempt操作证据保留。

下一具体动作：新建独立 remediation任务，最小修复 Linux detached process兼容性并补 Linux真实 `Start-LoopProcess` no-network smoke；完成 commit/push、exact-head CI、server fixed artifact部署与离线验证后，只能由新的 `ATTEMPT_08`创建全新 run。不得进入 `NQ-GATEW-FREEZE-BLOCKER-1-REAL-OKX-READONLY-SOAK-ACCEPTANCE`。
