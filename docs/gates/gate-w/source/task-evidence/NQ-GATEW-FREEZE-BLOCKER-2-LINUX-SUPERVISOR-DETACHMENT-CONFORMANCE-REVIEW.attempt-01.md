# NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-CONFORMANCE-REVIEW — Attempt 01

## Review target and evidence

- 独立复核 `scripts/gatew/gatew-okx-readonly-soak.ps1` 的 Linux transient start/status/resume/stop、reason taxonomy、owner/PID/cmdline/sentinel、offline smoke 与 Windows regression。
- 核对 Attempt-07 RCA、起始 exact-head CI、Authority、允许/禁止路径、双引擎 self-test、WSL systemd fixture、AST/native command、IDE problems、targeted/full Maven 与 secret/scope backstop。
- 本 review 与实现检查分阶段执行；发现的启动失败 residual 与 heartbeat/chmod race 已在最终 diff 中最小修复并重新运行相关验证。

## Findings

- P0：0。
- P1：0。Linux 不再执行 `Start-Process -WindowStyle Hidden`；systemd 是 lifecycle authority，失败启动会定向 stop 并证明无 residual；真实 Linux smoke 已成为可执行的提交后 hard gate。
- P2：0。
- P3：0。
- Post-commit exact-head CI、目标服务器 artifact 与跨 SSH real smoke 尚未发生，保持 `NOT_RUN / PENDING`，不伪装成本地 finding 已关闭。

## Conformance verified locally

- transient system service 可脱离调用 SSH/session cgroup，由 system manager 持有；unit 使用 `--collect`，无永久 service 文件。
- unit/runId/path/native executable 均受 strict validation；没有 shell string、`bash -c`、`eval`、`Invoke-Expression`、`nohup`、`pkill` 或 `killall`。
- 冻结属性在 WSL 真实 systemd fixture 中精确回读：`Restart=no`、`TimeoutStopUSec=30s`、`UMask=0077`、`WorkingDirectory=/tmp`（fixture）、`PrivateTmp=yes`、`PrivateNetwork=yes`、`NoNewPrivileges=yes`、`KillMode=mixed`、`loaded/active/running/MainPID>0`；stop 后 `MainPID=0/LoadState=not-found/inactive/dead`。
- production unit builder 固定 `User/Group=nqgatew` 与 `/opt/nexus-quant/gatew-soak`；PS 5.1/7 fixture 对参数数组和 property parser/contract 均 PASS。
- status 以 `systemctl show` 为 authority；同时验证 `/proc` UID、exact command line、runId/evidence manifest、owner-only sentinel、heartbeat。sentinel/MainPID/cmdline 三重绑定关闭 PID reuse。
- duplicate unit、active unit 与同 runId residual 均 fail-closed；residual matcher覆盖固定 tuple上的额外参数，stop 后必须 residual=0。
- stop 不依赖 heartbeat 新鲜度；`systemctl stop`、inactive、PID 0、residual 0、reset/collect全部成功后才完成 fail-close，且不使用宽泛进程终止。
- terminal no-change guard 在 harness commit校验前只读返回，避免新 commit 部署后重复 stop改写两个历史 terminal run。
- owner-only目录与 sentinel 使用 realpath allowlist；递归 chown/chmod只在 worker启动前或停止后执行，高频 smoke heartbeat期间只定向处理 sentinel，避免 atomic temp race。
- offline smoke unit强制 `PrivateNetwork=true`且不加载 env；exact schema证明 `credentialAccessed=false/networkCalled=false/acceptanceClockStarted=false`，禁止 sample/failure/final-summary、URL、secret/raw material。
- reconnect status返回 unit/MainPID/heartbeat sequence；目标服务器流程必须在新 SSH 中证明 MainPID不变与heartbeat推进，不允许同会话自证。
- unit net namespace 使用 `nsenter + ss` 验证 listener count=0；stop成功后删除经 allowlist验证的临时目录。
- Windows accepted Start-Process参数由 self-test精确比较，PS 5.1与PS 7各52 cases PASS；canonical fixture hash exact match，原36-case schema/hash/legacy/unsafe覆盖全部保留。
- targeted/full Maven均23/23 modules `SUCCESS / BUILD SUCCESS`；真实 OKX/network/credential access为0。
- IDEA problems=0；PowerShell parse errors=0、duplicate functions=0、forbidden command AST hits=0、missing required reasons/actions=0、forbidden changed paths=0、secret literal hits=0、`git diff --check` PASS。

## Historical evidence and safety boundary

- 本地 diff 不读取或修改服务器 run；两组 pre-deployment SHA-256 已记录在 implementation evidence。
- 服务器 smoke 前后必须验证 run 16/17 append/rewrite/delete/resume/final-summary count均为0、acceptance clock保持 false；hash不一致立即阻断。
- smoke不加载 management env，因此不能访问 credential；`PrivateNetwork=true`在 kernel namespace层阻断网络，即使 worker未来误引入网络调用也无法出站。
- 本任务不修改 endpoint allowlist、permission probe contract、V31/V35、API、scheduler、migration、frontend、research、deploy、CI workflow、LIVE或交易状态机。

## Known limitations

- WSL没有`pwsh`，本轮未安装系统依赖；WSL fixture只证明真实 systemd property/collect，不证明完整 supervisor process。
- 完整 Linux detached smoke只能在 implementation exact-head CI GREEN 后，于固定目标服务器、两个独立 SSH会话中执行；在此之前最终任务状态不能写 `REAL_NO_NETWORK_DETACHMENT_SMOKE_PROVEN`。
- 本机没有`gitleaks`且未下载；本地 custom-regex backstop通过，pinned gitleaks留给exact-head CI验证。
- 既有 SLF4J/Mockito/JDK agent、checkout EOL和WSL localhost/NAT warning均为非阻断环境 warning，不改变 conformance verdict。

## Boundary confirmation

未读取、录入、轮换或输出 credential；未调用真实 OKX；未重跑 permission probe；未启动、恢复或续跑真实 soak；未启用 LIVE、order/cancel/transfer/withdraw、AI或DH。Authority 保持 GateW `IN_PROGRESS|NOT_FROZEN`、GateW-FREEZE `NOT_STARTED`。

## Decision

`PASS / LINUX_SUPERVISOR_DETACHMENT_ACCEPTED / READY_TO_COMMIT`（通过 / Linux supervisor 脱离修复已接受 / 可进入提交前复核）。

该 decision 只接受本地 implementation/conformance；最终 `COMMITTED / CI_GREEN / SERVER_DEPLOYED / REAL_NO_NETWORK_DETACHMENT_SMOKE_PROVEN` 必须等待提交后的外部 hard gates。
