# NQ-V101-FINAL-RELEASE-DECISION

日期：2026-10-25（Asia/Shanghai）。**Decision: PENDING_GIT_DELIVERY**。

固定正式候选的源码、发行包、技术升级证据及实际 GitHub 签名授权均核对通过；本轮另补同一正式包的一次全新安装定向验证，通过。发现包内 `INSTALL.md` 同时保留不能执行的直接升级路径，并将 Bridge 镜像 TAR 写成外层 ZIP。已仅修正当前工作树的安装说明，等待 Git 交付；原源码导出、包、过渡清单和签名均保持冻结，不能据此宣布修正后候选已合格。

本报告用于所有者决策，不授予产品发布、其他安装实例升级或 LIVE 权限。没有创建 Tag / Release，没有 Commit / Push / PR / Merge。

## 固定身份与保护

| 对象 | 实际身份 |
| --- | --- |
| Version / Schema | `1.0.1` / `47` |
| Release Source Commit | `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71` |
| Release Source Tree | `c323207865b8ef6a978746dd2ae63f15fdcc3ca4` |
| Source Manifest SHA256 | `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1` |
| Release Source Hash | `58da58497d748e913ccfd5a28f2dc62fd1a5afdf6ef346ee94da4e0f4ac779f8` |
| Package Manifest SHA256 | `c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d` |
| `images-amd64.tar` SHA256 | `477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2` |
| 外层 ZIP SHA256 | `0af219635ab681d254895b7163c6b45c3850732a8b17e1c78b887f94d188c274` |
| Signing Workflow Commit | `86d791c133d4e501b22b9e8612e7435e08ddbcb9` |
| Signing Run / Attestation | `40108650146` / `59432473` |
| Transition SHA256 | `c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56` |
| Attestation Bundle SHA256 | `c6b378b86aef5e6d3cc2207956f1aed29e6517bca19810a697b7927767baa488` |

签名工作流来源与正式发行来源不同，按各自用途验证，没有用签名工作流 Commit 替换发行包来源。

当前修改目录为 `C:/Users/Lingyu/.codex/worktrees/nq-transition-authorization/nexus-quant`，分支 `codex/nq-v101-transition-authorization`，起始 HEAD 为 `ce03c9ee6cc9e39034c1b811a7d35df64eb13d4e`，起始工作树干净。主工作区 `E:/Project/nexus-quant` 仍在 `codex/nq-v101-legacy-upgrade-bridge`：两份暂存资格报告及两份未跟踪历史报告均保留，原暂存 Tree `a86ec81cc9914ac1e45504acd233d44bed7025b5` 不变。它们未混入本轮候选。

本轮实际证据目录：`E:/nq-v101-final-release-decision-20261025/`。初始与最终 index/工作树身份及旧报告摘要另存于该目录，避免本报告自引用 Tree。

## 已核对的来源与制品

原正式材料目录：`E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/`。

本轮逐字节复核不可变导出的 **2,182** 个文件、实际包的 **21** 个文件、外层 ZIP 所有文件与包目录的对应关系，以及完整镜像 TAR。SHA 均与冻结记录一致。Bridge 与比较工具还分别对照固定 Commit 的真实 Git blob；不是仅与同包自带摘要比较。Source Manifest 的 Git 字节摘要、Secret Scan 的源码 Commit 和 release-source hash 均一致。既有 **148** 份资格证据的文件摘要全部匹配，只重新计算完整性，没有重新执行其测试。

| 检查 | 结论与边界 |
| --- | --- |
| SOURCE_IDENTITY | PASS；精确 Commit、Tree、Manifest、2,182 文件与 Release Source Hash 对应 |
| PACKAGE_INTEGRITY | PASS；Manifest / TAR / ZIP 与全部包文件一致 |
| PACKAGE_CONTENT | BLOCKED_FOR_RELEASE_DOCUMENTATION；所需运行资产、Bridge、比较工具存在，但安装说明含下述实质错误 |
| SECURITY_MITIGATION | PASS_FOR_VERIFIED_MITIGATION；复用同一实际镜像的证书链行为证明 |
| INSTALLER_INTEGRATION | TECHNICAL_PASS；正式 Bridge、普通安装器及新安装路径可运行；对外说明须交付修正 |

