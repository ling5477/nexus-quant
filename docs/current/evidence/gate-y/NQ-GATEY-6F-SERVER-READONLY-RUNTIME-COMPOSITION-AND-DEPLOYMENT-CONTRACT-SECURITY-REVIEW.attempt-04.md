# NQ-GATEY-6F server read-only runtime composition and deployment contract Security Review attempt-04 final

## 1. Final security decision

`PASS / GATEY_6F_SECURITY_REVIEW_ATTEMPT_04_FINAL_ACCEPTED / ROLLBACK_AUTHORIZATION_BOUNDARY_VERIFIED / RECEIPTS_AUDIT_ONLY_VERIFIED / CALLER_CONTROLLED_AUTHORIZATION_PATHS_CLOSED / CURRENT_FACT_REVERIFICATION_FAIL_CLOSED / CAPABILITY_NEUTRAL_RUNTIME_REGRESSION_GREEN / ORIGINAL_SECURITY_FINDINGS_CLOSED / P0_0 / P1_0 / READY_TO_COMMIT`（通过 / GateY-6F 最终安全复核接受 / 可进入提交前复核）。

本结论只接受当前未提交 GateY-6F implementation、review、remediation 完整链的本地代码与合同证据。它不表示 production backup/restore、production health probe、部署、migration、LIVE、第一笔真实订单或 micro-live 已验证或授权。

## 2. Task classification 与 baseline

- classification：`FINAL_INDEPENDENT_SECURITY_REVIEW + ROLLBACK_AUTHORIZATION_BOUNDARY_REVIEW + TARGETED_REGRESSION`；NQ-only、L 级、`REVIEW_ONLY`。
- branch：`dev`；staged=`0`。
- `git fetch origin` 成功；`HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1`。
- current authority：GateY-6F=`NOT_STARTED`（未开始）、LIVE=`DISABLED`（关闭）、kill switch=`ENGAGED`（已接合）；first real order/micro-live 未授权，soak 未开始。
- review 写入前 changed set：expected/actual=`45/45`、missing/extra=`0/0`；全部属于 GateY-6F implementation/review/remediation 链。写入本 evidence 后只允许 index、`TESTING.md`、`WORKLOG.md` 最小更新。

## 3. Scope

本轮只复核：

1. exported `Test-GateY*` 是否仍能产生 rollback/deployment authorization；
2. receipt/evidence 是否固定为 `AUDIT_EVIDENCE_ONLY`；
3. `Assert-GateYRollbackContract` 是否拒绝 caller evidence，并在当前 production verifier 能力不足时 fail-closed；
4. Attempt-03 public-command caller PoC 是否永久失败；
5. capability-neutral composition、HardLink、production qualification context、Linux installer 既有关闭项是否回归。

明确不扩展 stable-open race、新 POSIX taxonomy、default full `NexusQuantApplication` proof、Spring 架构重设计或 deployment framework completeness。

## 4. Exported verifier surface

实际 exported `Test-GateY*` / `Assert-GateY*` / `Invoke-GateY*` 为：

```text
Assert-GateYHealthReceipt
Assert-GateYPostActivationHealth
Assert-GateYRegularFileIdentity
Assert-GateYRollbackContract
Invoke-GateYSyntheticRollbackAssessment
Test-GateYBackupArtifact
Test-GateYFlywayHistoryObservation
Test-GateYPostActivationHealth
Test-GateYReadonlyRelease
Test-GateYReleaseSchemaCompatibility
Test-GateYRestoreEvidence
```

`Test-GateYFlywayHistoryObservation`、`Test-GateYReleaseSchemaCompatibility`、`Test-GateYBackupArtifact`、`Test-GateYRestoreEvidence` 与 `Test-GateYPostActivationHealth` 只生成 audit/diagnostic observation。统一 schema 为：

```text
schemaVersion=gatey-deployment-audit-evidence.v2
evidenceRole=AUDIT_EVIDENCE_ONLY
authorizationEligible=false
```

源码未发现 `VerifiedReceiptAuthority`、`ConditionalWeakTable`、`New-GateYVerifiedReceipt`、trusted object registry、private mint identity 或 receipt identity authorization 仍位于 authorization path。

## 5. Final P0 review

