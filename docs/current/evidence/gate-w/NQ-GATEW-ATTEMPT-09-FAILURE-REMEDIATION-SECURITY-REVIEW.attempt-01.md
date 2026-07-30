# NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW — Attempt 01

## 1. 当前结论

`CONDITIONAL_PASS / P1_MINIMAL_FIX_LOCALLY_VALIDATED / COMMIT_A_CI_PENDING / EXACT_COMMIT_BUNDLE_PENDING / PENDING_LINUX_ROOT_INSTALL_VERIFICATION / ATTEMPT_10_NOT_AUTHORIZED`（有条件通过 / P1 最小修复已完成本地验证 / Commit A CI 待执行 / 精确提交不可变包待验证 / Linux root 安装验证待执行 / Attempt-10 未获授权）。

本轮独立复核 Attempt-09 失败整改实现后确认两个 P1，并在原整改文件边界内完成最小修复：

1. `COMPLETION_MARKER_WRITABLE_BY_WORKER`：worker 原先可在自身 evidence 目录写入被 acceptance 信任的 completion marker。
2. `STOP_INTENT_RECOVERY_AND_REASON_ALLOWLIST`：stop intent 原先接受任意安全格式 reason code，且同输入 stale intent 在无 exit fact 的崩溃恢复窗口中会永久阻断 canonical 重试。

修复后本地 P0=`0`、开放 P1=`0`。本记录写入时尚未创建 Commit A，未取得 Commit A exact-head CI，也未从 clean Commit A 构建新的 `EXACT_COMMIT` canonical bundle，因此不得提前写成最终 `SECURITY_REVIEW_ACCEPTED`。

## 2. 基线与 authority

- 原任务指定 Starting HEAD：`7f14679b56ef0161ce9754de1dcd934a20de98a9`。
- Remediation implementation commit：`92adff7e55c2200692e892db2189132c243a1ac5`。
- Implementation exact-head CI：run `30474856153 / completed / success / 10 of 10`。
- 本轮 fact-source reconciliation 后实际起始 HEAD：`5bd1649d6e1652b54dd15a67c9efa9bd50c24f13`。
- Fact-source reconciliation exact-head CI：run `30513313694 / completed / success / 10 of 10`。
- 分支：`dev`；开始安全修复前 `HEAD == origin/dev`。

