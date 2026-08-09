# NQ-GATEW-ATTEMPT-13-PREPARATION-AND-START — Attempt 01

## 1. 结论

`PASS / ATTEMPT_13_CREATED / STARTUP_COMPLETE / FRESH_SSH_VERIFIED / HASH_CHAIN_VALID / ACCEPTANCE_CLOCK_STARTED / SOAK_RUNNING / PENDING_168H`（通过 / Attempt-13 已创建 / 启动完成 / fresh-SSH 已验证 / hash-chain 有效 / acceptance clock 已启动 / soak 运行中 / 待满 168 小时）。

本轮启动部署已结束。168 小时期间由正式 worker/systemd 自动采证，不要求 Codex 或人工持续在线；期满验收由独立 `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE` 任务执行。本结论不表示 168h acceptance 已通过、GateW 已冻结、LIVE ready 或 trading authorized。

## 2. 本地与治理基线

- 分支：`dev`；起始 `HEAD == origin/dev == b103069d8bfcecccba0b4d590317ddccc66898b9`；tracked/staged clean。
- 起始 exact-head CI：`NQ CI Baseline` run `30710943874 / completed / success / 10 jobs / bad=0`。
- Authority before：work batch=`GateW-ATTEMPT-13-PREPARATION-AND-START / ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED`；Attempt-13=`NOT_CREATED / AUTHORIZED`；production deployment=`NOT_STARTED`。
- Runtime contract 只接受有序事件：`PRODUCTION_PREFLIGHT_PASSED → PERMISSION_VERIFIED → IMMUTABLE_RELEASE_DEPLOYED → ATTEMPT_13_CREATED → WORKER_STARTED → FIRST_VALID_HEARTBEAT_CONFIRMED → ACCEPTANCE_CLOCK_STARTED`。

## 3. Immutable release 与生产 hard gates

- Release/source：`b103069d8bfcecccba0b4d590317ddccc66898b9`。
- Manifest SHA-256：`f5b891e0d5547f25077a165a636ca6b40600bc8deedfe78f1110f7bddb44e4cb`。
- Bundle SHA-256：`e4e0264e78d0cc35598af7dddd4f41c59da44cba452abdce6814ab44cd3e79d9`；bytes=`61,236,224`。
- Artifact/JAR/USTAR=`131/122/132`；JAR=`122/122`，37,551 entries，133,989,252 bytes；descriptor diff=`0`。
- 服务器 `/opt/nexus-quant/current` 解析到 `/opt/nexus-quant/releases/b103069d8bfcecccba0b4d590317ddccc66898b9`；root ownership、POSIX mode、worker write denial 与 immutable verifier 全部通过。
- Descriptor v1 备份只记录路径 `/tmp/nq-gatew-attempt13-precreate-v1-backup.json` 与 SHA-256 `2cf895fa6c5de38ff45f62ef681fd5a4af3d0d86e273362021d3e7e4d028ca9a`，未读取或输出凭证内容。
- Descriptor v2、23-field sanitized precreate readback、PostgreSQL/management、persisted read permission、trade/withdraw disabled、IP allowlist 与 kill switch hard gates 均通过；`readyForAttemptCreation=true`，credential material exposure=`false`。

## 4. Attempt-13 与唯一启动

- 唯一 RunId：`gatew-soak-20260801T180544Z-140bbcd1`；此前因 CRLF 被拒绝且未创建 state 的 `gatew-soak-20260801T180324Z-20cfdbd1` 不复用。
- Canonical `start` 只调用一次；未因 SSH 状态不确定重放。
- 正式 unit：`nq-gatew-soak@gatew-soak-20260801T180544Z-140bbcd1.service`；`active/running`。
- MainPID/initial MainPID=`478613/478613`；`NRestarts=0`；`ExecMainStartTimestampMonotonic=6755802950269`；residual process count=`1`（唯一正式 worker）。
- Lifecycle=`RUNNING / FORMAL_WORKER_RUNNING`；无 terminal status、exit fact 或 completion marker。

## 5. 首样本、fresh-SSH 与 evidence

