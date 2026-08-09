# NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START — Attempt 02

## 1. 结论

本轮最终结果为：

```text
BLOCKED /
FINAL_IMMUTABLE_RELEASE_VERIFIED /
PRECREATE_PERMISSION_FACT_VERIFIED /
ATTEMPT_10_CREATED /
START_CONTRACT_FAILED /
WORKER_NOT_STARTED /
FIRST_HEARTBEAT_NOT_CREATED /
ACCEPTANCE_CLOCK_NOT_STARTED /
TERMINALIZED /
ROLLED_BACK /
LIVE_DISABLED
```

最终 immutable release 已通过双引擎 exact build、服务器 staging/installed canonical verifier、root ownership 与 worker write denial；canonical pre-create 也成功验证 persisted permission fact、PostgreSQL、management、credential metadata、kill switch 与 release binding。唯一 RunId 创建后，启动前静态核验发现 `worker.env` 中九个安全开关被持久化为空字符串，而 worker frozen contract 要求每项精确为 `false`。因此未调用 `start`，未启动 worker，未调用 OKX，未创建首 heartbeat 或 168 小时时钟。

Run 随后通过 canonical stop/fail-close 进入 terminal，current 与 unit links 已 canonical 回滚到 last-known-good release。GateW 继续为 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结），不得进入 168h acceptance、freeze、archive 或 tag。

## 2. 任务与起始 authority

- Task classification：NQ-only `PRODUCTION_READONLY_DEPLOYMENT / IMMUTABLE_RELEASE_VERIFICATION / ATTEMPT_10_PREPARATION_AND_START`。
- 实际生产部署 source/control commit：`f06a38f2269445c544169cede1092ce70168913b`。
- exact-head CI：run `30694580482 / completed / success / 10 of 10 / bad=0`，`headSha` 精确匹配。
- 起始 authority：`GateW / IN_PROGRESS|NOT_FROZEN`；work batch=`GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW`；status=`ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`；next action=`NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`；Attempt-10=`NOT_CREATED / AUTHORIZED`；production deployment=`NOT_STARTED`。
- 历史 `attempt-01.md` 未修改；本文件使用 authority 已授权的不可覆盖 attempt-02 路径。

## 3. 本轮代码整改与 CI

生产操作前关闭两个 P1 合同缺陷：

1. Commit `0e29e00d2126e9b8377cca35fff10ef2346720a9` 将正式 REAL cadence 从 60 秒修正为 900 秒，offline 保持 60 秒；exact-head CI run `30693727633` 为 10/10 success。
2. Commit `f06a38f2269445c544169cede1092ce70168913b` 将 `acceptanceStartAt` 精确绑定第一条有效 heartbeat，并把 heartbeat sequence/timestamp 纳入 unit-start snapshot 与 fresh-SSH/clock v2 hard gate；exact-head CI run `30694580482` 为 10/10 success。

本轮没有从已含生成物的 control worktree 构建。两个 fresh detached worktree 使用 `core.autocrlf=false / core.eol=lf`，分别由 PowerShell 5.1 与 PowerShell 7 构建并精确移除。

## 4. 最终 immutable release

| 项目 | 结果 |
| --- | --- |
| release/source commit | `f06a38f2269445c544169cede1092ce70168913b` |
| manifest SHA-256 | `9bf1bb6c03eefac609301e27afcc8fe81599aa2fdc044da1884138bc5fb1d1ee` |
| bundle SHA-256 | `d145671385ae14976b6a276a23d6a6aa9ee6aa5a3c3bd07bfe9d1d5ae7ad8e29` |
| bundle bytes | `61,220,864` |
| artifact / JAR / USTAR | `131 / 122 / 132` |
| JAR entries / bytes fully read | `37,551 / 133,989,252` |
| duplicate empty directories | `4` |
| PS5.1 / PS7 manifest、bundle | bytes identical |
| artifact descriptors diff | `0` |
| USTAR entry order diff | `0` |

服务器 staging 与 installed release 均返回 `PASS / IMMUTABLE_RELEASE_VERIFIED`：manifest、bundle、artifact、122/122 JAR full-stream/CRC、duplicate contract、Java 21 与 POSIX 全部通过。Installer 另返回 `PASS / ROOT_OWNED_RELEASE_VERIFIED`，release 为 `root:root/0755`，`nqgatewWritable=false`。

