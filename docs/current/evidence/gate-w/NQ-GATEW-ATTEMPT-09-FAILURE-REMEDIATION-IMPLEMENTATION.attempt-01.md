# NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION — Attempt 01

## 1. 结论

`IMPLEMENTED / SELF_REVIEWED / CI_GREEN / PENDING_SECURITY_REVIEW / ATTEMPT_10_NOT_AUTHORIZED`（已实现 / 已自审 / CI 已通过 / 待独立安全审查 / Attempt-10 未获授权）。

本轮已在本地完成 verifier 语义拆分、168 小时 acceptance hard gates、轻量 fail-close finalizer、显式 acceptance finalizer、受控 stop intent、terminal schema v2、systemd fail-close 合同与 32 场景离线回归。Implementation commit `92adff7e55c2200692e892db2189132c243a1ac5` 的 exact-head `NQ CI Baseline` run `30474856153` 为 `completed / success / 10 of 10`。

该结论不表示服务器已部署新 release，不恢复或接受 Attempt-09，不授权 Attempt-10，不表示 GateW freeze ready/frozen，也不构成 LIVE 或交易写侧授权。

## 2. Task classification 与范围

- 主类型：NQ-only `GATEW_TOOLING_REMEDIATION / CODE_CHANGE`；辅助类型：`CI_CD / SYSTEMD_CONTRACT / IMMUTABLE_RELEASE_BUILD_PROOF / IMPLEMENTATION_REPORT`。
- 起始基线：`dev`；`HEAD == origin/dev == 017639101ac95d462f84e76fc50e26bc1939467b`。
- 起始 exact-head CI：run `30461728822 / completed / success / 10 of 10`，`headSha=017639101ac95d462f84e76fc50e26bc1939467b`。
- Authority before：

```text
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-09
work_batch_status=FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED
next_action=NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-IMPLEMENTATION
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
```

- 固定失败事实：Attempt-09 有效时长 `471795.0520427s < 604800s`，最后有效样本早于 `plannedAcceptanceAt`，最终 unit 为 inactive/dead、MainPID=`0`，worker 终止分类为 `OPERATOR_OR_AUTOMATION_STOP`，精确发起者保持 `UNKNOWN`。
- 明确不做：SSH、远端取证、finalizer 重跑、服务器修改、release 部署、service 操作、Attempt-10 创建、freeze/archive/tag、credential 读取、OKX 调用、交易写侧变更。

## 3. Verifier 合同修复

旧 `verify` action 固定返回：

```text
BLOCKED / VERIFY_ACTION_SPLIT_REQUIRED
```

新的正式动作与精确语义如下：

| Action | 成功结果 | 只负责 |
| --- | --- | --- |
| `verify-evidence` | `PASS / FORMAL_EVIDENCE_VERIFIED` | sequence、record checksum、hash chain、evidence schema、安全计数结构与 immutable release binding |
| `verify-acceptance` | `PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED` | active unit、PID/restart/start continuity、clock、168h、last sample、heartbeat、evidence/release/security/kill-switch hard gates |
| `verify-terminal` | `PASS / FORMAL_TERMINAL_VERIFIED` | terminal schema/checksum、RunId/release/clock/result binding、write-once/CAS 与冲突拒绝 |

不再存在 evidence-only 成功返回 `PASS / FORMAL_SOAK_VERIFIED` 的路径；32 场景回归明确验证 `ambiguousFormalSoakVerifiedRemoved=true`。

### Acceptance hard gates

`verify-acceptance` fail-closed 校验：

- 正式 unit 必须 `active/running`，MainPID 必须非零并与 create-once `worker-start` 一致；
- `NRestarts=0`，不得出现第二次 `ExecMainStart`，不得存在提前 exit fact；
- `acceptanceStartAt` 与 `plannedAcceptanceAt` 必须保持 immutable，观测有效时长必须 `>=604800s`；
- 最后有效样本与 heartbeat 必须覆盖 `plannedAcceptanceAt`；
- evidence/hash chain、immutable release、forbidden endpoint、fallback、raw response、secret exposure 与所有已存样本 kill switch 必须全部通过；
- unit、PID、restart、start count、clock、duration、last sample、安全计数、hash/release 或 kill-switch 任一不满足即返回 `FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED`。

Acceptance unit 状态在重型 evidence/release verification 完成后重新采样，避免使用 stale unit snapshot 形成 acceptance 窗口。

## 4. Finalizer 与 terminal

### 自动 fail-close finalizer