- 首条有效 heartbeat：`2026-08-01T18:13:13.9139125Z`；sequence=`1`；state/reason=`RUNNING / READ_ONLY_SAMPLE_ACCEPTED`。
- 首样本只执行冻结的 account config/balance typed read-only allowlist；failures=`0`。
- Fresh-SSH：`PASS / FRESH_SSH_RECORDED`；同一 PID=`478613`；heartbeat advanced=`true`；`freshSshVerificationAt=2026-08-01T18:24:06.8814000Z`。
- Canonical `verify-evidence`：`PASS / FORMAL_EVIDENCE_VERIFIED`；immutable release=`PASS`；hash-chain=`PASS / HASH_CHAIN_VERIFIED`；sampleCount=`1`。
- Last hash：`cdb43355791a3977bac2e12bf7b70a1540074121bf4aa984482f32472f405bda`；evidence manifest SHA-256=`3ec42822fc2ff5b015f999b0ceb62b152c5179c9431cd17c584d59e3d2eaf003`。
- forbidden/fallback/raw response/secret exposure=`0/0/0/0`；未保留或输出 API key、secret、passphrase、signature、credential payload、原始 config/balance 或账户余额。

## 6. Acceptance clock 与任务边界

- `acceptanceClockStarted=true`。
- `acceptanceStartAt=2026-08-01T18:13:13.9139125Z`，精确锚定首条有效 heartbeat。
- `plannedAcceptanceAt=2026-08-08T18:13:13.9139125Z`，精确 `+168h`。
- Clock binding checksum：`a4dab5f2d5d3be26762e4b876c6695d16427a1311071277488308446f6285200`。
- 当前 heartbeat reason=`ACCEPTANCE_CLOCK_STARTED`；`acceptanceVerified=false`，completion marker 不存在，符合期满前状态。
- 本任务不执行 `verify-acceptance`、`finalize-acceptance` 或连续 168 小时人工/Codex 在线观察。

## 7. 透明保留的客户端错误与根因

- 首次只读 status 使用错误 unit 前缀，返回的 inactive 不是正式 worker；正确 unit 读回始终 active/running。
- 元数据只读脚本末尾发生一次 Bash parser error；所需白名单字段已在错误前完整读回，无生产写入。
- 首次 fresh-SSH 使用错误 release 子路径，`pwsh` 在加载脚本前退出；改用 immutable release 的 `bin/` canonical path 后通过。
- 两次 clock wrapper 分别在 SSH 60 秒客户端超时和 CRLF RunId 校验处中止；服务器无残留 control/verifier，clock create-once 语义未冲突。使用无 CR 直接参数、keepalive 与 5 分钟客户端等待后，canonical action 幂等读回 `ACCEPTANCE_CLOCK_ALREADY_STARTED`。
- 这些问题只影响客户端等待/读回，不是 worker、OKX、release 或部署流程缺陷；未修改代码、未手工改 state、未重放 canonical start。

## 8. Authority after 与边界

- Work batch：`GateW-OKX-READONLY-SOAK-ATTEMPT-13 / RUNNING|PENDING_168H`。
- Attempt-13：`RUNNING / SOAK_IN_PROGRESS`；production deployment=`STARTED`。
- 唯一下一动作：`NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE`；只能在计划时间到达后执行。
- LIVE=`DISABLED`；kill switch=`ENGAGED`；order/cancel/transfer/withdraw=`0`；RunId reuse=`FORBIDDEN`；auto retry=`DISABLED`。
- Attempt-10/11/12 保持终态不可变；未进入 GateW freeze/archive/tag，未修改 DH/Integration runtime。

## 9. Findings 与交付状态

- P0：0。
- P1：0。
- P2：SSH 长动作需要客户端等待大于 verifier 实际耗时；本轮以 keepalive 和 5 分钟有界等待完成，不需要代码修复。
- P3：0。

Final decision：`PASS / STARTUP_DEPLOYMENT_COMPLETE / SOAK_RUNNING / PENDING_168H / LIVE_DISABLED / KILL_SWITCH_ENGAGED`。

本 authority/evidence sync 的最终 commit/push/exact-head CI 在本文写入时为 `NOT_RUN`；取得 CI GREEN 后，本启动任务关闭，不继续在线观察。
