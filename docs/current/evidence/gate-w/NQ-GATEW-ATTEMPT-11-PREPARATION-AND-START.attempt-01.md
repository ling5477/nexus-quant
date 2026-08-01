# NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START — Attempt 01

## 1. 结论

本轮最终结果为：

```text
BLOCKED /
IMMUTABLE_RELEASE_VERIFIED /
PRODUCTION_PREFLIGHT_PASSED /
PERSISTED_PERMISSION_FACT_VERIFIED /
ATTEMPT_11_CREATED /
WORKER_EXITED_BEFORE_FIRST_HEARTBEAT /
PREREQUISITE_READBACK_UNAVAILABLE /
ACCEPTANCE_CLOCK_NOT_STARTED /
TERMINALIZED /
ROLLED_BACK /
LIVE_DISABLED /
KILL_SWITCH_ENGAGED
```

Attempt-11 的 immutable release、服务器 root/POSIX verifier、canonical pre-create 与 persisted permission fact 均通过。唯一正式 `start` 调用创建 worker 后，Java prerequisite 在配置加载阶段退出，worker 未能生成 sanitized result，最终映射为 `FAIL / PREREQUISITE_READBACK_UNAVAILABLE`。首条有效 heartbeat、hash-chain 起点、unit-start snapshot 与 168 小时时钟均未建立。

Run 已自动 fail-close；服务器 current 与两个 unit links 已原子恢复 last-known-good release。不得修改或复用失败 RunId，不得自动重试或创建后续 Attempt。

## 2. Task classification 与起始 authority

- 类型：NQ-only / L 级 `PRODUCTION_READONLY_DEPLOYMENT / IMMUTABLE_RELEASE_VERIFICATION / ATTEMPT_11_PREPARATION_AND_START`。
- 起始 control/release source commit：`bfc68b89e81213ad2b240bf26b4118676abfd75e`。
- Exact-head CI：run `30698530051 / completed / success / 10 jobs / bad=0`，`headSha` 精确匹配。
- 起始 authority：`GateW / IN_PROGRESS|NOT_FROZEN`；work batch=`GateW-ATTEMPT-11-PREPARATION-AND-START`；status=`ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`；next action=`NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START`。
- Attempt-10 唯一 RunId `gatew-soak-20260801T102353Z-932e26a4` 保持 terminalized，未修改、复用或重启。
- LIVE=`DISABLED`；kill switch=`ENGAGED`；order/cancel/transfer/withdraw 禁止。

## 3. Immutable release identity

两份 fresh detached worktree 分别使用 Windows PowerShell 5.1 / 7 构建，132 个 USTAR descriptors diff=`0`，bundle bytes identical；构建 worktree 已清理。

| 项目 | 结果 |
| --- | --- |
| release/source commit | `bfc68b89e81213ad2b240bf26b4118676abfd75e` |
| manifest SHA-256 | `f48343f039c83add4816f64ae73821518959de79db11fb7a1eb726f3baace506` |
| bundle SHA-256 | `5bc25ac8679c1afe463d82d45eb98efe4d7c4a31645f81a74eba960d1180db38` |
| bundle bytes | `61,222,400` |
| artifact / JAR / USTAR | `131 / 122 / 132` |
| JAR entries / bytes fully read | `37,551 / 133,989,252` |
| duplicate empty directories | `4` |

服务器 staging 使用 `-SkipPosix` 后通过 canonical verifier；installed release 再次得到 `PASS / IMMUTABLE_RELEASE_VERIFIED`，`posixVerified=true`，release owner/mode=`root:root/0755`，`nqgatewWritable=false`。

## 4. 生产 preflight、上传与部署