## 5. 生产 preflight 与部署

- SSH target：`admin@47.251.74.35`；仅使用附件指定 private-key path reference，未读取或输出 key 内容；远端仅使用 `sudo -n`。
- Preflight 时间：`2026-08-01T10:01:22Z`；hostname=`iZrj9gpab986sm4d0bb6agZ`；NTP=`yes`；磁盘 available=`22,401,444 KiB`；PostgreSQL `127.0.0.1:55432` accepting。
- Current before：`/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- Active GateW units / jobs / nqgatew worker process / runtime run dirs：`0 / 0 / 0 / 0`。
- 最新历史 state 仍为 Attempt-09 run `gatew-soak-20260722T111144Z-ac00f878`。
- Credential references 只核对 owner/mode/size/mtime；未读取内容，元数据未变化。
- 上传使用 8 个小分片，每片远端 SHA-256 匹配；服务器重组后 full bundle hash 和 132 USTAR entries 匹配。
- Canonical activation 将 current 从 `c16f27c3...` 原子切换到 `f06a38f2...`；两个 unit links 精确绑定新 release，`systemd-analyze verify` 通过，units started=`0`。

## 6. Permission / pre-create gate

Final release canonical `precreate-prerequisite` 于 `2026-08-01T10:22:06.0524188Z` 返回：

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
diagnosticOnly=true
noSideEffect=true
credentialMaterialExposed=false
```

该 gate 验证的是 frozen persisted permission fact，没有增加 wall-clock TTL，也没有发起新的 OKX 请求。Worker 未启动，因此本轮实时 config/balance 两个只读 endpoint probe 为 `NOT_RUN`；不得把 persisted fact 写成新的实时 provider smoke。

## 7. Attempt-10 与启动前失败

- 唯一 RunId：`gatew-soak-20260801T102353Z-932e26a4`。
- Prepare：`PASS / FORMAL_SOAK_PREPARED`；release、manifest、starting CI 精确匹配；lifecycle=`STARTING`；historical evidence count=`394`。
- Frozen manifest：cadence=`900` 秒；duration=`168` 小时；endpoint allowlist version=`gatew-okx-private-readonly-v1`；acceptanceClockStarted=`false`。
- Unit 在核验时为 inactive，MainPID=`0`，NRestarts=`0`。

启动前读取九个明确非敏感 safety flag 后发现：

```text
NQ_LIVE_ENABLED=""
NQ_REAL_ORDER_SUBMISSION_ENABLED=""
NQ_TRANSFER_ENABLED=""
NQ_WITHDRAW_ENABLED=""
NQ_AI_ENABLED=""
NQ_DH_RUNTIME_ENABLED=""
NQ_REAL_PROVIDER_ENABLED=""
NQ_REAL_CLIENT_ENABLED=""
NQ_REAL_EXCHANGE_ENABLED=""
```

Worker `Assert-ProductionBoundary` 要求上述值逐项精确为 `false`，否则返回 `<NAME>_MUST_BE_FALSE`。本 run 不允许在创建后修改 frozen `worker.env`，也不得依赖 Spring default false 代替 frozen contract，因此未调用 `start`。

## 8. Fail-close 与 rollback

Canonical `stop` 写入 stop intent 并启动 fail-close。由于 worker 从未启动，没有 systemd exit-fact，stop 返回 `FAIL / OPERATOR_STOP_NOT_PROVEN`；final terminal 为：

```text
lifecycle=BLOCKED
reasonCode=EXIT_FACT_MISSING_OR_INVALID
acceptanceResult=REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP
stopClassification=UNAUTHORIZED_OR_UNKNOWN_STOP
finalizerKind=AUTOMATIC_FAIL_CLOSE
terminalChecksum=6d89796a0f861e7ee692d5abe07cfc07bb2d48691190501faf8ac2be9d5db7dd
```

最终生产事实（`2026-08-01T10:35:53Z`）：

```text
current=/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f
worker/failclose unit links=c16f27c3...
active units=0
jobs=0
residual processes=0
sample records=0
failure records=0
first-valid-heartbeat=absent
worker-start=absent
unit-start-snapshot=absent
acceptance-clock=absent
```

