# NQ-GATEY-4-REVIEWED-PATH-SET-FORWARD-ADDENDUM

> attempt：`01`
> 性质：forward-only 审查证据补充；不改写既有 GateY-4 实现或安全审查证据。
> 边界：本文件不接受 GateY-4、不初始化 GateY-5、不解除 kill switch、不启用 LIVE。

```text
review_schema=1
gate=GateY-4
baseline_commit=6b5d918c0f90925fce5a6ab4862afbe4cc1522ef
canonical_implementation_commit=44ac9b3c014bcd7a46499c4180053742e64c7709
superseded_parallel_commit=e4d1ab5ecdd69389b06b8dd41314d6131a6e3cbc
target_head=a280e8ba311c9950d273a88d3e92732eb5e592c2
target_tree=77b4571b124ea58733623ad8e5367d0101a39065
reviewed_path_count=44
reviewed_path_set_sha256=6b44210616c772f400f17f3d2703b9fd213d979675adaf5ecf7c3c4d9a74086e
reviewed_blob_manifest_sha256=b3ad060d34011947a72474bcf9670a0a46e685a0fefc652500bf3d2ec883613f
p0=0
p1=0
review_decision=ACCEPTED_FOR_FORWARD_ANCESTRY_RECONCILIATION
```

## 1. 结论与适用范围

- `baseline_commit..target_head` 的实际变更集合与 handoff 预期集合完全相等：`expected=44`、`actual=44`、`missing=0`、
  `unexpected=0`。
- `canonical_implementation_commit`（Candidate A）的 tree 与 `target_head` 的 tree 完全相同，44 个目标 blob 全部一致；Candidate
  A 是本次独立审查所对应的 canonical implementation。
- `superseded_parallel_commit`（Candidate B）只覆盖其中 26 个路径；这 26 个共同路径的目标 blob 全部一致，但缺少 18
  个已审查路径，因此只记录为 incomplete parallel commit，不作为完整实现证据。
- merge commit 的第一父为 Candidate A、第二父为 Candidate B；merge tree 与 Candidate A tree 相同，合并解析选择 Candidate A
  的完整实现集合。
- 独立逐路径审查未发现 P0/P1。既有 P2 风险继续保留：生产锁窗口尚未实测；短生命周期 JDBC/Jackson 解密 `String` 残留仍受
  infra/JIT 边界与 mutable material 清理约束。
- 本补充只冻结审查范围和目标 blob，不改变 `docs/current/STATUS.md` 的 authority。GateY-4 仍为
  `REVIEW_ACCEPTED|READY_TO_COMMIT`，GateY-3 仍是 accepted batch。

## 2. 摘要序列化与校验规则

两个 SHA-256 均使用以下确定性序列化：路径按 UTF-16 ordinal comparer 对 Git path 做升序排列；内容编码为 UTF-8（无
BOM）；行结束符统一为 LF；最后一行后保留且仅保留一个 LF。

- path-set 输入：每行一个 path。
- blob-manifest 输入：每行 `<path><TAB><target_blob_sha>`。

## 3. 完整 reviewed path set（44）

```text
README.md
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfiguration.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfigurationTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationEnvelope.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicy.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/PrivateReadonlyDiagnosticEndpointContract.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapability.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicy.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialReference.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionService.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentEvidence.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerOperationSafetyGate.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StableArtifactSnapshotResult.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationResult.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifier.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactConsumer.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReader.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicyTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicyTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionServiceTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifierTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReaderTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeService.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeObservation.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeRequest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactory.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeServiceTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactoryTest.java
docs/current/README.md
docs/current/ROADMAP.md
docs/current/STATUS.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-IMPLEMENTATION.attempt-01.md
docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW.attempt-01.md
docs/current/evidence/gate-y/README.md
scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1
scripts/gatey/verify-gatey4-worker-deployment-boundary.ps1
```

## 4. 完整 target blob manifest（44）

