# NQ Shadow report proof hash P1 remediation evidence（attempt-02）

```text
task=NQ_DH_SHADOW_REPORT_PROOF_HASH_P1_REMEDIATION
pass=NQ_ONLY
finding=NQ-JAVA-REVIEW-001-R1
finding_state=REMEDIATED / PENDING_INDEPENDENT_REVIEW
candidate_base_head=c10c5f5751e859241ce1ddf335ffab93fb1b5365
attempt_01_report_hash_claim=INVALIDATED_BY_INDEPENDENT_REVIEW
attempt_01_immutable=YES
attempt_01_sha256=8a674e82c1fed1180b665b71f3df60d566aff6de8d61c863cf22f17dd22e560f
report_proof_algorithm=shadow-report-proof-v1
report_proof_sha256=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1
baseline_deterministic_content_sha256=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1
remote_ci=REMOTE_CI_NOT_RUN
```

## 1. Forward-only说明

`NQ-JAVA-REVIEW-001.attempt-01.md` 中的 `29ad994c...` normalized report hash无法由仓库代码独立复现，该 claim已由独立审查否定。attempt-01保持原字节与原 SHA-256，不回写、不声称历史 claim原本正确。本 attempt-02只提供向前修复证据。

## 2. Existing deterministic hash audit

现有 `shadow-baseline.json.deterministic_content_sha256`已经表达稳定 finding projection：

```text
included finding fields = rule_id, path, classification, fingerprint
finding identity = rule_id + path + fingerprint
excluded top-level volatility = generated_at_utc, report_artifact, baseline head, platform/runtime fields
excluded display/derived fields = symbol_or_line, summary, severity, architecture_scope,
                                  is_in_baseline, is_new_code, checker/ruleset/configuration metadata
```

因此本轮不创建第二套近义 baseline hash，不修改 scanner report schema，也不修改 baseline finding projection。`shadow-report-proof-v1`是现有 `deterministic_content_sha256`的可执行 report proof合同。

## 3. Canonical report proof contract

```text
algorithm version = shadow-report-proof-v1
input report schema = 2.0.0 or 3.0.0
required array = violations
required count = current_violation_count (2.0.0) / current_count (3.0.0)
included fields/order = rule_id, path, classification, fingerprint
finding ordering = InvariantCulture(rule_id, path, fingerprint, classification)
                   + ordinal tie-breaker
path = repository-relative, '/' separator, no empty/dot/dot-dot segment
JSON = compact array of ordered four-field objects, no trailing newline
encoding = strict UTF-8 without BOM
input EOL = CRLF normalized to LF; bare CR rejected
digest = SHA-256 lowercase hex
duplicate identity = fail closed
invalid schema/count/field/path/JSON/UTF-8 = fail closed
```

Top-level时间和输出路径不进入 proof；finding四字段任一变化、finding增删都会改变 proof。

## 4. Exact reproduction command

```powershell
pwsh -NoProfile -File scripts/java-standard/invoke-java-shadow-scan.ps1 `
  -OutputPath artifacts/java-shadow/shadow-report.json

pwsh -NoProfile -File scripts/java-standard/get-shadow-report-proof-hash.ps1 `
  -ReportPath artifacts/java-shadow/shadow-report.json
```

Expected stable output：

```text
SHADOW_REPORT_PROOF_RESULT=PASS
REPORT_PROOF_ALGORITHM=shadow-report-proof-v1
REPORT_PROOF_SCHEMA=2.0.0
REPORT_PROOF_FINDING_COUNT=158
REPORT_PROOF_SHA256=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1
```

## 5. Permanent regression

```text
same report twice = SAME_HASH
LF vs CRLF = SAME_HASH
generated_at_utc changed = SAME_HASH
report_artifact changed = SAME_HASH
finding order changed = SAME_HASH
schema 2.0.0 vs 3.0.0 equivalent projection = SAME_HASH
rule_id/path/classification/fingerprint mutation = HASH_CHANGED
finding added/removed = HASH_CHANGED
invalid schema = REPORT_PROOF_INVALID / EXIT_2
duplicate finding identity = REPORT_PROOF_INVALID / EXIT_2
```

永久入口：`scripts/java-standard/tests/Test-ShadowReportProofHash.ps1`。

## 6. Windows validation

```text
runtime=PowerShell 7.6.5 / Windows
canonical_contract_test=PASS / EXIT_0
report_proof_contract_test=PASS / EXIT_0
governance_verifier=PASS / EXIT_0
shadow_run_1=VIOLATION_FOUND / EXIT_0 / 158_FINDINGS
shadow_run_2=VIOLATION_FOUND / EXIT_0 / 158_FINDINGS
raw_report_sha256_run_1=20dd9ecd3122b4c3ea056b344a558d8a2fa3a38bf1074672da48f7601fa597ec
raw_report_sha256_run_2=8bc618691de0325d740d71c9e4ddfbed581126aa785be8f046e613c8ac2af0fa
proof_run_1=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1 / EXIT_0
proof_run_2=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1 / EXIT_0
raw_reports_differ_because_output_path_differs=YES
proof_hash_equal=YES
```

## 7. Linux/LF validation

真实 WSL `/tmp` disposable Git checkout；不是 Windows bind mount checkout。10个 canonical configuration inputs为 `w/lf`，候选 proof脚本也为 LF-only。

```text
runtime=PowerShell 7.6.4 / Linux / VERIFIED_ARCHIVE_SHA256
canonical_contract_test=PASS / EXIT_0
report_proof_contract_test=PASS / EXIT_0
governance_verifier=PASS / EXIT_0
shadow_run_1=VIOLATION_FOUND / EXIT_0 / 158_FINDINGS
shadow_run_2=VIOLATION_FOUND / EXIT_0 / 158_FINDINGS
raw_report_sha256_run_1=d6e7dc5d9bbf1829753fdf52a99c4369479510cbe14aa5541244634526cf304b
raw_report_sha256_run_2=ba96b7fed9a85a2788eee7be5a01fa3c6ea389c0b16511b4d466fb56bc17ab67
proof_run_1=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1 / EXIT_0
proof_run_2=fe0c7d3f117401e13f751005464545b838eb3ca3b508052abbce6dc5adaddcb1 / EXIT_0
windows_linux_proof_hash=EQUAL
```

## 8. Remaining scope

```text
NQ-JAVA-REVIEW-001-R1=REMEDIATED / PENDING_INDEPENDENT_REVIEW
REMOTE_CI_NOT_RUN
NOT_ADDRESSED=NQ-JAVA-REVIEW-002,NQ-JAVA-REVIEW-003,NQ-JAVA-REVIEW-004,
              CROSS-JAVA-REVIEW-001,CROSS-JAVA-REVIEW-002
```

本 attempt不关闭 finding，不声明整个 Java Engineering review通过，不授权 merge。
