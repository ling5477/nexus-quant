# NQ-JAVA-REVIEW-002 remediation evidence（attempt-01）

```text
task=NQ_DH_JAVA_ENGINEERING_REMAINING_P1_FINAL_REMEDIATION
finding=NQ-JAVA-REVIEW-002
original_finding=MIGRATION_CHECKSUM_CONTRACT_WEAKENED
state=REMEDIATED / PENDING_FINAL_INDEPENDENT_REVIEW
baseline_head=f3d605699f72ffeb9c93c0ca9d308476c1484eec
remote_ci=REMOTE_CI_NOT_RUN
```

## Root cause

既有Java test将working-tree CRLF规范为LF后计算checksum，解决了checkout portability，但它无法区分“Windows checkout表示变化”与“committed Git blob本身已被提交为CRLF或发生其他byte drift”。因此semantic/portable checksum不能单独证明reviewed Git object未变。

## Dual contract

### Working-tree portability

保留现有Java合同：strict UTF-8、BOM拒绝、CRLF→LF、bare CR拒绝、trailing newline保留。新增PowerShell regression进一步覆盖LF/CRLF等价以及BOM、bare CR、content mutation、trailing-newline mutation。

### Exact Git blob identity

新增 `scripts/java-standard/verify-v40-migration-git-blob.ps1`，直接通过Git object database读取：

```text
object=HEAD:backend/nq-infra/src/main/resources/db/migration/V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql
expected_git_blob_sha1=63052fcd7473e1b6e8a8975c1be45679010b01bb
expected_raw_sha256=1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3
expected_bytes=44098
expected_eol=LF / CR_COUNT_0
expected_bom=ABSENT
```

Raw bytes通过redirected process stdout stream读取，不经过PowerShell text pipeline或EOL normalization。现有 Java governance verifier已调用该入口，因此原有唯一Shadow CI job会阻断Git blob drift；无需修改CI workflow。

`.gitattributes`新增migration SQL `text eol=lf`，只负责checkout/commit hygiene，不替代Git object验证。

## Permanent regression

`scripts/java-standard/tests/Test-V40MigrationGitBlobContract.ps1`在disposable Git repo中证明：

```text
committed reviewed LF blob=PASS
committed CRLF blob=V40_GIT_BLOB_CONTRACT_MISMATCH / EXIT_2
committed content mutation=V40_GIT_BLOB_CONTRACT_MISMATCH / EXIT_2
committed trailing-newline mutation=V40_GIT_BLOB_CONTRACT_MISMATCH / EXIT_2
working-tree LF/CRLF canonical hash=EQUAL
BOM/bare CR=FAIL_CLOSED
semantic/trailing-newline mutation=HASH_CHANGED
```

## Validation

```text
Windows PowerShell V40 contract test=PASS
Linux/LF PowerShell V40 contract test=PASS
governance verifier V40_GIT_BLOB_CONTRACT=PASS
V40 target JUnit=4 TESTS / 0 FAILURES / 0 ERRORS / 0 SKIPPED
MigrationContract suite=17 TESTS / 0 FAILURES / 0 ERRORS / 0 SKIPPED
V40 migration tracked diff=0
V40 reviewed Git blob/raw SHA=UNCHANGED
```

## Historical integrity and limitations

`docs/evidence/migration-checksum-reconciliation/attempt-01/**`保持不变；本轮不改V40 migration SQL，不更改Flyway schema/behavior。正式closure留给 `NQ_DH_JAVA_ENGINEERING_FINAL_INDEPENDENT_REVIEW`。