失败 release `/opt/nexus-quant/releases/f06a38f2...` 与 RunId evidence 保留。任务专用远端 `/tmp` upload chunks、staging root 与 tar 已在精确路径核验后清理；未删除 release 或历史 Attempt。

## 9. Heartbeat、hash chain 与 counters

| 项目 | 结果 |
| --- | --- |
| Supervisor / worker | `NOT_STARTED` |
| First heartbeat | `NOT_CREATED` |
| Hash-chain start | `NOT_CREATED` |
| acceptanceStartAt / plannedAcceptanceAt | `null / null` |
| 168h clock | `NOT_STARTED` |
| OKX config / balance calls | `0 / 0` |
| forbidden endpoint / order / cancel / transfer / withdraw | `0 / 0 / 0 / 0 / 0` |
| raw private response exposure | `0` |
| credential material exposure | `0` |
| LIVE | `DISABLED` |
| kill switch | `ENGAGED`（pre-create verified） |

## 10. Findings

### P0

- 无。

### P1

- `Prepare-FormalRun` 在 REAL 模式的 pre-create evaluation 返回后，从已恢复的 process environment 读取 safety flags，导致九个字段写成空字符串；worker frozen contract 要求精确 `false`。该缺陷阻断唯一 Attempt-10 启动，且创建后禁止就地修补或复用 RunId。

### P2

- Governance runtime 只有包含 `WORKER_STARTED` 事件的 `STARTUP_FAILED` transition，没有 `ATTEMPT_CREATED / WORKER_NOT_STARTED / PRESTART_CONTRACT_FAILED` taxonomy。本轮不能伪造 worker started，只能将 current work batch 标为通用 `BLOCKED`。
- Canonical stop 对从未启动的 unit 没有 exit-fact，最终把有 root stop intent 的停止分类为 `UNAUTHORIZED_OR_UNKNOWN_STOP` 并返回 `OPERATOR_STOP_NOT_PROVEN`。虽然安全上 fail closed 且无残留，但审计分类不能表达 pre-start controlled abort。

### P3

- 两次本地 SSH wrapper 因 PowerShell 双层插值在远端调用前失败；另两次长时间无输出的 SSH 会话被服务器关闭。所有远端不确定状态均通过 fresh SSH 精确读取后从幂等边界继续，没有重放 Attempt prepare/start 或 release activation。

本地 current-authority 收口首次正式回归中，`test-current-authority-next-action.ps1` 的跨文档 fixture 因硬编码旧 `NOT_CREATED / AUTHORIZED / NOT_STARTED` 快照而返回 `CROSS_DOCUMENT_ROADMAP_FIXTURE_INVALID`。本轮仅将该 fixture 改为从 ROADMAP 当前唯一声明动态派生对齐正例和六类负例；未修改 authority checker、schema 1.3.0、status/action mapping 或 runtime taxonomy。

## 11. Authority 与下一动作

Authority after 使用 governance 已存在的通用 blocker 状态：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-PREPARATION-AND-START
work_batch_status=BLOCKED
work_batch_commit=f06a38f2269445c544169cede1092ce70168913b
work_batch_ci_run=30694580482
next_action=NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START-BLOCKED
```

Attempt-10=`FAILED / STOPPED`；production deployment=`STOPPED`；worker=`STOPPED`（实际从未启动）；acceptance clock=`NOT_STARTED`；RunId reuse=`FORBIDDEN`；auto retry=`DISABLED`。

在独立 authority/remediation 任务明确新增 canonical 路线前，没有被授权的重试动作。不得复用本 RunId、创建 Attempt-11、修改失败 run、重新切换 current、启动 168h、进入 GateW freeze，或触碰 LIVE/交易写侧。

## 12. Final decision

```text
BLOCKED /
ATTEMPT_10_START_CONTRACT_FAILED /
ATTEMPT_10_TERMINALIZED /
WORKER_NOT_STARTED /
OKX_NOT_CALLED /
168H_SOAK_NOT_STARTED /
ROLLED_BACK /
LIVE_DISABLED /
KILL_SWITCH_ENGAGED
```