```text
README.md	fcf011f96a176dcda184124334d6415fa85cc8c6
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java	f5656d676c7ac07f861c52b429ce3fefd2b37de9
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java	6b9d414d08178147f5e04787e9d3e86ea515e756
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java	949da49362b3dee8a46ede28497fd6dbeee30832
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfiguration.java	c2fe6ba4a6c7cd993e48f31d6353f65f9c5dbdbb
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java	8c82d661ed0554bbc1a07fd09df07b4d21549e75
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java	c62d34b201f52bc4fb391f8dd0ae9eadc10c37a3
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfigurationTest.java	9253b0f7c8640ea4cf5b2d4a1232ea0d91f4bfae
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationEnvelope.java	1a5e8400cefb702d98d90001b2e00908aad435e4
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicy.java	0c7f5b954a983a4b3e533a0038f5ece7f414b8a5
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/PrivateReadonlyDiagnosticEndpointContract.java	3f34e45cef9f9d1d18aad48506acd948549c5fd9
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapability.java	79f5b2bcfb0d4432dabfbb91311ec3e7437fceda
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicy.java	61afd5227d22de20b9e8b91b9c93d77030d64e46
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialReference.java	b4124db90a7d1a136df7d10bc21aba1689827ff2
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionService.java	dd55bea588b91ec7a52d6934de4b93de33af8493
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentEvidence.java	d95867e6cff25d64fcbd18e6dd5baeaa87aa1fa0
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerOperationSafetyGate.java	8322706ec9149a9e80bcd8dd3f25577bb550401e
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StableArtifactSnapshotResult.java	6b67a2321decc59a8cfd7e8d6582fb42a08679b1
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationResult.java	72287e94739b13280e235d34cb1812889317e5be
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifier.java	de3919878fc50948199e25b37582c7348f15d32c
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactConsumer.java	de4d70c9196ba9e43ed3bcf333e10eb170124b95
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReader.java	363bbe07385e226c0b24070aeb7151800f75fadb
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicyTest.java	0316aad93f33fba08c1c0700354a98a4a9cdfe4c
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicyTest.java	2d74b984fd4d9a16c7cf1d5a3753a50e9350ab98
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionServiceTest.java	f26b88f9527b03e8cdbe9242380ffa12c3374813
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifierTest.java	61fb3bd43f599983ef55c2d0f4e83999f8cd4dc0
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReaderTest.java	36258e2412a03197f5e80d92e3a84d352b5c0fc9
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java	5bf23214ca8681dd11769a5b96b725a8c45e7816
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeService.java	9eaf863e8f804340e9ec9973d674fa502de44aab
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeObservation.java	b0b3ac1a5e169186510cdb1c7f8bc4df368f2e57
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeRequest.java	545d0d1804804ff2203366cff403393b39c900c7
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactory.java	a2af5ca5803b8de880df2d1300717aed9ad396a3
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeServiceTest.java	440aa52eafa32c62140edb8d0cc1ab19238d0eaf
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactoryTest.java	fdc930da73719e7fe444f80882ae8fcbd2d79cda
docs/current/README.md	80c06f0ce36456b6d0d204a9ad36c7fe99018170
docs/current/ROADMAP.md	f782765c57595a6b2d3d78114670a772276a937e
docs/current/STATUS.md	e266d2975e276df82cc2395ba2c586cfbb271601
docs/current/TESTING.md	d4d8320a7b034fbcbe01e7c8527ae2ee76a567ef
docs/current/WORKLOG.md	1f6c2c3c8a1e726449b08c41e9847537f57f2e47
docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-IMPLEMENTATION.attempt-01.md	61a10571d30a8b0c94b0ec6619a21740709336fb
docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW.attempt-01.md	ee930cc8e138cd758832a63ccd522680433f9d32
docs/current/evidence/gate-y/README.md	5f221fc7835103c6c8a935c7a4fb4024d7b46dfa
scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1	78a0d70656387fa43c4f3f53e7612f9dd68962c7
scripts/gatey/verify-gatey4-worker-deployment-boundary.ps1	f6bc0367cf3c2f9bb45e721271112c3eeaf7f39c
```

## 5. 独立逐路径 disposition

