# NQ-V101-FINAL-CANDIDATE-REFREEZE-AND-TRUST-REBIND

日期：2026-10-25（Asia/Shanghai）。**Decision: PENDING_SIGNING_METADATA_GIT_DELIVERY**。

已从PR #80合并后的固定Commit导出、校验、扫描并构建新的正式来源Self-Hosted候选包；修正后的INSTALL.md进入包目录及外层ZIP。完成新旧OCI图、配置、层、JAR及前端逐文件差异证明，运行字节保持一致。新包的全新安装、登录、受限身份、RLS、V47、重启与健康检查实际通过。新实例过渡清单及签名固定绑定已准备，12项原授权测试通过；尚未交付签名元数据，未请求新人工审批、未产生新签名，不授予最终发行资格。

本任务第十节不授予本轮Commit/Push/Merge权限；上轮授权仅用于文档修复。已精确暂存本轮材料，等待单独Git交付。旧Attestation59432473继续仅对应旧候选及旧隔离实例，不能用于本次新Subject。

## 固定来源与保护

| 项目 | 实际值 |
| --- | --- |
| Version | `1.0.1` |
| Source Commit | `b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae` |
| Source Tree | `3b77583e0e1e0d2b52ccfa0cade1588d0075bc45` |
| PR #80 | MERGED；远端合并身份重新读取确认 |
| PR CI | Run40156903275，9/9 SUCCESS（已交付证据） |
| Post-merge CI | Run40157573104，精确b0433fca…，重新读取确认9/9 SUCCESS |
| Source Manifest SHA256 | `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1` |
| Release Source Hash | `c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c` |
| Exported Files | `2182`，逐文件路径及SHA256另存exported-file-hashes.json |

执行git fetch origin成功，origin/dev与固定b0433fca…一致。随后创建独立受管工作树 `C:/Users/Lingyu/.codex/worktrees/nq-v101-final-refreeze/nexus-quant`，初始为固定Commit的detached HEAD，index Tree与Source Tree一致且工作树干净。没有将旧工作区的未提交内容复制进正式源码。

主工作区 `E:/Project/nexus-quant` 的原两份暂存报告及两份未跟踪报告保留，原暂存Tree `a86ec81cc9914ac1e45504acd233d44bed7025b5` 不变。上轮交付工作树 `C:/Users/Lingyu/.codex/worktrees/nq-transition-authorization/nexus-quant` 也未修改。旧包、ZIP、Transition、Attestation及148份旧资格证据保留原字节；仅复核完整性，没有重跑其全部测试。

本轮新材料目录为 `E:/nq-v101-final-refreeze-20261025-b0433fca/`，旧材料仍在 `E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/`。后者的固定Commit为1dc5ea7e…、Tree为c3232078…，没有被新身份覆盖。

## 不可变源码与正式包

通过git show提取新固定Commit的三个正式源码工具，实际执行coverage、export、verify、release boundary及Secret Scan。2,182个导出文件全部来自Git blob；不是从当前修改工作树打包。Source Manifest文件自身未变，所以实际摘要与旧值相同；Release Source Hash已重新计算，确实变化。

Gitleaks8.18.4使用先前已验证的官方checksum证据，重新校验归档SHA `9ba442ca7dda19885a2e569f43a127289feeb2b5fb0dfa251dafd277f4a0ba91` 与二进制SHA `b3ef977b1b8b3f6921e16759e7cfbd91cf738c4f6aa13e676d8e0ed264103912`。对本次新导出实际扫描，secretFindings=0；精确无密钥PEM协议常量误报仍由原工具合同识别，没有扩大豁免或跳过扫描。

首次boundary调用exit1是任务专用嵌入式Python的._pth限制导致无法导入同目录release_source，未运行到产品边界判断。只修正外部runpy/sys.path调用，保留release-boundary.log，重试release-boundary-r2.log通过；冻结工具与候选源码均未修改。其余已通过导出步骤没有重新执行。实际工具及Bridge解释器使用此前已验证的任务专用PSF Python3.14.9，未改变宿主全局Python。

从新导出中的 `release/build-package.ps1 -Architectures amd64` 构建到全新package-amd64目录，exit0。Distribution为NQ_SELF_HOSTED；Windows11 amd64、DockerDesktop29.9.2、Compose5.6.1、Linux Containers；PostgreSQL16.15，目标Schema47。构建后再次运行正式source verify通过，导出没有被构建或后续签名材料修改污染。