- 只读取 root-owned exit fact、stop intent、lifecycle/clock 与固定 release descriptor；
- 不执行完整 hash-chain 或 acceptance verifier，不访问 Java/JDBC、credential、网络或 OKX；
- 使用 `REJECTED_RUNTIME_EXIT`、`REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP`、`REJECTED_INSUFFICIENT_DURATION`、`REJECTED_FINALIZER_ERROR` 四类拒绝终态；
- systemd unit 保持 `Type=oneshot / Restart=no`，预算改为 `TimeoutStartSec=30s`，并启用 `PrivateNetwork=true`、`IPAddressDeny=any`、`RestrictAddressFamilies=AF_UNIX`、空 capability；
- 最近一次本地 32 场景回归中的 fail-close fixture 用时 `173ms < 30000ms`。

### 显式 acceptance finalizer

- 只能在持久化 acceptance proof 精确为 `PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED` 后调用；
- write-once、CAS protected；相同输入幂等，冲突输入拒绝；
- 不重新访问外部网络，不修改 acceptance clock，不创建新 Attempt；
- 唯一接受终态为 `ACCEPTED_168H_READONLY_SOAK`；
- proof 不存在、无效或不匹配时固定返回 `BLOCKED / ACCEPTANCE_VERIFY_REQUIRED`。

Terminal schema v2 对 accepted/rejected 双向绑定 RunId、release、clock、stop intent 与 acceptance result。Accepted terminal 必须有完整 acceptance proof；`releaseCommit=UNKNOWN` 只允许 `REJECTED_FINALIZER_ERROR`，不能通过 accepted terminal verification。

## 5. Stop attribution 与 worker completion

- Canonical stop helper 在请求停止前写 root-owned、write-once、checksum-protected 的脱敏 stop intent，字段包含 `runId/requestId/requestedAt/requestedByUid/reasonCode/releaseCommit/checksum`。
- Stop intent 最大有效期为 300 秒；缺失、过期、checksum 错误或字段不匹配均分类为 `UNAUTHORIZED_OR_UNKNOWN_STOP`。
- 合法 intent 分类为 `AUTHORIZED_CONTROLLED_STOP`，但不会把 root 的强制操作错误描述为可被工具阻止。
- Worker 到期只写 create-once completion marker 并保持 `ACCEPTANCE_READY`，等待显式 acceptance finalizer；`serviceResult=success` 本身不构成接受。

## 6. Attempt-09 固定 fixture

脱敏 fixture 固定使用：

```text
acceptanceStartAt=2026-07-22T11:19:59.5201964Z
plannedAcceptanceAt=2026-07-29T11:19:59.5201964Z
lastValidSample=2026-07-27T22:23:14.5722391Z
observedDuration=471795.0520427
requiredDuration=604800
MainPID=0
unit=inactive/dead
workerExit=TERM
terminal=missing
```

实际回归结果：

```text
verify-evidence=PASS / FORMAL_EVIDENCE_VERIFIED
verify-acceptance=FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED
acceptance finalizer=BLOCKED / ACCEPTANCE_VERIFY_REQUIRED
Attempt-10 created=false
```

## 7. 文件

Implementation Commit A 新增：

- `scripts/gatew/gatew-soak-remediation-contract.psm1`
- `scripts/gatew/tests/fixtures/attempt-09-rejected.json`
- `scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1`

Implementation Commit A 修改：

- `deploy/systemd/nq-gatew-soak-failclose@.service`
- `scripts/docs/governance-workflow-contract.json`
- `scripts/docs/test-current-authority-next-action.ps1`
- `scripts/gatew/build-gatew-release-bundle.ps1`
- `scripts/gatew/gatew-okx-readonly-soak-control.ps1`
- `scripts/gatew/gatew-okx-readonly-soak-failclose.ps1`
- `scripts/gatew/gatew-okx-readonly-soak.ps1`
- `scripts/gatew/verify-gatew-release.ps1`

`scripts/docs/governance-workflow-lib.ps1` 未修改。Backend、frontend、research/py、migration、CI workflow、credential、OKX adapter、交易/订单/风控/ledger 主链与历史 Attempt-09 evidence 均未修改。

## 8. 验证

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| Windows PowerShell 5.1 / PowerShell 7 control self-test | PASS | 双引擎各 `49` cases；`PASS / FORMAL_CONTROL_SELF_TEST` |
| Windows PowerShell 5.1 / PowerShell 7 fail-close self-test | PASS | 双引擎各 `8` cases；`PASS / LIGHTWEIGHT_FAILCLOSE_SELF_TEST` |
| Windows PowerShell 5.1 / PowerShell 7 worker self-test | PASS | 双引擎各 `59` cases；`PASS / SUPERVISOR_SELF_TEST` |
| `scripts/gatew/tests/run-gatew-soak-remediation-regression.ps1` | PASS | 双引擎各 `32` cases；Attempt-09/evidence/acceptance/finalizer/stop intent/systemd/no-network/no-credential/Attempt-10 负例全覆盖 |
| builder / installer self-test | PASS | 双引擎均通过；LF normalization、deterministic JAR、unit release binding、release ID/enablement/pre-create contract 通过；network/credential=false |
| governance next-action / lifecycle | PASS | 双引擎 `CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`、`GOVERNANCE_LIFECYCLE_REGRESSION`、`TASK_EVIDENCE_POLICY_VALID` |
| current authority / current doc links | PASS / 1 EXISTING WARNING | authority errors=`0`；links=`135 checked / 1 GateJ historical warning / 0 errors` |
| Commit A exact-head CI | PASS | run `30474856153 / completed / success / 10 of 10`；`headSha=92adff7e55c2200692e892db2189132c243a1ac5` |

