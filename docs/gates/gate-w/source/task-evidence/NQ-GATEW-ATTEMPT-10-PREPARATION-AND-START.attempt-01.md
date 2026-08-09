# NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START — Attempt 01

## 1. 结论

本轮结果为：

```text
BLOCKED /
CREDENTIAL_OR_PERMISSION_PRECHECK_FAILED /
IMMUTABLE_RELEASE_ACTIVATED /
SYSTEMD_CONTRACT_VERIFIED /
ATTEMPT_10_NOT_CREATED /
ACCEPTANCE_CLOCK_NOT_CREATED /
UNITS_NOT_STARTED /
OKX_NOT_CALLED
```

修复版 immutable release 已通过 canonical verifier，并由 canonical installer 原子激活；systemd unit links 已绑定同一
release，静态合同验证通过。随后 canonical `precreate-prerequisite` 返回 `readyForAttemptCreation=false`，因此按 hard gate
在创建 RunId、state/runtime directory、acceptance clock 和启动 worker 前停止。

GateW 仍为 `IN_PROGRESS / NOT_FROZEN`（进行中 / 未冻结）。本轮不表示 Attempt-10 已开始，也不表示真实 OKX
permission、账户健康、余额充分、LIVE ready、trading authorized 或 GateW freeze ready。

## 2. 任务与起始基线

- Task classification：NQ-only
  `PRODUCTION_READONLY_SOAK_PREPARATION / IMMUTABLE_RELEASE_ACTIVATION / SYSTEMD_INSTALLATION / PRECREATE_HARD_GATE`。
- 起始分支：`dev`；worktree/staged clean。
- 起始 `HEAD == origin/dev == 509a9f35cdf707bc2598b7263c5638790249111d`。
- 起始 governance CI：`30549800762 / completed / success / 10 of 10`；`headSha` 精确匹配起始 HEAD。
- Release source：`c16f27c3c68d2484ad140d0557b879de08b7c78f`。
- Release source CI / runtime `StartingCiRun`：`30537845010 / completed / success / 10 of 10`。
- Manifest SHA-256：`eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977`。
- Bundle SHA-256：`60a11dde87a4cbfcff8adbd32966b3dd28463d3399b8ba25db01eb836ed0ec1b`。
- Authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT`；唯一治理动作是 `NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START`。
- WIP branch `wip/gatew-release-tooling-local-20260730` 的远端 commit 为 `9ece62a4be9ab94643b770e7524897279f096ac4`；它不是
  `dev` ancestor，未混入本轮 release。

## 3. 服务器启动前审计

- Hostname：`iZrj9gpab986sm4d0bb6agZ`。
- 审计时间：`2026-07-30T14:34:05+00:00`；NTP=`yes`；boot=`2026-05-15 21:33:54`。
- `sudo -n id`：root。
- Current before：`/opt/nexus-quant/releases/1b501488076fae79e15b84579a02f5c580fa51b3`。
- Attempt-09 worker：`inactive/dead`；MainPID=`0`；NRestarts=`0`。
- Active GateW worker/fail-close、timer、job：`0 / 0 / 0`。
- GateW pwsh/java worker process：`0`。
- 宽泛 `pgrep 'gatew|nq-gatew'` 命中的进程均为持续运行的 `java -jar app/nq-app.jar` 管理进程产生的本地 PostgreSQL idle
  backend；通过 socket owner/PID 树确认不是 GateW worker、pwsh、timer 或 systemd job。
- 保留历史 failed units；未执行 `reset-failed`，未删除历史运行事实。

## 4. Immutable release 验证与 activation

Direct canonical verifier：

```text
PASS / IMMUTABLE_RELEASE_VERIFIED
sourceCommit=c16f27c3c68d2484ad140d0557b879de08b7c78f
sourceTreeMode=EXACT_COMMIT
manifest=eaf83f95f51fc938d55c4c0235eee86e9de78c67990e142cf3d0b6c62c9e8977
artifactCount=131
posixVerified=true
```

Canonical installer verify 同时返回：

```text
PASS / ROOT_OWNED_RELEASE_VERIFIED
nqgatewWritable=false
```

Canonical activation 返回：

```text
PASS / EXACT_COMMIT_RELEASE_ACTIVATED
activeGateWUnits=0
```

Current after：

```text
/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f
```

Symlink 为 root 所有，activation 时间为 `2026-07-30 22:38:17.173029262 +0800`。未手工执行 `rm current` 或 `ln -s`，未修改
release owner、mode 或 content。

## 5. Systemd 合同

Canonical `install-units` 返回：

```text
PASS / FORMAL_UNITS_BOUND_TO_FIXED_RELEASE
activeGateWUnits=0
```

Unit links 精确指向：

```text
/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f/systemd/nq-gatew-soak@.service
/opt/nexus-quant/releases/c16f27c3c68d2484ad140d0557b879de08b7c78f/systemd/nq-gatew-soak-failclose@.service
```

`systemd-analyze verify` exit `0`。Worker 合同为 `User=nqgatew / Restart=no / 无 RuntimeMaxSec / NoNewPrivileges=true`
；fail-close 为
`Type=oneshot / TimeoutStartSec=30s / Restart=no / PrivateNetwork=true / RestrictAddressFamilies=AF_UNIX / NoNewPrivileges=true`
。验证只出现既有、无关的 `cloudmonitor.service` warning。

Acceptance finalizer 由当前 immutable control helper 的显式 action 承载，不是独立自动启动 unit；本轮未调用。

## 6. Pre-create hard gate

Canonical action：

```text
sudo -n pwsh -NoProfile \
  -File /opt/nexus-quant/current/bin/gatew-okx-readonly-soak-control.ps1 \
  -Action precreate-prerequisite