| 制品身份 | 本轮实际值 |
| --- | --- |
| Package Manifest SHA256 | `f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db` |
| Image Archive / images-amd64.tar SHA256 | `7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006` |
| Outer Package ZIP SHA256 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |
| Backend native OCI index | `sha256:3dd3480ad2dd33407c898168f100e6d7e439c274f85abfda8dc059cc8e611b14` |
| Backend config digest | `sha256:45cfb9dce5fd8b287538bd27ab99beece0d996cfbc933dc09bf627d814e4ad21` |
| Frontend native OCI index | `sha256:4b5cea7f2df4811998d5f8e0f3792793b11af8dc529d8b256d1b79d7c98d6ba0` |
| Frontend config digest | `sha256:174ebfacfc90b9977315313bb2cd49895f9c33e1b34e6f1afe88a7c2bfa2bf9f` |
| PostgreSQL native OCI index | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| PostgreSQL config digest | `sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b` |
| Java应用JAR SHA256 | `8993ccf000f6844b9da7623df8177a1121a77ddc9a9411eb00597012010a5a24` |
| Windows运维安装器SHA256 | `15fcafb76abdc816166fa20226a7be7481ccb19d175b5854485b033b8bd03293` |
| Runtime Compose SHA256 | `1bd398f6fd1c531b18c1cefadf7109162df8790197cdcc31e5058007ca9b943e` |
| Runtime配置SHA256 | `e8b029d53bb87ca484542fc82a68003c06b5eb034e243dae707349fd32edac09` |
| Bridge SHA256 | `555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8` |
| Baseline Tool SHA256 | `523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c` |
| 新INSTALL.md SHA256 | `3e7235bb397c07d069c770e61cd63e06bbab9b7d478ce5920ba56ad3dc6fc637` |

21个包文件均记录实际SHA256，生成外层ZIP后逐文件round-trip验证。实际镜像提取JAR无nq-public-demo模块，distribution=NQ_SELF_HOSTED；前端distribution.json一致；47个实际SQL与新固定Git导出的migration摘要一致。Bridge与比较工具同时对照新Commit的Git blob和实际包字节，均匹配。

INSTALL四项要求均由实际导出、包目录及ZIP确认：跨协议必须使用Legacy Bridge；普通update不能完成本次转换；targetArchiveSha256与--target-archive绑定images-amd64.tar；其他实例不得继承原隔离授权。外层ZIP摘要没有写入targetArchiveSha256。

## 新旧候选差异与资格继承

实际比较1dc5ea7e…→b0433fca…共10条Git路径变化：INSTALL和三份证据为RELEASE_DOCUMENTATION；其余为此前PR #79的SIGNING_INFRASTRUCTURE，包括两份公开输入、工作流、helper/测试及.gitignore例外。不能仅根据PR #80的三文件描述推断整个来源区间。

在正式source-manifest选取的2,182个文件中，文件集合完全相同，**唯一内容差异为INSTALL.md**。RELEASE_METADATA的sourceCommit与release-source hash重新计算；RUNTIME_SOURCE、DATABASE_MIGRATION、BUSINESS_LOGIC差异均为0。详细Git路径、分类及实际是否进入发行源见source-differential.json。

| 对比对象 | 实际证明及判定 |
| --- | --- |
| Backend / Frontend OCI索引 | 实际摘要变化；索引中linux/amd64运行平台描述符完全相同，变化在非执行BuildKit attestation描述符；TAR还增加顶层attestation引用 |
| 三个amd64平台清单 | 原始摘要及完整JSON一致；没有只根据tag判断 |
| 三个镜像config与运行层 | config完整JSON及有序layer digest全部相同；归档实际blob内容重新哈希与路径摘要一致 |
| PostgreSQL | 原/新index、amd64平台、config及层均一致；不回退XZ安全更新 |
| Java应用 | 完整JAR SHA256相同，不仅比较版本或class名称 |
| 前端 | 从实际镜像提取的109个文件路径及SHA256全部相同 |
| Flyway | 47个SQL内容相同；没有改历史migration |
| Bridge / 比较工具 / Windows安装器 / Runtime | 完整包文件SHA256相同 |
| JDK策略 | 源码策略与安装脚本相同；运行平台config与层相同，另读新包实际JRE文件确认 |
| 包级差异 | INSTALL.md、images-amd64.tar及其.sha256、package-amd64.env及其.sha256、package-amd64.images.json；外层ZIP自然变化 |

