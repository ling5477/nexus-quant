# NQ-GATEW-SOAK-TOOLING-REDESIGN-DECISION — Attempt 01

## 1. Decision status

- Task classification：
  `ARCHITECTURE_DECISION / OPERATIONAL_SAFETY_REVIEW / SYSTEMD_RUNTIME_CONTRACT / FAILCLOSE_DESIGN / ACCEPTANCE_STRATEGY_REVIEW / DOCUMENTATION`
  ，NQ-only，L 级。
- Decision：`PASS / SOAK_TOOLING_REDESIGN_DECIDED / READY_FOR_SINGLE_IMPLEMENTATION`（通过 / soak tooling 重构方案已决定 /
  可进入唯一实现任务）。
- 唯一选择：正式版本化 `nq-gatew-soak@.service` + 独立 `nq-gatew-soak-failclose@.service` + root-controlled
  control/terminal helper。
- 当前 tooling 仍为 `BLOCKED / NOT_READY_FOR_REAL_SOAK`；本 decision 通过不代表运行实现、离线闭环、服务器部署或 168 小时
  soak 已通过。
- 下一任务唯一为 `NQ-GATEW-SOAK-TOOLING-REDESIGN-IMPLEMENTATION`；不再创建 plan review、decision review 或 decision
  freeze。

## 2. Baseline and authority