```

返回：

```text
checkedAt=2026-07-30T14:40:47.4464278+00:00
postgresReachable=false
managementHealthy=false
killSwitchEngaged=false
credentialConfigured=false
activeCredentialCount=0
credentialType=UNKNOWN
credentialLocalStatus=UNKNOWN
tradePermissionExpectedDisabled=false
withdrawPermissionExpectedDisabled=false
readyForAttemptCreation=false
diagnosticOnly=true
noSideEffect=true
credentialMaterialExposed=false
```

该 action exit 非零，治理合同判定：

```text
BLOCKED / CREDENTIAL_OR_PERMISSION_PRECHECK_FAILED
```

只读、脱敏 RCA 确认：

- pre-create descriptor 为 `root:root/0600`；schema、字段闭集、loopback host、固定 secret reference、management
  URL、credential type 和 environment 静态合同均有效。
- encrypted DB secret 文件只核对元数据：`root:root/0600`、非空；未读取或输出内容。
- management loopback health HTTP=`200`。
- PostgreSQL `127.0.0.1:55432` accepting connections。
- launcher `test-support.jar/modules/lib` 闭集存在，owner/mode 正确。
- current/release binding 保持精确。
- failure 收敛于 canonical Java readback 内部；helper 按合同不暴露可能包含敏感细节的原始异常，只返回统一前置失败结果。

进一步定位需要绕过 canonical helper、直接解密或捕获可能包含敏感信息的底层错误，不在本轮授权范围内，因此未执行。未运行
`prepare`，未重试 pre-create。

## 7. 停止决策与最终服务器事实

最终复核时间：`2026-07-30T14:45:21+00:00`。

```text
current=c16f27c3c68d2484ad140d0557b879de08b7c78f
new Attempt-10 run directories=0
new acceptance clock=0
active GateW units=0
GateW timers=0
systemd jobs=0
GateW pwsh/java worker processes=0
Attempt-09=inactive/dead
Attempt-09 MainPID=0
Attempt-09 NRestarts=0
OKX calls=0
```

因为 `prepare/start` 均未执行，所以 RunId、runtime manifest、worker-start evidence、MainPID、samples、heartbeat、hash
chain、acceptance clock、`verify-evidence`、`verify-acceptance`、`verify-terminal` 和 legacy `verify` 均为 `NOT_RUN`。

## 8. 执行中的非业务命令问题

- 首次 immutable verify 使用了仓库源码布局的 `scripts/gatew/...` 路径；release closed set 的实际 canonical 路径是
  `bin/...`。该命令在脚本加载前 exit `1`，服务器变更为 0；定位 closed-set 文件后使用正确 `bin/verify-gatew-release.ps1`
  验证通过。
- Activation action 主体返回 PASS 且 current/active unit 已复核；SSH here-string 尾部 CRLF 使随后仅用于展示 job count 的
  `wc -l` exit `1`。未重放 activation，随后使用新连接精确复核 current、unit、timer、job 状态。
- Template unit 直接传给 `systemctl show` 被 systemd 拒绝；改为读取 immutable unit 文件并运行 `systemd-analyze verify`
  ，未启动 unit。

这些命令问题未触发 retry、未重复激活、未创建 RunId、未调用 OKX，也不改变 pre-create hard gate 的失败结论。

## 9. Findings

### P0

- 无。

### P1

- Canonical `precreate-prerequisite` 的 Java readback fail closed，无法证明 PostgreSQL、management、kill switch、credential
  metadata hard gates，因此 Attempt-10 创建和启动必须阻断。

### P2

- Pre-create helper 将 descriptor、secret reader、Java/JDBC、management/readback schema 任一异常统一投影为全
  false；在不暴露原始异常的前提下，目前缺少可进一步分类的安全诊断码。后续 RCA 应增加脱敏错误分类，不得直接输出底层异常。

### P3

- PowerShell 经 SSH 的 here-string CRLF 会污染远端尾部 shell 命令；后续 wrapper 应在发送前显式执行 LF normalization。

## 10. 回滚与边界

- 本轮未自动回滚 current：新 release 已通过 immutable、POSIX、ownership、systemd 验证，且没有 unit 运行；生产回滚属于新的授权切换，不擅自执行。
- 后续若明确授权回滚，只能在 active GateW units/jobs 均为 0 时，对旧 release `1b501488...` 使用 canonical `activate` 与
  `install-units`，随后运行 `systemd-analyze verify`；禁止手工修改 symlink 或 unit。
- 未读取或输出任何 API Key、Secret、Passphrase、signature、credential payload、DB password 或 raw provider response。
- 未修改 credential、OKX permission、IP allowlist、cadence、retry budget、release 内容或历史 Attempt-09 evidence。
- 未触达 LIVE、order/cancel/transfer/withdraw、private WebSocket、AI、DH runtime、freeze/archive/tag。

## 11. Authority 一致性

Machine-readable authority 暂时保持：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-REMEDIATION-IMMUTABLE-RELEASE-DEPLOYMENT
work_batch_status=DEPLOYMENT_VERIFIED|CI_GREEN|ATTEMPT_10_PREPARATION_PENDING
work_batch_commit=c16f27c3c68d2484ad140d0557b879de08b7c78f
work_batch_ci_run=30537845010
next_action=NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START
```

原因：Attempt-10 仍未创建，preparation 仍 pending；当前治理合同没有表达失败的新状态或动作，本轮不自行扩展治理状态。再次执行该
action 前，必须先完成独立、脱敏、无 credential exposure 的 pre-create RCA/fix，并重新获得生产授权；禁止直接重试。