| 镜像 | 实际包身份 |
| --- | --- |
| Backend | `sha256:3812ebf7b020445e5c3698e712ff8470d0ffd5b87831b9f81301d1c63f5c5f29` |
| Frontend | `sha256:014300bbbc2c7966d5b7c8360ee0aaa757472b900dcc6e7dbbe47de8c78ee738` |
| PostgreSQL | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| Windows 运维安装器 SHA256 | `15fcafb76abdc816166fa20226a7be7481ccb19d175b5854485b033b8bd03293` |
| POSIX 运维安装器 SHA256 | `c3bc0881d9b9525c557784dc55626c58ab44ca0d99fb8c84f9c45ded1f18370e` |
| Bridge SHA256 | `555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8` |
| Baseline Tool SHA256 | `523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c` |

正式构建与源码扫描复用 `immutable-source-run.json`、`source-secret-scan.log` 和 `formal-package-build.log`。Secret Scan 为 Gitleaks 8.18.4，实际候选 secretFindings=0；原始一条协议分隔符误报及其解释保留。归档/二进制已有官方 checksum 证据经重新哈希，未把先前一次下载失败改写为成功。没有重新全栈扫描。

JDK 证书缓解复用 `package-jdk-proof.json` 与 JDK/JRE 四份证书/TLS结果。实际 Maven JDK 与包内 JRE 均为 Temurin `21.0.12.1+1`，各自 48 变体、14 完整问题链拒绝，accepted=0、unexpected=0；正常默认链及 Maven Central HTTPS 接受。JDK 身份为 `sha256:e3ec91b6e7a0563d48c7409999060490a38fa2dcd9dbb04fad07fd2aeaa864ef`，JRE 绑定上表 Backend。结论仍是 **CVE-2026-91403 = VERIFIED_MITIGATION**，不是 Adoptium 正式修复二进制或厂商支持承诺。

## 实际签名及完整实例授权

从成功 Run 实际下载 artifact `12565208981`，未生成本地替代签名。GitHub API 返回 Environment 审批人为 `ling5477`（128130348），状态 approved；Run 为 workflow_dispatch、attempt1、精确签名工作流 Commit，结论 success。Environment `nq-release-transition-signing`（25219760021）仅允许 dev，required reviewer 为所有者，`can_admins_bypass=false`。这是所有者人工批准，不声称存在第二维护者审批。

使用本地原始清单运行 `gh attestation verify`，指定实际 Bundle、仓库、精确工作流、signer/source digest、dev ref、GitHub OIDC issuer 和自定义 Predicate。CLI 实际密码学验签成功后，另调用已交付 helper 的完整声明验证：证书 SAN 严格相等、Subject、完整 Predicate 均一致；还逐项核对本地实际配置和包来源。远端按 Subject 查询返回的 Bundle 与实际下载 Bundle 的 JSON 对象完全一致。

| 项目 | 验证结果 |
| --- | --- |
| Trust Provider / Root | GitHub Actions OIDC；Sigstore Fulcio / Rekor 信任根由 gh 实际验证 |
| Signing Identity | `https://github.com/ling5477/nexus-quant/.github/workflows/nq-release-transition-attest.yml@refs/heads/dev`，证书严格匹配 |
| OIDC Issuer | `https://token.actions.githubusercontent.com` |
| Predicate Type | `https://github.com/ling5477/nexus-quant/attestations/legacy-transition-authorization/v1` |
| Subject | `transition-v1.json`，SHA256 为上表固定摘要 |
| Signature / DSSE | VERIFIED；含已验证 Rekor 时间 `2026-10-25T00:00:32+08:00` |
| Source Identity | 完整发行源码 Commit / Tree 与原始 Transition 一致 |
| Official Source Package | 复核官方 v1.0.0 Release 418603194、asset659954162、官方 ZIP digest `37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931`；原包 Manifest `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1` |
| Target Package | 签名内完整目标 Manifest / TAR 摘要与本地实际字节一致 |
| Installation Binding | 原事务保存的真实配置 SHA256 `0c149976a9b2c2d60f8b2dcdb7938bb4ee66706f6bfbce97e51d83593eb01954`，项目及 amd64 一致 |
| Post-upgrade Config | 当前配置 `36f3513a4db660dbae491de05e0cadb700200d343725c1fefa97e13ffc04cf14` 匹配 COMMITTED receipt 的 targetConfigSha256；没有冒充原配置 |
| Installation ID | `2a94e292-6b9b-4fd8-8499-9cef3163766e`；项目 `nq-v101-formal-bridge-20261024` |
| Authorization | EXPLICIT_OWNER_APPROVAL / SIGNED_AUTHORIZATION |

