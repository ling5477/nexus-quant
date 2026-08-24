# NQ Shadow canonical binding P1 remediation evidence

```text
task=NQ_DH_SHADOW_CANONICAL_BINDING_P1_REMEDIATION
pass=NQ_ONLY
finding=NQ-JAVA-REVIEW-001
finding_state=REMEDIATED / PENDING_INDEPENDENT_REVIEW
candidate_base_head=53dfa55cc09e842e1567acbab35227262b3d5ddf
previous_pass_claim=INVALIDATED_BY_INDEPENDENT_REVIEW
previous_configuration_hash=04d79fd9e6ea8defdf3b20ad56001ce11ba973f9c7674e511860ca9a41c431e6
configuration_hash_algorithm=git-canonical-v1
candidate_configuration_hash=47f37f2dbdca21df5c5cfc7b6ba14bdf845d20109863906a188b4cc5dd4d34bc
baseline_head=53dfa55cc09e842e1567acbab35227262b3d5ddf
ruleset_version=huangshan-platform-2.0.0
scanner_version=2.0.0-powershell-lexical
remote_ci=REMOTE_CI_NOT_RUN
```

## Root cause and forward-only handling

The previous baseline stored a working-tree/raw-byte-derived configuration hash. The final scanner configuration no longer reproduced that value on either Windows or an LF checkout, while historical attempt evidence still recorded Shadow scan and determinism as PASS. Historical attempt-01/02 evidence is preserved unchanged. This file records the forward-only invalidation and remediation.

## Canonical configuration identity

Inputs, sorted with ordinal comparison by repository-relative `/` path:

1. `docs/standards/java/common-java-engineering-standard.md`
2. `docs/standards/java/java-platform-profile.md`
3. `docs/standards/java/spring-platform-profile.md`
4. `docs/standards/java/architecture-overlay.md`
5. `docs/standards/java/nq-java-domain-overlay.md`
6. `docs/standards/java/alibaba-huangshan-rule-mapping.yaml`
7. `docs/standards/java/java-rule-exceptions.yaml`
8. `docs/standards/java/java-shadow-scope.json`
9. `docs/standards/java/platform-profile.json`
10. `scripts/java-standard/invoke-java-shadow-scan.ps1`

Contract: strict UTF-8; BOM forbidden; checkout CRLF canonicalized to LF; bare CR forbidden; trailing-newline count and all other bytes preserved. SHA-256 input uses versioned binary length framing: magic, algorithm, file count, path-byte-length/path, content-byte-length/content. Empty files are included. No trim, comment removal, semantic reserialization, absolute path, culture-sensitive sort, or filesystem enumeration order is used.

## Validation

```text
canonical_contract_fixtures=PASS
lf_crlf_fixture_hash=a3c2a9e5cf94e2d0570bcf46d84bad751684272c0760ff9b576159fcc89c8172 / EQUAL
content_mutation=PASS / HASH_CHANGED
line_deletion=PASS / HASH_CHANGED
path_mutation=PASS / HASH_CHANGED
content_swap=PASS / HASH_CHANGED
trailing_newline_mutation=PASS / HASH_CHANGED
bom=PASS / CONFIG_INVALID
bare_cr=PASS / CONFIG_INVALID
invalid_utf8=PASS / CONFIG_INVALID

windows_verifier=PASS
windows_shadow_run_1=VIOLATION_FOUND / EXIT_0
windows_shadow_run_2=VIOLATION_FOUND / EXIT_0
windows_normalized_report_sha256=29ad994c71845af8206949de07004d9d46e9893220fc0a668e81079cf4d7cd89 / DETERMINISTIC

linux_runtime=mcr.microsoft.com/powershell:7.5-ubuntu-24.04 / POWERSHELL_7.5.0 / GIT_2.43.0
linux_candidate_transfer=DOCKER_CP / NO_BIND_MOUNT
linux_config_inputs_eol=LF / 10_OF_10
linux_verifier=PASS
linux_shadow_run_1=VIOLATION_FOUND / EXIT_0
linux_shadow_run_2=VIOLATION_FOUND / EXIT_0
linux_report_sha256=29ad994c71845af8206949de07004d9d46e9893220fc0a668e81079cf4d7cd89 / DETERMINISTIC
windows_linux_configuration_hash=EQUAL
windows_linux_normalized_report_hash=EQUAL

baseline_violation_count=158
baseline_existing=144
baseline_ruleset_expansion=14
baseline_new_code=0
finding_set_change=0

baseline_hash_tamper_verifier=BASELINE_CONFIGURATION_HASH_MISMATCH / EXIT_2
baseline_hash_tamper_scanner=BASELINE_CONFIGURATION_HASH_MISMATCH / EXIT_2
violation_found_semantics=REPORT_ONLY / EXIT_0
```

## Remaining limitations

This remediation does not address `NQ-JAVA-REVIEW-002`, `NQ-JAVA-REVIEW-003`, `NQ-JAVA-REVIEW-004`, `CROSS-JAVA-REVIEW-001`, or `CROSS-JAVA-REVIEW-002`. It does not close the finding; independent review remains required.
