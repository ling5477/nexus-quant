# NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-REVIEW — Attempt 01

## Review target

- Task：`NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-REMEDIATION`。
- 类型：`SECURITY_REMEDIATION / LINUX_PROCESS_SUPERVISION / TRANSIENT_SYSTEMD_RUNTIME / NO_NETWORK_SMOKE`。
- 起始基线：`dev`，`HEAD == origin/dev == 7a023c627ff1c63d179abb1740016aae60e95125`；`NQ CI Baseline` run `29581459469` 为 `completed / success / 10 jobs / bad=0`。
- Authority：GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；next action `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`；LIVE `DISABLED`。
- Review 范围：`scripts/gatew/gatew-okx-readonly-soak.ps1`、Attempt-07 failure evidence、Linux process/systemd/SSH 生命周期与 no-network smoke 合同。
- 明确不涉及：真实 OKX 调用、credential 内容、permission probe、真实 soak、旧 run 恢复、LIVE、交易写侧、API、scheduler、migration、frontend、deploy、`.github`。

## RCA

1. 旧 Linux 路径与 Windows 共用 `Start-Process -WindowStyle Hidden -PassThru` 启动 `run-loop`；Windows PowerShell 5.1/7 支持该参数，Linux PowerShell 7.6.3 抛 `NotSupportedException`。
2. 异常发生在子进程创建前，因此没有可脱离的 `run-loop`，也没有新的 supervisor PID/cgroup 可跨 SSH 存活；外层 SSH wrapper 是否使用 `nohup` 不能修复该平台调用失败。
3. 旧合同依赖父 PowerShell 启动普通子进程，没有 system manager 作为生命周期 authority；即便删除 `-WindowStyle`，普通子进程仍缺少 systemd unit/cgroup、固定 User/Group、定向 stop 与残余进程证明。
4. `supervisor.json` 只在 `Start-Process` 成功返回后写入；异常发生在此之前，所以 PID 与 startedAt 都未可靠回写。
5. 全局 catch 只透传 `BLOCKED|FAIL / <SAFE_CODE>`；平台异常不是独立安全 code，最终被折叠为 `FAIL / SUPERVISOR_INTERNAL_ERROR`。
6. 原 36-case self-test 只覆盖 fixture、schema、hash、blob/artifact 与 Windows 启动参数，没有创建真实 Linux detached process，因而未暴露 `-WindowStyle` 平台不兼容。
7. 旧 PowerShell 参数数组未发现直接 shell 拼接注入，但缺少 systemd unit-name/runId strict validation 与固定 native executable contract；不得用 `bash -c`、`eval`、`Invoke-Expression` 或拼接后的 `nohup` 作为修复。
8. 旧 status/stop 仅信任 PID 文件与 `StartTime`，无法以 systemd cgroup 证明唯一 supervisor，也不能充分防止 PID 复用、重复 supervisor、SSH orphan 或 stop 后 residual。

## Frozen Linux supervision contract

- Linux 只使用 transient system service：`systemd-run` 创建 `nq-gatew-soak-<validated-runId>.service`；不创建 `/etc/systemd/system/*.service`。
- `runId` 同时通过 `[a-zA-Z0-9._-]` 安全字符校验与现有 GateW runId 结构校验；所有 native invocation 使用固定 executable 与参数数组，不经过 shell command string。
- 固定属性：`User=nqgatew`、`Group=nqgatew`、`WorkingDirectory=/opt/nexus-quant/gatew-soak`、`Restart=no`、`KillMode=mixed`、`TimeoutStopSec=30`、`PrivateTmp=true`、`NoNewPrivileges=true`、`UMask=0077`、`--collect`。
- 真实 `run-loop` 只加载固定 owner-only `EnvironmentFile=/opt/nexus-quant/gatew-soak/config/management.env`；只核对 metadata，不读取或输出内容。
- 启动后必须从 `systemctl show` 验证 `LoadState/ActiveState/SubState/MainPID/ExecMainStatus/FragmentPath/User` 及全部冻结属性，再将 `unitName/mainPid/startedAt/runId/workerAction` 原子写入 owner-only sentinel。
- Linux status 以 `systemctl show` 为 authority，同时核对 `/proc/<MainPID>` owner、完整固定 cmdline、manifest/runId/evidence directory、sentinel 与 heartbeat 新鲜度。
- active identity 使用 exact cmdline；residual 扫描使用固定 `pwsh + supervisor path + action + runId` tuple，可识别带额外参数的同 runId 残余进程。
- stop 固定为 `systemctl stop -> ActiveState=inactive -> MainPID=0 -> residual=0 -> reset-failed -> LoadState=not-found`，之后才写脱敏终态并 ENGAGE kill switch；禁止 `pkill/killall`。
- terminal run 的重复 stop 为 `NO_CHANGE / TERMINAL_RUN`（无变化 / 终态 run），不写 `stop-request.json`、heartbeat 或历史 evidence。
- Windows 继续使用原 `Start-Process -WindowStyle Hidden -PassThru` 参数合同，不受 Linux 分支影响。

