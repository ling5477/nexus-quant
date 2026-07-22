# NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START — Attempt 03

## 1. 结论

`PASS / ATTEMPT_09_CREATED / OKX_CONFIG_PASS / OKX_BALANCE_PASS / SYSTEMD_DETACHMENT_VERIFIED / HEARTBEAT_ADVANCING / HASH_CHAIN_VALID / ACCEPTANCE_CLOCK_STARTED / SOAK_RUNNING / PENDING_168H`（通过 / Attempt-09 已创建 / OKX config 与 balance 只读采样通过 / systemd 脱离 SSH 已验证 / heartbeat 持续推进 / hash chain 有效 / 验收时钟已启动 / soak 运行中 / 待满 168 小时）。

GateW 继续为 `IN_PROGRESS / NOT FROZEN`（进行中 / 未冻结）。本证据不表示 GateW accepted/frozen、LIVE ready 或 trading authorized。

## 2. 本地与治理基线

- 分支：`dev`；起始 `HEAD == origin/dev == 771b878d1e76f432853d1412dfb6febf8388eb40`；tracked/staged clean。
- 起始 exact-head CI：`NQ CI Baseline` run `29840306100 / completed / success / 10 of 10`，`headSha=771b878d1e76f432853d1412dfb6febf8388eb40`。
- 起始 authority：GateW=`IN_PROGRESS|NOT_FROZEN`；Attempt-09=`NOT_STARTED`；唯一动作=`NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-START`。
- 治理合同最小增加 `RUNNING|PENDING_168H -> SOAK_ACCEPTANCE` 与精确、大小写敏感的 `NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE`；错拼、缺少 `168H-ACCEPTANCE`、Attempt-10 与小写近似均拒绝。
- `test-current-authority-next-action.ps1` 与 `test-governance-workflow-lifecycle.ps1` 均 PASS。

## 3. 服务器 hard gate

- 固定主机与 `admin`/指定公钥；所有连接使用 `-F NUL`、`BatchMode=yes`、`IdentitiesOnly=yes`、`PreferredAuthentications=publickey`、`ConnectTimeout=15`。
- `/opt/nexus-quant/current` 精确解析到 `/opt/nexus-quant/releases/1b501488076fae79e15b84579a02f5c580fa51b3`；未构建、上传或切换 release。
- immutable verifier：`PASS / IMMUTABLE_RELEASE_VERIFIED`；`EXACT_COMMIT`；manifest SHA-256=`8cf4ca653cc2eec4564385c59bcc0f90252ce1001798c6c2adcc11f96b7601b6`；129 artifacts；POSIX verified。
- release/manifest owner-mode=`root:root/0755` 与 `root:root/0644`；`nqgatewWritableCount=0`；symlink/artifact/hash 合同由 verifier 通过。
- `systemd-analyze verify` exit 0；启动前 active units/MainPID/drop-ins/residual=`0/0/0/0`；REAL runs/clocks=`0/0`。
- management `127.0.0.1:18889` HTTP 200；PostgreSQL `127.0.0.1:55432` accepting；公网非 SSH listeners=0。

## 4. Pre-create prerequisite

正式独立 action 返回：

- `postgresReachable=true`
- `managementHealthy=true`
- `killSwitchEngaged=true`
- `credentialConfigured=true`
- `activeCredentialCount=1`
- `credentialType=OKX_API_V5`
- `credentialLocalStatus=ACTIVE`
- `tradePermissionExpectedDisabled=true`
- `withdrawPermissionExpectedDisabled=true`
- `readyForAttemptCreation=true`
- `diagnosticOnly=true`
- `noSideEffect=true`
- `credentialMaterialExposed=false`

执行后 REAL runs/clocks/active units 仍为 `0/0/0`。未手工展开 DB URL/user、未手工 SQL、未读取 credential 文件或输出 credential material。

## 5. Attempt-09 与首次真实只读采样