`Evidence` 中的 `D` 表示独立阅读 baseline→target diff；`S` 表示读取 target source；`T` 表示读取或执行关联测试；`E` 表示核对
target-bound evidence/CI。新增文件的 baseline blob 记为 `NEW`。

|  # | Path                                                                                                                                                   | Change | Baseline blob                              | Target blob                                | Category        | Purpose                         | Evidence | Disposition                                             |
|---:|--------------------------------------------------------------------------------------------------------------------------------------------------------|--------|--------------------------------------------|--------------------------------------------|-----------------|---------------------------------|----------|---------------------------------------------------------|
|  1 | `README.md`                                                                                                                                            | M      | `a93054e1b84b40cfd3e062ffbb49050ad965e83e` | `fcf011f96a176dcda184124334d6415fa85cc8c6` | Docs            | current 入口同步                | D,E      | REVIEWED_ACCEPTED_DOC                                   |
|  2 | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java`                                                | M      | `5c3b3f8528f2d0dcc9f6e3b0c246d0771fc83332` | `f5656d676c7ac07f861c52b429ce3fefd2b37de9` | App config      | adapter 装配边界                | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
|  3 | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java`                                          | M      | `b6874e91c2f3bb7f3ddf2249157a2050e760576a` | `6b9d414d08178147f5e04787e9d3e86ea515e756` | App config      | account 模块装配                | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
|  4 | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java`                          | M      | `6ec112d10b7f3245fd48d7448a7b994de377d5dd` | `949da49362b3dee8a46ede28497fd6dbeee30832` | App config      | private-readonly 诊断装配       | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
|  5 | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfiguration.java`                           | A      | `NEW`                                      | `c2fe6ba4a6c7cd993e48f31d6353f65f9c5dbdbb` | App config      | worker deployment boundary 装配 | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
|  6 | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java`                      | M      | `30b997803f0b0fe50507b64689af34cf0772df64` | `8c82d661ed0554bbc1a07fd09df07b4d21549e75` | Test            | 诊断配置回归                    | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
|  7 | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java`                  | M      | `cc3c1c52ab5e4c7c65a45f56240400fb337532e9` | `c62d34b201f52bc4fb391f8dd0ae9eadc10c37a3` | Test            | Spring context probe            | D        | REVIEWED_ACCEPTED_TEST / NON_BEHAVIORAL_WHITESPACE_ONLY |
|  8 | `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/WorkerDeploymentBoundaryConfigurationTest.java`                       | A      | `NEW`                                      | `9253b0f7c8640ea4cf5b2d4a1232ea0d91f4bfae` | Test            | worker boundary 配置回归        | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
|  9 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationEnvelope.java`                                  | A      | `NEW`                                      | `1a5e8400cefb702d98d90001b2e00908aad435e4` | Core            | kill propagation envelope       | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 10 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicy.java`                                    | A      | `NEW`                                      | `0c7f5b954a983a4b3e533a0038f5ece7f414b8a5` | Core            | kill propagation policy         | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 11 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/PrivateReadonlyDiagnosticEndpointContract.java`                      | A      | `NEW`                                      | `3f34e45cef9f9d1d18aad48506acd948549c5fd9` | Core contract   | endpoint allowlist contract     | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 12 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapability.java`                                     | A      | `NEW`                                      | `79f5b2bcfb0d4432dabfbb91311ec3e7437fceda` | Core contract   | credential capability enum      | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 13 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicy.java`                               | A      | `NEW`                                      | `61afd5227d22de20b9e8b91b9c93d77030d64e46` | Core policy     | capability deny-by-default      | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 14 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialReference.java`                                      | A      | `NEW`                                      | `b4124db90a7d1a136df7d10bc21aba1689827ff2` | Core contract   | opaque credential reference     | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 15 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionService.java`                               | A      | `NEW`                                      | `dd55bea588b91ec7a52d6934de4b93de33af8493` | Core service    | deployment admission 编排       | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 16 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentEvidence.java`                                       | A      | `NEW`                                      | `d95867e6cff25d64fcbd18e6dd5baeaa87aa1fa0` | Core evidence   | admission evidence model        | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 17 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerOperationSafetyGate.java`                                      | A      | `NEW`                                      | `8322706ec9149a9e80bcd8dd3f25577bb550401e` | Core safety     | mutation/credential deny gate   | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 18 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StableArtifactSnapshotResult.java`                        | A      | `NEW`                                      | `6b67a2321decc59a8cfd7e8d6582fb42a08679b1` | Core artifact   | stable handle result            | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 19 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationResult.java`                  | M      | `8b5b379a976ea72d7d8661644d53de2c421d616e` | `72287e94739b13280e235d34cb1812889317e5be` | Core artifact   | verification result 扩展        | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 20 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifier.java`                 | M      | `f17c1e708a3e562daa09fb1f4f7ab8904b1b1e46` | `de3919878fc50948199e25b37582c7348f15d32c` | Core artifact   | trusted-root verifier           | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 21 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactConsumer.java`                | A      | `NEW`                                      | `de4d70c9196ba9e43ed3bcf333e10eb170124b95` | Core artifact   | verified-open consumer          | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 22 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReader.java`                  | A      | `NEW`                                      | `363bbe07385e226c0b24070aeb7151800f75fadb` | Core artifact   | stable handle reader            | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 23 | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/KillSwitchPropagationPolicyTest.java`                                | A      | `NEW`                                      | `0316aad93f33fba08c1c0700354a98a4a9cdfe4c` | Test            | kill policy 回归                | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 24 | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/ScopedCredentialCapabilityPolicyTest.java`                           | A      | `NEW`                                      | `2d74b984fd4d9a16c7cf1d5a3753a50e9350ab98` | Test            | credential policy 回归          | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 25 | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/WorkerDeploymentAdmissionServiceTest.java`                           | A      | `NEW`                                      | `f26b88f9527b03e8cdbe9242380ffa12c3374813` | Test            | admission service 回归          | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 26 | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifierTest.java`             | M      | `04110df404f2facf34ef5b9b92802447a9022f01` | `61fb3bd43f599983ef55c2d0f4e83999f8cd4dc0` | Test            | verifier 回归                   | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 27 | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/VerifiedOpenStrategyArtifactReaderTest.java`              | A      | `NEW`                                      | `36258e2412a03197f5e80d92e3a84d352b5c0fc9` | Test            | stable handle 回归              | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 28 | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java`                              | M      | `6ef9966a5aba2b351938f0dab480ed2fd9db94a6` | `5bf23214ca8681dd11769a5b96b725a8c45e7816` | Infra           | scoped credential execution     | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 29 | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeService.java`                            | M      | `c1ab44986b8a0ba5cbe9de2ceb39f18022251e24` | `9eaf863e8f804340e9ec9973d674fa502de44aab` | Infra           | private-readonly probe          | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 30 | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeObservation.java`                     | A      | `NEW`                                      | `b0b3ac1a5e169186510cdb1c7f8bc4df368f2e57` | Infra model     | probe observation               | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 31 | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/ScopedPrivateReadonlyProbeRequest.java`                         | A      | `NEW`                                      | `545d0d1804804ff2203366cff403393b39c900c7` | Infra model     | scoped probe request            | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 32 | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactory.java`     | A      | `NEW`                                      | `a2af5ca5803b8de880df2d1300717aed9ad396a3` | Infra evidence  | endpoint policy evidence        | D,S,T    | REVIEWED_ACCEPTED_CODE                                  |
| 33 | `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeServiceTest.java`                        | M      | `fd050f6b1520d0a80dd76128b6e5fd75aa382aa6` | `440aa52eafa32c62140edb8d0cc1ab19238d0eaf` | Test            | probe service 回归              | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 34 | `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/deployment/infra/okx/OkxPrivateReadonlyEndpointPolicyEvidenceFactoryTest.java` | A      | `NEW`                                      | `fdc930da73719e7fe444f80882ae8fcbd2d79cda` | Test            | endpoint evidence 回归          | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 35 | `docs/current/README.md`                                                                                                                               | M      | `c32e86701e20859baf68cc59dc81eaec82e1cede` | `80c06f0ce36456b6d0d204a9ad36c7fe99018170` | Docs            | current evidence 入口           | D,E      | REVIEWED_ACCEPTED_DOC                                   |
| 36 | `docs/current/ROADMAP.md`                                                                                                                              | M      | `ac6ffeb9198e780fd18810926a06fda467004285` | `f782765c57595a6b2d3d78114670a772276a937e` | Docs            | 允许动作同步                    | D,E      | REVIEWED_ACCEPTED_DOC                                   |
| 37 | `docs/current/STATUS.md`                                                                                                                               | M      | `aa29f61371feeb8e8e38d7a82fb94856c55fc63e` | `e266d2975e276df82cc2395ba2c586cfbb271601` | Authority       | GateY-4 review-ready 状态       | D,E      | REVIEWED_ACCEPTED_AUTHORITY_SNAPSHOT                    |
| 38 | `docs/current/TESTING.md`                                                                                                                              | M      | `be0b16d021d7eef341e18135a39156eec4ba995d` | `d4d8320a7b034fbcbe01e7c8527ae2ee76a567ef` | Evidence ledger | test evidence                   | D,E      | REVIEWED_ACCEPTED_LEDGER                                |
| 39 | `docs/current/WORKLOG.md`                                                                                                                              | M      | `78ad24b5194b8248f67308f31b25f774be8c78f0` | `1f6c2c3c8a1e726449b08c41e9847537f57f2e47` | Evidence ledger | work evidence                   | D,E      | REVIEWED_ACCEPTED_LEDGER                                |
| 40 | `docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-IMPLEMENTATION.attempt-01.md`                     | A      | `NEW`                                      | `61a10571d30a8b0c94b0ec6619a21740709336fb` | Evidence        | implementation report           | D,E      | REVIEWED_ACCEPTED_EVIDENCE                              |
| 41 | `docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW.attempt-01.md`                    | A      | `NEW`                                      | `ee930cc8e138cd758832a63ccd522680433f9d32` | Evidence        | security review report          | D,E      | REVIEWED_ACCEPTED_EVIDENCE                              |
| 42 | `docs/current/evidence/gate-y/README.md`                                                                                                               | M      | `2493dfdec5f536587b808895ca0da5c00d489d47` | `5f221fc7835103c6c8a935c7a4fb4024d7b46dfa` | Docs            | GateY evidence index            | D,E      | REVIEWED_ACCEPTED_DOC                                   |
| 43 | `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1`                                                                                    | A      | `NEW`                                      | `78a0d70656387fa43c4f3f53e7612f9dd68962c7` | Test script     | 6 项 regression runner          | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |
| 44 | `scripts/gatey/verify-gatey4-worker-deployment-boundary.ps1`                                                                                           | A      | `NEW`                                      | `f6bc0367cf3c2f9bb45e721271112c3eeaf7f39c` | Verify script   | static safety verifier          | D,T,E    | REVIEWED_ACCEPTED_TEST                                  |

