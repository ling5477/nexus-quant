# NQ-JAVA-REVIEW-003 remediation evidence（attempt-01）

```text
task=NQ_DH_JAVA_ENGINEERING_REMAINING_P1_FINAL_REMEDIATION
finding=NQ-JAVA-REVIEW-003
original_finding=SONGSHAN_ACTIVE_INPUT
state=REMEDIATED / PENDING_FINAL_INDEPENDENT_REVIEW
baseline_head=f3d605699f72ffeb9c93c0ca9d308476c1484eec
remote_ci=REMOTE_CI_NOT_RUN
```

## Root cause

Songshan mapping虽声明 `SUPERSEDED`，但仍位于current standards root，且被Java governance verifier列入required files并读取；因此上一版mapping仍是Huangshan current checker成功的mandatory dependency。

## Current/history boundary

文件原字节移动到：

```text
docs/standards/java/history/alibaba-songshan-rule-mapping.yaml
```

历史identity保持：

```text
sha256=b246db0a3119b1d10c43b8ca876fcd3c7e6dea7b7b9c3d8563e590f08edaa0aa
git_blob=b3df1f6d1cb4114c230b20e9df1b5b515d94e419
status=SUPERSEDED / HISTORY_ONLY
```

Current verifier不再包含或读取 `alibaba-songshan-rule-mapping.yaml`，active mapping只剩Huangshan 319-rule mapping。`source-history.json`继续保留Songshan→Huangshan lineage metadata，但history mapping本身不是current checker input。

## Integrity contract

新增 `Test-SongshanHistoryBoundary.ps1`，永久验证：

```text
current root Songshan mapping=ABSENT
history Songshan mapping=PRESENT
history content/blob=UNCHANGED
current verifier exact Songshan filename references=0
CURRENT_ACTIVE_SONGSHAN_INPUT_COUNT=0
Huangshan mapping blob=UNCHANGED
Huangshan status=CURRENT_EXTERNAL_REFERENCE
Songshan provenance status=SUPERSEDED
README history pointer=PRESENT
```

## Validation

```text
Windows Songshan history boundary test=PASS
Linux/LF Songshan history boundary test=PASS
Windows governance verifier=PASS
Linux/LF governance verifier=PASS
CURRENT_ACTIVE_SONGSHAN_INPUT_COUNT=0
SONGSHAN_MAPPING_STATUS=HISTORY_ONLY
HUANGSHAN_RULE_COUNT=319
```

## Historical integrity and limitations

Huangshan mapping内容、source provenance、lineage diff与历史Songshan content均未改写。正式closure留给 `NQ_DH_JAVA_ENGINEERING_FINAL_INDEPENDENT_REVIEW`。
