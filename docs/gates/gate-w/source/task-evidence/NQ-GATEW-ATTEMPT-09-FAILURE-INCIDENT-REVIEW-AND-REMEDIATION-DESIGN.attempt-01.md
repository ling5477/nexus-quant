# NQ-GATEW-ATTEMPT-09-FAILURE-INCIDENT-REVIEW-AND-REMEDIATION-DESIGN — Attempt 01

## 1. 结论

`PASS / ATTEMPT_09_FAILURE_INCIDENT_REVIEW_COMPLETED / ROOT_CAUSE_CLASSIFIED / REMEDIATION_REQUIREMENTS_FROZEN / AUTHORITY_SYNCED / ATTEMPT_10_NOT_AUTHORIZED`（通过 / Attempt-09 失败事件复盘完成 / 根因已分类 / 整改要求已冻结 / authority 已同步 / Attempt-10 未获授权）。

Attempt-09 的最终验收为 `REJECTED`（已拒绝），soak 为 `FAILED_INSUFFICIENT_DURATION`（因有效时长不足而失败）。GateW 继续为 `IN_PROGRESS / NOT FROZEN`（进行中 / 未冻结）；本结论不表示 GateW accepted/frozen，也不授权创建 Attempt-10。

## 2. 范围、基线与固定事实

- 任务类型：NQ-only `PRODUCTION_INCIDENT_REVIEW`；服务器操作仅限既有只读取证。本证据固化阶段没有重跑远端取证、finalizer 或任何 service action。
- 起始仓库：`dev`；`HEAD == origin/dev == 557980eaf5e6302d9a46d718b124f0f530aa74f1`。
- 起始 exact-head CI：`NQ CI Baseline` run `30009870551 / completed / success / 10 of 10`，`headSha=557980eaf5e6302d9a46d718b124f0f530aa74f1`。
- RunId：`gatew-soak-20260722T111144Z-ac00f878`；runtime release：`1b501488076fae79e15b84579a02f5c580fa51b3`。
- Acceptance clock：`2026-07-22T11:19:59.5201964Z` 至 `2026-07-29T11:19:59.5201964Z`。
- Worker exit：`2026-07-27T22:25:46.8916254Z / killed / TERM`；completion marker=`false`。
- Last valid sample：`2026-07-27T22:23:14.5722391Z`。
- Observed/required/shortfall：`471795.0520427 / 604800 / 133004.9479573` seconds。
- Formal verifier：`PASS / FORMAL_SOAK_VERIFIED`；final acceptance：`REJECTED`。前者只证明其实际覆盖的 evidence-integrity 条件，不能覆盖后者。

## 3. Worker `SIGTERM` 只读取证与分类

正式 unit 为 `nq-gatew-soak@gatew-soak-20260722T111144Z-ac00f878.service`。

| 检查项 | 安全证据 | 判断 |
| --- | --- | --- |
| 静态生命周期合同 | `RuntimeMaxUSec=infinity`；`Restart=no`；无 drop-in | 不是 runtime limit，也不是 `Restart=` 自动重启 |
| 第一次 stop transaction | systemd 于 `2026-07-27T22:25:39Z` 开始停止正式 worker，随后向进程发送 `TERM`；正式退出事实为 `2026-07-27T22:25:46.8916254Z / killed / TERM` | 直接终止机制是 systemd stop transaction |
| 事件窗口内第二次进程 | 第一次停止后出现一次独立 start，进程 PID=`301042`；`2026-07-27T22:27:58Z` 又出现第二次 stop transaction | 不是同一 MainPID 连续运行；不是 systemd `Restart=` 策略触发 |
| dependency / timer | reverse dependency 与相关 timer 均未发现可解释该 stop 的触发源 | `DEPENDENCY_STOP` 不成立；已检查范围内无 timer 归因 |
| OOM / resource pressure | kernel OOM、memory pressure 与 `systemd-oomd` 在事件窗口均无命中 | `RESOURCE_PRESSURE` 不成立 |
| server reboot / shutdown | `last -x` 与 boot ledger 未显示事件窗口内重启或关机 | `SERVER_SHUTDOWN_OR_REBOOT` 不成立 |
| worker self termination | systemd journal 明确记录 stop transaction 与 `TERM` | `WORKER_SELF_TERMINATION` 不成立 |

Worker 终止分类为：

