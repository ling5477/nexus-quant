# GateAUDIT Phase5B Canonical Deployment and Restore Remediation Attempt-03 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Decision:
IMPLEMENTED / PHASE5B_CANONICAL_DEPLOYMENT_AND_RESTORE_REMEDIATION_ATTEMPT_03_COMPLETE /
P0_0 / P1_3_REMEDIATED / P2_KEY_PERMISSION_REMEDIATED /
PENDING_INDEPENDENT_REVIEW

This evidence does not close P5-F002 or P5-F003 and does not accept Phase5B.
```

## 1. Baseline and inherited findings

```text
branch=audit/post-gatey-agent-baseline
HEAD=origin/audit/post-gatey-agent-baseline=4c2b393ef0b3806a60cd3240c2e75ba1b350cc87
origin/dev ancestor=YES
staged=0
starting functional fingerprint=e3e479a002fcb874c1e038caacdae11c5a6c09b3c57651f6d6b6ea4f4fb1ad18
Attempt-03 functional files=20
Attempt-03 functional fingerprint=88c221735ad8d1b1b7aad17df1e4f9262702a3a54ab06f751bc49eb0a56c9b6c
authority=PASS
```

Attempt-02 independent Review=`FAIL / P1_3 / P2_1`。本轮只整改external provenance trust root、activation freshness、CI mandatory semantics和existing-key permission revalidation；PG16与JAR只做regression。

## 2. External release admission

- 新增bundle外`nq-canonical-release-admission.v1`，绑定source commit/tree、producer contract、release manifest/root digest、schema、PostgreSQL major、artifact set与CI execution identity。
- Admission root禁止位于release root内部。Current uncommitted candidate只能生成`TEST_ONLY / authorizationEligible=false` synthetic admission；当前local release=`nq-test-82227abd7decacce22e43e54`，external admission SHA-256=`8cd5aa41d56e011c8bbd96272c665f8b432c08781d8510a5785090d3ddbf9384`。
- Future production path只接受fixed root-owned `/etc/nexus-quant/release-admission/<releaseId>.json/.sha256`；caller path/digest在production policy不构成trust source。Exact-head CI wrapper生成并独立验证admission，随provenance material交付；OIDC/id-token仍deferred。
- Valid backend JAR、frontend、deployment asset及whole artifact set与internal manifest同时替换，在external root保持不变时全部REJECTED。无trusted placement而仅传caller admission也REJECTED。

## 3. Activation freshness and key identity

- 新增HMAC-bound `activation-head.json`，按generation单调递增并绑定transactionId、current/previous release、previousHeadDigest与activationDigest。
- PREPARED recovery要求`generation=head+1`与predecessor digest匹配；COMPLETED recovery只允许current authoritative head或确定性补写下一head。Old completed/PREPARED/rollback journal、old head以及A→B→A后旧record重放全部REJECTED。
- 原PREPARED/pointer/completion failure recovery、cross-install、DB-incompatible rollback和caller-target regression继续通过。
- 每次读取existing authority key均检查regular/no-link、link count=1；Linux额外检查expected owner和mode=`0600`。Windows suite=`57 cases PASS`；Linux PowerShell suite=`62 cases PASS`，实际拒绝wrong/missing/hard-link、0644、0666、symlink与wrong-owner。

## 4. CI mandatory capability enforcement

六项deployment-critical能力拆成single-purpose wrapper steps：release build/admission、release verify/admission、install/activate、restore、backup integrity、post-restore validation。Validator对每项通用检查required owner、唯一exact invocation、unconditional以及禁止failure swallowing语法。

每个critical step参数化生成removed/conditional/soft-fail/failure-ignored四类变体；加Phase5A regression后`MUTATIONS_REJECTED=64`。Registry仍为真实`24 / missing 0 / unknown 0`，没有Task-ID/test-name production特判。

## 5. Accepted capability regression

```text
PostgreSQL server/pg_dump/pg_restore=16.15
server_version_num=160015
migrations/latest=46/V46
pending=0
backup size=793514
backup SHA-256=269a4fa9b247ede5c615bf0e9f1def768a41ea38a45f54f2ce5e7ac258d37c9d
source/restored canary=75|464|276|46|V46|1
repository/app-context smoke=PASS/PASS
restore negatives=7/7 REJECTED
PG17=REJECTED before migration
JAR local-name/local-method=REJECTED
legal data descriptor=PASS (inherited Review + permanent regression)
```

## 6. Boundary

```text
P5-F002/P5-F003=REMEDIATED_PENDING_INDEPENDENT_REVIEW
three inherited P1=REMEDIATED_PENDING_INDEPENDENT_REVIEW
key P2=REMEDIATED_PENDING_INDEPENDENT_REVIEW
JAR P2=REVIEW_VERIFIED_CLOSABLE / UNCHANGED
P5-F007/P5-F008/P5-F009=OPEN
remote enforcement=NOT_APPLIED / NOT_VERIFIED
platform attestation=DEFERRED
production/LIVE/credential access=NONE
staged/commit/push=0/NONE/NONE
```

下一动作保持`NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-REVIEW`。最终关闭权属于新的独立高风险Review。

<!-- nq-runtime-scan:historical-reference:end -->
