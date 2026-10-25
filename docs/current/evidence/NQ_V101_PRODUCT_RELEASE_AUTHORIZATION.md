# NQ-V101-PRODUCT-RELEASE-AUTHORIZATION

日期：2026-10-25（Asia/Shanghai）。**Decision: RELEASE_READY_AWAITING_OWNER_AUTHORIZATION**。

固定 v1.0.1 Self-Hosted 候选的来源、原始包、证据完整性、适用差异证明和真实实例签名均核对通过，未发现本次限定发行范围内仍开放的实际发布阻断。正式支持范围为 Windows 11 amd64 / 已运行 Docker Desktop / Linux Containers；Public Demo 排除，LIVE 禁用。**项目所有者尚未作出本次产品发布决定；没有创建 Tag 或 GitHub Release。**

本轮只新增本报告、[Release 执行方案](NQ_V101_RELEASE_EXECUTION_PLAN.md) 和 [Release Notes 定稿](NQ_V101_RELEASE_NOTES_FINAL.md)。三者属于开发证据，不进入冻结发行源码或包；未开发功能、修改算法、Tenant/RLS、Flyway、签名记录或制品，未重建、重压缩、重新签发或重新运行全量资格。

## 最终候选身份

| 对象 | 本轮核对的固定身份 |
| --- | --- |
| Product / Version / Distribution | NexusQuant Self-Hosted / 1.0.1 / NQ_SELF_HOSTED |
| Release Source Commit / package SOURCE_SHA | `b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae` |
| Release Source Tree | `3b77583e0e1e0d2b52ccfa0cade1588d0075bc45` |
| Source Manifest SHA256 | `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1` |
| Release Source Hash | `c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c` |
| Package Manifest / Release ID | `f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db` |
| images-amd64.tar SHA256 | `7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006` |
| Outer ZIP SHA256 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |
| Backend native OCI index | `sha256:3dd3480ad2dd33407c898168f100e6d7e439c274f85abfda8dc059cc8e611b14` |
| Frontend native OCI index | `sha256:4b5cea7f2df4811998d5f8e0f3792793b11af8dc529d8b256d1b79d7c98d6ba0` |
| PostgreSQL native OCI index | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| Signing Workflow Commit / 当前远端 dev | `99f63020c62a89926fbe79f9322532a37ce3ed1b` |
| Signing Commit Tree | `7866e703a2435559f8c7a7d394f7dd3a08f25833` |
| Planned Snapshot Tree | `d5c3e2b20777440503339d467a84ccf6e968de33` |
| Snapshot Commit / Tag target Commit | NOT_CREATED；后续应为上述精确 Tree 的独立 Snapshot Commit |

后三种 Git 身份不混同：完整发行源码 b0433fca…、签名工作流 99f63020…、尚未创建的独立 Snapshot。包 SOURCE_SHA 永远绑定本次 b0433fca…，不随签名提交或 Tag 改写。

## 发布前远端状态

实际执行 `git fetch origin`，因 GitHub 443 连接失败退出 1；`git ls-remote --tags` 也发生连接 reset，不标记成功。本地主工作区 origin/dev 仍为 b0433fca…，**不能冒充最新远端**。随后使用 GitHub API / 已连接 GitHub 工具及 gh 只读请求补足：