## Detachment reason taxonomy

至少冻结并由全局安全 catch 透传：

```text
SYSTEMD_RUN_UNAVAILABLE
TRANSIENT_UNIT_CREATE_FAILED
TRANSIENT_UNIT_NOT_ACTIVE
TRANSIENT_UNIT_MAIN_PID_MISSING
TRANSIENT_UNIT_USER_MISMATCH
SUPERVISOR_PROCESS_EXITED
SUPERVISOR_HEARTBEAT_NOT_ADVANCING
SUPERVISOR_RECONNECT_STATUS_FAILED
SUPERVISOR_STOP_FAILED
SUPERVISOR_RESIDUAL_PROCESS_FOUND
```

附加 fail-closed code：`TRANSIENT_UNIT_PROPERTY_MISMATCH`、`LINUX_RUNTIME_PATH_INVALID`、`SUPERVISOR_SENTINEL_INVALID`、`SUPERVISOR_PROCESS_IDENTITY_MISMATCH`。任何错误输出都不得包含完整命令、env 值、credential 或 provider material。

## Offline smoke contract

- 独立 action：`linux-smoke-start/status/stop/loop`；worker 只写固定 schema heartbeat。
- unit 强制 `PrivateNetwork=true`，不加载 `EnvironmentFile`；manifest/heartbeat 固定 `credentialAccessed=false`、`networkCalled=false`、`acceptanceClockStarted=false`。
- status 从新 SSH 会话核对相同 `MainPID`、推进后的 heartbeat、`nqgatew` owner、unit 属性、sentinel 与 unit net namespace 内 listener count=0。
- stop 后核对 inactive/PID 0/residual 0/unit collected，再删除仅位于 `target/gatew-linux-transient-smoke/<validated-runId>` 的临时 smoke 文件。
- smoke 不创建 samples/failures/final-summary，不调用 OKX，不读取 credential，不执行 permission probe，不启动 acceptance clock，不修改既有 run。

## Historical evidence hard boundary

- `gatew-soak-20260716T145410Z-230ae5be` 与 `gatew-soak-20260717T122834Z-eb5ef11c` 均为 immutable terminal evidence。
- 本地实现阶段不挂载、恢复、append、rewrite、delete 或生成其 `final-summary.json`；服务器部署前后必须逐文件比较已记录 SHA-256，任一差异立即 `BLOCKED / HISTORICAL_RUN_MUTATED`。

## Findings and decision

- P0：0。
- P1：0；设计关闭 `LINUX_DETACHED_START_INCOMPATIBLE` 与 `LINUX_START_LOOP_PROCESS_SMOKE_MISSING` 两个根因。
- P2：0。
- P3：0。
- Post-commit exact-head CI 与服务器真实跨 SSH smoke 尚未发生，属于 acceptance hard gates，不在本 review 中提前写 PASS。

Decision：`PASS / LINUX_SUPERVISOR_DETACHMENT_REMEDIATION_DESIGN_ACCEPTED / IMPLEMENTATION_AUTHORIZED`（通过 / Linux supervisor 脱离修复设计已接受 / 允许实施）。