```text
OPERATOR_OR_AUTOMATION_STOP
```

能够直接证明的是“systemd 收到了两次 stop transaction”。现有允许范围内没有审计记录能把请求精确归因到某个 operator、脚本、调度器或上游控制面，因此精确发起者必须保持：

```text
UNKNOWN
```

SSH 连接阻断不属于服务器重启、worker continuity failure 的原因分类；本结论只依据事件窗口内的正式服务器证据。

## 4. Finalizer timeout 根因

正式 finalizer unit 为 `nq-gatew-soak-failclose@gatew-soak-20260722T111144Z-ac00f878.service`。

| 检查项 | 证据 | 判断 |
| --- | --- | --- |
| unit contract | `Type=oneshot`；`TimeoutStartSec=2min`；`Restart=no` | finalizer 只有 120 秒启动预算 |
| 执行窗口 | ExecMain 从 `2026-07-27T22:26:46Z` 运行至 `2026-07-27T22:28:47Z`，约 120 秒后由 systemd 发送 `TERM` | 与 `TimeoutStartSec=2min` 精确吻合 |
| 终态文件 | `terminal-status.json=false`；completion marker=`false`；lifecycle 仍为 `RUNNING` | finalizer 未完成 create-once terminal commit |
| path / permission / lock / CAS | 允许范围内未发现相应直接失败证据 | 不归类为 path、permission、lock 或 CAS failure |
| 技术可重入性 | terminal 缺失时实现允许再次进入 finalize | 仅说明实现可重入；不构成本 Attempt 的治理授权 |

Finalizer 失败分类为：

```text
FINALIZER_SYSTEMD_TIMEOUT
```

当前证据只能证明 systemd timeout 是直接终止原因；不能在无额外执行阶段 trace 的情况下猜测 120 秒内具体哪一个内部步骤占满预算。本轮按硬边界没有重跑 finalizer；Attempt-09 不可恢复、不可续时、不可重新 finalization。

## 5. Formal verifier 合同覆盖矩阵

Runtime Commit A 的 `control -Action verify` 在 `Verify-FormalRun` 中显式调用 `Assert-FormalWorkerState ... -AllowInactive`；REAL soak 的 terminal 文件为可选分支。worker `evidence-verify` 会验证配置的 `durationHours >= 168`，但不会计算已观测有效时长。

| Hard gate | `control -Action verify` 是否检查 | 现有 REAL acceptance 是否强制 | 缺失风险 |
| --- | --- | --- | --- |
| immutable release binding | 是 | 是，经 formal verify 间接覆盖 | 低；本事件未发现 release drift |
| sample sequence / `previousRecordHash` / `recordHash` | 是 | 是，经 evidence verifier 间接覆盖 | 能证明链完整，不能证明时间连续 |
| valid REAL sample shape、raw/secret/forbidden counters | 是 | 是，经 evidence verifier 间接覆盖 | 能证明已写样本的安全形状，不能证明 168 小时完整窗口 |
| unit 必须 `active/running` | 否；显式 `-AllowInactive` | 否 | unit 已停止仍可返回无条件 `PASS` |
| Initial MainPID continuity | 否 | 否 | PID 已变化或发生第二次 start 仍可能通过 |
| `NRestarts` / start transaction continuity | 否 | 否 | stop/start 或 restart 不能被 acceptance fail-close 捕获 |
| `acceptanceStartAt` / `plannedAcceptanceAt` | 仅作为可选结构投影输出 | 否；未形成 REAL hard gate | clock 存在不等于窗口已完成 |
| observed valid duration `>= 604800s` | 否；只检查 manifest 配置时长 | 否 | 本次仅 `471795.0520427s` 仍返回 `FORMAL_SOAK_VERIFIED` |
| `lastSampleAt >= plannedAcceptanceAt` | 否 | 否 | 最后样本提前约 36.95 小时仍可通过 |
| terminal result / lifecycle terminal state | REAL 模式下否；terminal 可缺失 | 否 | finalizer timeout、terminal 缺失不阻止无条件 `PASS` |
| finalizer success | 否 | 否 | automatic finalization 失败与 evidence-integrity PASS 被错误合并 |

因此，`PASS / FORMAL_SOAK_VERIFIED` 的真实语义是“当前实现所检查的 formal evidence integrity 与 release binding 通过”，不是“168 小时 soak acceptance 通过”。该命名会让 inactive、MainPID=0、时长不足、planned time 未到、terminal 缺失的 run 获得看似完整验收的 PASS，定级为 P1 验收阻断缺陷。

