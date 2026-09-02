# GateAUDIT Phase5B Canonical Deployment and Restore Remediation Attempt-02 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Task:
NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-REMEDIATION

Attempt:
02

Decision:
IMPLEMENTED / PHASE5B_CANONICAL_DEPLOYMENT_AND_RESTORE_REMEDIATION_ATTEMPT_02_COMPLETE /
P0_0 / P1_4_REMEDIATED / P2_JAR_INTEGRITY_REMEDIATED /
PENDING_INDEPENDENT_REVIEW

This evidence does not close P5-F002 or P5-F003 and does not accept Phase5B.
```

## 1. Baseline and inherited review

```text
branch=audit/post-gatey-agent-baseline
HEAD=origin/audit/post-gatey-agent-baseline=4c2b393ef0b3806a60cd3240c2e75ba1b350cc87
origin/dev ancestor=YES
staged=0
authority=PASS / CURRENT_AUTHORITY_VALID
inherited Review fingerprint=8c57410750a4d90ab34f0f0a012869eab20bf5b1eaaea0912988535a190f01ac
```

Inherited Review=`FAIL / P0_0 / P1_4 / NOT_READY_TO_COMMIT`：

1. `CANONICAL_RELEASE_SOURCE_IDENTITY_MISBINDING`
2. `CANONICAL_DEPLOYMENT_ATOMICITY_AND_ROLLBACK_AUTHORIZATION_NOT_ENFORCED`
3. `CURRENT_SCHEMA_TARGET_MAJOR_RESTORE_NOT_PROVEN_AND_UNSUPPORTED_MAJOR_ACCEPTED`
4. `CRITICAL_CAPABILITY_REGISTRY_AND_PERMANENT_MUTATION_INCOMPLETE`
5. P2 `JAR_LOCAL_HEADER_CENTRAL_DIRECTORY_MISMATCH_NOT_REJECTED`

## 2. Source identity remediation

- `-AllowCandidateWorktree`已删除。`DEPLOYABLE`要求HEAD匹配且`git status --porcelain=v1 --untracked-files=all`为空；tracked、staged、untracked任一变化均拒绝。
- DEPLOYABLE artifact必须由Phase5A closed-set backend/frontend artifact manifest绑定，额外generated input拒绝。
- Manifest新增`deployable`、`sourceState`、`sourceTreeIdentity`与`requiredPostgresqlMajor`。正式release使用`COMMITTED_CLEAN + git-tree:<HEAD tree>`；本地candidate使用`UNCOMMITTED_CANDIDATE + candidate-sha256:<projection>`。
- TEST_ONLY ID固定为`nq-test-*`，production policy拒绝`deployable=false`；当前未提交candidate本地产物=`nq-test-32c7977b08ad1ddb32909e0e`，不得作为production release。
- 独立临时Git fixture证明clean PASS、tracked/staged/untracked/spoof/unmanifested input全部REJECTED。

## 3. Activation and rollback remediation

- Activation transaction=`HMAC-bound PREPARED journal → atomic pointer → COMPLETED`。authority key与journal仅存在installation root，Linux mode=`0600`。
- `AUTHORITY_PREWRITE`与`POINTER_SWAP`失败均保持previous pointer；`COMPLETION_WRITE`失败留下可信PREPARED journal，`recover`按实际current pointer确定性收敛为COMPLETED或ABORTED；unknown state fail-closed。
- Rollback不再接受caller target，只消费HMAC绑定的last completed activation，校验installation identity、current pointer、previous release与signed database state。
- Production activation/rollback要求signed database state；missing/forged/stale/current mismatch/previous missing均拒绝。DB schema高于previous release requirement时返回`DATABASE_RECOVERY_REQUIRED`，不切换pointer。
- 相关source/release/activation/rollback/JAR suite共`40 cases PASS`。

## 4. PostgreSQL 16 target proof

Docker pinned image仍受CloudFront `EOF`影响；未使用PG17替代。使用Ubuntu 24.04官方security repository的PostgreSQL 16.15 packages，仅下载并解包到WSL `/tmp`，未执行system package install。

```text
server=PostgreSQL 16.15
server_version_num=160015
pg_dump=16.15
pg_restore=16.15
migration count=46
latest/Flyway current=V46
pending=0
backup size=793514
backup SHA-256=366e801bda848d2af5a689973e9e7459882bac1520b443a9a86c6c889b959317
source canary=75|464|276|46|V46|1
restored canary=75|464|276|46|V46|1
repository smoke=PASS
application-context smoke=PASS
```

Restore negatives=`7/7 REJECTED`：tampered、truncated、wrong schema、wrong PostgreSQL major、restore failure、post-restore mismatch、missing Flyway history。Windows native PostgreSQL 17.7 runtime在migration前被`UNSUPPORTED_POSTGRESQL_MAJOR`拒绝。所有temporary cluster均清理。

## 5. CI capability and mutation remediation

Canonical registry从aggregate entry拆分为真实24项，新增独立：

```text
canonical release build
canonical release verifier
canonical install/activation
current-schema restore drill
backup integrity check
post-restore validation
```

Validator输出`CRITICAL_CAPABILITIES_OWNERSHIP=24 / MISSING=0 / UNKNOWN=0`。新增release-verifier removed、backup conditional、backup soft-fail；加上既有suite后`MUTATIONS_REJECTED=50`，Phase5A原40项与Phase5B prior 47项全部继续REJECTED。

## 6. JAR integrity remediation

Verifier现逐entry比较ZIP local header与central directory的raw name、general-purpose flags、compression method、CRC/size语义、local offset与data-descriptor boundary；合法无descriptor与12/16-byte descriptor均支持。Review真实穿透类型的local-name mutation和local-method mismatch在重新绑定artifact/manifest identity后均REJECTED；全量entry读取、81,920-byte固定buffer与资源上限保持。

## 7. Boundary and next action

```text
P5-F002=REMEDIATED_PENDING_INDEPENDENT_REVIEW
P5-F003=REMEDIATED_PENDING_INDEPENDENT_REVIEW
JAR P2=REMEDIATED_PENDING_INDEPENDENT_REVIEW
P5-F007/P5-F008/P5-F009=OPEN
remote enforcement=NOT_APPLIED / NOT_VERIFIED
platform attestation=DEFERRED
production/LIVE/credential access=NONE
staged/commit/push=0/NONE/NONE
```

Rollback仅允许文件级反向补丁；不得reset/rebase accepted branch。下一动作保持`NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-REVIEW`，最终关闭权属于新的独立高风险Review。

<!-- nq-runtime-scan:historical-reference:end -->
