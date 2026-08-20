# NQ-GATEY-6F server read-only runtime composition and deployment contract Security Review attempt-03

## 1. Review decision

`FAIL / GATEY_6F_SECURITY_REVIEW_ATTEMPT_03_REJECTED / P0_1 / CALLER_CONTROLLED_VERIFIED_RECEIPT_MINTING_REMAINS / NOT_READY_TO_COMMIT / NO_DEPLOYMENT / NO_SCOPE_EXPANSION`（失败 / Attempt-03 独立安全审查拒绝 / 仍有一个 P0 / 不可提交 / 不部署）。

Attempt-02 remediation 删除了公开 `New-GateYDeploymentReceipt`，但没有真正把 caller-supplied evidence 与 trusted evidence 分离：多个 exported verifier 仍可直接消费 caller 创建的临时 JSON/artifact，并在当前 module instance 内登记为 trusted receipt，随后被 exported rollback evaluator 接受。

## 2. Baseline 与范围

- branch=`dev`；本地 `HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1`；staged=`0`。
- authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`；GateY-6F=`NOT_STARTED`、LIVE=`DISABLED`、kill switch=`ENGAGED`。
- 审查范围：Attempt-02 remediation 的 GateY receipt/verifier/evaluator、capability-neutral Spring composition、相关 tests 与 evidence。
- 排除：服务器、生产 DB、credential、OKX/Binance、PLACE/CANCEL、transfer/withdraw、LIVE、kill disengage、部署与 migration。

## 3. P0 — exported verifier 仍可铸造 trusted receipt

### 根因

`New-GateYVerifiedReceipt` 虽未 export，但 `Test-GateYFlywayHistoryObservation`、`Test-GateYBackupArtifact`、`Test-GateYRestoreEvidence` 仍是 public PowerShell commands。它们接受 caller 传入的临时路径和 JSON/artifact，将其检查结果加入 module-private `ConditionalWeakTable` authority registry。`Assert-GateYRollbackContract` 也是 export，且只验证该 registry 中的对象身份与 digest。

registry 证明的只是“某个 verifier command 在当前 module instance 创建了对象”，没有证明 verifier 在受信 runner、root-owned evidence root、真实 Flyway/backup/restore execution 或不可由 caller 控制的 execution context 中运行。

### Dynamic PoC

在一次本地临时目录中，使用**仅 export 的 commands**创建：

1. `gatey-disposable-flyway-observation.v1` JSON；
2. `gatey-disposable-backup.v1` JSON；
3. public `Test-GateYFlywayHistoryObservation`、`Test-GateYBackupArtifact`、`Test-GateYRestoreEvidence`；
4. public `Assert-GateYRollbackContract`。

未调用 private mint function、未访问服务器、未使用 credential 或网络。结果为：

```text
compatibility=UNKNOWN
backupProducer=nq-gatey-backup-verifier
restoreProducer=nq-gatey-restore-verifier
codeRollback=REQUIRES_DATABASE_RECOVERY
databaseRecovery=VERIFIED_BACKUP_AND_RESTORE_REQUIRED
serverMutation=false
```

这直接违反本批 P0 约束“caller forged backup metadata 不得 mint VerifiedBackupReceipt”。`Test-GateYReleaseSchemaCompatibility` 将 caller 传入的 disposable previous release 降为 `UNKNOWN` 是有效修复，但没有覆盖 Flyway/backup/restore 的 public verifier path。

### Secondary proof

PowerShell module-private function 不是 access-control boundary：通过 module scope invocation 也可调用 `New-GateYVerifiedReceipt`，并使 exported `Assert-GateYRollbackContract` 返回 `codeRollback=ALLOWED`。这不是主 PoC，因为 public verifier path 已足以复现，但说明“未 export”本身不能构成 trusted receipt authority。

### Impact 与校准

- 影响面：rollback/migration precondition 的完整性与后续 deployment automation 决策。
- 前置条件：caller 能调用本模块的 documented PowerShell commands 并写入其正常 temporary evidence inputs；不需要 server、credential、network 或 private module function。
- counterevidence：当前 invoker 标记 `deploymentPerformed=false`，PoC 没有执行部署；但本任务的明确安全目标正是禁止 caller metadata 获得 verified evidence，后续 automation 若信任该 precondition 将继承错误结论。
- 结论：按本 work order 的 P0 trust-boundary hard gate，P0=1；不得进入 commit 或 deployment。

## 4. P1 composition review

P1=0（本轮未发现新增 P1）：production Java 中未再发现 `GateYReadonlyQualification`、`GATEY_READONLY_QUALIFICATION`、`!gatey-readonly-qualification` 或 `nq.gatey.readonly-qualification`。`provider-observation` / `trading-components` condition 均使用 `matchIfMissing=false`；GateY 仅保留 deployment profile alias。Attempt-02 的 full qualification context evidence继续适用，但这不抵消 P0。

## 5. Validation

| Check | Result | Notes |
| --- | --- | --- |
| public-command P0 PoC | FAIL（阻断） | caller-created temp evidence 被 public verifier 铸造为 trusted backup/restore receipt，rollback evaluator 接受 |
| module-scope secondary PoC | FAIL（阻断） | private mint 可由 PowerShell module scope 调用；不作为主攻击前提 |
| GateY PowerShell 5.1 regression | PASS（通过） | 32/32；未覆盖 public verifier authority bypass |
| GateY PowerShell 7 regression | PASS（通过） | 32/32；未覆盖 public verifier authority bypass |
| authority / diff check | PASS（通过） | authority errors=0，`git diff --check` exit=0 |
| Codex Security scan | PARTIAL（部分覆盖） | scanId=`a03a44c9-5cbf-433f-8e94-8cfbfc7d210c`；24 tracked files no issue；app inventory未纳入 untracked GateY source，P0由人工动态审查发现 |
| CodeRabbit | NOT RUN（未完成） | WSL CLI 已认证，但 review service WebSocket closed，未产生 issue 结果 |

## 6. Boundary confirmation

```text
Server SSH read/write = 0/0
Deployment = 0
Production Migration = 0
Production Backup/Restore = 0/0
Systemd/server symlink change = 0/0

Credential metadata/material read = 0/0
Decrypt = 0
OKX GET/POST = 0/0
PLACE/CANCEL = 0/0
Transfer/Withdraw = 0/0
ExecutionIntent/ExecutionReceipt delta = 0/0
Order/Ledger delta = 0/0
LIVE enable = 0
Kill disengage = 0
```

PoC 仅在本机系统临时目录内创建和清理 disposable files；不修改仓库实现或生产状态。

## 7. Required remediation direction

1. 不要把 caller-invokable verifier command 的输出直接视为 trusted evidence。
2. 将 receipt creation/evaluator input 绑定到不可由 caller 选择的 trusted execution context：例如固定 root-owned runner/evidence root、受控 producer process identity 或 evaluator 对真实 immutable inputs 的独立重验证。
3. 删除或降权 export 的 `Test-GateY*` verifier outputs，使它们不能单独满足 rollback/deployment contract。
4. 新增永久 attack regression：仅使用 exported commands + caller temp files 时必须无法获得 `VERIFIED_BACKUP_AND_RESTORE_REQUIRED` 或任何 deploy-ready state。

## 8. Final decision

- P0：1 — `CALLER_CONTROLLED_VERIFIED_RECEIPT_MINTING_REMAINS`。
- P1：0。
- Deferred P2/P3：沿用 Attempt-02 remediation 记录；本轮不扩大范围。
- Commit recommendation：`DO NOT COMMIT`。
- Next concrete action：`NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-P0-REMEDIATION-ATTEMPT-03`。