- SSH target：`admin@47.251.74.35`；仅使用用户指定 private-key path reference，未读取、复制或输出 key 内容；远端提权仅使用 `sudo -n`。
- 首轮只读 preflight：NTP synchronized=`yes`；PostgreSQL `127.0.0.1:55432` accepting；active units/jobs/residual/runtime dirs=`0/0/0/0`；Attempt-10 path 精确为 1；credential 只检查 metadata。
- 系统 `degraded` 来自 2026-07-19/07-22 已知 historical failed-unit facts；均对应已知 state dirs，当前 active/residual 为 0。本轮未执行 `reset-failed`。
- Current before：`/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- 整包 SCP 因 connection reset 退出 `255`，仅生成精确 `/tmp` partial；确认后删除该 partial。随后改为 15 个不超过 4 MiB 的分片；多次 SSH reset/timeout 后，逐片 size/SHA 与 stream SHA 验证全部通过。
- 远端重组 tar：size=`61,222,400`、SHA-256=`5bc25ac8...0db38`、USTAR=`132`、mode=`600`。
- 首次 staging 未传 `-SkipPosix`，按合同正确 fail-close 为 `BLOCKED / ROOT_RELEASE_VERIFY_REQUIRED`；以 staging 适用参数重跑后通过。
- Install SSH 因长时间无输出被远端关闭；fresh SSH 只读确认安装已原子完成，release 存在、无 `.install-*` residue、ownership/mode 与 worker write denial 正确。
- Canonical activation 将 current 与 unit links 从 `c16f27c3...` 切换到 `bfc68b89...`，active/jobs/residual=`0/0/0`。`systemd-analyze` 只报告既有 `cloudmonitor.service` warning，与 GateW 无关。

本地有一条批处理命令在 PowerShell parser 阶段失败，未到达远端；后续改为分步执行。该错误不计为生产成功命令，也未重放 Attempt create/start。

## 5. Permission / pre-create gate

Canonical pre-create 于 `2026-08-01T12:52:46.2379928Z` 返回：

```text
releaseBindingVerified=true
postgresReachable=true
managementHealthy=true
killSwitchEngaged=true
credentialConfigured=true
activeCredentialCount=1
credentialType=OKX_API_V5
credentialLocalStatus=ACTIVE
permissionFactPresent=true
permissionFactFresh=true
readPermissionStatus=VERIFIED
tradePermissionExpectedDisabled=true
withdrawPermissionExpectedDisabled=true
ipAllowlistStatus=VERIFIED
blockerCodes=[]
readyForAttemptCreation=true
credentialMaterialExposed=false
```

该 gate 只读取 frozen persisted permission fact，没有调用 OKX。Credential material、raw private response exposure=`0`；不得把它表述为新的实时 provider smoke。

## 6. Attempt-11、worker 与 fail-close

- 唯一 RunId：`gatew-soak-20260801T125700Z-cb211abb`。
- Prepare：`PASS / FORMAL_SOAK_PREPARED`；release=`bfc68b89...`、starting CI=`30698530051`、cadence=`900` 秒、duration=`168` 小时、acceptance clock=`false`；九项 safety flags 均为字面量 `false`。
- Attempt-10 terminal path mtime 在执行期间保持 `1785580273`，证明旧失败 Attempt 未被修改。

两次手工 `unit-preflight` 诊断均未写 lifecycle 或启动 worker，且必须保留为错误上下文调用：

1. 未注入 systemd Process environment，返回 `BLOCKED / RELEASE_ENVIRONMENT_BINDING_CHANGED`。
2. 显式注入 release 三元组，但 systemd 尚未创建 `RuntimeDirectory`，返回 `BLOCKED / PATH_CONTRACT_INVALID`。

这两次结果证明 `unit-preflight` 只能在完整 systemd `ExecStartPre` 上下文执行，不能写成 canonical preflight 首轮通过。

Canonical `start` 只调用一次，最终返回 `FAIL / WORKER_TERMINATED_DURING_START`。生产终态：

```text
lifecycle=FAILURE_STOPPED
reasonCode=WORKER_EXIT_WITHOUT_EXPLICIT_ACCEPTANCE
acceptanceResult=REJECTED_RUNTIME_EXIT
stopClassification=NOT_PROVEN
finalizerKind=AUTOMATIC_FAIL_CLOSE
worker MainPID=456996
NRestarts=0
exit=exited/2
first valid heartbeat=absent
unit-start snapshot=absent
acceptance clock=absent
samples=0
failures=0
active=0
residual=0
credentialAccessed=false
networkCalled=false
okxCalled=false
```

Worker journal 的安全错误码为 `FAIL / PREREQUISITE_READBACK_UNAVAILABLE`。一次 broad journal grep 输出范围过宽并意外打印大量 PowerShell module source；未发现 credential material，但该命令范围不合适，后续禁止重复，诊断必须使用精确 unit/time/error-code selector。

## 7. Root cause

`Get-FormalRealWorkerEnvironmentValues` 仍从执行 `prepare` 的 root Process environment 继承以下 operational runtime values：

```text
SPRING_PROFILES_ACTIVE
NQ_GATEW_OKX_READONLY_SOAK_ENABLED
CI
NQ_NO_OUTBOUND
NQ_GATEW_SOAK_OWNER_ID
NQ_GATEW_SOAK_ACCOUNT_ID
NQ_GATEW_SOAK_CURRENCIES
```

本次 `worker.env` 中七项均为 `EMPTY`。九项 safety flags 已冻结为 `false`，但 operational values 未从 canonical persisted/pre-create facts 冻结，导致 Java `PrerequisiteMain` 在配置加载阶段退出，未生成 sanitized result；worker 因而 fail-closed 映射为 `PREREQUISITE_READBACK_UNAVAILABLE`。

不得现场修改失败 run。该 P1 必须在独立 code remediation/authority 任务中修复，并使用新 Attempt/新 RunId 路线重新授权。

## 8. Heartbeat、hash chain 与 counters

| 项目 | 结果 |
| --- | --- |
| Supervisor / worker | `STOPPED / STOPPED` |
| First valid heartbeat | `ABSENT` |
| Hash-chain start | `ABSENT` |
| acceptanceStartAt / plannedAcceptanceAt | `null / null` |
| 168h clock | `NOT_STARTED` |
| Samples / failures | `0 / 0` |
| Credential / network / OKX calls | `0 / 0 / 0` |
| Forbidden endpoint / order / cancel / transfer / withdraw | `0 / 0 / 0 / 0 / 0` |
| Credential/raw response exposure | `0 / 0` |
| LIVE | `DISABLED` |
| Kill switch | `ENGAGED` |

## 9. Rollback 与保留项

Canonical activate 已原子恢复：

```text
current=/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f
worker link=c16f27c3...
failclose link=c16f27c3...
active/jobs/residual=0/0/0
```

失败 release `/opt/nexus-quant/releases/bfc68b89e81213ad2b240bf26b4118676abfd75e/` 与 Attempt-11 evidence `/var/lib/nexus-quant/gatew-soak/gatew-soak-20260801T125700Z-cb211abb` 保留，不得删除。任务专用远端 `/tmp` staging、tar 与 15 个 upload parts 已精确删除并复核 match count=`0`；本地三个 gitignored `artifacts/20260801-gatew-attempt11-*` 构建/上传目录已精确删除并复核不存在。临时副本不可直接恢复，但可从 source commit 重新可复现构建，且不影响 installed release 或 Attempt evidence。

首轮清理后保留项验证错误假设 evidence 位于 `.../runs/<RunId>`，因此在该 `test -d` 处退出 1；随后使用 exact RunId 在限定 `/var/lib/nexus-quant/gatew-soak`、`maxdepth=4` 下重新定位并确认真实路径，同时确认 current=`c16f27c3...`、failed release retained 与 `/tmp` match count=`0`。

## 10. Findings

### P0

- 无。

### P1

- 七项 operational runtime values 从临时 Process environment 继承，未冻结到 canonical persisted/pre-create facts，导致唯一 worker 在首 heartbeat 前退出，Attempt-11 失败且不得重试。

### P2

- SSH/SCP 在 61 MB release 传输与长时间无输出安装时多次 reset/timeout；虽经逐片 SHA、fresh SSH 和幂等边界安全收敛，但生产传输可靠性与长命令观测性需要独立改进。
- `unit-preflight` 的手工可诊断性依赖 systemd Process/RuntimeDirectory 上下文，脱离 unit 直接调用会得到非业务 blocker。

### P3

- Broad journal grep 范围过宽；后续生产诊断必须使用精确 selector，避免输出无关 module source。

## 11. Authority after 与 Final decision

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-11-PREPARATION-AND-START
work_batch_status=BLOCKED
work_batch_commit=bfc68b89e81213ad2b240bf26b4118676abfd75e
work_batch_ci_run=30698530051
next_action=NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START-BLOCKED
```

Attempt-11=`FAILED / STOPPED`；production deployment=`STOPPED`；worker=`STOPPED`；acceptance clock=`NOT_STARTED`；RunId reuse=`FORBIDDEN`；auto retry=`DISABLED`。

Final decision：

```text
BLOCKED /
ATTEMPT_11_STARTUP_FAILED /
ATTEMPT_11_TERMINALIZED /
WORKER_STOPPED /
FIRST_HEARTBEAT_ABSENT /
OKX_NOT_CALLED /
168H_SOAK_NOT_STARTED /
ROLLED_BACK /
LIVE_DISABLED /
KILL_SWITCH_ENGAGED
```

当前无被授权的重试或 remediation 动作。下一任务只能是独立 authority/code-remediation 决策；不得修改/复用 RunId、重新连接生产启动 Attempt-11、创建后续 Attempt、启动 acceptance clock 或进入 freeze/archive/tag。
