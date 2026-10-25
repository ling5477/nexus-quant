# NQ-V101-RELEASE-SOURCE-AND-FORMAL-UPGRADE-QUALIFICATION — 续验

日期：2026-10-24，Asia/Shanghai。**Git 交付完成；正式升级资格 BLOCKED / POSTGRES_CONFIG_DIGEST_CHANGED。**

按用户后续授权，从合并后 dev 原样迁移并交付 manifest 候选，再恢复正式不可变构建和真实升级验证。本报告另存，遵守原四文件不得重新生成的要求；原 `NQ_V101_RELEASE_SOURCE_AND_FORMAL_UPGRADE_QUALIFICATION.md` 保留当时 `PENDING_GIT_DELIVERY` 历史快照，不用它描述本次最新结果。

## Git 交付与候选保护

| 身份 | 本次实际值 |
| --- | --- |
| 原分支 / HEAD | `codex/v101-full-stack-security-patch` / `402dcc729b3ecc81c20a740a64fd9e979b6a01d9`，未改写 |
| 新分支起点 | `bf0875842898890de685666ae4dfab93330e02b8` |
| 原 HEAD 与 dev 的共同 Tree | `342e875072b62ed7d48d9112b44ea2ed1d0ca386` |
| 新分支 | `codex/nq-v101-release-manifest-delivery` |
| 四文件验收 Tree | `9ccdfcc481c848e9e7f8d1bed7095a183113126f`，切换前后完全一致 |
| 四文件 Commit | `033b28acb66e81f5b966b2507c23889a949b0f27`，parent 为上述 bf087584 |
| CI 测试夹具修复 Commit | `885b76ae92f0d3f26cb3d31a15bd2d607d799faf`，只改 `scripts/ci/tests/test_release_schema.py` |
| 最终候选 / 合并后 Tree | `2145e55070afad4fcdfe0227c11e893a81fba19e` |
| PR | [#77](https://github.com/ling5477/nexus-quant/pull/77)，普通 merge |
| 合并后 Source Commit | `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb` |
| PR CI | [40006303222](https://github.com/ling5477/nexus-quant/actions/runs/40006303222)，attempt 1，head `885b76ae92f0d3f26cb3d31a15bd2d607d799faf`，**9/9 SUCCESS** |
| Post-merge CI | [40007589742](https://github.com/ling5477/nexus-quant/actions/runs/40007589742)，attempt 1，push / head `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb`，**9/9 SUCCESS** |

初次 PR CI `40005556129` 的 PostgreSQL job `120592438801` 真实失败：V34 测试夹具复制了当前 V47 manifest，官方生产校验器正确拒绝 `Formal release migration coverage mismatch`。先在本地复现相同失败，再仅修复合成测试夹具，并增加 V34 合同拒绝 V35 清单项的负例。26/26 schema 合同测试通过。未修改校验器、V34 历史合同或原四文件；原29/29 release-source 测试证据仍绑定相同四文件内容。首次失败不被后续成功覆盖。

原四文件在 `033b28ac…`、最终候选、合并提交之间 blob 全部相同。旧未跟踪 `NQ_V101_FULL_STACK_SECURITY_PATCH_CLOSEOUT.md` 未暂存、未修改，SHA-256 保持 `9cd418d744ce0c90257b6bf086c657cd34c282a56be3b98c696f1fcf62fa61e6`。未 cherry-pick、复制/重新生成四文件、改写原分支历史或强制 push。

## 正式不可变源码与构建

实际调用原 `release_source.py coverage → export → verify`、`verify_release_boundary.py`、`scan_release_secrets.py`，全部指定完整已合并提交 `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb`；输出均位于仓库外。工具文件与该提交 Git blob 核对一致，未生成虚假来源元数据、未从工作区复制正式源码。

- 实际 included 文件 **2,180**；required runtime **2,124**、migration **47**，遗漏为零。
- Source manifest SHA-256：`13321160cf0b753fccc9145589d032afba9c9a00b5594d22973a03d840d7a5a5`。
- Release Source SHA-256：`4965fe5fa6f8573d27391dd4216fb203c6917ede7160515664e3b05e137c6668`。
- 正式边界 errors=0；保留11个既有精确业务/兼容例外，没有新增或扩大豁免。
- Gitleaks **8.18.4 windows/amd64**：实际官方 release / asset `173813539`，归档 SHA-256 `9ba442ca7dda19885a2e569f43a127289feeb2b5fb0dfa251dafd277f4a0ba91` 与官方 checksum asset `173813540` 一致；实际二进制 SHA-256 `b3ef977b1b8b3f6921e16759e7cfbd91cf738c4f6aa13e676d8e0ed264103912`。默认规则实际扫描导出树：rawFindings=1、精确协议 delimiter 误报=1、secretFindings=0；historicalAllowlistUsed=false。
- 构建仅调用导出目录内原 `release/build-package.ps1 -Architectures amd64`，实际 backend/frontend Docker build、PG pull、image save 和原 OCI/config/layer 绑定检查成功。构建后原 source verify 再次通过，导出字节未变。
- 实际 JAR 含 V1–V47，47个 SQL SHA-256 与导出的原 Git blob 完全匹配。发行属性为 `NQ_SELF_HOSTED`，无 `nq-public-demo` JAR；实际前端 `distribution.json` 为 `NQ_SELF_HOSTED`，原 Vite build guard 没有被绕过。该制品检查不替代 V47 HTTP API 隔离资格。

| 制品身份 | 实际值 |
| --- | --- |
| Backend OCI Image Digest | `sha256:6b3fc8c00028301e90949bad6e68e1056bc3c260d1d49010f14f397ecd1b1344` |
| Frontend OCI Image Digest | `sha256:f9997564b8039e1e0f16d20f995940cda277babfc34eb3fce2420dfc65bb9057` |
| PostgreSQL OCI Image Digest | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| Schema | V47 |
| Package Manifest SHA-256 | `7d2e42e45f4a18f7e0b7e16253ca1faee078bb96e92a4bfdbe5df0955a1315b3` |
| Images TAR SHA-256 | `4eadf505823423dda89ac107faf69237b560e8c70db62a54ff7ad1a1355092cc` |
| 外层候选 ZIP SHA-256 | `bada1788e4c813cff9a9898950028e567342bb3adcb34f99f007e250f9ca2701` |
| 实际 backend JAR SHA-256 | `8993ccf000f6844b9da7623df8177a1121a77ddc9a9411eb00597012010a5a24` |

镜像身份来自本机原 OCI/Docker archive；未上传 registry。候选 ZIP 只封装原打包器的19文件，逐文件解压流摘要与包目录相同，未重建或改写包内内容。这是保留的本地正式来源候选，不是已发布产品 Release。

宿主、CPython patch、工具完整递归依赖闭包和完整构建可复现性仍未取得资格；没有重开全量安全扫描，也没有将未知自动定为 P1。Gitleaks 的本次实际 Windows 下载路径已有官方摘要证明，未据此宣称所有平台/工具均已验证。`release/support-matrix.json` 中的 v1.0.0 历史资格不能扩展为本次 v1.0.1 资格。

## 正式包的 JDK 缓解行为

构建 JDK 由同一正式 Dockerfile、同一未变导出树的实际 `build` target 取得；最终 JRE 直接使用包内 backend image digest。公开证书/转换器 fixture 来自此前已验证证据 ZIP，源 ZIP SHA-256 `29831f26d6355b84948dbada851fca0b1e930494d4e7c2d7223f7ff22c9a04bc`，本次按文件记录摘要，重新编译并在新镜像真实执行，未复用旧执行结果。

| 项 | Maven 构建 JDK | 最终运行 JRE |
| --- | --- | --- |
| Vendor | Eclipse Adoptium / Temurin | Eclipse Adoptium / Temurin |
| 实际完整版本 | `21.0.12.1+1-LTS` | `21.0.12.1+1-LTS` |
| 实际 OCI Image Digest | `sha256:32cc580154f3dd70b2f702a60c8ea37097d397956439a7c1558365ffe5f7514b` | `sha256:6b3fc8c00028301e90949bad6e68e1056bc3c260d1d49010f14f397ecd1b1344` |
| java.home | `/opt/java/openjdk` | `/opt/java/openjdk` |
| 实际 TrustStore | `/opt/java/openjdk/lib/security/cacerts` | 同左 |
| `javax.net.ssl.trustStore` override | null；无 jssecacerts | 同左 |
| blocked.certs SHA-256 | `2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10` | 同左 |
| 48种受影响证书合法编码 | 48 POLICY_BLOCKED / accepted=0 | 同左 |
| 14条完整受影响链（有效fixture锚） | 14 POLICY_BLOCKED / accepted=0 | 同左 |
| 正常默认 JSSE 链 | ACCEPTED | ACCEPTED |
| Maven Central HTTPS | ACCEPTED / HTTP200 | ACCEPTED / HTTP200 |

两组 unexpected=0。真实根只进入进程内测试 KeyStore，未进入产品 cacerts。默认根路径测试中无法构建信任路径的链不被算成策略阻断证明。正常 TLS 未放宽生产算法或证书检查。本次结论 **CVE-2026-91403 = VERIFIED_MITIGATION**，不是 VENDOR_PATCHED 或 Temurin 厂商支持声明，也不凭版本号判断。

## 官方 v1.0.0 安装、恢复与更新器实际失败

只读重新获取官方 v1.0.0 Release `418603194` 与 provenance：官方 ZIP SHA-256 `37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931`，provenance SHA-256 `48eb8cf2df69a983f32dfc04dca7453250c5e89872f91bad474cb45b264d34b9`。本机原 ZIP、正式包19文件逐一匹配；未从当前源码重建 v1.0.0。

在全新任务安装目录 `E:/nq-v101-formal-qualification-20261024-033b28ac/installation`，使用项目 `nq-v101-formal-20261024`、UI19080/API19088。先证明 Compose project 与 PostgreSQL data mount 指向此新目录，再执行官方 v1.0.0 install、原 backup restore、32表 capture/compare。官方旧包 source 为 `5231266d366680ed5c6c1e38ab84adb33ffcd644`，manifest 为 `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1`。原用户实际 HTTP 登录通过，凭据/令牌未写入普通证据。

| 执行 | 实际结果 |
| --- | --- |
| 原业务备份恢复后，对原始32表基线 | **MATCH**，32表、changes=[] |
| 官方升级前 backup | PASS；manifest `7ba218a9d8ce77e298a7770be6bd5a5432c13482870d7661033b8605c6b60eab`，dump `a1a45926ae05edfeac830a3e3ac7bde979aafa4e17106cce46f5c23953debc65`，配置/旧资产摘要验证通过 |
| 可信目标包内新版 `nexusquant.ps1 -Action check-update` | exit0，Current=1.0.0 / available=1.0.1 / updateAvailable=True / AUTO_UPDATE=OFF |
| 同一正式安装器 `-Action update -ConfirmUpdate` | **exit1**，PG config digest guard 拒绝 |
| 更新失败前后32表 compare | **MATCH**，beforeVersion=afterVersion=1.0.0，changes=[] |
| 失败后运行态 | 三个原 v1.0.0 容器健康；未切换到目标镜像 |
| 数据库 | Flyway V1–V34，34条 success、failed=0；nq_runtime 不存在 |
| 更新事务 | pending-update=false，transactions=0；拒绝发生在任何更新事务或迁移前 |

实际报错来自目标包 `installers/nexusquant.ps1:664`：

```text
Update requires newer version, monotonic schema and unchanged verified PG16 configuration digest
```

| PG 身份 | 官方 v1.0.0 | 新 v1.0.1 候选 |
| --- | --- | --- |
| Config Digest | `sha256:275447c94b11b151decd1f29877965301d5a77032037c46de93d990f739f00a9` | `sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b` |
| Native OCI Image | `sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d` | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| 创建日期（UTC） | `2026-10-06T01:33:18.890712895Z` | `2026-10-21T00:38:09.198430998Z` |
| 实际 postgres --version | 16.15 / Debian 16.15-1.pgdg13+2 | 同左 |

进一步只读比较：RootFS 不同，Config 的入口/命令/环境等字段相同；相同版本字符串不证明相同制品。原打包器实际 pull `postgres:16.15` 得到新的镜像，而正式更新合同要求 PG config digest 保持一致。**这是实际升级兼容性阻断，不是允许忽略的日志告警。** 未修改 manifest、改写摘要、手动迁移、执行 flyway repair、替换旧 schema 或绕过安装器。

本次只证明：**拒绝的升级没有损坏原事实**；并未发生 V34→V47，不能把两个 MATCH 解释为成功升级后的历史数据资格。没有出现需要重分类的 DIFFERENT，本轮也没有删除数据来制造 MATCH。

## 验收边界、停止与保留

| 门禁 | 状态与覆盖范围 |
| --- | --- |
| IMMUTABLE_SOURCE | PASS，实际已合并提交 export/verify |
| RELEASE_MANIFEST | PASS，2180逐文件、V1–V47；已提交来源 |
| SOURCE_SECRET_SCAN | PASS，默认规则实际扫描、无历史allowlist |
| OFFICIAL_PACKAGE_BUILD | PASS，仅正式构建与制品身份；不代表升级/发布资格 |
| JDK_MITIGATION_IN_PACKAGE | PASS / VERIFIED_MITIGATION |
| FORMAL_UPGRADE | **BLOCKED / POSTGRES_CONFIG_DIGEST_CHANGED** |
| HISTORICAL_DATA | **BLOCKED for upgraded V47**；V100 restore与被拒绝尝试的32表为 MATCH |
| DATABASE_ROLE_SPLIT / RLS | BLOCKED / V47升级路径 NOT_RUN |
| RESTORE | 原V100真实恢复 PASS；V47失败后的rollback/restore资格 BLOCKED / NOT_RUN |
| SELF_HOSTED_RUNTIME | 正式制品身份/无Demo JAR验证通过；V47 HTTP启动、注册/平台API拒绝未验证 |
| RELEASE_UX | 官方CLI版本检测 PASS；浏览器Release Notes/明确确认/What's New/重复弹窗/中英文/版本历史 NOT_RUN |

因正式升级在前置身份检查阻断，不再执行依赖升级成功的 Research→Backtest→Publish→Canonical SIM→Ledger/Position/Performance、角色切换/migration/启动阶段故障注入、更新中断与V47恢复验证。不能用此前开发镜像或历史实现验证替代这些缺失证据。

任务自有三个容器已通过正式 stop 关闭，数据/备份/日志和镜像保留；没有操作其他正在运行的用户安装。GLOBAL_TRADING kill=ENGAGED，LIVE/real provider/client/exchange 均 false。无 Tag、产品 Release、registry push、Public Demo 公网部署或真实交易。

P0：本次限定范围未发现。P1：**1项升级资格阻断**（PG config digest不兼容）。P2：没有本轮已识别的未修复产品P2；测试夹具修复已交付。原证据中宿主/CPython/完整递归闭包未知状态保持，不据此机械扩展为安全P1。

所有原始证据位于 `E:/nq-v101-formal-qualification-20261024-033b28ac/`。主索引 `git-delivery.json`、`immutable-source-run.json`、`formal-package-identity.json`、`package-jdk-proof.json`、`official-upgrade-run.json`、`upgrade-blocker-evidence.json`、`postgres-image-comparison.json`、`pre-upgrade-backup-verification.json` 及 `evidence-index.json`。备份、认证配置和数据库 dump 为本机受限运行材料，不纳入 Git。

保留的检查器错误：首次制品检查假设 distribution 属性位于 `BOOT-INF/classes/META-INF/`，实际 Spring Boot 制品位置为 `META-INF/nq-distribution.properties`，产生 KeyError。只修正外部检查器路径，在新检查目录复核通过；原脚本、失败说明和首次提取保留，正式源码与制品未变。

## 最终输出

```text
Task: NQ-V101-RELEASE-SOURCE-AND-FORMAL-UPGRADE-QUALIFICATION
Baseline:
Source Commit: 15ffbc58e15adcb11d97ba296dc4ce4b882b0beb
Source Tree: 2145e55070afad4fcdfe0227c11e893a81fba19e

Release:
Target Version: 1.0.1
Source Manifest: PASS
Source SHA256: 4965fe5fa6f8573d27391dd4216fb203c6917ede7160515664e3b05e137c6668
Backend Image: sha256:6b3fc8c00028301e90949bad6e68e1056bc3c260d1d49010f14f397ecd1b1344
Frontend Image: sha256:f9997564b8039e1e0f16d20f995940cda277babfc34eb3fce2420dfc65bb9057
PostgreSQL Image: sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97
Package Manifest: 7d2e42e45f4a18f7e0b7e16253ca1faee078bb96e92a4bfdbe5df0955a1315b3
Package SHA256: bada1788e4c813cff9a9898950028e567342bb3adcb34f99f007e250f9ca2701

Security:
JDK Mitigation: VERIFIED_MITIGATION_IN_FORMAL_PACKAGE
Secret Scan: PASS
Build Chain: ACTUAL_PACKAGE_PATH_PASS_WITH_RECORDED_UNVERIFIED_LIMITS

Upgrade:
Source Version: 1.0.0
Target Version: 1.0.1
Official Installer: EXECUTED / UPDATE_REJECTED_BEFORE_MUTATION
Flyway: V34_RETAINED / V47_UPGRADE_NOT_RUN
Database Roles: NOT_VERIFIED_FOR_V47_UPGRADE
RLS: NOT_VERIFIED_FOR_V47_UPGRADE
Historical Facts: MATCH_FOR_REJECTED_UPDATE / V47_NOT_VERIFIED
Canonical SIM: NOT_RUN
Update UX: CLI_CHECK_PASS / BROWSER_NOT_RUN
Backup: PASS_V100_PRE_UPGRADE
Rollback: NOT_RUN / NO_UPDATE_TRANSACTION_CREATED

Verification:
Source: PASS
Package: BUILD_AND_IDENTITY_PASS / UPGRADE_NOT_QUALIFIED
PostgreSQL: OLD_V100_HEALTHY / TARGET_IDENTITY_INCOMPATIBLE
HTTP: ORIGINAL_USER_LOGIN_PASS / POST_UPGRADE_CHAIN_NOT_RUN
Frontend: FORMAL_BUNDLE_BUILD_PASS / POST_UPGRADE_UI_NOT_RUN
Recovery: V100_RESTORE_PASS / V47_RECOVERY_NOT_RUN

Findings:
P0: 0_IDENTIFIED_IN_SCOPE
P1: 1_POSTGRES_CONFIG_DIGEST_CHANGED
P2: 0_UNRESOLVED_IN_SCOPE

Decision: BLOCKED / POSTGRES_CONFIG_DIGEST_CHANGED
Git Delivery: MERGED / PR_CI_9_OF_9_SUCCESS / POST_MERGE_CI_9_OF_9_SUCCESS
Public Demo: NOT_READY
Public Email: NOT_QUALIFIED
Public Marketdata License: UNVERIFIED
Public TLS: NOT_VERIFIED
LIVE: DISABLED
Private Trading: DISABLED
Commit: GIT_DELIVERY_COMMITTED / QUALIFICATION_REPORT_NOT_COMMITTED
Push: GIT_DELIVERY_PUSHED / QUALIFICATION_REPORT_NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```

按“正式不可变包完成但升级存在真实阻断”停止条件结束。本轮未做额外产品修复或重新开启全量审计；保留制品和失败，不声明 RELEASE_CANDIDATE_QUALIFIED。