`package-differential.json` 保留新旧完整OCI运行图、各层摘要及前端逐文件摘要。没有忽略新镜像/归档字节变化；已定位为非执行证明元数据和包身份差异。旧BuildKit构建元数据也不冒充本轮所有者签名授权。

```text
PREVIOUS_TECHNICAL_EVIDENCE: REUSABLE_WITH_EXPLICIT_DELTA_PROOF
NEW_BRIDGE_EXECUTION: NOT_RUN
BRIDGE_SMOKE_REQUIRED_BY_RUNTIME_DELTA: NO
```

原Bridge对PG过渡、V34→V47、受限角色/RLS、旧认证/经济事实、正常交接及真实中断恢复的证明，在对应运行字节和合同未变范围内继承。研究、回测、策略发布、Canonical SIM的业务实现与依赖相同，继承既有实际1订单/1成交/6账务分录证据，不重跑。官方v1.0.1备份恢复也按同包执行逻辑及运行层完全相同的范围继承；没有把本轮源v1.0.0实例的restore当成新v1.0.1恢复验收。

继承不覆盖新源码/包身份、自动换用旧签名、新实例实测升级、其他宿主或通用公开升级；这些分别处理。本轮新候选没有执行Bridge，因此不能称“新候选实际升级PASS”。

## 新候选实际运行与JDK缓解

官方新包安装至 `E:/nq-v101-final-refreeze-20261025-b0433fca/fresh-installation`，项目 `nq-v101-refreeze-fresh-20261025`，仅loopback19880/19888。使用包内正式PowerShell安装器，不使用开发Compose或手工migration。

- 三个实际healthy容器的镜像身份与本次新Manifest一致。
- 空库正式维护路径执行47条Flyway，全部success、max47、failed0。
- nq_runtime可登录但无SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/REPLICATION；nq_schema_owner NOLOGIN且无提升权限。业务容器只获得运行口令，不获得维护口令。
- 98个已启用RLS表均FORCE，无启用但未FORCE项；无租户上下文以nq_runtime读取accounts为0。
- 初始admin强制改密；改密前业务API403；正式改密后旧token401，新口令可登录。版本API实际为本次b0433fca…、本次Manifest、1.0.1/V47。Self-Hosted注册和平台入口403。
- UI HTTP200仅证明服务可达，不声称新做完整浏览器业务验收。官方restart后配置SHA保持、新口令及版本可读，doctor通过。
- LIVE、REAL_EXCHANGE、REAL_PROVIDER、REAL_CLIENT实际全部false；由官方stop停止任务容器，数据保留。

实际新运行JRE为Temurin21.0.12.1+1，读取四项真实文件摘要与原链测试镜像完全一致：java=`999c141a…dcbe1`、cacerts=`98d34a90…f3419`、blocked.certs=`2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10`、java.security=`36f2d2e0…8bf41`。默认TrustStore实际为 `/opt/java/openjdk/lib/security/cacerts`，无jssecacerts及覆盖默认TrustStore/java.security.properties的启动配置。

运行平台/层与构建输入未变，既有JDK/JRE的48变体、14完整问题链拒绝和正常链/HTTPS接受证据可继承；未重跑证书行为矩阵。结论仍为 **CVE-2026-91403=VERIFIED_MITIGATION**，不是Adoptium已发布修复二进制或厂商支持声明。既有宿主维护、递归工具安全闭包、完全可复现性等非阻断未知范围保留，不新开全栈扫描。

## 新授权实例与过渡清单

原隔离实例已是v1.0.1，未被重置或冒充v1.0.0。另建 `legacy-installation`，项目 `nq-v101-refreeze-legacy-20261025`，仅loopback19980/19988。以官方v1.0.0 ZIP的发布摘要验证原19个包文件，使用原正式安装器安装并恢复已有V34备份；没有修改旧Release或安装器。旧密码实际登录成功、版本1.0.0/V34，32表基线捕获到enrolled-v100-baseline.json，然后使用原官方stop停止全部任务容器。

该实例用于此次签名授权登记，**未执行新Bridge**。其V34库不是原已升级V47库。实例由项目、绝对NQ_HOME与真实原始runtime.env摘要绑定；没有伪造installation UUID，也没有复制原实例摘要。原始配置和口令仅保留任务本地，不上传。

新清单 `E:/nq-v101-final-refreeze-20261025-b0433fca/transition-v1.json` 的原始字节SHA256：

`bf3aa164f2f26f6b3f65b379ece7e457dffa31b3cd6be6fe5dd1a57bd0865d6e`

