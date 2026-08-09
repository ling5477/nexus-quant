# NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-IMPLEMENTATION — Attempt 01

## Task classification and current fact

- 类型：`SECURITY_REMEDIATION / CODE_CHANGE / TRANSIENT_SYSTEMD_RUNTIME / NO_NETWORK_SMOKE / COMMIT_AND_PUSH / EXACT_HEAD_CI / SERVER_DEPLOYMENT`。
- 当前本地结论：`PASS / LINUX_SUPERVISOR_DETACHMENT_REMEDIATED_LOCALLY / NO_NETWORK_SMOKE_CONTRACT_PROVEN / READY_TO_COMMIT`（通过 / Linux supervisor 脱离已完成本地修复 / no-network smoke 合同已证明 / 可进入提交前复核）。
- 起始基线：`dev`；`HEAD == origin/dev == 7a023c627ff1c63d179abb1740016aae60e95125`；starting CI run `29581459469` 为 `completed / success / 10 jobs / bad=0`。
- Commit：`UNCOMMITTED`；implementation exact-head CI：`NOT_RUN`；server deployment：`PENDING`；服务器真实跨 SSH smoke：`NOT_RUN`。
- 真实 OKX calls：`0`；credential 内容访问：`0`；permission probe：`NOT_RERUN`；真实 soak：`NOT_STARTED`。

## Implementation

### Transient systemd supervisor

- `Start-LoopProcess` 在 Linux 改为 `systemd-run` transient system service；Windows 分支保留原 `Start-Process -WindowStyle Hidden -PassThru`。
- unit 名绑定 validated runId，全部 native 参数为数组；固定 `/usr/bin/systemd-run`、`/usr/bin/systemctl`、`/usr/bin/pwsh` 与 supervisor 路径，无 `bash -c/eval/Invoke-Expression/nohup` command string。
- 固定并回读验证 `User/Group/WorkingDirectory/Restart/KillMode/TimeoutStopSec/PrivateTmp/NoNewPrivileges/UMask/PrivateNetwork/EnvironmentFile/FragmentPath`；启动结果必须为 `loaded/active/running/MainPID>0/User=nqgatew`。
- 真实 run 仅加载固定 owner-only env 文件且不读取内容；smoke unit 不加载 env，并额外使用 `PrivateNetwork=true`。
- `supervisor.json` 原子写入 unit/PID/start/run/action；只对新 sentinel 做 realpath allowlist 后的定向 chown/chmod，避免与高频 heartbeat atomic replace 竞争。
- 启动任一步骤失败时，定向 stop transient unit、验证 PID 0/residual 0/collected，再执行 fail-close；不得留下继续运行的 unit。

### Status, resume and stop

- Linux status 以 `systemctl show` 为 authority，并校验 `/proc` UID/cmdline、manifest/runId/evidence directory、owner-only sentinel 与 heartbeat。
- exact active identity 与较宽 residual tuple 分离，既防 PID 复用，也能发现带额外参数但仍绑定同 script/action/runId 的残余 supervisor。
- resume 使用 `AllowInactive` 区分已退出 supervisor 与终态 evidence；重新启动后返回 `unitName/mainPid/supervisorStartedAt` 并恢复 owner-only heartbeat。
- Linux stop 不因 stale heartbeat 拒绝停机；固定执行 `systemctl stop -> inactive -> MainPID=0 -> residual=0 -> reset-failed -> LoadState=not-found -> ENGAGE`。
- terminal run 重复 stop 返回 `NO_CHANGE / TERMINAL_RUN`，在 harness commit 校验前完成只读 guard，避免部署新 commit 后误写历史 terminal run。
- status/start/resume/stop 输出增加 unit、MainPID、systemd state、heartbeat 与 collect 结果，不输出 env 或 credential。

### Linux no-network smoke

- 新增 `linux-smoke-start/status/stop/loop` 四个 action，worker 只写固定 schema heartbeat，不调用 Maven、HTTP、OKX、credential 或 permission probe。
- manifest/heartbeat/sentinel 使用 exact field allowlist；manifest 绑定 detached tracked-clean `harnessCommit` 与 supervisor artifact SHA-256；固定 `credentialAccessed=false`、`networkCalled=false`、`acceptanceClockStarted=false`，禁止 samples/failures/final-summary 与 URL/secret/raw material。
- status 使用 `nsenter --target <MainPID> --net ss -H -lntup` 验证 unit network namespace listener count=0，并返回 heartbeat sequence/observedAt 供新 SSH 比较。
- stop 定向清理 unit；仅当 inactive/PID 0/residual 0/collected 和 evidence 合同均通过后，删除 realpath allowlist 内的临时 smoke 目录。

### Reason taxonomy and regression

- 增加 10 个任务要求的 detachment code，并增加 property/path/sentinel/process identity 四个细分 code；global catch 只透传安全分类。
- self-test 由 36 cases 扩展到 52 cases，新增 transient args、runId/path injection、systemctl parse/contract、inactive/MainPID/User fail-closed、heartbeat advance、systemd unavailable、unit-create failure、residual、Windows args 与 offline smoke boundary。

## Files created

- `docs/current/evidence/gate-w/NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-REVIEW.attempt-01.md`
- `docs/current/evidence/gate-w/NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-IMPLEMENTATION.attempt-01.md`
- `docs/current/evidence/gate-w/NQ-GATEW-FREEZE-BLOCKER-2-LINUX-SUPERVISOR-DETACHMENT-CONFORMANCE-REVIEW.attempt-01.md`

## Files changed

- `scripts/gatew/gatew-okx-readonly-soak.ps1`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/evidence/gate-w/README.md`

`STATUS.md` 与 `ROADMAP.md` 不修改，Authority 保持不变。

## Local validation

安全环境：`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。

