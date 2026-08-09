# NQ-GATEW-SOAK-TOOLING-REDESIGN-IMPLEMENTATION — Attempt 01

## 1. 任务与结论

- 日期：`2026-07-19`。
- 执行模式：`CONTINUE_EXISTING_UNCOMMITTED_WORKTREE`。
- 分类：NQ-only / L 级
  `SYSTEMD_SERVICE_IMPLEMENTATION / RUNTIME_OWNERSHIP_HARDENING / INDEPENDENT_FAILCLOSE_RECOVERY / TERMINAL_STATE_MACHINE / OFFLINE_ACCEPTANCE_SMOKE`。
- 起始基线：`dev`；`HEAD == origin/dev == aac38cbde1bf9b99ba9e9fa2d96f8474c601aac7`；staged area 为空；8 个既有未提交文件全部位于任务
  allowlist。
- 当前本地结论：`PASS / FORMAL_SYSTEMD_SOAK_TOOLING_LOCALLY_IMPLEMENTED / P0_0 / P1_0 / READY_TO_COMMIT`（通过 / 正式
  systemd soak tooling 已完成本地实现 / 无 P0/P1 / 可进入提交前复核）。
- 尚未完成：implementation commit、exact-head CI、目标服务器部署、目标服务器 `systemd-analyze verify`、正式 unit
  完整离线验收。因此不得提前写 `CI_GREEN / SERVER_DEPLOYED / FULL_OFFLINE_ACCEPTANCE_PROVEN / READY_FOR_ATTEMPT_09`。

## 2. 目标与不做

目标是把 GateW soak Linux production lifecycle 收敛为版本化的正式 template unit，并将 worker、root control、independent
fail-close 三类 authority 分离；通过本地 regression 证明 literal config、credential boundary、terminal
create-once、operator/failure 分离与离线 cycle fixture。

本任务不修改 Java production code、migration、API、Controller、scheduler、frontend、research、CI workflow、`STATUS.md` 或
`ROADMAP.md`；不读取或输出 credential；不调用 OKX；不启动真实 soak；不启用 LIVE、Shadow、AI、DH、real provider 或交易写侧。

## 3. 现有实现审计矩阵

| 文件                                                  | 已实现能力                                                                                                                               | 本轮修正 / 结论                                                                                                                                     | 安全与测试                                                                 | 是否保留   |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|------------|
| `deploy/systemd/nq-gatew-soak@.service`               | `Type=simple`、`nqgatew`、`Restart=no`、`KillMode=mixed`、30 秒 stop、0700 runtime/state/log、hardening、`OnFailure/OnSuccess` finalizer | 符合固定 unit 合同                                                                                                                                  | worker 只写 evidence；root `ExecStartPre/ExecStopPost` 只处理 control fact | 保留       |
| `deploy/systemd/nq-gatew-soak-failclose@.service`     | root oneshot、独立 `failclose.env`、只加载 DB credential、loopback-only                                                                  | 符合独立故障域合同                                                                                                                                  | 不加载 OKX credential；公网 deny                                           | 保留       |
| `scripts/gatew/gatew-okx-readonly-soak-control.ps1`   | install/prepare/start/status/verify/stop/offline-fail、目录 owner/mode/symlink、frozen config、offline drop-in、lifecycle                | 无本地 P0/P1                                                                                                                                        | PS5.1/7 各 20 cases                                                        | 保留       |
| `scripts/gatew/gatew-okx-readonly-soak-failclose.ps1` | bounded recovery、独立 Java launcher、kill-switch readback、PID/residual、terminal create-once、operator/failure 分离                    | 无本地 P0/P1                                                                                                                                        | PS5.1/7 各 22 cases；credential/network=false                              | 保留       |
| `scripts/gatew/gatew-okx-readonly-soak.ps1`           | formal `run-loop`、Windows legacy regression、evidence/hash、正式 offline cycle 1/2 + cycle 3 controlled failure fixture                 | 删除完整旧 Linux transient/start/status/stop/smoke authority；Linux generic actions与 direct fail-close 均 fail closed 到 root controller/finalizer | 禁止 token 0 命中；AST 0 error；IDEA problems 0；PS5.1/7 各 40 cases       | 修正后保留 |
| `GateWOkxReadonlySoakCycleTest.java`                  | 正式 credential读取、offline/real action、闭合 DTO 输出                                                                                  | 覆盖 formal worker contract                                                                                                                         | 定向套件通过                                                               | 保留       |
| `GateWOkxReadonlySoakSupportTest.java`                | launcher、literal config、systemd/terminal/helper 静态与行为回归                                                                         | 覆盖单一 Linux production authority                                                                                                                 | 41 tests 通过                                                              | 保留       |
| `GateWOkxReadonlySoakFailCloseTest.java`              | offline bootstrap/sample/controlled failure 与 kill-switch fixture                                                                       | 覆盖独立 fail-close domain path                                                                                                                     | 9 tests，1 个正式入口按门禁 skipped                                        | 保留       |

未删除文件；只从 worker 中删除约 1,400 行旧 transient/smoke production authority 与对应失效 self-test。`systemd-run`、
`linux-smoke-*`、`Start-LinuxTransientUnit`、`Stop-LinuxTransientUnit`、`New-LinuxTransientUnitArguments` 在 worker/deploy
production source 中均为 0 命中。

## 4. Authority 与权限合同

- Worker authority：`nq-gatew-soak@.service` 以 `nqgatew:nqgatew` 运行，只通过正式 `run-loop` 写
  `/var/lib/nexus-quant/gatew-soak/<runId>/evidence`。
- Root authority：control helper 负责 install、prepare、lifecycle、intent、exit fact、status/verify 与 operator stop；`control`
  为 `root:root/0700`。