### 5.1 Attempt-03 caller PoC

独立 PoC 只使用 exported commands、caller-controlled 系统临时目录与 caller-controlled canonical JSON/artifact；未调用或修改 module internal function。

结果：

```text
FLYWAY_HISTORY evidenceRole=AUDIT_EVIDENCE_ONLY authorizationEligible=false
COMPATIBILITY evidenceRole=AUDIT_EVIDENCE_ONLY authorizationEligible=false
BACKUP_VERIFICATION evidenceRole=AUDIT_EVIDENCE_ONLY authorizationEligible=false
RESTORE_VERIFICATION evidenceRole=AUDIT_EVIDENCE_ONLY authorizationEligible=false

Assert result=BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE
serverMutation=false
```

未得到 `codeRollback=ALLOWED`、`PRE_DEPLOYMENT_READY` 或 `POST_ACTIVATION_ACCEPTED`。Attempt-03 caller-controlled authorization path 已关闭。

### 5.2 Serialization、clone、stale 与 cross-binding

- canonical write/read 后 `authorizationEligible=false`。
- JSON serialize → deserialize 与 clone 后均不可作为 Assert input。
- stale artifact 被 synthetic evaluator 重新读取，只生成新 audit digest；旧 evidence 不能授权。
- cross-release、cross-schema、different-backup restore、caller-generated/modified digest 均不能改变 authorization decision。
- 删除全部 audit evidence 文件不影响授权安全性；`Assert-GateYRollbackContract` 不消费 evidence root 或 receipt 文件。

### 5.3 Assert current-fact verification

`Assert-GateYRollbackContract` 的新签名只接受 manifest、manifest hash 与 release root。任何 legacy compatibility/backup/restore evidence 或把 evidence object 放入 release-root 参数，均返回：

```text
BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE
```

在 disposable、network-none Linux 容器中构造 canonical `/opt/nexus-quant/releases/<releaseId>` root-owned/POSIX release，`Test-GateYReadonlyRelease -RequirePosix` 通过；无 caller receipt 的 Assert 对当前 production verification 精确返回：

```text
BLOCKED / ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED
```

该结果证明 production Flyway/backup/restore verifier 尚未实现时 fail-closed；它不是本轮 finding，也不构成 production verification 已完成。

### 5.4 Compatibility、backup/restore 与 health

- caller compatibility assertion：拒绝；disposable release compatibility 仅为 non-authoritative audit observation。
- caller backup/restore：只能产生 audit evidence；不能得到 database recovery authorization。
- production backup/restore：`0/0`，未执行、未声明通过。
- caller health evidence：`Assert-GateYHealthReceipt` 固定拒绝。
- post-activation health：未执行真实 JVM/loopback probe，production Assert 固定 `BLOCKED / QUALIFICATION_HEALTH_NOT_VERIFIED`。

### 5.5 Synthetic assessment boundary

`Invoke-GateYSyntheticRollbackAssessment` 返回：

```text
decision=PASS / GATEY_READONLY_SYNTHETIC_ROLLBACK_ASSESSMENT
authorizationEligible=false
deploymentAcceptance=false
codeRollback=SYNTHETIC_REQUIRES_DATABASE_RECOVERY
databaseRecovery=SYNTHETIC_VERIFIED_BACKUP_AND_RESTORE
```

`SYNTHETIC` 状态均不能推导 production ready、production verified、deployment accepted 或 rollback authorized。

## 6. P1 targeted regression

### 6.1 Capability-neutral composition

- production `src/main/java` 对 `GateYReadonlyQualification|GATEY_READONLY_QUALIFICATION|!gatey-readonly-qualification|nq.gatey.readonly-qualification` 搜索为 `0 matches`。
- production capability 使用 `nq.runtime.provider-observation.enabled` 与 `nq.runtime.trading-components.enabled`；base default=false，相关 `@ConditionalOnProperty` 均 `matchIfMissing=false`。
- GateY 只保留 deployment profile alias；未重新进入 production Java security branching。
- full Maven 包含 qualification production context 与 capability-neutral regression，23 modules `BUILD SUCCESS`。

结果：`CAPABILITY_NEUTRAL_COMPOSITION_REGRESSION_GREEN`。

### 6.2 Release HardLink