```text
scope: ISOLATED_V100_TO_V101_UPGRADE
validationMode: OWNER_AUTHORIZED_DIGEST_ATTESTATION
productReleaseAuthorized: false
liveAuthorized: false
```

签名 runner 实际校验完整 Transition、目标 Manifest 和冻结源工具；本地 TAR / 安装配置由所有者确认，再由本轮本地实物核对，不声称 GitHub 读取了本地安装秘密、构建或独立哈希该 TAR。此为 **SIGNED_AUTHORIZATION，不是 BUILD_PROVENANCE**。原始配置、口令、JWT、数据库和私钥未上传 GitHub。

原历史报告中 PENDING_WORKFLOW_GIT_DELIVERY / PENDING_MANUAL_APPROVAL / INDEPENDENT_TRUST_ANCHOR_MISSING 属于对应历史时点。本轮实际远端签发与验签已解除**原固定实例及原固定候选**的该项阻断，不改写历史报告，也不扩展为产品发布授权。

## 技术资格复用及全新安装

以下来自原正式包真实执行记录，输入身份已复核，没有重跑研究、回测、SIM、完整迁移或恢复故障矩阵。

| 能力 | 证据与结论 |
| --- | --- |
| Legacy Bridge / PG 镜像过渡 | PASS；正常事务 `20261024T125624000Z-6775b954` COMMITTED，镜像过渡保留 XZ 安全更新 |
| Flyway | V34→V47 PASS；V1–V34 checksum/version/success 前缀保持 |
| Restricted Runtime / RLS | PASS；应用 nq_runtime，nq_schema_owner NOLOGIN，原实测29个受限 SECURITY DEFINER |
| 旧认证与账户 | PASS；旧口令登录、NQ_LOCAL、历史账户及经济事实保持 |
| 研究 / 回测 / 策略发布 | PASS；原 `formal-product.json` 的实际正式 API 与持久身份 |
| Canonical SIM | PASS；新 canonicalAccountId=4，1 Order / 1 Trade / 6 Ledger Entries / 1 Position，风险 ALLOW，经济身份无重复 |
| Installer Handoff | PASS；原正式安装器 status/doctor/stop/start/restart/backup/restore 7/7 |
| Backup / Restore | PASS；官方备份 `20261024T130325352Z-7b35d4a5`，真实恢复移除备份后额外研究记录，32表 MATCH |
| Failure Recovery | PASS；正式包 V47 中断后通过已知 Bridge 事务完整恢复到 V34；未让旧镜像接管升级库 |
| Check-update / Confirmation | PASS；原正式 v1.0.0 对目标实际返回 UPDATE_INELIGIBLE，缺少 Bridge 明确确认被拒绝，签名授权有真实人工批准 |
| What's New / Version History | 已实现；沿用既有前端定向测试及版本资产证据。本轮未新增浏览器交互验收；正式版本 API、安装版本与关闭自动更新已实际验证，不把早期 fixture 当作新浏览器证明 |

32表历史事实继续保留原始结果：初始 v1.0.0 → 产品写入前为 **DIFFERENT**，仅解释新增权益观测；正常 Bridge 事务内 before/after 为 MATCH；本轮原始基线→最终库仍 DIFFERENT，包含已授权新研究、回测和 SIM 事实。**Missing Original Primary Keys=0；Changed Original Business Fields=0**。`formal-controlled-additions-proof.json` 给出逐行父键归属。Post-Restart / Post-Restore 均 MATCH。单独结论 `PRESERVED_WITH_EXPLAINED_ADDITIONS` 不覆盖原始 DIFFERENT，不重裁历史例外。

此前正式包只取得升级实例资格，不能将 v1.0.0 新装或开发夹具冒充 v1.0.1 新装。本轮按授权额外执行一次原固定正式包的新装定向验证：