## 6. Findings

### P0

- 无。

### P1

- `OPERATOR_OR_AUTOMATION_STOP` 在 168 小时前终止 worker，并出现第二次独立 start/stop；continuity 已不可恢复，Attempt-09 必须拒绝。
- finalizer 的 `TimeoutStartSec=2min` 终止正式 finalize，未生成 terminal result。
- formal verifier 把 evidence integrity 命名为 `FORMAL_SOAK_VERIFIED`，且缺少 active state、PID continuity、restart/start、observed duration、planned sample 与 terminal/finalizer hard gates，可能产生验收假阳性。

### P2

- stop transaction 的精确 operator/automation 发起者缺少可用审计归因，当前只能保持 `UNKNOWN`。
- finalizer 缺少可安全定位 120 秒预算消耗点的阶段级耗时证据。

### P3

- 无。

## 7. 冻结的整改要求

1. 将 evidence integrity 与 soak acceptance 拆成不同结果；evidence-only 成功不得继续使用无条件 `FORMAL_SOAK_VERIFIED`。
2. full acceptance 必须在同一 fail-closed contract 内同时验证 continuity、observed duration、clock、security counters、immutable release、terminal 与 finalizer success。
3. unit inactive、MainPID 与 initial PID 不一致、存在额外 start/restart、`NRestarts` 非预期、observed duration `< 604800s`、`lastSampleAt < plannedAcceptanceAt`、terminal 缺失或 finalizer timeout 均必须拒绝 acceptance。
4. finalizer timeout 必须与最坏情况下的 bounded verifier 工作量一致，或拆分为可观测、可恢复且仍保持 create-once/CAS 语义的阶段；不得用无限 timeout 掩盖无界工作。
5. 增加 Attempt-09 固定失败事实 fixture，以及 inactive、PID changed、short-duration、missing-terminal、finalizer-timeout、planned time 未到的负例。
6. 保留 hash chain、release binding、raw/secret/forbidden 与 typed read-only endpoint 的既有严格检查，不得为通过新 fixture 放宽安全边界。
7. 增加 stop/start transaction 的审计归因要求；无法证明发起者时继续输出 `UNKNOWN`，不得猜测。
8. acceptance hard gate 必须由 immutable tooling 自身判定，禁止依赖 operator 在工具外手工拼装。

## 8. Authority 同步

```text
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-09
work_batch_status=FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED
work_batch_commit=1b501488076fae79e15b84579a02f5c580fa51b3
work_batch_ci_run=29837563573
next_action=NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION

Attempt-09=REJECTED
soak=FAILED_INSUFFICIENT_DURATION
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
GateW=IN_PROGRESS|NOT_FROZEN
```

治理合同只为上述 status/work batch/action 增加 contract-driven、大小写敏感的精确三元组。错误 status、错误 Attempt、错误 action、近似拼写、大小写错误与 Attempt-10 均拒绝；未命中该 status 的其他生命周期继续使用原有规则。

## 9. 验证透明度与边界

- 首轮治理编辑曾被 IDE 的陈旧全文件格式化覆盖：新增 helper/负例丢失，`test-current-authority-next-action.ps1` 的表面 PASS 不计入验收；同轮 lifecycle 回归报 `ScriptBlock.ContainsKey`。恢复原格式并只插入最小 diff 后，两项回归均通过。
- 新增负例第一次执行时发现近似 action 会被既有通用 `IMPLEMENTATION` 分类器识别；精确失败三元组仍正确拒绝。测试随后改为断言“不得分类为精确失败 action type 且三元组必须拒绝”，避免改变其他 Gate 的既有 classifier 语义；重跑通过。
- 本轮未重跑远端取证、finalizer、worker、prepare、acceptance clock 或 OKX；未修改服务器、release、systemd、credential、allowlist、evidence/hash chain。
- Attempt-10 未创建且未授权；未进入 freeze/archive/tag；LIVE、下单、撤单、转账、提现、AI 与 DH runtime 均未触达。
- 本证据文件内不能自引用尚未产生的 evidence commit 或其 CI run；commit/push 与 exact-head CI 由本任务外层 Git closeout 记录。

唯一下一动作：

```text
NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION
```