### whitespace-only path 判定

`OkxPrivateReadOnlyPermissionProbeSpringContextTest.java` 的 raw diff 仅删除 `static class Dependencies {` 后一个空行；使用
`git diff --ignore-all-space --ignore-blank-lines` 无输出。因此它被明确处置为 `NON_BEHAVIORAL_WHITESPACE_ONLY`
，未将其计作功能或安全行为变化。

## 6. Candidate 与 merge 一致性

### Candidate A

- `git diff --name-only baseline..CandidateA`：44。
- 相对 reviewed set：`missing=0`、`unexpected=0`。
- Candidate A tree：`77b4571b124ea58733623ad8e5367d0101a39065`，与 target tree 相同。
- 44 个 Candidate A blob 与 target blob：`mismatch=0`。
- disposition：`REVIEW_CONFORMANT_CANONICAL_IMPLEMENTATION`。

### Candidate B

- `git diff --name-only baseline..CandidateB`：26。
- 与 reviewed set 共同路径：26；共同路径 blob mismatch：0。
- 缺少 reviewed paths：18；unexpected paths：0。
- disposition：`INCOMPLETE_PARALLEL_COMMIT / SUPERSEDED_BY_MERGE_RESOLUTION`。

缺少的 18 个 reviewed paths：

```text
README.md
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountModuleConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfiguration.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyDiagnosticsConfigurationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/StrategyArtifactVerificationResult.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifier.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/artifact/TrustedRootStrategyArtifactVerifierTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeService.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateReadonlyProbeServiceTest.java
docs/current/README.md
docs/current/ROADMAP.md
docs/current/STATUS.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
```