测试环境固定：

```text
CI=true
NQ_NO_OUTBOUND=true
NQ_AI_ENABLED=false
NQ_DH_RUNTIME_ENABLED=false
NQ_REAL_EXCHANGE_ENABLED=false
```

## 9. Immutable release build proof

在 clean Commit A 上使用 canonical builder：

```text
SourceTreeMode=EXACT_COMMIT
releaseId=92adff7e55c2200692e892db2189132c243a1ac5
sourceCommit=92adff7e55c2200692e892db2189132c243a1ac5
manifestSha256=9ab5dd5523d0e7beea558491928d814bec790024496f94d09a1d1cf7848be75b
artifactCount=130
```

- `PASS / IMMUTABLE_RELEASE_BUNDLE_BUILT` 与 `PASS / IMMUTABLE_RELEASE_VERIFIED`；
- manifest 中 LF/BINARY artifacts=`8/122`，mode `0755/0644` artifacts=`5/125`，未声明文件=`0`；
- 全部 LF artifacts 通过 UTF-8/LF/no-CR 校验；全部 artifacts 的 size/SHA-256 与 closed manifest 一致；
- bundle 内服务器 Git checkout 引用=`0`，release 不依赖服务器 Git checkout；
- 篡改副本返回 exit `2 / BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH`；
- 本地 bundle 与篡改副本验证后已精确删除。

本机为 Windows，因此实际 root-owned POSIX install/verify 未执行，`posixVerified=false`。本轮只证明 manifest mode/ownership 输入合同可生成，以及 installer 权限逻辑与 self-test 通过；服务器 root-owned install、POSIX verify、systemd verify 与 deployment 均保留给独立安全审查通过后的后续任务。

## 10. Governance 与 authority

治理合同新增且只新增精确三元组：

```text
work_batch_status=IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVIEW
work_batch=GateW-ATTEMPT-09-FAILURE-REMEDIATION
next_action=NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW
```

错误状态、错误 work batch、Attempt-10、近似 action、大小写错误与附加后缀均拒绝；非合同映射继续使用原严格规则。

Authority after：

```text
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-09-FAILURE-REMEDIATION
work_batch_status=IMPLEMENTED|CI_GREEN|PENDING_SECURITY_REVIEW
work_batch_commit=92adff7e55c2200692e892db2189132c243a1ac5
work_batch_ci_run=30474856153
next_action=NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW
Attempt-09=REJECTED
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
```

## 11. Findings、限制与边界

### P0

- 无。

### P1

- 无；implementation self-review 与离线回归未发现开放 P1。独立 security review 尚未执行，因此本结论不能替代独立审查。

### P2

- 显式 acceptance finalizer 若在停止 worker 后、写 terminal 前发生进程级崩溃，会 fail-closed 并保持未接受，而不会形成假阳性；独立安全审查仍需评估该中断窗口、恢复策略与幂等边界。
- Windows 本地只验证 POSIX/ownership 生成与静态 installer contract，没有执行 Linux root-owned install。

### P3

- IDE PowerShell inspection 对部分类型转换表达式存在误报；Microsoft PowerShell 5.1/7 的 AST、self-test 与实际执行均通过。本轮未运行会破坏 `$script:` 语法的 IDE PowerShell formatter。
- Root/current README 存在既有阶段摘要漂移，但不在本任务 allowlist，且不覆盖 `STATUS.md`，保持不动。

未运行：SSH、远端 immutable verifier、服务器 install/activate/systemd verify、远端 unit、真实 OKX、credential、permission probe、168 小时新 soak、Attempt-10、freeze/archive/tag。Exact-head CI 已实际执行 backend/frontend/research/security/DB/E2E 10 个 jobs；本地未重复运行与本次 PowerShell/systemd tooling diff 无关的全量 Maven/frontend/Python 命令。

## 12. Decision

```text
IMPLEMENTED /
SELF_REVIEWED /
CI_GREEN /
PENDING_SECURITY_REVIEW /
ATTEMPT_10_NOT_AUTHORIZED
```

唯一下一动作：

```text
NQ-GATEW-ATTEMPT-09-FAILURE-REMEDIATION-SECURITY-REVIEW
```

建议 evidence/current authority commit message：

```text
docs(gatew): record attempt 09 remediation implementation
```