- 新目录 `E:/nq-v101-final-release-decision-20261025/fresh-installation`，项目 `nq-v101-final-fresh-20261025`，仅 loopback19780/19788；没有使用旧安装或用户安装。
- 从原 `package-amd64/installers/nexusquant.ps1 -Action install` 安装；不是手工迁移或开发 Compose。初始 Docker daemon 停止，官方安装器启动后，在 daemon 运行状态完成 preflight；该观察不扩展为 Docker bootstrap/daemon recovery 支持资格。
- 实际三个 healthy 镜像与上表一致，空库47个 Flyway success；nq_runtime 无 SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/REPLICATION，NOLOGIN owner；98个已启用表 FORCE RLS，无未 FORCE 表，无租户上下文读 accounts=0。
- 初始管理员要求改密，改密前业务 API403；正式改密后旧 token401，新口令登录成功。正式版本 API绑定原 Commit/Manifest/V47；Self-Hosted 注册及平台入口403。HTTP UI200仅作服务检查，不声称新增完整浏览器业务测试。
- 官方 restart 后配置字节 MATCH、新口令及版本保持；doctor通过。LIVE/真实 exchange/provider/client全部 false。由官方 stop 停止任务容器，保留数据和脱敏证据；没有重复完整恢复或 Canonical SIM。

新装的启动、角色、改密、重启资格为 PASS；关键业务和备份恢复直接复用相同包/镜像已通过的正式升级实例结果。新装没有改成对其他宿主或其他 Bridge 实例的授权。

## 发行文档最小修正与发现分类

检查冻结来源的 README、INSTALL、CHANGELOG、VERSION、source-manifest、support-matrix 及 builder。VERSION=1.0.1；support-matrix明确为v1.0.0历史资格，不能据此宣称v1.0.1跨平台支持。CHANGELOG预发布措辞、旧资格未完成状态属冻结历史，优先用本轮候选 Release Notes表达最终范围，不为美化改写所有文件。

| ID / 分类 | 实际问题、处置与影响 |
| --- | --- |
| DOC-01 / RELEASE_BLOCKING | 包内INSTALL“从v1.0.0进入角色分离候选”要求直接运行新版普通update；但实际PS安装器要求POSTGRES_CONFIG_DIGEST不变，本候选必然拒绝。后文Bridge说明与其冲突。删除错误直接升级命令，明确唯一跨协议路径为可信实例授权的Bridge。没有弱化拒绝合同。 |
| DOC-02 / RELEASE_BLOCKING | 包内INSTALL把targetArchiveSha256及--target-archive写为外层ZIP；实际清单、Manifest和正式执行均绑定images-amd64.tar。两者SHA不同，会在TRANSITION_IDENTITY_MISMATCH拒绝。改为Manifest声明的镜像TAR，明确不得以ZIP摘要替换。 |
| DOC-03 / NON_BLOCKING（随同修正） | 澄清“无需Python”限新装/常规运维；Bridge需Python3.12+；补充其他安装需自身实例可信授权，不能继承当前隔离清单。 |
| PLATFORM / RELEASE_LIMITATION | 仅Windows11 amd64、Docker Desktop Linux Containers定向技术资格通过；Linux/macOS/ARM64未取得原生资格。Public Demo未公网就绪；LIVE禁用。 |
| UPGRADE / RELEASE_LIMITATION | VERIFIED_INSTANCE_UPGRADE=QUALIFIED，仅原固定候选/固定实例；GENERAL_PUBLIC_UPGRADE=NOT_AUTOMATICALLY_QUALIFIED。现工作流硬绑定本实例，不是自动通用授权服务。其他用户需另行维护者可信签发并验证完整实例绑定；尚无此授权则停止升级。 |
| SECURITY / RELEASE_LIMITATION | 既有宿主PowerShell/.NET维护、全部递归插件工具安全闭包、完整字节可复现性仍UNVERIFIED_NON_BLOCKING；无新已证明可达阻断。不以功能PASS或签名替代安全证明。实际Bridge Python3.14.9沿用官方归档与Authenticode证据。 |
| ECONOMICS / RELEASE_LIMITATION | 缺少可信来源的普通账户权益不虚构收益、真实手续费来源、OHLC实际事件时点及旧Paper未知币种权益限制保留。 |
| C4 / HISTORICAL_EXCEPTION | 保留ACCEPT_WITH_EVIDENCE_EXCEPTION / ADMITTED_WITH_DOCUMENTED_EXCEPTION及长时重跑豁免；原NOT_ACCEPTED / EVIDENCE_GAPS不改写，本轮不重新审裁。 |
| OWNER / AUTHORIZATION_REQUIRED | 产品最终发布授权尚未获得；是技术及文档条件完成后等待所有者的独立决定，隔离签名不能替代。当前并非只缺该授权，仍有DOC-01/02待交付。 |