Authority 在 Commit A 前保持：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-09-FAILURE-REMEDIATION
work_batch_status=IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVIEW
next_action=NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW
Attempt-09=REJECTED
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
```

## 3. 审查范围

已审查：

- `017639101ac95d462f84e76fc50e26bc1939467b..92adff7e55c2200692e892db2189132c243a1ac5` 的 GateW remediation production/test/systemd/docs diff；
- `92adff7e55c2200692e892db2189132c243a1ac5..7f14679b56ef0161ce9754de1dcd934a20de98a9` 的 current evidence/authority diff；
- verifier 拆分、Attempt-09 负例、acceptance hard gates、terminal schema/CAS、显式 finalizer 崩溃窗口、automatic fail-close、stop intent、systemd unit、release builder/verifier/installer 与 governance 映射；
- fact-source reconciliation commit `5bd1649d6e1652b54dd15a67c9efa9bd50c24f13` 后的 canonical next action 一致性。

明确未执行：

- SSH、服务器连接、远端取证、server install/activate、systemd state 修改；
- 真实 OKX、credential material、permission probe、网络调用；
- 新 168 小时 soak、Attempt-10、freeze/archive/tag、LIVE 或交易写侧。

## 4. Finding 1：completion marker 信任边界

### 修复前

worker 在 worker-owned evidence 目录写入 `completion-marker.json`，而 root control 将该文件作为 acceptance hard gate。checksum 只能证明内容未损坏，不能证明写入主体可信，因此 worker 身份可伪造受信 completion 条件。

### 最小修复

- worker 不再创建 completion marker，只确认自然完成边界并保持 `ACCEPTANCE_READY`；
- root control 在其他 acceptance hard gates 全部通过后，才创建 `gatew-soak-completion-marker-v2`；
- marker 固定写入 root-owned `control/completion-marker.json`，要求 `root:root/0600`；
- marker 精确绑定 `runId/releaseCommit/mainPid/lastValidSampleAt/evidenceManifestSha256/evidenceFinalChainHash/completedAt/checksum`；
- acceptance proof、显式 finalizer 与 terminal verifier 均重新校验 root marker；
- marker create-once；相同输入幂等，冲突输入 fail-closed。

### 验证

新 12-case security regression 覆盖 root-control-only、marker schema/checksum/evidence binding、所有其他 acceptance precondition 在 marker 写入前通过、proof/marker 在 stop 与 terminal 写入前复核，以及临时文件崩溃窗口。

## 5. Finding 2：stop intent reason 与恢复语义

### 修复前

- `reasonCode` 只受通用安全格式约束，未知 reason 可进入受控停止信任边界；
- 同输入 stale write-once intent 若出现在 exit fact 之前，会永久阻断 canonical 重试，需要手工删除文件恢复。

### 最小修复

- reason allowlist 精确限制为 `OPERATOR_STOP_REQUESTED` 与 `ACCEPTANCE_FINALIZATION`；
- 错误 RunId、release、checksum、future timestamp、reason 或冲突输入继续拒绝；
- 同输入 stale intent 且不存在 exit fact 时，将原文件不改内容地移动为 `stop-intent-retired-<requestId>.json`，随后创建新的 canonical intent；
- 已存在 exit fact 的 stale intent 固定返回 `BLOCKED / STOP_INTENT_STALE_AFTER_EXIT`；
- `OPERATOR_STOPPING` 可恢复进入同一 canonical stop path；
- stale retirement 与新 intent 创建发生在 root control 的共享 terminal authority lock 内。

## 6. Finalizer、并发与 fail-closed 结论

- evidence verifier 只证明 evidence integrity，成功值保持 `PASS / FORMAL_EVIDENCE_VERIFIED`；
- acceptance verifier 继续逐项强制 unit/PID/restart/start count/clock/168h/last sample/heartbeat/hash/release/security/kill-switch，成功值唯一为 `PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED`；
- terminal verifier 校验唯一正式 terminal、schema/checksum、RunId/release/clock/proof/marker/stop intent/result binding，成功值唯一为 `PASS / FORMAL_TERMINAL_VERIFIED`；
- legacy `verify` 固定 `BLOCKED / VERIFY_ACTION_SPLIT_REQUIRED`，可执行路径无 `FORMAL_SOAK_VERIFIED` 成功值；
- acceptance 与 fail-close 使用同一 `failclose.lock`、独占文件锁和 create-once terminal；
- fail-close 先取得锁或 acceptance 先取得锁均只产生一个 terminal；已有 ACCEPTED/REJECTED terminal 不可被另一方覆盖；
- acceptance proof 已写但 terminal 缺失时，同输入 canonical finalizer 可重试；冲突输入拒绝；
- `.create-*` 临时文件不匹配正式 `terminal-status*.json`，不会被 terminal verifier 当成正式 terminal；
- Attempt-09 fixture 继续精确得到 evidence PASS、acceptance FAIL、finalizer BLOCKED，未按 fixture 文件名硬编码结果。

## 7. Automatic fail-close 与 systemd

- automatic fail-close 不运行完整 hash chain/acceptance verifier，不调用 Java/JDBC，不访问网络、OKX、credential 或 raw provider payload；
- 只读取 bounded local control facts，使用 25 秒锁预算并受 systemd `TimeoutStartSec=30s` 限制；
- fail-close unit 保持 `Type=oneshot`、`Restart=no`、`PrivateNetwork=true`、`RestrictAddressFamilies=AF_UNIX`、空 capability、`NoNewPrivileges=true`；
- worker unit 保持 `Restart=no`；未发现相关 timer、scheduler 自动续跑或 Attempt-10 创建路径；
- service success 不等于 soak accepted；accepted terminal 仍要求 acceptance proof 与 root-owned completion marker。

## 8. 本地验证

安全环境：

```text
CI=true
NQ_NO_OUTBOUND=true
NQ_AI_ENABLED=false
NQ_DH_RUNTIME_ENABLED=false
NQ_REAL_EXCHANGE_ENABLED=false
```

| Command / evidence | Result |
| --- | --- |
| PowerShell 5.1 / 7 control self-test | PASS；双引擎各 `49` cases |
| PowerShell 5.1 / 7 fail-close self-test | PASS；双引擎各 `8` cases |
| PowerShell 5.1 / 7 worker self-test | PASS；双引擎各 `59` cases |
| PowerShell 5.1 / 7 remediation regression | PASS；双引擎各 `32` cases |
| PowerShell 5.1 / 7 security regression | PASS；双引擎各 `12` cases |
| PowerShell 5.1 / 7 builder / installer self-test | PASS |
| PowerShell 5.1 / 7 next-action / lifecycle / task-evidence regression | PASS |
| `check-current-authority.ps1` | `PASS / CURRENT_AUTHORITY_CONSISTENT`；errors=`0` |
| `check-doc-links.ps1 -Roots docs/current` | PASS；`135 checked / 1 existing GateJ warning / 0 errors` |
| PowerShell AST / actual execution | PASS；changed scripts 在两引擎均可解析并执行 |
| `git diff --check` | PASS |

一次将多项治理检查合并到单个 60 秒窗口的命令在 PowerShell 7 lifecycle 完成前超时；超时不计为通过。随后将 lifecycle/task-evidence、authority 与 doc links 拆分重跑，均独立通过。

## 9. Immutable release 与 Linux 残余

当前只完成 builder/verifier/installer 静态审查和双引擎 self-test。必须在 Commit A exact-head CI 10/10 GREEN 后，从 clean Commit A 构建 canonical `EXACT_COMMIT` bundle，并重新证明：

- `artifactCount=130`；
- closed artifact set、manifest hash、每个 artifact size/SHA-256；
- UTF-8/LF/no-CR 与 `0755/0644` mode contract；
- undeclared artifacts=`0`，server Git checkout dependency=`0`；
- tampered artifact 被拒绝；
- bundle 不含本机绝对路径、credential、日志、临时 evidence。

Windows 本地必须继续记录 `posixVerified=false`。root ownership、worker 不可写、symlink 与 systemd installation 只能在后续独立 Linux root install/deployment verification 中证明：

```text
PENDING_LINUX_ROOT_INSTALL_VERIFICATION
```

该残余不授权部署或 Attempt-10。

## 10. 文件

当前安全修复新增：

- `scripts/gatew/tests/run-gatew-soak-remediation-security-regression.ps1`

当前安全修复修改：

- `scripts/gatew/gatew-soak-remediation-contract.psm1`
- `scripts/gatew/gatew-okx-readonly-soak-control.ps1`
- `scripts/gatew/gatew-okx-readonly-soak.ps1`
- `scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1`

未修改 systemd unit、deploy、backend、frontend、research、migration、CI workflow、credential 配置、OKX adapter、交易/风控/ledger 主链或历史 Attempt-09 evidence。

## 11. Findings

### P0

- 无。

### P1

- 已关闭：`COMPLETION_MARKER_WRITABLE_BY_WORKER`。
- 已关闭：`STOP_INTENT_RECOVERY_AND_REASON_ALLOWLIST`。
- 开放 P1：无。

### P2

- `PENDING_LINUX_ROOT_INSTALL_VERIFICATION`：Windows 无法证明目标 Linux 上 root ownership、mode、symlink/reparse 与 worker 不可写的实际安装结果。

### P3

- IDE PowerShell inspection 对 cast-heavy 既有表达式存在误报；真实 PowerShell 5.1/7 AST 与执行均通过。本轮未运行会产生大范围噪声的 IDE PowerShell formatter。

## 12. 当前停止线

Commit A、Commit A exact-head CI、新 `EXACT_COMMIT` bundle 与篡改验证完成前，结论保持：

```text
CONDITIONAL_PASS /
P1_MINIMAL_FIX_LOCALLY_VALIDATED /
COMMIT_A_CI_PENDING /
EXACT_COMMIT_BUNDLE_PENDING /
PENDING_LINUX_ROOT_INSTALL_VERIFICATION /
ATTEMPT_10_NOT_AUTHORIZED
```

当前唯一下一动作仍为：

```text
NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW
```