GateY 27-case regression 在 PowerShell 5.1、PowerShell 7 与 disposable Linux 均通过；永久覆盖 external HardLink、parent link traversal 与 artifact tamper。Linux installer 13/13 覆盖 post-verification HardLink swap、installed HardLink、source mutation independence、owner/mode、no-overwrite、atomic current 与 previous release preservation。

结果：`HARDLINK_REGRESSION_GREEN`。

### 6.3 Production qualification context

full Maven 通过；既有 `GateYReadonlyQualificationProductionContextTest`、configuration/authority tests 与 mutation-runtime bean 计数回归未失败。未新增 credential/network startup side effect，未发现 mutation runtime 可达。

结果：`PRODUCTION_CONTEXT_REGRESSION_GREEN`。

### 6.4 Linux installer

cached `mcr.microsoft.com/powershell:7.5-ubuntu-24.04`、`--network none --rm`、repository read-only mount 下，installer regression=`13/13 PASS`、`productionMutation=false`。

结果：`LINUX_INSTALLER_REGRESSION_GREEN`。

## 7. Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| `git fetch origin` + baseline | PASS（通过） | branch=dev；HEAD/origin-dev exact match；staged=0 |
| changed-set inventory | PASS（通过） | review pre-write expected/actual=45/45、missing/extra=0/0 |
| independent exported-command PoC | PASS（通过） | audit-only；Assert blocked；temporary directory only |
| canonical Linux current-fact Assert | PASS（通过） | POSIX release verified；`ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED` |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23 modules；`BUILD SUCCESS`；failures/errors=0；48.297s |
| GateY PowerShell 5.1 | PASS（通过） | 27/27；manifest SHA-256=`4cab3507fe07465a29c1c33032187770f446d940415b7f2d749caa1f079898c9` |
| GateY PowerShell 7 | PASS（通过） | 27/27；manifest hash 与 PS5.1 一致 |
| disposable Linux GateY | PASS（通过） | 27/27；cached image、`--network none --rm` |
| disposable Linux installer | PASS（通过） | 13/13；`productionMutation=false` |
| GateW frozen regression | PASS（通过） | 34/34；GateW diff=0 |
| migration inventory | PASS（通过） | V1～V41、41 files、continuous、target=V41、migration diff=0 |
| production Java stage semantic scan | PASS（通过） | 0 matches |
| current authority | PASS（通过） | errors=0；`PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `git diff --check` | PASS（通过） | exit=0；仅既有 LF→CRLF warning |

已保留的非阻断执行记录：第一次 combined Linux wrapper 虽两份脚本均输出 PASS，但因包装层复用残留 `$LASTEXITCODE` 最终 exit=1；随后两个脚本均以独立 `-File` 命令重跑，exit=0。第一次独立 PoC 因外层 PowerShell 展开内层变量而 parser exit=1；修正 quoting 后同一 PoC exit=0。第一次 migration inventory probe 使用字典序排列，错误显示 target=V9；数值排序重跑后 V1～V41 continuous。canonical Linux PoC 第一次在工具 JavaScript 解析阶段失败，容器未启动；移除冲突字符后 exit=0。上述失败均未被记为通过，且没有生产副作用。

未运行 frontend、Python、生产 server、production DB、production health 或远端 exchange 测试；它们不属于本轮范围。

## 8. Findings

### P0

- 无。

### P1

- 无。

### Deferred P2/P3

- P2：stable-open identity residual，非本轮 blocker。
- P2：full default `NexusQuantApplication` context proof，非本轮 blocker。
- P3：Javadoc drift，非本轮 blocker。

本轮未发现这些 residual 实际形成权限绕过、资金风险、credential 泄露或 mutation runtime 可达，因此不升级。

## 9. Side-effect counters

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

Disposable Linux 写入只发生在 `--network none --rm` 容器的 `/tmp`、容器内 `/opt/nexus-quant` fixture 与临时 system user；容器退出即删除，不属于 server/production mutation。

## 10. Commit recommendation 与 next action

- Commit recommendation：`READY TO COMMIT`（可进入提交前复核）。
- 唯一下一动作：`NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-COMMIT-AND-EXACT-HEAD-CI`。
- 本轮未执行 `git add`、commit、push 或 tag。