唯一源码输入修正为根 `INSTALL.md`（文档，无运行时代码或历史迁移变更）。本轮精确暂存该文件、本报告和 `NQ_V101_RELEASE_NOTES_CANDIDATE.md`，不提交。原冻结包没有被覆盖或重建。

最小后续处理：先由用户另行授权这份文档修复的Git交付，再按新Commit完成受影响的不可变来源/打包身份验证。安装说明属于发行源和外层ZIP内容，修正后必须形成新身份；旧Manifest/TAR/签名的适用范围需按实际新产物逐项判断，**不能预填新摘要或沿用旧签名冒充新候选**。业务实现和镜像若实际未变，原业务证据按相同输入范围复用，不要求重做全栈审计、148项测试或完整Bridge。

Release Notes候选已准备，标注待Git交付和最终发布授权；不得直接对外称v1.0.1已经发布。

## 最终输出

```text
Task: NQ-V101-FINAL-RELEASE-DECISION

Release:
Version: 1.0.1 / CANDIDATE
Source Commit: 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
Source Tree: c323207865b8ef6a978746dd2ae63f15fdcc3ca4
Package Manifest SHA256: c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d
Archive SHA256: 477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2 / IMAGES_TAR

Evidence:
Immutable Source: PASS / 2182_FILES_MATCH
Formal Package: INTEGRITY_PASS / INSTALL_DOCUMENTATION_BLOCKED
Security: PASS_WITH_VERIFIED_MITIGATION_AND_RETAINED_LIMITATIONS
Bridge: PASS / ORIGINAL_FIXED_CANDIDATE
Database Roles: PASS
RLS: PASS
Flyway: V34_TO_V47_PASS / FRESH_V47_PASS
Canonical SIM: PASS / 1_ORDER_1_TRADE_6_LEDGER_ENTRIES / REUSED
Backup Restore: PASS / REUSED
Failure Recovery: PASS / REUSED

Authorization:
Attestation ID: 59432473
Signing Workflow: 86d791c133d4e501b22b9e8612e7435e08ddbcb9 / RUN_40108650146
Signature Verification: PASS / CRYPTOGRAPHIC_AND_FULL_CLAIM
Owner Transition Approval: APPROVED / FIXED_ISOLATED_INSTANCE_ONLY
Product Release Authorization: NOT_GRANTED

Scope:
Windows 11 amd64: TECHNICAL_QUALIFIED / RELEASE_PENDING_DOCUMENTATION_DELIVERY
Fresh Install: PASS / ORIGINAL_FORMAL_PACKAGE / TARGETED
Verified Instance Upgrade: QUALIFIED / ORIGINAL_FIXED_INSTANCE_AND_CANDIDATE_ONLY
General Public Upgrade: NOT_AUTOMATICALLY_QUALIFIED
Linux: NOT_QUALIFIED
macOS: NOT_QUALIFIED
Public Demo: NOT_READY
LIVE: DISABLED

Historical Facts:
Strict Comparison: DIFFERENT / ORIGINAL_RESULT_RETAINED
Missing Original Keys: 0
Changed Original Fields: 0
Explained Additions: RECORDED / PRESERVED_WITH_EXPLAINED_ADDITIONS
Post-Restart Comparison: MATCH
Post-Restore Comparison: MATCH

Release Notes: READY_AS_DRAFT / NOT_PUBLICATION_AUTHORIZATION

Findings:
Release Blocking: 2_DOCUMENTATION_ERRORS / INSTALL.md_MINIMUM_FIX_PENDING_GIT_DELIVERY
Release Limitations: PLATFORM;GENERAL_UPGRADE_AUTHORIZATION;PUBLIC_DEMO;LIVE;SCOPED_SECURITY_UNKNOWNS;ECONOMIC_DATA
Historical Exceptions: RETAINED / C4_ADMITTED_WITH_DOCUMENTED_EXCEPTION

Decision: PENDING_GIT_DELIVERY
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