### Merge resolution

- merge parents：`44ac9b3c014bcd7a46499c4180053742e64c7709`、`e4d1ab5ecdd69389b06b8dd41314d6131a6e3cbc`。
- merge tree 与 Candidate A tree 相同。
- disposition：`MERGE_SELECTS_CANDIDATE_A_COMPLETE_REVIEWED_TREE`。

## 7. 测试证据绑定

- exact target-head GitHub Actions run `31671837597`：`completed/success`，10 jobs，非成功 job 为
  0；URL：<https://github.com/ling5477/nexus-quant/actions/runs/31671837597>。
- target-bound security review evidence 记录：Linux stable-handle `14/0/0/0`（reader 11 + verifier 3）；后端全量
  `1450/0/0/40`；ArchUnit `12/12`；GateY-4 script regression `6/6`。
- 本次 addendum 在未修改 target tree 的前提下独立重跑
  `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1`：exit code `0`，`PASS`（通过）。
- 本次未在本地重跑 Linux-only stable-handle 与后端全量测试；其约束来自 target tree 内已冻结证据和 exact-head
  CI。本补充不把未重跑项目表述为本地通过。
- 绑定链：`reviewed path set -> target blob manifest -> target tree -> target head -> exact-head CI`。Candidate A tree 与
  target tree 相同，故既有实现与安全审查证据可被确定性绑定到本次冻结范围。

