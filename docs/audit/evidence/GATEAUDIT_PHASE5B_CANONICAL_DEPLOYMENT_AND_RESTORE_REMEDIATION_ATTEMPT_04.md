# GateAUDIT Phase5B Activation Concurrency Remediation Attempt-04 Evidence

<!-- nq-runtime-scan:historical-reference:start -->

```text
Evidence classification:
HISTORICAL_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY

Decision:
IMPLEMENTED / PHASE5B_ACTIVATION_CONCURRENCY_REMEDIATION_ATTEMPT_04_COMPLETE /
P0_0 / P1_CONCURRENCY_REMEDIATED / P2_KEY_PROOF_COMPLETE /
PENDING_INDEPENDENT_REVIEW

This evidence does not close P5-F002 or P5-F003 and does not accept Phase5B.
```

## 1. Scope and baseline

Only `ACTIVATION_AUTHORITY_CONCURRENT_FORK` was changed. External admission、CI mandatory enforcement、PostgreSQL contract and JAR verifier were regression-only.

```text
branch=audit/post-gatey-agent-baseline
HEAD=origin/audit/post-gatey-agent-baseline=4c2b393ef0b3806a60cd3240c2e75ba1b350cc87
origin/dev ancestor=YES
staged=0
authority=PASS
Review Attempt-03 fingerprint=88c221735ad8d1b1b7aad17df1e4f9262702a3a54ab06f751bc49eb0a56c9b6c
Attempt-04 functional files=21
Attempt-04 functional fingerprint=a75bbe32c8061edbd21904741d77fae37072d9f8f29fa92f1fac6ceec2a71eb8
```

## 2. Installation-scoped cross-process serialization

- Fixed path=`<installationRoot>/.activation-operation.lock`；caller不能指定lock path。Lock file只做serialization，signed head/journal仍是authority。
- Cross-process primitive=`FileStream(FileShare.None)`；同一installation root排他，不同root相互独立。Linux验证regular/owner/0600/link-count，Windows验证reparse/hard-link identity。
- Acquire顺序=`lock → reread/recover head/journal/pointer → derive generation → PREPARED → pointer → COMPLETED/head → release`。activate、rollback、recover共用同一lock。
- Timeout=`ACTIVATION_OPERATION_LOCK_TIMEOUT`，默认15秒且可配置有限范围。Timeout regression证明head/journal/pointer fingerprint零变化。
- Process termination由OS关闭exclusive handle；30秒holder被终止后下一recover成功，不删除活owner lock file。

## 3. Multi-process proof

Windows真实独立PowerShell processes：

```text
initial generation=1
concurrent activation processes=8
successes=8
failures=0
successful generations=2..9 unique
final generation=9
fork count=0
final pointer=head.currentRelease

activation-vs-rollback=SERIALIZED / generation +2 / pointer consistent
recovery-vs-activation=SERIALIZED / prepared first reconciled / generation +2
lock timeout=REJECTED / zero authority side effects
holder crash=OS lock released / next recovery PASS
```

Windows suite=`66 cases PASS`。

## 4. Linux key and lock proof

Official PowerShell 7.6.2 `.deb` was SHA-256 verified and extracted only under WSL `/tmp`; no system install. Root-owned disposable Git fixture executed the full suite：

```text
Linux suite=75 cases PASS

authority key:
0600 correct owner=PASS
0644/0666/wrong owner/symlink/hard-link/missing/wrong key=REJECTED
failure side effects on head/journal/pointer=0

operation lock:
0600 correct owner=PASS
0644/0666/wrong owner/symlink/hard-link=REJECTED
```

All WSL PowerShell/test runtime directories were deleted after validation.

## 5. PostgreSQL 16 regression

```text
server/pg_dump/pg_restore=16.15
server_version_num=160015
migrations/latest=46/V46
Flyway validate=PASS
pending=0
backup size=793522
backup SHA-256=4640baf3360b1a63414acc6e7b22c67573303ed330cf5e65087ea67f0d1331bd
source/restored canary=75|464|276|46|V46|1
repository/app-context smoke=PASS/PASS
restore negatives=7/7 REJECTED
PG17=REJECTED before migration
```

The official PostgreSQL packages were only extracted in WSL `/tmp`; runtime and clusters were removed.

## 6. Accepted-area regression and boundary

```text
external admission whole-set/TEST_ONLY/caller override=PASS
CI mutations=64/64 REJECTED
Phase5A mutations=40/40 REJECTED
JAR local-name/local-method/CRC/truncation=REJECTED
valid JAR data descriptor=PASS
GateW/GateY/legacy modifications=0
production/LIVE/credential access=NONE

ACTIVATION_AUTHORITY_CONCURRENT_FORK=REMEDIATED_PENDING_INDEPENDENT_REVIEW
EXISTING_ACTIVATION_KEY_OWNER_MODE_NOT_REVALIDATED=PROVEN_PENDING_INDEPENDENT_REVIEW
P5-F002/P5-F003=REMEDIATED_PENDING_INDEPENDENT_REVIEW
P5-F007/P5-F008/P5-F009=OPEN
staged/commit/push=0/NONE/NONE
```

Next action remains `NQ-GATEAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE-REVIEW`.

<!-- nq-runtime-scan:historical-reference:end -->