| 项目                         | 已核对事实                                                  |
|------------------------------|-------------------------------------------------------------|
| Repository / branch          | `E:/Project/nexus-quant` / `dev`                            |
| Starting HEAD                | `773872e5c17977b98f28a2c13bf79ad223627c62`                  |
| `origin/dev`                 | 与 Starting HEAD 精确一致                                   |
| Starting worktree            | clean；staged / unstaged / untracked 均为 0                 |
| Starting CI                  | `29641988419 / completed / success / exact head / 10 of 10` |
| Current authority            | GateW `IN_PROGRESS\|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；LIVE `DISABLED` |
| Authority checker            | `PASS / CURRENT_AUTHORITY_CONSISTENT`                       |
| Acceptance clock             | `NOT_STARTED`                                               |
| Active GateW unit / residual | `0 / 0`（任务输入事实；本轮未访问服务器复查）               |

`docs/current/STATUS.md` 仍是 current authority。本任务由明确任务指令授权为 GateW tooling redesign decision；它是
`NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` 之前的安全补救决策链，不修改 `STATUS.md`、`ROADMAP.md`、GateW 状态或 authority 中的
next action。

本轮只读审查真实代码、历史 evidence 与 Git 变更；未修复代码、未部署、未连接服务器、未调用 OKX、未读取 credential、未创建
Attempt-09/10、未启动 acceptance clock。

## 3. Evidence reviewed

### 3.1 Current implementation

- `scripts/gatew/gatew-okx-readonly-soak.ps1`
    - 固定 Linux 环境、runtime、evidence、smoke 路径：lines 48-51。
    - transient unit 参数与 `EnvironmentFile`：lines 608-673。
    - runtime environment 创建、owner 调整与删除：lines 1315-1378。
    - transient unit start、property/process 校验与 exec 后环境文件删除：lines 1573-1665。
    - supervisor 内部 `Stop-FailClosed`：lines 1815-1832。
    - run-loop failure、stop request 与终态写入：lines 1989-2035、2112-2155。
    - offline smoke evidence、两 cycle worker、stop 后删除目录：lines 2186-2305、2414-2746。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java`
    - `bootstrap / sample / engage` 共享 launcher 和 DB 初始化：lines 159-194。
    - idempotent engage 与 read-back：lines 353-379。
    - JDBC URL 与 `inet_server_addr()` 双重 DB locality 校验：lines 422-434、875-925。
    - canonical output root、realpath 与 symlink 防护：lines 591-622。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java`
    - DB locality、engage 不读取 exchange credential、sanitizer 和路径合同 regression。
- `backend/nq-app/src/main/resources/application-gatew-okx-readonly-soak.yml`
    - profile、loopback DB、LIVE/AI/DH/real write flags 与 credential master-key runtime contract。

### 3.2 Historical and remediation evidence

- Attempt-01 至 Attempt-08 全部 server deployment/bootstrap evidence。
- sanitizer review / implementation / conformance evidence。
- Linux detachment review / implementation / conformance evidence。
- `408bb739` 后的 supervision closure、literal environment 与 offline path 修复提交：`ea7b1d69`、`773872e5`。
- `docs/current/GATEW_PLAN.md:165-179` 的连续 7 天、24 小时 checkpoint、99.5% 和 hard-criterion reset 合同。

Attempt 链的决定性事实如下：

| Attempt | 结果                                                                                                  | 对本 decision 的含义                                |
|---------|-------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| 01      | SSH timeout                                                                                           | 未进入 runtime                                      |
| 02      | SSH authentication blocked                                                                            | 未进入 runtime                                      |
| 03      | server isolation 不满足                                                                               | 证明部署边界必须先于启动                            |
| 04      | isolation/DB 完成，credential/CI runtime auth/permission path 缺失                                    | 证明启动前 hard gates 必须集中验证                  |
| 05      | credential 加密落库，真实 permission probe HTTP failure                                               | 不足以启动 soak                                     |
| 06      | permission/IP metadata 成功，sanitizer 拒绝合法 balance category                                      | 真实 outcome 未形成 durable sample                  |
| 07      | 首样本成功，Linux `Start-Process -WindowStyle Hidden` 失败                                            | 普通子进程不能作为 Linux lifecycle authority        |
| 08      | 首样本与初始 reconnect/PID 成功；第二周期 DB locality 失败；automatic engage 失败；人工 recovery 成功 | 暴露 runtime、recovery、terminal 三个结构性同故障域 |

Attempt-08 的直接证据为：

- `SOAK_DATABASE_NOT_LOCAL` 发生在第二周期、credential/OKX network 前；见 Attempt-08 lines 166-184。
- automatic engage 复用同一无效 DB environment，进入 `STOP_FAILURE / KILL_SWITCH_ENGAGE_FAILED`；见 lines 226-243。
- 人工 `failure-stop` 最终形成 `FAILURE_STOPPED / OPERATOR_FAILURE_STOP`，unit/PID/residual 已安全收口且 kill switch
  `ENGAGED`；见 lines 186-211。
- 普通控制命令可以形成故障终态，证明 `FAILURE_STOPPED` 与 operator intent 未隔离。
- collected transient unit 使普通 `status` 返回 generic failure；见 lines 247-253。
- acceptance 候选时钟已作废并保持 `NOT_STARTED`；见 lines 160-162。

HEAD 已修复 literal EnvironmentFile 与 canonical offline path，但没有改变以下结构事实：worker 与 recovery 仍共享
launcher/environment，terminal 仍依赖可覆盖 heartbeat，offline smoke 仍只证明 bootstrap → engage 两记录并在 stop 后删除目录。

## 4. Current tooling diagnosis

### P0

- 无。当前无 active GateW unit、无 residual、kill switch 为 `ENGAGED`、LIVE 禁用；本轮未执行真实操作。

### P1

1. **Runtime ownership 不是 service-manager contract。** transient unit 依赖脚本在启动前创建/chown `/run` 与 evidence
   目录；service unit 本身没有 `RuntimeDirectory=` / `StateDirectory=` 声明，权限正确性不能由已版本化 unit 直接证明。
2. **Automatic recovery 与 worker 处于同故障域。** `Stop-FailClosed` 从 supervisor 内以相同 launcher、DB 环境和 evidence
   root 执行 `engage`；Attempt-08 已实证同一 DB 环境错误可同时击穿 worker 和 recovery。

### P2

1. **终态语义可伪造。** `failure-stop` 是普通 operator action，却可写 `FAILURE_STOPPED / OPERATOR_FAILURE_STOP`；普通 stop
   使用 `STOPPED`，未冻结 `OPERATOR_STOPPED`。
2. **Heartbeat 同时承担 live snapshot 与 terminal authority。** 可覆盖的 `heartbeat.json` 不能作为 create-once terminal
   record，也不能把 systemd result、PID=0、residual=0、kill-switch read-back 与 evidence hash 原子绑定。
3. **Offline smoke 未证明要求的失败闭环。** 当前 cycle 1 是 bootstrap、cycle 2 是 automatic engage；没有两个 read-only
   PASS、受控 cycle 3 failure、真正的 `OnFailure`、durable `FAILURE_STOPPED`，并在 stop 后删除 smoke directory。
4. **目录职责分散。** repo `target`、`/opt/.../evidence`、`/run/.../worker.env`、sentinel、heartbeat 和 terminal state
   混合，durable state 与 ephemeral runtime 的边界不清晰。

### P3

- 根 `CLAUDE.md` 仍有旧 GateJ 叙述；`STATUS.md` 优先级更高，因此不构成 current authority conflict，也不在本任务
  allowlist。后续自然触碰时再修正，不为此新增 docs-only task。

## 5. Why patching must stop

`ea7b1d69` 已对 transient 方案增加大量 start/status/stop/env/smoke 分支，`773872e5` 又修正 offline root。继续 patch
transient unit 仍需由 3,600+ 行脚本自行模拟 package-managed service 应有的目录、owner、unit identity、terminal 和 recovery
contract；每次修复都会同时扩大 worker、operator control 与 fail-close 的共享故障域。

当前问题不是单个 literal path 或参数缺失，而是 ownership authority、recovery authority 和 terminal authority 没有分离。继续补
transient unit 无法以最小可审查变更关闭这三个结构缺陷，因此必须停止多轮局部 patch，改为一次正式 implementation。

## 6. Options and decision matrix

评分采用 1～5 分，5 为该维度最优；九项等权，总分只用于显示取舍，安全 hard gate 不因总分抵消。

| 维度                 | A：正式 systemd template | B：继续 transient unit | C：容器化 supervisor |
|----------------------|-------------------------:|-----------------------:|---------------------:|
| 运行可靠性           |                        5 |                      3 |                    3 |
| 权限可证明性         |                        5 |                      2 |                    4 |
| fail-close 可靠性    |                        5 |                      2 |                    3 |
| 终态可审计性         |                        5 |                      2 |                    4 |
| 部署复杂度           |                        3 |                      4 |                    1 |
| 回滚复杂度           |                        4 |                      4 |                    2 |
| 证据完整性           |                        5 |                      3 |                    3 |
| 与现有 NQ 架构一致性 |                        5 |                      3 |                    2 |
| 长期维护成本         |                        5 |                      2 |                    1 |
| **总分**             |              **42 / 45** |            **25 / 45** |          **23 / 45** |

### Option A — selected

正式版本化 template unit 可直接声明 `User/Group`、`RuntimeDirectory`、`StateDirectory`、`LogsDirectory`、fixed `ExecStart`
、cgroup stop、hardening 与 `OnFailure`。template fragment 在实例停止后仍存在，`status` 不依赖 transient `--collect`；独立
fail-close service 可使用独立配置和最小 credential。部署增加两个 unit 与一次 `daemon-reload`，但这是有界、版本化、可回滚的复杂度。

### Option B — rejected

transient unit 可以继续补 root pre-create、literal env、helper 与 terminal sentinel，但 unit contract 仍由命令行动态拼装并散落在脚本中；每个
run 都要重新证明 property、owner 和 path，collected unit 仍造成 terminal diagnostics 缺口。更重要的是，它保留当前高维护成本和
patch inertia，不能提供比正式 unit 更强的安全收益。

### Option C — rejected

容器可提供 namespace 与 mount 权限，但当前 management app/PostgreSQL/systemd 已在主机上运行。容器化会新增 image
provenance、credential/evidence mount、host-loopback DB、Docker daemon 权限、systemd/Docker 双重监督和额外 rollback
surface；这些复杂度不解决 NQ 特有的 kill-switch transaction 与 terminal 语义，且不符合当前最小变更原则。

## 7. Selected architecture

### 7.1 Components

| 组件                                      | 权限域                           | 唯一职责                                                                                                     |
|-------------------------------------------|----------------------------------|--------------------------------------------------------------------------------------------------------------|
| root-controlled `soakctl` / finalizer     | root                             | preflight、run 目录与 frozen config、合法状态转换、operator intent、systemd start/stop、terminal create-once |
| `nq-gatew-soak@<runId>.service`           | `nqgatew:nqgatew`                | 两个 typed read-only operation、cadence、heartbeat、samples/failures；不能写 control/terminal                |
| `nq-gatew-soak-failclose@<runId>.service` | root-controlled hardened oneshot | 独立 DB locality 校验、幂等 engage、read-back、PID/residual 复核、failure/operator terminal                  |
| versioned offline fixture                 | worker + fail-close 两 unit      | 无 OKX/无 exchange credential地证明完整 lifecycle；不能启动 acceptance clock                                 |

`nq-gatew-soak@.service` 的失败通过 `OnFailure=nq-gatew-soak-failclose@%i.service` 触发。operator stop 由 root-controlled
`soakctl` 先记录 `OPERATOR_STOPPING`，再停止 worker 并显式启动同一 idempotent fail-close unit。

`ExecStopPost` 只能把 systemd 的 `$SERVICE_RESULT/$EXIT_CODE/$EXIT_STATUS` 写入 root-owned control record，并以
non-blocking 方式补充触发 fail-close；它不得连接 DB、执行 `engage` 或写成功终态。automatic engage 的唯一执行位置是独立
fail-close service。

### 7.2 Non-negotiable invariants

- worker 永远不能写 `control/`、`terminal-status.json` 或 recovery config。
- fail-close 永远不加载 OKX credential、credential master key、account/currency config 或 provider client。
- 任何 unit restart、PID 变化、manual resume 或 run 目录复用都使该 run 不再具备 acceptance 资格。
- 一个 `runId` 只能创建一次；terminal 后无状态回退、resume、append 或 terminal overwrite。
- 所有 CLI/native invocation 使用固定 executable + 参数数组；禁止 `bash -c`、`eval`、`Invoke-Expression`、command string 与
  arbitrary path。

## 8. Directory, owner and lifecycle contract

`runId` 继续使用精确格式 `^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$`。每个路径组件必须以 `lstat + realpath` 验证为非
symlink 且留在固定 root；不接受 URL/path 编码、`..`、额外分隔符、大小写变体或任意 unit instance。

| 路径                                              | 创建者                                  | Owner / mode                           | 生命周期与持久性                                   | 读写者                         | Secret                                            |
|---------------------------------------------------|-----------------------------------------|----------------------------------------|----------------------------------------------------|--------------------------------|---------------------------------------------------|
| `/etc/nexus-quant/gatew-soak/`                    | versioned installer，root               | `root:root / 0750`                     | 静态；只在独立配置变更时替换                       | root 写；systemd manager 读    | 可含 encrypted credential blob，不含 plaintext    |
| `/etc/nexus-quant/gatew-soak/worker.env`          | root                                    | `root:root / 0600`                     | 静态 non-secret literal template；run 前复制并冻结 | root 写；systemd manager 读    | 否                                                |
| `/etc/nexus-quant/gatew-soak/failclose.env`       | root                                    | `root:root / 0600`                     | 独立最小 DB recovery template                      | root 写；fail-close manager 读 | 否                                                |
| `/etc/nexus-quant/gatew-soak/credentials/*.cred`  | root + `systemd-creds`                  | `root:root / 0600`                     | encrypted source；运行中禁止轮换                   | root/systemd only              | 是，加密态；不得进入 evidence                     |
| `/run/nexus-quant/gatew-soak/<runId>/`            | worker unit `RuntimeDirectory=`         | `nqgatew:nqgatew / 0700`               | ephemeral；unit terminal 后由 systemd 删除         | worker 写；root 读             | 否                                                |
| `/run/credentials/nq-gatew-soak@<runId>.service/` | systemd `LoadCredentialEncrypted=`      | systemd 管理的只读 owner-only mode     | unit 生命周期；自动删除                            | 仅该 unit                      | 是，解密态；不得复制                              |
| `/var/lib/nexus-quant/gatew-soak/<runId>/`        | root preflight + `StateDirectory=` leaf | parent `root:root / 0750`              | durable；terminal 后保留                           | root 读；按子目录授权          | control 可含 encrypted blob；evidence 不含 secret |
| `.../<runId>/evidence/`                           | `StateDirectory=`                       | `nqgatew:nqgatew / 0750`；files `0640` | durable append/hash evidence；不自动清理           | worker 写；root verifier 读    | 否                                                |
| `.../<runId>/control/`                            | root preflight                          | `root:root / 0700`；files `0600`       | durable lifecycle/recovery/terminal authority      | root helper/fail-close only    | 仅 `recovery/*.cred` 可含 encrypted blob          |
| `/var/log/nexus-quant/gatew-soak/<runId>/`        | worker unit `LogsDirectory=`            | `nqgatew:nqgatew / 0750`；files `0640` | durable sanitized logs；按明确 retention 清理      | worker 写；root 读             | 否                                                |

固定文件职责：

- runtime：`runtime-sentinel.json`、临时 launcher output、non-secret progress IPC；PID authority 始终来自 systemd `MainPID`。
- evidence：root-created `manifest.json`、worker `heartbeat.json`、`samples.jsonl`、`failures.jsonl`、per-cycle sanitized
  artifacts；heartbeat 只是 live snapshot，不是 terminal。
- control：`lifecycle.json`、`systemd-result.json`、`acceptance-clock.json`、frozen `worker.env`/`failclose.env`、encrypted
  per-run credential copy、`terminal-status.json`。
- logs：只允许 safe reason code、runId、cycleId、traceId、duration、systemd result；禁止 DB URL/password、argv/env dump、raw
  provider material、tenant/account identifier、balance/asset/position。

任何目录不得 world writable 或 group writable；不得递归 chown/chmod 未经 realpath allowlist 的路径。durable run 与 offline
acceptance evidence 不在 stop 时删除；清理只能由独立 retention 操作在 hash 已记录、GateW freeze/归档需要已满足且用户授权后执行。

## 9. Environment and secret contract

1. `worker.env` 和 `failclose.env` 只允许 exact key allowlist、literal scalar RHS、UTF-8 no-BOM、LF、无 duplicate key；禁止
   `$VAR`、`${VAR}`、command substitution、backtick、NUL/newline 注入和路径变量引用。
2. DB password 与 credential master key 使用 `LoadCredentialEncrypted=`；worker 仅加载 `db-password` 与
   `credential-master-key`，fail-close 仅加载 `db-password`。
3. OKX API key/secret/passphrase 继续只存在于既有 encrypted DB credential path；unit
   config、filesystem、argv、log、evidence、terminal 均不得出现 plaintext 或 direct input。
4. root preflight 在任何 bootstrap/disengage 前验证：systemd credential 可解密、owner/mode、literal config、JDBC URL
   host/name、loopback DB connect、SQL `current_database()` 与 `inet_server_addr()`、fail-close helper artifact hash和 unit
   fragment hash。
5. preflight 将 non-secret config 与 encrypted credential blob 复制为 per-run root-only frozen recovery bundle；worker
   无权修改。运行中配置/credential generation 变化必须 operator stop 并创建新 run，不允许热替换或 resume。
6. secret 不进入 environment file、argv、journal、evidence 或 error message；helper 只把 credential file 内容注入目标 child
   process environment，并在 child exit 后清除引用，不输出值。

## 10. Systemd service contract

### 10.1 Worker template

`nq-gatew-soak@.service` 必须至少冻结：

```text
Type=exec
User=nqgatew
Group=nqgatew
Restart=no
KillMode=control-group
TimeoutStopSec=30s
RuntimeDirectory=nexus-quant/gatew-soak/%i
RuntimeDirectoryMode=0700
StateDirectory=nexus-quant/gatew-soak/%i/evidence
StateDirectoryMode=0750
LogsDirectory=nexus-quant/gatew-soak/%i
LogsDirectoryMode=0750
UMask=0077
PrivateTmp=true
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
CapabilityBoundingSet=
AmbientCapabilities=
OnFailure=nq-gatew-soak-failclose@%i.service
```

- unit 不设 `WantedBy`，不允许 boot auto-start；只能由 root-controlled preflight command 显式启动。
- `ExecStart` 为固定绝对路径和参数；不经 shell；fragment 与 helper artifact 必须匹配 implementation commit 中的
  SHA-256/Git blob identity。
- worker 可写范围仅为本 run runtime/evidence/logs；repo、unit、config、control、credential source 均 read-only 或
  inaccessible。
- 真实 read-only worker 只保留现有 typed config+balance allowlist，不新增 endpoint、write
  path、order/cancel/transfer/withdraw、LIVE authorization。
- `status` 同时读取 formal unit `LoadState/ActiveState/SubState/MainPID/ExecMainStatus`、runtime sentinel、root lifecycle
  与 heartbeat；inactive formal instance 是正常可诊断状态，不再要求 `not-found`。

### 10.2 Fail-close template

`nq-gatew-soak-failclose@.service` 必须为独立 hardened `Type=oneshot`：

- root-controlled、固定 helper、固定 per-run `failclose.env` 与 `db-password.cred`；不继承 worker environment。
- `Restart=no`；helper 内部按 frozen config 执行最多 3 次 bounded DB attempt，每次有连接/statement timeout，总时限由
  `TimeoutStartSec` 约束；禁止无限重试。
- `IPAddressDeny=any` + `IPAddressAllow=localhost`，只允许 loopback DB；不解析或访问 OKX host。
- read-only repo/helper/config，只有本 run `control/` 可写；不得写 worker evidence 来伪造 read-only cycle。
- 同一 run 并发调用以 root-only lock 串行；重复调用若已 `ENGAGED` 且 terminal 已存在，只返回
  `NO_CHANGE / ALREADY_FAIL_CLOSED`，不覆盖文件。
- fail-close 自身失败不得循环触发自身或 worker。

## 11. Fail-close recovery contract

automatic engage 唯一放在独立 fail-close service；supervisor 内部与 `ExecStopPost` 均不得直接 engage。

固定顺序：

1. 验证 runId、realpath、unit/helper/config/credential identity 与 root-only lock。
2. 读取 root-owned transition intent 和 systemd result；operator intent 不能改写为 failure intent。
3. stop/kill worker cgroup 到 `inactive + MainPID=0 + residual=0`；超时则继续尝试 engage，但终态只能 `BLOCKED`，不能写
   `*_STOPPED`。
4. 从独立 frozen config 重新执行 URL/name 与 SQL server-address DB locality 校验。
5. 在小事务中读取 `GLOBAL_TRADING`；若已 `ENGAGED` 则 idempotent success，若为 `DISENGAGED` 则按当前 version
   engage；其他/冲突状态 fail-closed。
6. 事务外重新读取并确认 `ENGAGED`，记录安全 reason、version、elapsed 与 recovery attempt；不记录 DB/credential/provider
   material。
7. 只有 kill switch `ENGAGED`、unit inactive、PID 0、residual 0、evidence final hash 可验证时，才根据不可变 intent 写
   `FAILURE_STOPPED` 或 `OPERATOR_STOPPED`。
8. 任一步无法确认时写 root-only terminal `BLOCKED`，其中 `killSwitchObservedState=UNKNOWN` 或实际读回状态；返回非 0
   并要求人工安全恢复。不得伪报 `ENGAGED`。

## 12. Lifecycle state machine

root-owned `lifecycle.json` 是状态 authority；worker heartbeat 只能报告 progress，不能执行状态转换。

### 12.1 Allowed transitions

| From                | To                  | 条件                                                                                  |
|---------------------|---------------------|---------------------------------------------------------------------------------------|
| none                | `PREPARING`         | root preflight 创建全新 run                                                           |
| `PREPARING`         | `STARTING`          | config/credential/helper/unit/DB/offline prerequisite 全部通过                        |
| `PREPARING`         | `BLOCKED`           | 在任何 disengage 或 worker start 前 hard gate 失败                                    |
| `PREPARING`         | `OPERATOR_STOPPING` | operator 取消已分配 run                                                               |
| `STARTING`          | `RUNNING`           | formal unit active、MainPID>0、首条有效 sample、heartbeat 与 fresh reconnect 全部通过 |
| `STARTING`          | `FAILURE_STOPPING`  | bootstrap 后任一启动/worker/systemd hard failure                                      |
| `STARTING`          | `OPERATOR_STOPPING` | root-owned operator intent                                                            |
| `RUNNING`           | `FAILURE_STOPPING`  | worker nonzero、hard criterion、unexplained gap、identity/evidence violation          |
| `RUNNING`           | `OPERATOR_STOPPING` | root-owned operator stop                                                              |
| `RUNNING`           | `COMPLETED`         | 满 168h 且 completion verifier、engage、inactive/PID/residual/evidence 全部通过       |
| `FAILURE_STOPPING`  | `FAILURE_STOPPED`   | automatic recovery 与 terminal hard gates 全通过                                      |
| `FAILURE_STOPPING`  | `BLOCKED`           | recovery、DB read-back、PID/residual 或 evidence integrity 任一未知/失败              |
| `OPERATOR_STOPPING` | `OPERATOR_STOPPED`  | operator recovery 与 terminal hard gates 全通过                                       |
| `OPERATOR_STOPPING` | `BLOCKED`           | recovery、DB read-back、PID/residual 或 evidence integrity任一未知/失败               |

`FAILURE_STOPPED`、`OPERATOR_STOPPED`、`COMPLETED`、`BLOCKED` 为 terminal states。terminal 无任何出边；要再次运行必须创建新
runId。

### 12.2 Illegal transitions

- operator command 直接写 `FAILURE_STOPPING` 或 `FAILURE_STOPPED`。
- worker/heartbeat 直接写任一 terminal state。
- `PREPARING/STARTING/RUNNING` 跳过对应 stopping state进入 `*_STOPPED`。
- terminal → 任意状态；`BLOCKED`/`FAILURE_STOPPED` resume；同一 runId restart。
- 未满 168h、未验证 acceptance clock 或 unit/PID/residual 未收口时写 `COMPLETED`。
- `killSwitchObservedState != ENGAGED` 时写 `FAILURE_STOPPED`、`OPERATOR_STOPPED` 或 `COMPLETED`。

## 13. Terminal status contract

`terminal-status.json` 位于 root-only `control/`，使用 temp + fsync + create-once/no-replace 原子提交；parent、temp、target
均拒绝 symlink/hardlink escape。terminal 已存在时只读验证并返回 no-change，禁止覆盖、rename replacement 或“修正”历史。

最小 schema：

```text
schemaVersion
runId
runMode
lifecycleState
reasonCode
observedAt
harnessCommit
unitName
serviceResult
execMainStatus
mainPid
residualProcessCount
killSwitchObservedState
killSwitchVersion
recoveryAttemptCount
acceptanceClockStarted
acceptanceStartedAt
acceptanceInvalidatedAt
acceptanceInvalidationReason
evidenceManifestSha256
evidenceFinalChainHash
networkCalled
credentialAccessed
```

- `mainPid` 必须 0、`residualProcessCount` 必须 0，才可形成三个 safe terminal states。
- `networkCalled` / `credentialAccessed` 延续 launcher schema，表示 provider network 与 exchange credential；DB recovery
  credential 另由 root audit 记录，不混淆为 exchange credential。
- `BLOCKED` 可记录 `killSwitchObservedState=UNKNOWN`；其他三个 terminal 必须为 `ENGAGED`。
- terminal 不包含 env、argv、DB URL/user/password、credential reference/value、tenant/account ID、raw response、余额或资产。

## 14. Acceptance clock contract

### 14.1 Start

acceptance clock 只能由 root-controlled controller 写入 create-once `acceptance-clock.json`，开始时间为以下两者较晚值：

1. 第一条合同有效、hash verified、`PASSED_READ_ONLY` 的真实 typed config+balance sample `observedAt`；
2. fresh SSH/session 对同一 formal unit、同一 `MainPID`、推进 heartbeat、正确 owner/fragment/config identity 的验证时间。

开始前还必须满足：implementation exact-head CI green、完整 offline acceptance PASS、preflight/DB locality/credential
permission/IP metadata/kill switch bootstrap hard gates、unit active、residual=0。manifest `startedAt`、systemd
`ActiveEnterTimestamp` 或单独首样本均不能代替 acceptance start。

### 14.2 Invalidation

以下任一发生，clock 立即作废并进入 stopping/terminal：

- forbidden endpoint/mutating call、credential exposure、账户/资金/订单/ledger side effect。
- hard failure、DB locality failure、kill-switch recovery unknown、evidence/hash/identity violation。
- worker restart、PID 变化、manual resume、unit inactive/crash、同一 runId复用。
- 超过冻结预算的 scheduler gap、cycle completion <99.5%、未解释连续 gap或 hard criterion failure。
- operator stop。

预算内 transient rate-limit/timeout/5xx 只按 GateW plan 统计与恢复，不因单次 transient 自动伪造成 hard failure；一旦达到
frozen failure threshold，必须进入 `FAILURE_STOPPING`。24h checkpoint 只做早期 review，不重置、不缩短 168h。

## 15. Complete offline acceptance contract

离线闭环必须在一个全新 `OFFLINE_ACCEPTANCE` run 中一次完成，且完整 evidence 保留，不得像现实现一样在 stop 后删除。真实 mode
必须拒绝 failure injection 配置。

执行顺序与成功判定：

1. 记录 implementation commit、exact-head CI、versioned unit/helper hash和所有历史 GateW run durable file pre-hash。
2. 安装两个 formal template，`daemon-reload`，证明 fragment 来自固定 commit；创建 root/evidence/runtime/logs 权限合同。
3. 只加载 local fixture DB 与 DB recovery credential；不加载 credential master key或 OKX credential；systemd egress强制
   loopback-only。
4. start worker，证明 unit `loaded/active/running`、`User/Group=nqgatew`、`MainPID>0`、hardening与目录属性精确。
5. cycle 1 使用 fake typed read-only fixture并形成 `PASS`；`networkCalled=false / credentialAccessed=false`。
6. cycle 2 同样形成 `PASS`；hash chain和 heartbeat sequence推进。
7. 关闭启动 SSH/session；fresh SSH/session 证明同一 unit、同一 MainPID 存活且 heartbeat继续推进。
8. root-only offline control 允许受控 cycle 3 failure；worker记录安全 failure后 nonzero exit，不能由 operator直接写
   failure terminal。
9. `OnFailure` 自动启动独立 fail-close；重新验证 loopback DB，幂等 engage并 read-back `ENGAGED`。
10. 形成 create-once `FAILURE_STOPPED`；worker service `loaded/inactive`、`MainPID=0`、residual=0，fail-close oneshot完成。
11. post-hash证明所有历史 evidence count/hash不变；新 smoke evidence、systemd result、recovery和terminal完整可验证。
12. 最终精确返回：

```text
serviceCreated=true
cycle1=PASS
cycle2=PASS
heartbeatAdvanced=true
reconnectSameMainPid=true
cycle3=CONTROLLED_FAILURE
automaticEngage=ENGAGED_OR_ALREADY_ENGAGED
terminalState=FAILURE_STOPPED
workerActiveState=inactive
mainPid=0
residualProcessCount=0
historicalEvidenceChanged=false
networkCalled=false
credentialAccessed=false
acceptanceClockStarted=false
```

任一字段不是精确期望值，implementation 为 `BLOCKED`。完整离线链未通过前不得访问 OKX、不得创建 Attempt-09/10、不得启动 real
acceptance clock。

## 16. 168-hour requirement decision

结论： **继续保留连续 168 小时 read-only soak，不修改 GateW plan，不缩短时长。**

理由：`GATEW_PLAN.md:165-179` 的风险目标包含 scheduler gap/overlap、rate-limit/timeout/auth 分类、freshness、resource
usage、kill-switch propagation 和恢复事件；这些风险不能由短 smoke 代替。Attempt-06～08 的失败均来自 tooling，而不是 168
小时风险目标不可实现。正式 systemd lifecycle、独立 recovery 与 durable terminal 正是使原要求可实现、可审计的修复。

24 小时 checkpoint 保留为 early review；计划 cycle 完成率仍至少 99.5%；任一 hard criterion 失败仍必须重置或明确延期。

## 17. Single implementation allowlist

下一轮只允许一个任务：`NQ-GATEW-SOAK-TOOLING-REDESIGN-IMPLEMENTATION`。它必须一次完成 runtime、fail-close、terminal 与完整
offline acceptance，不再拆 plan/review/freeze 文档链。

### 17.1 Allowed create

```text
deploy/systemd/nq-gatew-soak@.service
deploy/systemd/nq-gatew-soak-failclose@.service
scripts/gatew/gatew-okx-readonly-soak-failclose.ps1
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakFailCloseTest.java
docs/current/evidence/gate-w/NQ-GATEW-SOAK-TOOLING-REDESIGN-IMPLEMENTATION.attempt-01.md
```

### 17.2 Allowed modify

```text
scripts/gatew/gatew-okx-readonly-soak.ps1
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakSupportTest.java
docs/current/evidence/gate-w/README.md
docs/current/TESTING.md
docs/current/WORKLOG.md
```

### 17.3 Required implementation outcomes

- 删除 Linux transient start/status/stop 作为真实路径；Windows helper 只保留到相关 regression，不能成为 GateW 服务器
  authority。
- formal unit、root/evidence/runtime/logs 权限、separate fail-close config/credential、create-once terminal与状态机全部有
  self-test/static test。
- fail-close helper 只能复用 `KillSwitchService` / `JdbcKillSwitchStateRepository` 的 idempotent domain path；不得直接拼
  SQL、绕过 audit/transaction/version check或依赖 worker EnvironmentFile。
- PowerShell 5.1/7 regression、targeted/full Maven、unit parser/static contract、真实 Linux systemd offline chain、fresh SSH
  reconnect、historical hash与 secret/scope scan全部通过。
- implementation 可在明确授权的隔离 GateW server上安装上述 versioned unit并执行 loopback-only offline acceptance；不得调用
  OKX、读取 exchange credential、启动 real soak或 acceptance clock。

### 17.4 Explicitly excluded

```text
STATUS.md
ROADMAP.md
GATEW_PLAN.md
frontend/**
research/**
.github/**
migration/**
Docker/Compose files
API/Controller/scheduler/endpoint allowlist
exchange credential create/rotate/decrypt/permission probe
OKX call
Attempt-09/10
LIVE/order/cancel/transfer/withdraw
```

如果实现发现必须修改 allowlist 外文件、增加 migration/API/production runtime 或降低任何安全合同，必须停止并输出
`BLOCKED / IMPLEMENTATION_SCOPE_EXPANSION_REQUIRED`，不能自行扩大范围。

## 18. Validation for this decision

- Starting repository/branch/status/HEAD/origin/CI：已按任务前置命令核对并通过。
- `scripts/docs/check-current-authority.ps1`：`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- 真实代码与 Attempt-01～08、两个 blocker remediation 链、HEAD 后续修复均已审查。
- 本任务不运行 Maven/前端/Python测试：仅文档 decision，无代码变更；代码行为依据源码与已有 exact-head CI/evidence，未伪报新
  runtime validation。
- commit/push 与 decision commit exact-head CI：在本文件创建时为 `PENDING`，由任务收尾输出真实结果。

## 19. Risk, rollback and boundary

- 风险：implementation 尚未发生，P1/P2 结构缺陷仍存在；任何 real soak start 均继续被禁止。
- 决策回滚：反向移除本 decision、GateW evidence index 行与 WORKLOG append；不回滚/删除历史 Attempt evidence，不使用
  `git reset --hard`。
- implementation 回滚合同：停止 formal worker/fail-close instance，确认 kill switch `ENGAGED`、unit inactive、PID/residual
  0；恢复上一版本 unit/helper并 `daemon-reload`；保留失败 run evidence/terminal，禁止复用 runId。
- 未触达：backend/frontend/research/scripts/deploy/.github/migration 代码，server，credential，OKX，LIVE，交易写侧，AI，DH，authority。

## 20. Final decision

```text
PASS /
SOAK_TOOLING_REDESIGN_DECIDED /
FORMAL_SYSTEMD_TEMPLATE_SELECTED /
INDEPENDENT_FAILCLOSE_SELECTED /
168_HOURS_RETAINED /
READY_FOR_SINGLE_IMPLEMENTATION
```

Next action：`NQ-GATEW-SOAK-TOOLING-REDESIGN-IMPLEMENTATION`。