## 8. 安全边界与 findings

- P0：0。
- P1：0。
- P2：
    - `PRODUCTION_LOCK_WINDOW_NOT_MEASURED=OPEN`；生产锁窗口未实测，不得据此初始化 LIVE 或 deployment acceptance。
    - 短生命周期 JDBC/Jackson 解密 `String` 残留继续受 infra/JIT 边界限制；mutable credential material 仍须清理且不得记录到日志。
- P3：0。
- `kill_switch=ENGAGED`；`live=DISABLED`。
- REAL mutation / `PLACE` / `CANCEL` / transfer / withdraw / worker start / deploy / credential access 均未被本补充启用。
- `REAL_PRIVATE_READONLY_SMOKE=NOT_RUN`；`API_KEY_REQUIRED`；`REMOTE_PERMISSION_FACT_VERIFIED=NOT_RUN`；
  `IP_ALLOWLIST_REMOTELY_VERIFIED=NOT_VERIFIABLE`。
- 未读取 `.env`、key、token、cookie、secret、private key 或 exchange credential。

## 9. Forward-only 处置

- 该 addendum 是新的 descendant evidence；不修改历史实现、安全审查或 merge commit。
- 它只允许后续提交本文件及其索引/ledger 记录，然后等待该新 exact head 的 CI。
- 它不把 GateY-4 从 work batch 提升为 accepted batch，不创建 GateY-5 authority，不修改 `STATUS.md`/`ROADMAP.md`。

最终决定：
`PASS / GATEY_4_REVIEWED_PATH_SET_FORWARD_ADDENDUM_CREATED / EXACT_44_PATH_SCOPE_FROZEN / BLOB_MANIFEST_FROZEN / CANDIDATE_A_REVIEW_CONFORMANT / CANDIDATE_B_INCOMPLETE_PARALLEL_COMMIT / MERGE_SELECTS_CANDIDATE_A / P0_0 / P1_0 / READY_TO_COMMIT_ADDENDUM`。

下一具体动作：`COMMIT_ADDENDUM_AND_WAIT_EXACT_HEAD_CI`。