| NQ_LEGACY_BRIDGE_V1字段 | 本轮实际绑定 |
| --- | --- |
| format | NQ_LEGACY_BRIDGE_V1 |
| bridgeSourceCommit | b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae |
| bridgeSha256 | 555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8 |
| baselineToolSha256 | 523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c |
| sourceManifestSha256 | c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1（官方v1.0.0包Manifest，不是源码清单） |
| targetManifestSha256 | f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db |
| targetArchiveSha256 | 7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006（images-amd64.tar） |
| targetSourceCommit | b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae |
| architecture | amd64 |
| installationConfigSha256 | 9bef9096d144095298187e3fda6146f2d4f5428acbd18cea2805fe482f062e73 |
| projectName | nq-v101-refreeze-legacy-20261025 |

完整11字段合同未改变，工作树公开副本与本地原文件字节完全相同。当前只证明LOCAL_VERIFIED及真实实例内容绑定，没有把本地摘要冒充独立签名。

未签初稿曾以Windows CRLF写入，git diff --check正确拒绝后停止暂存；初稿按原字节保留于transition-v1-draft-crlf-r1.json。随后规范为UTF-8/LF并重新计算完整摘要，更新helper、公开清单、审批材料及本报告，重新执行12项测试和签名材料Secret Scan；没有在改字节后沿用旧摘要。该过程没有改动冻结发行包或历史签名。

原始历史32表结果继续保留：Original Strict Comparison=DIFFERENT，Missing Original Keys=0，Changed Original Fields=0，Explained Additions=RECORDED；原正式升级后的Post-Restart和Post-Restore均MATCH。它们仍属于旧实际运行，并由未变字节证明支持技术继承；不是本轮新升级测量。新登记源实例只捕获V34基线，未运行升级后比较，不填入历史预期值冒充新测量。

## 签名绑定修改、边界与验证

仅修改三个现有签名材料：

1. `.github/authorizations/transition-v1.json`：本轮实际新清单。
2. `.github/authorizations/target-package-amd64.properties`：本轮新包Manifest原始字节，无秘密。
3. `scripts/release/authorize_legacy_transition.py`：四个固定身份常量及固定项目名更新。

`.github/workflows/nq-release-transition-attest.yml` 本身完全未改。以新固定源码版本helper为基准逐字节验证，除了上述五处身份替换之外内容相同；触发、仓库/actor/ref限制、Environment审批、OIDC、Action固定Commit、Subject/Predicate/精确证书SAN、完整声明及密码学校验规则均未弱化。原测试文件未修改，12项现有授权定向测试全部通过；合成测试不是真实签名。

复用已接受签名实现的独立审查，仅对本轮固定身份数据变化做实际字节、来源、制品、实例及原规则不变检查；没有声称本轮新增独立代码审查或review-of-review。未重新设计签名设施。

远端Environment实读仍为nq-release-transition-signing（25219760021），required reviewer=ling5477（128130348），仅dev，can_admins_bypass=false；没有修改规则、自动批准或dispatch旧工作流。后续真实审批需逐项核对新清单，而非复用旧批准。

三个签名文件在本次coverage audit中均为REVIEW_REQUIRED，且均不在正式export的文件集合中；已进行上述有界身份审阅。它们不是新增正式运行源码。新证据报告也不属于发行输入。正式source verify/build边界通过且内容未变，因此未来签名元数据Commit可与Release Source Commit不同，不须把签名提交再次作为发行来源冻结。

本轮暂存包含上述三个文件及本报告；准确路径、Blob/SHA256、最终Staged Tree、定向Secret Scan与保护检查见外部final-candidate.json及signing-material-secret-scan.json。报告不内嵌自身Tree，避免自引用。

待Git交付及真实人工批准后，仍必须取得新的Attestation ID、Bundle、Signer Workflow Commit和审批记录，并独立验证新Subject字节、仓库/工作流/Environment身份及完整Predicate。原Attestation59432473及其Bundle仅保留旧范围。新签名仍须保持：

```text
scope: ISOLATED_V100_TO_V101_UPGRADE
productReleaseAuthorized: false
liveAuthorized: false
```

审批人所需完整新身份见本轮材料目录MANUAL_APPROVAL_REQUEST.md；当前没有请求审批，不能把既有Environment配置或12项测试当作APPROVED、ATTESTATION_CREATED或验签PASS。

## 剩余阻断与最小后续