- RunId：`gatew-soak-20260722T111144Z-ac00f878`；唯一 REAL run；未创建 Attempt-10。
- `prepare`：`PASS / FORMAL_SOAK_PREPARED`；mode=`REAL_READONLY_SOAK`；release/manifest/CI 绑定 Commit A 与 run `29837563573`；clock=`false/null/null`。
- Unit：`nq-gatew-soak@gatew-soak-20260722T111144Z-ac00f878.service`；`active/running`；worker=`nqgatew`；初始 MainPID=`4074358`。
- 首个有效 config/balance 时间均为 `2026-07-22T11:15:45.1582519Z`。
- 首样本：`PASSED_READ_ONLY / SUCCESS_2XX / READ_ONLY_WITH_IP_ALLOWLIST / ENGAGED`；credential/network 已由 systemd 正式路径访问；`allowedEndpointCategory=ACCOUNT_CONFIG_AND_BALANCE_READ`；config/balance=`SUCCEEDED/SUCCEEDED`；`realCycleOutcomeProven=true`。
- 允许 endpoint 精确为 2 个 typed GET：`/api/v5/account/config` 与 `/api/v5/account/balance`；禁止 endpoint=0；raw response=0；secret exposure=0。
- 未保留或输出 API key、secret、passphrase、signature、credential payload、raw config/balance、账户标识、资产名称或余额数值。

## 6. Fresh SSH、heartbeat 与 hash chain

- 首次 start SSH 在等待 worker 时由远端关闭；没有重发 start。新 SSH 通过正式 status 发现原 unit 已 `RUNNING`。
- 持久化 `worker-start` MainPID=`4074358`；fresh SSH systemd MainPID=`4074358`；same MainPID=`true`。
- 正式 `record-fresh-ssh`：`PASS / FRESH_SSH_RECORDED`；`freshSshVerificationAt=2026-07-22T11:19:59.5201964Z`；`heartbeatAdvanced=true`。
- status 后续从 heartbeat sequence 1 推进到 2；再次 verifier 时 sampleCount=3，3 个均为 valid REAL PASS，fallback=0。
- clock 启动后首轮复核：`PASS / HASH_CHAIN_VERIFIED`；sampleCount=3，last hash=`ca419e7a3082855948100bc6e463abe76d23cf9ee3dcc56d28d55a3e39bde8c7`；raw/secret/forbidden=`0/0/0`；drop-in=0。
- 提交前健康快照：heartbeat sequence=8；sampleCount=9、valid REAL PASS=9、fallback/raw/secret/forbidden=`0/0/0/0`；last hash=`f1fd7f9fbefb15f6b7d9d607ef352cf49879baa294b61e38a385fc9aaaa2f66d`；unit/MainPID/clock 均未改变。

## 7. 168 小时 acceptance clock

- `acceptanceStartAt=2026-07-22T11:19:59.5201964Z`，精确等于 config、balance、fresh SSH 三者最大值。
- `plannedAcceptanceAt=2026-07-29T11:19:59.5201964Z`，精确 `+168h`。
- 首次调用：`PASS / ACCEPTANCE_CLOCK_STARTED`；clock owner-mode=`root:nqgatew/0640`。
- 相同确认二次调用：`NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED`，时间完全不变。
- immutable control 隔离 self-test 50/50 PASS；其中不同 clock 值二次写入按 `BLOCKED / ACCEPTANCE_CLOCK_CONFLICT` 拒绝；self-test `noNetworkCalled=true / credentialAccessed=false`，真实健康 unit 未停止。

## 8. 执行中透明保留的非业务错误

- 初次 management probe 误用未经事实支持的 8080，连接拒绝；仓库合同与 listener 元数据确认 canonical 18889 后 HTTP 200。该错误发生在任何 run 创建前。
- pre-create 本体 PASS 后，SSH 包装器尾部因 CRLF 把整数解析为 `0\r` 而 exit 2；本体闭合 JSON及 run/clock/unit 零副作用已独立复核。
- 第一次 `prepare` 包装器传入字面量 `__RUN_ID__`，control 返回 `BLOCKED / RUN_ID_INVALID`，REAL run count 仍为 0；随后只使用已生成但未创建的合法 runId 完成唯一 prepare。
- 直接 worker evidence-verify 因未注入 formal evidence-root/release 环境返回脱敏 internal error；使用 control 定义的正式 systemd 环境后独立 verifier PASS，证明不是链损坏。

## 9. 边界与下一动作

- LIVE=`DISABLED`；real order submission、cancel、transfer、withdraw=`DISABLED`；kill switch=`ENGAGED`。
- 未修改 backend/frontend/research/scripts/gatew/deploy/.github/migration、immutable release、systemd unit、server current、credential 或 OKX allowlist。
- 未执行 controlled failure、final offline acceptance、GateW freeze/archive/tag、release cleanup 或新业务部署。
- 服务器继续运行 Commit A；本次 governance/evidence commit 不部署到服务器。
- 唯一下一动作：`NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE`；只能在计划时间到达后验证连续 168 小时，不得提前宣称 accepted/frozen。