- Fail-close authority：独立 root oneshot 重新验证本地 DB、最多 3 次 bounded recovery、readback `ENGAGED`、核对
  unit/PID/residual/runtime，再 create-once terminal。
- Runtime ownership：正式 unit 声明 0700 `RuntimeDirectory/StateDirectory/LogsDirectory`；control self-test 覆盖 runId
  allowlist、lexical escape、symlink/reparse、terminal无出边。
- Secret contract：worker使用 `LoadCredentialEncrypted` 读取 DB password 与 credential master key；finalizer只加载 DB
  password；credential 不进入 EnvironmentFile、argv、evidence 或 terminal。非敏感 config 必须是完整 literal，变量引用 fail
  closed。
- Terminal：允许 `FAILURE_STOPPED / OPERATOR_STOPPED / COMPLETED / BLOCKED`；create-once，root owner-only；operator stop不得生成
  failure terminal。
- Lifecycle：Linux start/status/verify/stop 只由正式 root control helper + template unit负责；worker不再包含第二套 systemd
  lifecycle authority。

## 5. 本地验证

安全环境：
`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。

| Command / evidence                                            | Result                                   | Scope                                                                                                                           |
|---------------------------------------------------------------|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| PowerShell 5.1 / 7 worker self-test                           | PASS                                     | 各 40 cases；hash/tamper/append/resume/v1 immutable/formal offline fixture；`linuxProductionAuthority=FORMAL_ROOT_CONTROL_ONLY` |
| PowerShell 5.1 / 7 control self-test                          | PASS                                     | 各 20 cases；runId、literal config、state machine、path escape、terminal create-once、operator/failure separation               |
| PowerShell 5.1 / 7 fail-close self-test                       | PASS                                     | 各 22 cases；3 次 bounded recovery、lock、exit fact、terminal create-once、existing BLOCKED nonzero                             |
| PowerShell AST / source scan                                  | PASS                                     | parse errors=0；legacy helper calls=0；五类禁止 token=0                                                                         |
| IDEA reformat / file problems                                 | PASS                                     | worker reformat `ok`；errors/warnings=0                                                                                         |
| Java targeted GateW suite                                     | PASS                                     | 23/23 reactor modules；51 tests、0 failures、0 errors、2 skipped                                                                |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app -am test` | PASS                                     | 合同 no-outbound环境；23/23 modules `SUCCESS / BUILD SUCCESS`                                                                   |
| `mvn -f backend/pom.xml test`                                 | PASS                                     | 同一环境；23/23 modules；`nq-app` 211 tests、0 failures/errors、9 skipped                                                       |
| `git diff --check`                                            | PASS                                     | whitespace error=0                                                                                                              |
| WSL `systemd-analyze verify`                                  | `STRUCTURE_PARSED / FORMAL_PASS_PENDING` | unit可解析；WSL缺 `/usr/bin/pwsh` 且 drvfs mode映射产生告警，不能代替目标服务器正式 PASS                                        |

第一次 Java targeted 命令因 PowerShell 未引用 `-Dsurefire.failIfNoSpecifiedTests=false`，Maven把它解析为错误 lifecycle
token并在编译前退出；为两个 `-D` 参数加引号后原命令通过。既有非阻断 warning：Mockito dynamic-agent/JDK future、SLF4J NOP、部分
unchecked/deprecation 与 checkout EOL。

## 6. Findings

- P0：0。
- P1：0（本地实现范围）。旧 Linux transient authority 是唯一 P1，已删除并由 source self-test锁定。
- P2：0（本地实现范围）。
- P3：1；root `README.md` 的 GateW短摘要早于 current authority，但 `STATUS.md` 无冲突且该文件不在任务 allowlist，本任务不修改。
- 远端 hard gate：`PENDING`。目标服务器 formal unit acceptance 未通过前，不得把本地结论提升为最终成功。

## 7. 边界确认

- Authority 保持 GateW `IN_PROGRESS|NOT_FROZEN`、GateW-FREEZE `NOT_STARTED`、LIVE `DISABLED`。
- 真实 OKX calls=0；credential material access=0；permission probe未重跑；真实 soak未启动；acceptance clock未启动。
- 不修改历史 Attempt-01 至 Attempt-08 evidence；服务器验收必须比较其 hashes。
- 未触达 order/cancel/transfer/withdraw、LIVE、Shadow、AI、DH runtime、real provider 或 private trading write path。

## 8. Commit、CI、服务器与停止线

- Implementation commit：`UNCOMMITTED`。
- Exact-head CI：`NOT_RUN`。
- Server deployment / checkout：`PENDING`。
- Formal offline runId：`NONE`。
- `systemd-analyze verify` formal result：`PENDING_SERVER`。
- 完整 cycle 1/2、fresh SSH、cycle 3 failure、OnFailure、ENGAGED readback、terminal、PID/residual/runtime/listener：
  `NOT_RUN / PENDING_SERVER`。

CI GREEN 后只允许部署一次。若正式 unit 离线验收失败，立即执行停止线：确认 kill switch=`ENGAGED`、worker/finalizer
inactive、MainPID=0、residual=0；保留失败 run evidence；停止继续创建 remediation/Attempt-09/Attempt-10。服务器回滚为停止
unit、复核上述安全状态、从 owner-only backup 恢复 helper/unit并执行 `daemon-reload`；本地提交回滚使用后续显式
`git revert <implementation-commit>`，不得 reset/clean。

## 9. 下一动作

精确暂存本 evidence列出的实现、测试与允许的 docs 文件，完成 cached diff审查并创建唯一 implementation commit；push后等待
exact-head `NQ CI Baseline` 10 jobs全绿。只有 CI GREEN 后才部署目标服务器一次并执行完整正式-unit离线验收。