唯一当前签名阶段等待项是**签名元数据Git交付**；其后才是真实新人工批准、签发及密码学/完整声明验签。未经明确新Git授权，停在PENDING_SIGNING_METADATA_GIT_DELIVERY。没有其他已证明需要修改冻结发行源码的缺陷；不创建新发行源码Commit，不重建一套候选掩盖差异。

```text
INSTANCE_TRANSITION_TRUST: BLOCKED / NEW_ATTESTATION_NOT_CREATED
SELF_HOSTED_RELEASE_CANDIDATE: BLOCKED / SIGNING_REBIND_PENDING
PRODUCT_RELEASE_AUTHORIZATION: NOT_GRANTED
```

Windows11 amd64的限定技术证明已通过，但整体信任资格尚未完成。Linux/macOS不具原生资格，Public Demo未就绪、LIVE禁用，一般用户升级仍需其自身实例授权。旧长时观察、经济数据及供应链未覆盖范围保持原分类，不重裁历史例外。

## 最终输出

```text
Task: NQ-V101-FINAL-CANDIDATE-REFREEZE-AND-TRUST-REBIND

Source:
Commit: b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae
Tree: 3b77583e0e1e0d2b52ccfa0cade1588d0075bc45
Manifest SHA256: 6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1
Release Source Hash: c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c

Package:
Version: 1.0.1 / NQ_SELF_HOSTED / amd64
Backend Image: sha256:3dd3480ad2dd33407c898168f100e6d7e439c274f85abfda8dc059cc8e611b14
Frontend Image: sha256:4b5cea7f2df4811998d5f8e0f3792793b11af8dc529d8b256d1b79d7c98d6ba0
PostgreSQL Image: sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97
Manifest SHA256: f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db
Image Archive SHA256: 7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006
Outer Package SHA256: 5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb

Documentation:
Legacy Upgrade Instructions: CORRECT / VERIFIED_IN_PACKAGE_AND_ZIP
Target TAR Identity: VERIFIED / images-amd64.tar

Differential Qualification:
Source Changes: EXPORT_INSTALL_MD_ONLY;SIGNING_INFRASTRUCTURE_OUTSIDE_RELEASE_INPUT
Runtime Differences: NONE_IN_AMD64_PLATFORM_CONFIG_LAYERS_JAR_FRONTEND_MIGRATIONS
Inherited Evidence: REUSABLE_WITH_EXPLICIT_DELTA_PROOF
New Targeted Tests: NEW_PACKAGE_FRESH_RUNTIME_PASS;AUTHORIZATION_12_PASS

Installation:
Fresh Install: PASS / NEW_PACKAGE
Bridge Qualification: INHERITED / NEW_CANDIDATE_UPGRADE_NOT_RUN
Database Roles: PASS / NEW_FRESH_RUNTIME
RLS: PASS / NEW_FRESH_RUNTIME
Flyway: NEW_FRESH_V47_PASS;V34_TO_V47_INHERITED
Canonical SIM: PREVIOUS_PASS_INHERITED / NOT_RERUN
Backup Restore: PREVIOUS_V101_PASS_INHERITED / NOT_RERUN

Signing:
Transition SHA256: bf3aa164f2f26f6b3f65b379ece7e457dffa31b3cd6be6fe5dd1a57bd0865d6e
Signer Workflow Commit: NOT_COMMITTED / SIGNING_METADATA_PENDING
Attestation ID: NOT_CREATED_FOR_NEW_SUBJECT
Bundle SHA256: NOT_CREATED_FOR_NEW_SUBJECT
Human Approval: NOT_REQUESTED_FOR_NEW_SUBJECT
Signature Verification: NOT_RUN_FOR_NEW_SUBJECT
Instance Binding: LOCAL_VERIFIED / NEW_ACTUAL_V100_PROJECT_CONFIG / SIGNATURE_PENDING

Qualification:
Windows 11 amd64: TECHNICAL_PASS / RELEASE_TRUST_BLOCKED
General Public Upgrade: NOT_AUTOMATICALLY_QUALIFIED
Linux: NOT_QUALIFIED
macOS: NOT_QUALIFIED
Public Demo: NOT_READY
LIVE: DISABLED

Remaining Release Blockers: SIGNING_METADATA_GIT_DELIVERY;NEW_HUMAN_APPROVAL;NEW_ATTESTATION_AND_COMPLETE_VERIFICATION

Decision: PENDING_SIGNING_METADATA_GIT_DELIVERY
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