- [PR #81](https://github.com/ling5477/nexus-quant/pull/81) 确为 MERGED，合并于 `2026-10-25T05:12:55Z`；合并对象为 99f63020…，父为 b0433fca… 与 b225a990…。
- 当前远端 dev 实读为 99f63020…。[Post-merge CI 40164710397](https://github.com/ling5477/nexus-quant/actions/runs/40164710397) 为同一 exact head、push、completed/success，9/9 job 全部 success，无失败、跳过或 pending。源码 b0433fca… 的 [CI 40157573104](https://github.com/ling5477/nexus-quant/actions/runs/40157573104) 单独保留，不用新 CI 冒充旧源码 CI。
- b0433fca…→99f63020… 的远端 compare 只有三个签名输入/helper 和一份 refreeze 证据，共 4 路径，均不在 2,182 个发行文件中。不是以 dev 最新提交重做发行来源。
- 本地 `git tag -l v1.0.1` 无结果；远端精确 `refs/tags/v1.0.1` API 为 404；Release API 为 404，`gh release view v1.0.1 --repo ling5477/nexus-quant` 返回 release not found。故本轮观察时 Tag / Release 均 NOT_CREATED。
- 正式包真实存在并通过下节完整摘要核验。后续发布前须重新读取以上状态；同名对象出现即停止核对，不覆盖。

主工作区保持分支 `codex/nq-v101-legacy-upgrade-bridge`、HEAD `129fd8fb53326925e3d69db7a251fe4e24c95a79`。原 2 个暂存报告和 2 个未跟踪报告原字节、index listing 保持，不切换、提交或暂存。另两个既有 worktree 保持原状。

## 实物与既有证据核对

本轮材料根：[E:/nq-v101-product-release-authorization-20261025](E:/nq-v101-product-release-authorization-20261025)。原新候选材料为 [E:/nq-v101-final-refreeze-20261025-b0433fca](E:/nq-v101-final-refreeze-20261025-b0433fca)，原技术执行材料为 [E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e](E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e)。以下路径均在相应材料根下，精确命令、断言和结果可由本轮 `verify_release_preparation.py`、`verified-release-inputs.json`、`source-verification.json` 复核。

本轮使用冻结 b0433fca… Git blob 验证过的原 `release_source.py verify`，读取同一固定 Commit 的 manifest 和 blobs，对原导出 **2,182** 个文件重新检查路径、模式元数据、完整内容及 canonical hash，PASS。没有重新 export 或 build。包目录 **21** 文件集合和摘要、ZIP 完整目录及逐项流式摘要全部一致；完整 TAR 与 ZIP 均重新计算 SHA256。ZIP 为 **320,691,114 bytes**，TAR 为 **328,908,800 bytes**，Manifest 为 **1,168 bytes**。

VERSION、INSTALL、CHANGELOG、README、LICENSE、DISCLAIMER、THIRD_PARTY_NOTICES、installers、runtime、Manifest、images TAR 均核对实际包字节。`release/support-matrix.json` 在冻结源码/Snapshot 中，是 v1.0.0 历史矩阵，按既有 builder 合同不进入 21 文件安装 ZIP；该事实不构成安装资产遗漏，也不作为 v1.0.1 的支持承诺。发行说明明确覆盖其历史身份。

| 门禁 / 能力 | 结果 | 实际证据及适用范围 |
| --- | --- | --- |
| Immutable Source | PASS | 新根 `immutable-source-run.json`、当前 `source-verification.json`；2182 文件、Git Tree 与 Hash 匹配 |
| Official Package | PASS | 新根 `formal-package-identity.json`、本轮 `verified-release-inputs.json`；21 文件、Manifest/TAR/ZIP 全量 MATCH |
| Source / Package Differential | PASS | 新根 `source-differential.json`、`package-differential.json`；发行源仅 INSTALL 内容变；3 个 amd64平台 Manifest/config/运行层相同，非运行 BuildKit attestation 导致 index/TAR变化 |
| Runtime Assets / Installers / Bridge | MATCH | 本轮将包内 runtime、PS/SH、Bridge/比较器与 b0433fca… 实际 Git blob 比对，完整摘要一致 |
| Security | PASS_FOR_VERIFIED_SCOPE | 新根 `source-secret-scan.log` 绑定 c4787a97…，secretFindings=0；JDBC42.7.14；原补丁/JDK证据及下述缓解继承，不声称全工具安全闭包 |
| JDK mitigation | VERIFIED_MITIGATION | 新根 `jdk-mitigation-inheritance.json`；java/cacerts/blocked.certs/java.security 和实际运行层与原证书行为证明一致，无TrustStore覆盖；48变体与14问题链拒绝、正常链接受的原证据保留 |
| Windows Fresh Install | PASS | 新根 `fresh-install-verification.json`，绑定本次Source/Manifest；正式安装器、三个healthy镜像、强制改密/旧token拒绝、新密码/版本、重启及doctor；仅HTTP可达，不冒充新浏览器全流程 |
| Restricted Runtime / RLS | PASS | 同一新装记录；nq_runtime无提升权限，owner NOLOGIN，98个启用RLS表均FORCE，无租户上下文accounts=0；原根 `formal-runtime-inspection.json` 补充升级路径 |
| Flyway | V47 PASS；V34→V47 INHERITED | 新装47条success/failed0；原根 `formal-technical-validation.json` 及原事务，历史V1–V34 checksum/version/success不变 |
| Legacy Bridge Technical | INHERITED_WITH_DELTA_PROOF | 原事务 `20261024T125624000Z-6775b954` COMMITTED；同Bridge、安装器、runtime、运行层和47SQL；本次新候选升级 NOT_RUN |
| PostgreSQL Image Transition | PASS / INHERITED | 原事务 `image-transition.json`；新旧PG index/config/运行层相同，XZ安全更新保留 |
| Canonical SIM / 核心业务 | PASS / INHERITED | 原根 `formal-product.json`、`formal-technical-validation.json`、`formal-controlled-additions-proof.json`；canonicalAccountId4，1 Order/1 Trade/6 Ledger Entries/1 Position、Risk ALLOW、无重复经济身份 |
| Backup Restore | PASS / INHERITED | 原正式新版安装器备份 `20261024T130325352Z-7b35d4a5`；恢复后32表MATCH、备份后额外研究事实消失，durable facts SHA保持 |
| Failure Recovery | PASS / INHERITED | 原事务 `20261024T125433000Z-b44fae6b` 在V47真实中断，已知事务recover到V34；`formal-interruption.json` / `formal-known-recovery.json`；不重跑全部注入 |
| 原证据完整性 | PASS | 本轮实际重哈希原 `evidence-hashes.json` 的148份文件，148/148 MATCH，未重新运行对应资格 |
| Transition Attestation | VERIFIED | 下节真实下载Bundle、重新密码学验签及完整Predicate/Subject/实例绑定 |

新旧 JAR SHA256 均为 `8993ccf000f6844b9da7623df8177a1121a77ddc9a9411eb00597012010a5a24`，109 个实际前端文件逐项相等；三个运行平台配置和有序层摘要一致；47 SQL、Bridge、安装器及 runtime 未变。此为资格复用的确定性依据，不是只读取报告中的 PASS。原148份技术文件完整性已重算；本轮没有重做 Maven、682前端测试、研究/回测、SIM、Bridge、32表运行比较、数据库故障矩阵、全栈扫描或历史 GateAUDIT。

原始经济事实严格比较继续为 DIFFERENT，旧键缺失0、旧业务字段变化0；新增观测和定向产品事实已逐行证明。原Bridge事务内、重启后、恢复后为MATCH，各比较对象分开保留，未把新增权益观测删除或重裁为虚假全量MATCH。

## 实际签名授权

本轮通过 GitHub artifact **12596089690** 下载成功Run **40165347783 / attempt1** 的实际材料；签名记录指向 [Attestation 59600294](https://github.com/ling5477/nexus-quant/attestations/59600294)。没有重新生成签名。原样Bundle摘要为 `387a801e4379e8f0a1f1d07855d990d73a5c9edd0f346ac2d1810046be925422`，Transition摘要为 `bf3aa164f2f26f6b3f65b379ece7e457dffa31b3cd6be6fe5dd1a57bd0865d6e`，均与授权对象一致。

| 检查 | 本轮结果 |
| --- | --- |
| Human Approval | APPROVED；远端approvals实读ling5477 / ID128130348，环境25219760021 |
| Workflow | SUCCESS；workflow_dispatch、exact signer Commit99f63020…、completed/success |
| Environment | nq-release-transition-signing；required owner reviewer，can_admins_bypass=false；保留现有dev限制，不修改配置 |
| Attestation | CREATED / 59600294；实际artifact signing-record与Bundle身份匹配 |
| DSSE Signature | VALID；`gh attestation verify` 指定实际Bundle、repo、predicate、signer/source digest、dev ref和OIDC issuer重新验证exit0 |
| Signer Identity | MATCH；证书SAN严格等于 `https://github.com/ling5477/nexus-quant/.github/workflows/nq-release-transition-attest.yml@refs/heads/dev` |
| Transition Subject | MATCH；仅transition-v1.json及bf3aa164…摘要 |
| Complete Predicate | MATCH；CLI密码学验证后的完整statement predicate与实际authorization.json逐字段相等 |
| Instance Binding | MATCH；真实legacy-installation配置摘要9bef9096…、project=nq-v101-refreeze-legacy-20261025、amd64、原/目标Manifest与目标TAR均一致；不输出原始秘密配置 |

本轮密码学输出保留于材料根 `signature-verification.json/.stderr`，远端run、审批、CI及jobs分别另存。签名scope保持：

```text
scope: ISOLATED_V100_TO_V101_UPGRADE
validationMode: OWNER_AUTHORIZED_DIGEST_ATTESTATION
productReleaseAuthorized: false
liveAuthorized: false
```

**签名仅证明固定隔离实例的升级授权，不是正式产品发布许可，不是 GitHub runner 构建本地包的 provenance。** 新登记实例尚未运行Bridge，技术资格通过未变运行字节差异证明继承，不能把人工签发或验签当作新的实际升级PASS。其他用户不得复用该签名。

## 支持范围与发现判定

| 范围 | 本次正式声明 |
| --- | --- |
| Windows 11 amd64 / Docker Desktop Linux Containers | QUALIFIED_FOR_VERIFIED_SCOPE |
| Fresh Installation | PASS / 本固定新候选的定向实测 |
| Legacy Upgrade | INHERITED_WITH_DELTA_PROOF；执行仍需自身实例可信授权及明确确认 |
| General Public Upgrade | NOT_AUTOMATICALLY_AUTHORIZED；不承诺通用实例签发服务已提供 |
| Linux / macOS / ARM64 | NOT_QUALIFIED |
| Public Demo | NOT_READY / EXCLUDED |
| LIVE / 真实资金 | DISABLED / NOT_AUTHORIZED |

只使用本任务允许的四种发布发现分类：

| 分类 | 判定 |
| --- | --- |
| RELEASE_BLOCKER | 0开放。原包INSTALL的普通update与TAR/ZIP错误已由PR #80修正，并进入本次不可变导出/ZIP（INSTALL SHA3e7235bb…）；原信任等待已由PR #81、真实新审批/签发/完整验签解除。没有摘要不符、不可追溯、Windows缺资格、核心业务/RLS/恢复失效或缺JDK缓解证据 |
| DOCUMENTED_LIMITATION | 平台限定、Public Demo未就绪、LIVE关闭、普通用户升级需独立授权且服务不自动提供、经济数据来源与已解释新增观测、宿主/递归工具/SBOM/完全可复现性未覆盖边界；均已写入最终说明，不扩大为PASS |
| HISTORICAL_EXCEPTION | C4原NOT_ACCEPTED/EVIDENCE_GAPS及已接受UI证据例外/重跑豁免保留；旧FINAL_RELEASE_DECISION、FORMAL_BRIDGE、SIGNED_TRANSITION、REFREEZE报告各自BLOCKED/PENDING保留其原候选/时点，不冒充当前结论 |
| NON_BLOCKING | 原生Git传输失败由精确API及固定本地Git对象核对补足；本地origin/dev陈旧明确标出；来源矩阵/README/CHANGELOG历史措辞由Release Notes澄清；最终provenance在实际Snapshot/Tag产生后按计划生成 |

仓库 STATUS 机器区块仍记录旧V1发布准备时点，本轮不改写阶段 authority，不用其旧BLOCKED推导本候选失败，也不从本报告取得发布执行权限。后续执行仍以真实所有者新授权及精确对象检查为前提。

本次没有新的实现或控制规则变更；原独立候选审查在未变范围内复用，不重开全审计或review-of-review。P0/P1/P2计数是本次发布准备范围内的新开放finding，不表示整个项目不存在未知漏洞。

## 精确产品发布授权申请

申请项目所有者在以下固定对象及范围上，明确选择 **`APPROVE_PRODUCT_RELEASE`** 或 **`REJECT_PRODUCT_RELEASE`**：

```text
Product: NexusQuant Self-Hosted
Version: 1.0.1
Release Source: b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae
Release Source Tree: 3b77583e0e1e0d2b52ccfa0cade1588d0075bc45
Release Source Hash: c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c
Package Manifest: f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db
Image Archive: 7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006
Outer Package: 5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb
Supported Platform: Windows 11 amd64 / Docker Desktop / Linux Containers
Release Type: Self-Hosted
Public Demo: EXCLUDED
LIVE: DISABLED
Tag Target: FUTURE_INDEPENDENT_SNAPSHOT_WITH_TREE_d5c3e2b20777440503339d467a84ccf6e968de33
Release Notes: NQ_V101_RELEASE_NOTES_FINAL.md
Execution Plan: NQ_V101_RELEASE_EXECUTION_PLAN.md
```

批准仅对本固定对象、限定支持声明和上述操作计划有效，不改变实例级Bridge授权，不授予LIVE/真实资金/公网Demo权限。只有所有者明确批准后，才可启动后续正式交付任务，按该任务的明确动作授权创建Snapshot/Tag、Draft、上传资产并Publish。当前没有批准，不因用户未回复或先前Environment审批推定批准。

## 最终输出

```text
Task: NQ-V101-PRODUCT-RELEASE-AUTHORIZATION

Release:
Version: 1.0.1 / SELF_HOSTED
Release Source Commit: b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae
Release Source Tree: 3b77583e0e1e0d2b52ccfa0cade1588d0075bc45
Release Source Hash: c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c

Package:
Backend Image: sha256:3dd3480ad2dd33407c898168f100e6d7e439c274f85abfda8dc059cc8e611b14
Frontend Image: sha256:4b5cea7f2df4811998d5f8e0f3792793b11af8dc529d8b256d1b79d7c98d6ba0
PostgreSQL Image: sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97
Manifest SHA256: f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db
Image Archive SHA256: 7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006
Outer Package SHA256: 5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb

Evidence:
Immutable Source: PASS / 2182_FILES_AND_FIXED_GIT_BLOBS
Official Package: PASS / 21_FILES_AND_COMPLETE_ZIP_TAR
Security: VERIFIED_SCOPE_PASS / CVE-2026-91403_VERIFIED_MITIGATION / COVERAGE_LIMITS_RETAINED
Fresh Install: PASS / NEW_CANDIDATE_TARGETED_EXECUTION_EVIDENCE_REUSED
Legacy Bridge: INHERITED_WITH_DELTA_PROOF / NEW_CANDIDATE_UPGRADE_NOT_RUN
Restricted Runtime: PASS
RLS: PASS / 98_FORCED_TABLES
Flyway: V47 / FRESH_PASS / UPGRADE_INHERITED
Canonical SIM: PASS / INHERITED_WITH_DELTA_PROOF
Backup Restore: PASS / INHERITED_WITH_DELTA_PROOF
Failure Recovery: PASS / INHERITED_WITH_DELTA_PROOF

Trust:
Attestation ID: 59600294
Signing Run: 40165347783
Signer Identity: EXACT_WORKFLOW_SAN_MATCH / SIGNER_COMMIT_99f63020c62a89926fbe79f9322532a37ce3ed1b
Signature Verification: VALID / ACTUAL_BUNDLE_CRYPTOGRAPHIC_AND_COMPLETE_PREDICATE_MATCH
Transition Authorization: APPROVED / FIXED_ISOLATED_INSTANCE_ONLY
Product Release Authorization: NOT_GRANTED / OWNER_DECISION_PENDING

Scope:
Windows 11 amd64: QUALIFIED_FOR_VERIFIED_SCOPE
Fresh Installation: QUALIFIED_TARGETED_SCOPE
Legacy Upgrade: TECHNICAL_INHERITANCE / OWN_INSTANCE_AUTHORIZATION_REQUIRED
General Public Upgrade: NOT_AUTOMATICALLY_AUTHORIZED / NO_GENERAL_SIGNING_SERVICE_COMMITMENT
Linux: NOT_QUALIFIED
macOS: NOT_QUALIFIED
Public Demo: NOT_READY / EXCLUDED
LIVE: DISABLED

Release Preparation:
Release Notes: FINAL / PENDING_OWNER_PUBLICATION_AUTHORIZATION
Release Assets: ORIGINAL_ZIP_VERIFIED / CHECKSUM_PREPARED / FINAL_MAPPING_PENDING_REAL_SNAPSHOT_TAG
Source Snapshot Plan: 2182_FILES_PLUS_METADATA / EXPECTED_TREE_d5c3e2b20777440503339d467a84ccf6e968de33
Tag Target Plan: FUTURE_VERIFIED_INDEPENDENT_SNAPSHOT_COMMIT / NOT_SIGNING_MERGE
Publication Plan: READY / OWNER_THEN_SNAPSHOT_TAG_DRAFT_UPLOAD_VERIFY_PUBLISH_VERIFY
Rollback Plan: READY / NO_FORCE_TAG_OR_SILENT_ASSET_REPLACEMENT / V100_UNCHANGED

Findings:
P0: 0_NEW_OPEN_WITHIN_SCOPE
P1: 0_NEW_OPEN_WITHIN_SCOPE
P2: 0_NEW_OPEN_WITHIN_SCOPE / DOCUMENTED_LIMITATIONS_RETAINED

Release Blockers: 0_OPEN_TECHNICAL_OR_MISLEADING_RELEASE_CLAIM_BLOCKERS
Release Limitations: PLATFORM;GENERAL_UPGRADE_AUTHORIZATION;PUBLIC_DEMO;LIVE;SECURITY_COVERAGE;ECONOMIC_DATA
Historical Exceptions: C4_ORIGINAL_REVIEW_AND_OWNER_EVIDENCE_EXCEPTION_RETAINED

Decision: RELEASE_READY_AWAITING_OWNER_AUTHORIZATION
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