| Command | Result | Scope / environment |
| --- | --- | --- |
| Windows PowerShell 5.1 `-Action self-test` | `PASS` | 52 cases；canonical hash、v1 immutable、injection/systemd/status/stop/smoke/Windows regression 全通过 |
| PowerShell 7 `-Action self-test` | `PASS` | 52 cases；与 PS5 canonical fixture hash 精确一致 |
| WSL systemd transient system fixture | `PASS` | `/bin/sleep` only；`active/running/MainPID=461`；全部冻结属性生效；stop 后 PID 0、unit `not-found` |
| PowerShell AST/native command audit | `PASS` | parse errors=0；forbidden command AST hits=0；2 个动态 invocation 均为固定 executable + argument array |
| IDEA file problems | `PASS` | errors/warnings=0；未重新格式化文件 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test` | `PASS` | 23/23 modules `SUCCESS`；`BUILD SUCCESS`；`nq-app` 196 tests、0 failures/errors、8 existing skipped |
| `mvn -f backend/pom.xml test` | `PASS` | 23/23 modules `SUCCESS`；`BUILD SUCCESS`；同一 no-outbound 环境 |
| static conformance/backstop | `PASS` | duplicate functions=0；missing reasons/actions=0；forbidden changed paths=0；secret literal hits=0；`git diff --check` PASS |

已知非阻断 warning：既有 SLF4J NOP、Mockito dynamic-agent/JDK future warning与 checkout EOL warning；WSL 输出既有 localhost/NAT warning，不影响 unit contract。WSL 未安装 `pwsh`，未为此安装依赖；完整 supervisor detached smoke 只在 CI GREEN 后的目标服务器执行。本机 `gitleaks` 不可用且未下载，exact-head CI 的 pinned gitleaks 是提交后 hard gate。

当前 supervisor working-tree artifact SHA-256：`c0882ef2f1e9f20073b82f7766eb5eac8cf2d598ceb93419a7810e8f70229a2b`；Git filtered blob candidate：`30a44cf0124f85c51335d80b7588d4ba975bfee2`。服务器部署必须从 implementation commit detached checkout 重新核对，不能直接信任本地未提交标识。

## Historical run immutable baseline

部署前已冻结的服务器 hash：

| Run | File | Pre-deployment SHA-256 |
| --- | --- | --- |
| `gatew-soak-20260716T145410Z-230ae5be` | `manifest.json` | `ff3c5c868a65cc61d86064f98eff82ab5cfad0884b76e276fbdbebb7648c7996` |
| 同上 | `heartbeat.json` | `ae534d7e3a5b495fb84222ddcef45cd914d9c27d8e138c3a0c573e627bf40768` |
| 同上 | `samples.jsonl` / `failures.jsonl` | `eab0a6a85eb3477f661eb8c4a7a8e121136dd0b2ad7e9766afde1048d984e65b` |
| `gatew-soak-20260717T122834Z-eb5ef11c` | `manifest.json` | `654f3c2c866798e9f9a74915c97e0054445ccfb30222c39ca3dc32151b4e71c2` |
| 同上 | `heartbeat.json` | `6555ced4ad69cc2a07961b3a097c18fa627fe8e2a45c9890511b56658c9344bc` |
| 同上 | `samples.jsonl` | `3c7bef142d30875c433b7cf92c7921d78ae36b949bd7f333f8925f5d6a16ea12` |
| 同上 | `failures.jsonl` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| 同上 | `stop-request.json` | `88c56ba5b160066a7d6b433c8042a9f67ba3d8cb9cfe9861f7c000a45df3efa9` |

两者 `final-summary.json` 均 absent；本地实现没有访问服务器 evidence。CI GREEN 后必须逐文件做 post hash，任何变化立即 `BLOCKED / HISTORICAL_RUN_MUTATED`。

## Post-commit hard gates

- 精确暂存、commit `fix(gatew): harden Linux soak supervisor detachment`、push `dev`。
- implementation commit 的 `NQ CI Baseline` 必须 `completed / success / exact head / 10 jobs / bad=0`。
- 服务器 detached checkout 到 implementation commit，tracked dirty=0，本地/远端 supervisor artifact SHA-256 一致。
- 两个独立 SSH 会话完成 smoke start/reconnect status/stop；证明 MainPID 不变、heartbeat 推进、`nqgatew` owner、PrivateNetwork/no listener、credential/network/acceptance 均 false。
- stop 后 unit inactive/PID 0/residual 0/collected、临时 smoke 文件删除、kill switch 仍 ENGAGED、两个历史 run hash 完全不变。

上述 hard gate 当前均为 `NOT_RUN / PENDING`，本 evidence 不提前写 `COMMITTED / CI_GREEN / SERVER_DEPLOYED`。

## Boundary and rollback

- 无 backend/API/scheduler/migration/frontend/research/deploy/`.github`/archive/authority/交易能力 diff；真实 OKX、credential、permission probe、soak、LIVE/order/cancel/transfer/withdraw 均未触达。
- 工作区回滚：仅反向应用本任务 script/docs diff；不使用 `git reset --hard`，不删除或重写服务器历史 evidence。
- runtime 回滚：`systemctl stop <validated-unit>`，验证 PID 0/residual 0 后将服务器 detached checkout 回到 `7a023c627ff1c63d179abb1740016aae60e95125`；不得恢复或续跑旧 run。

下一动作：完成 conformance/cached diff review 后 commit/push；exact-head CI GREEN 后只部署 supervisor 并运行 offline smoke，不启动真实 soak。真实新 run 只能由后续 `ATTEMPT_08` 创建。
