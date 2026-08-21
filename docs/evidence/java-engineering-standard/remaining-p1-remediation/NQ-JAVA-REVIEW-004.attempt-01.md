# NQ-JAVA-REVIEW-004 remediation evidence（attempt-01）

```text
task=NQ_DH_JAVA_ENGINEERING_REMAINING_P1_FINAL_REMEDIATION
finding=NQ-JAVA-REVIEW-004
original_finding=EVIDENCE_INTEGRITY / FALSE_QUALITY_PASS
state=REMEDIATED / PENDING_FINAL_INDEPENDENT_REVIEW
baseline_head=f3d605699f72ffeb9c93c0ca9d308476c1484eec
quality_profile=NOT_AVAILABLE
remote_ci=REMOTE_CI_NOT_RUN
```

## Root cause and historical claim handling

Backend root及所有module POM均未声明Maven `quality` profile。历史命令 `mvn -Pquality validate`可在Maven发出“requested profile could not be activated”warning后仍exit 0；旧evidence只依据process exit记录了：

```text
quality_profile=PASS
```

该claim不构成有效quality gate evidence，现由本forward-only文件明确失效。历史文件保持immutable，不回写旧行：

```text
docs/evidence/migration-checksum-reconciliation/attempt-01/validation.txt
sha256=b64247d64e57a6599145d8688bf127b7af319f77e648a0bad2597b9b4f5eb22f
historical_quality_claim=INVALIDATED / PRESERVED
```

本轮没有新增POM profile、dependency或quality plugin来掩盖旧claim。

## Canonical quality validation semantics

新增 `scripts/java-standard/invoke-maven-profile-validation.ps1`：

1. 先以namespace-independent XML解析确认profile真实存在；
2. profile缺失时返回 `QUALITY_PROFILE_NOT_FOUND / EXIT_2`，不调用Maven；
3. profile存在后才调用Maven；
4. 即使Maven exit 0，只要输出missing-profile warning仍返回hard failure；
5. `-StatusOnly`对当前仓库明确输出 `QUALITY_PROFILE=NOT_AVAILABLE`，不冒充validation PASS。

Java governance verifier也从实际POM读取profile事实，当前输出固定为：

```text
QUALITY_PROFILE=NOT_AVAILABLE
```

## Permanent regression

`Test-MavenProfileValidation.ps1`使用disposable POM与cross-platform fake Maven验证：

```text
current backend quality status=NOT_AVAILABLE
existing profile + Maven exit 0=ACCEPTED
missing profile=QUALITY_PROFILE_NOT_FOUND / EXIT_2 / MAVEN_NOT_INVOKED
existing profile + Maven missing-profile warning + exit 0=MAVEN_PROFILE_NOT_FOUND / EXIT_2
```

## Validation

```text
Windows Maven profile validation test=PASS
Linux/LF Maven profile validation test=PASS
Windows governance verifier QUALITY_PROFILE=NOT_AVAILABLE
Linux/LF governance verifier QUALITY_PROFILE=NOT_AVAILABLE
POM profile additions=0
CI workflow changes=0
REMOTE_CI_NOT_RUN
```

## Remaining limitation

本轮不新增canonical quality profile；如果未来真实引入profile，必须另行审查其plugin、rule、scope、CI wiring与失败语义。正式closure留给 `NQ_DH_JAVA_ENGINEERING_FINAL_INDEPENDENT_REVIEW`。
