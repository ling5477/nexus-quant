# NQ-V101-FORMAL-BRIDGE-RELEASE-QUALIFICATION

日期：2026-10-24（Asia/Shanghai）。**TECHNICAL_BRIDGE_VALIDATION: PASS**；**RELEASE_CANDIDATE_QUALIFICATION: BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING**。

本轮从固定合并Commit导出并构建的候选包，已经在任务自有的Windows 11 amd64 / Docker Desktop环境完成真实v1.0.0→v1.0.1桥接、V34→V47、数据库镜像过渡、受限角色/RLS、旧身份与经济事实保持、完整产品链、新安装器七项接管、重启、备份恢复及数据库变化后的真实进程中断恢复。所有技术PASS只适用于这次隔离验证。

用户随后明确批准 `NQ-V101-FORMAL-BRIDGE-TRUST-EXCEPTION / APPROVED / ISOLATED_VALIDATION_ONLY`，只豁免本轮隔离测试的独立渠道前提。摘要仍无第三方独立背书；本地内容一致和用户豁免均不授予正式发行资格。没有修改源码/正式安装器、历史Flyway或业务算法，没有Commit、Push、Tag、Release、LIVE或公网部署。

## 冻结来源及原工作区

- Source Commit：`1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71`。
- Source Tree：`c323207865b8ef6a978746dd2ae63f15fdcc3ca4`。
- GitHub API确认该Commit及Tree仍可获取，PR #78确为MERGED；Post-merge CI `40085665454 / push / attempt 1 / success`，复用已交付的9/9任务证据。
- 原工作分支 `codex/nq-v101-legacy-upgrade-bridge`，HEAD为 `129fd8fb53326925e3d69db7a251fe4e24c95a79`，code tree与冻结来源相同；未切换或改写分支。
- 开始时无tracked/index修改；两份原未跟踪报告保留原字节，不作为发行输入。本轮只新增本资格报告。
- 本次原生git fetch发生TLS连接中断，未记为成功；远端固定身份通过GitHub API核对，本地完整Git对象Tree匹配。没有改用其他dev提交。

外部材料根：`E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/`。

## 正式不可变来源和包

官方三个Python工具从固定Git对象提取至外部control目录，记录各Git blob和SHA后执行。正式源码由原 `release_source.py` 读取同一Commit的Git blobs导出至仓库外，没有复制工作区源码。`coverage → export → verify → verify_release_boundary → scan_release_secrets` 均实际通过，打包后再次verify通过，未修改 `.release-source.json`。

| 项目 | 实际结果 |
| --- | --- |
| Source coverage | PASS，2182 included / 2124 required runtime / 47 migrations，遗漏0 |
| Source manifest SHA256 | `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1` |
| Release source hash | `58da58497d748e913ccfd5a28f2dc62fd1a5afdf6ef346ee94da4e0f4ac779f8` |
| Source verification / boundary | PASS / PASS |
| Secret Scan | PASS；Gitleaks 8.18.4，raw=1、既有精确PEM delimiter误报=1、secret=0、无historical allowlist |
| Gitleaks材料 | 官方release/asset身份可获取；本次checksum下载失败，复用同版本既有官方checksum证据并重新核验归档/二进制，未跳过实际扫描 |
| Gitleaks ZIP SHA256 | `9ba442ca7dda19885a2e569f43a127289feeb2b5fb0dfa251dafd277f4a0ba91` |
| Gitleaks binary SHA256 | `b3ef977b1b8b3f6921e16759e7cfbd91cf738c4f6aa13e676d8e0ed264103912` |
| Official package build | 原导出内 `release/build-package.ps1 -Architectures amd64`，exit0；Windows 11 amd64 / Docker Linux containers / Self-Hosted |
| VERSION / Schema | 1.0.1 / 47 |
| Backend Image | `sha256:3812ebf7b020445e5c3698e712ff8470d0ffd5b87831b9f81301d1c63f5c5f29` |
| Frontend Image | `sha256:014300bbbc2c7966d5b7c8360ee0aaa757472b900dcc6e7dbbe47de8c78ee738` |
| PostgreSQL Image | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| Package Manifest SHA256 | `c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d` |
| images-amd64.tar SHA256 | `477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2` |
| 外层候选ZIP SHA256 | `0af219635ab681d254895b7163c6b45c3850732a8b17e1c78b887f94d188c274` |

打包器的真实OCI/config/platform/layer绑定门禁通过；之后重算完整镜像TAR摘要、校验包内资产和外层ZIP逐文件round-trip。实际镜像提取的JAR为 `8993ccf000f6844b9da7623df8177a1121a77ddc9a9411eb00597012010a5a24`，发行属性NQ_SELF_HOSTED、无nq-public-demo JAR；47个migration的实际SQL摘要与冻结导出一致。实际前端distribution.json为NQ_SELF_HOSTED。这些制品事实不替代本轮尚未执行的目标HTTP业务与升级资格。

### 包内桥接器

| 最终包文件 | 冻结源文件 / Git blob / SHA256 |
| --- | --- |
| `installers/legacy_upgrade_bridge.py` | `release/installers/legacy_upgrade_bridge.py` / `8523dcd61cebee7b212beb4326b7fa157113f848` / `555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8` |
| `installers/upgrade_business_baseline.py` | `scripts/verify/upgrade_business_baseline.py` / `3f9ec653989d0a53f9325ec4614d3f43bed3d393` / `523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c` |

两个文件在实际package目录和最终ZIP中存在，字节与冻结Git对象一致，不只是开发仓库路径存在。

新镜像身份与历史候选不同，因此仅对本次实际构建JDK及包内最终JRE绑定既有证书行为夹具，未重跑历史业务单测。两者均48变体 POLICY_BLOCKED、14完整链 POLICY_BLOCKED、accepted=0、unexpected=0；正常默认链和Maven Central HTTPS接受。结论维持 `CVE-2026-91403=VERIFIED_MITIGATION`，没有厂商已修复二进制或Adoptium支持声明。

## 过渡清单、内容校验与显式信任豁免

- Transition：`E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/transition-v1.json`。
- SHA256：`c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56`，实际PowerShell Get-FileHash MATCH；桥接前及收尾再次核对，内容未改。
- Bridge / Target Commit均为已提交的 `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71`；没有 `UNCOMMITTED`，没有 `--local-candidate`。
- 原Manifest指向官方v1.0.0的 `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1`；目标Manifest为本次构建的 `c3437d4b…`；targetArchive只绑定实际 `images-amd64.tar` 的 `477e41ac…`，不混用外层ZIP。
- 清单绑定安装配置SHA `0c149976a9b2c2d60f8b2dcdb7938bb4ee66706f6bfbce97e51d83593eb01954`、项目 `nq-v101-formal-bridge-20261024`、架构amd64。固定任务安装根为材料根下 `installation/`，仅loopback 19680/19688。

先前缺少独立信任时确实停止，未修改数据库。用户回复最初仅提供本地经对话返回的预期摘要，该步骤只证明字节一致。之后用户另行明确授权仅对本轮隔离验证豁免独立渠道要求，才继续执行数据库操作。授权记录为 `trust-exception.json`；此前阻断报告和结果保留为 `qualification-report-before-user-hash-reply.md`、`qualification-result-before-user-hash-reply.json`。

```text
TRANSITION_HASH: LOCAL_VERIFIED
INDEPENDENT_TRUST_SOURCE: NOT_ESTABLISHED
TRUST_EXCEPTION: APPROVED_FOR_ISOLATED_VALIDATION_ONLY
TECHNICAL_BRIDGE_VALIDATION: PASS
FORMAL_RELEASE_TRUST: NOT_QUALIFIED
RELEASE_CANDIDATE_QUALIFICATION: BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING
```

## 官方v1.0.0与原安装器边界

从GitHub Release API核对Release `418603194` / asset `659954162`，原始ZIP SHA256 `37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931`；原包19文件与ZIP逐项一致。没有重建v1.0.0。原PS安装器SHA `14106d4251e654a09b4cb5f474948ef22ac9f3582fea49abe6e7afc43d0bbf29`、SH SHA `7396857ef801f51e632507a8d1c9fecd7851aecd5c3b4a3f722f94fed5ba05f8`保持原样。

原版实际install / restore / backup均exit0；恢复任务自有历史业务备份，原用户旧密码登录PASS，实际产品1.0.0、Schema34，生成32表基线与可恢复备份 `installation/backups/20261024T120101889Z-7f2a8bcc`。

本轮针对真实新目标重新执行原安装器check-update，回执request `801d075f-570b-4e36-8aae-a4f955acb73f`、installation `2a94e292-6b9b-4fd8-8499-9cef3163766e`，结果 `CHECKED / updateAvailable=true / UPDATE_INELIGIBLE`。这是预期合同，未修改旧安装器让其直接跨协议升级。实际回执为 `formal-v100-check-receipt.json`。

## 正式包Bridge与真实失败恢复

通过任务专用已验证Python，直接调用最终包内 `installers/legacy_upgrade_bridge.py`。外部harness只传CLI参数和观察持久阶段，不导入替换Bridge实现，不使用测试模式或源码故障钩子。

| 操作 | 实际结果 / 证据 |
| --- | --- |
| 缺少明确升级确认 | exit4 / EXPLICIT_CONFIRMATION_REQUIRED；未创建Bridge事务 |
| 未知恢复身份 | exit4 / BLOCKED；未修改数据库 |
| preflight | exit0 / VERIFIED；来源、配置、镜像、工具与安装路径验证通过 |
| 真实进程中断 | 事务 `20261024T125433000Z-b44fae6b`；在ROLES_AND_SCHEMA_VERIFIED持久阶段用taskkill终止Bridge进程树，实际SQL确认Schema47 |
| 中断后收敛 | 按本任务Compose项目立即停止应用/maintenance；不手动移除pending，不手工migration或repair |
| 已知事务恢复 | 原包内Bridge recover，显式RECOVER 1.0.0；exit0 / RECOVERED_TO_V100，Schema34、原配置SHA MATCH，恢复内部32表/旧认证检查通过 |
| 完整正常执行 | 事务 `20261024T125624000Z-6775b954`；exit0 / COMMITTED，实际运行身份和health验证通过 |

中断是在数据库已经改变后执行，实际恢复使用原始完整集群备份和已验证恢复副本；不是让旧镜像直接接管已升级数据库。原双pending护栏、持锁恢复及停机路径均未改动。冻结实现已有的迁移前失败、迁移后失败和提交前中断真实机制证据按任务第3/13节复用，未重复全部注入。旧证据为 `E:/nq-v101-legacy-bridge-20261024/` 内 `recovery-attempt1.log`、r5事务 `20261024T055856000Z-48187be1`、r7事务 `20261024T060819000Z-48cfb178`；本轮正式候选恢复另有上述新的实际事务，不以旧PASS替代。

正常Bridge持久回执：`installation/runtime/bridge-transactions/20261024T125624000Z-6775b954/receipt.json`；中断恢复回执在对应b44fae6b目录下 `recovery-receipt.json`。外部控制与结果为 `run_formal_bridge.py`、`formal-interruption.json`、`formal-known-recovery.json`、`formal-bridge-committed.json`。

## PostgreSQL、实际角色与运行安全

Bridge对原始停库集群执行备份/恢复证明，在原PG和目标PG分别核验事实/认证/物理身份、locale/collation/index；实际OS包差异只为liblzma5/xz-utils，目标保留 `5.8.1-1+deb13u2` 安全更新，pg_amcheck通过。正式事务 `image-transition.json` 保留实际库、包与镜像证据。

独立只读核对实际已安装配置、三个运行容器及数据库，而不只采信Bridge输出：

- 三个运行镜像config身份与本次Manifest一致，容器healthy，PostgreSQL挂载确为任务安装数据目录。
- maintenance仍为原 `nexusquant`；应用实际使用 `nq_runtime`，无SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/REPLICATION；`nq_schema_owner`无LOGIN且无这些提升权限。
- 后端实际环境中的口令对应受限运行角色；没有包含maintenance口令。普通管理周期中DB_PASSWORD、JWT_SECRET、CREDENTIALS_KEY和受限运行口令均保持。
- canonical启动合同的8个根表均启用且FORCE RLS；已启用RLS表无未FORCE项，内部调度元数据的运行角色直接写入/指定直接读取权限为0。无租户上下文的 `SET ROLE nq_runtime; SELECT count(*) FROM accounts` 实际返回0。
- 29个public SECURITY DEFINER函数均由受限NOLOGIN owner拥有，并配置search_path；实际认证产品调用通过相关路径。没有伪造租户上下文。
- 原账户均映射NQ_LOCAL。Flyway实际到V47、全部success，V1–V34原version/checksum/success前缀完全一致，没有修改历史migration。
- LIVE、REAL_EXCHANGE、REAL_PROVIDER、REAL_CLIENT实际容器值均为false，kill=ENGAGED；Self-Hosted注册和平台入口实际403，最终JAR无Public Demo模块。

证据：`formal-runtime-inspection.json`、正式事务 `database-role-validation.json`、`formal-installer-doctor.log`。doctor的updateAvailable=UNKNOWN表示未指定未来目标包，不被冒充更新检查PASS；本次原版目标检查有上节独立回执。

## 32表原始比较与历史事实保持

| 比较对象 | 原始严格结果 | 解释与边界 |
| --- | --- | --- |
| 2026-10-21历史参考→本轮V100源基线 | DIFFERENT | 仅新增观测a7722901…，原键缺失0/原字段变化0；不是升级证明 |
| 本轮初始V100基线→V101产品写入前 | DIFFERENT | 仅新增观测e14bca32…，原键缺失0/原字段变化0；PRESERVED_WITH_EXPLAINED_ADDITIONS |
| 本轮正常Bridge事务内before→after | MATCH | 32表无变化；此前原始DIFFERENT没有被改写 |
| 新产品链完成→官方重启后 | MATCH | 32表事实与新Canonical SIM保持 |
| 官方备份事实集→官方恢复后 | MATCH | 32表保持；备份后额外研究记录确实消失 |
| 本轮初始V100→所有验证后最终库 | DIFFERENT | 包含主动创建的新研究/回测/新Canonical SIM及已解释观测；所有旧主键缺失0，旧业务字段变化0 |

新观测 `e14bca32-a1dd-4358-b7d3-13199f80c9ae` 为2026-10-24 12:56:03 UTC（恢复V100后，正常Bridge开始12:56:24 UTC之前）、桶1992051、NO_BALANCE、equity=NULL、SIM、fundingEntryCount=0。它来自既有账户观测采样，不是新经济分录或收益。原初始观测a7722901…为12:01:00 UTC、桶1992048，也保留源阶段解释。

`formal-before-product-addition-details.json` 保留实际行与时间边界断言；原始比较分别保留于 `source-historical-comparison.json`、`formal-before-product-historical-comparison.json`、Bridge目录 `business-comparison.json`、`formal-restart-comparison.json`、`formal-restore-comparison.json`、`formal-after-restore-historical-comparison.json`。没有删除观测、放宽比较器或将DIFFERENT覆盖为MATCH。新增产品事实的逐表数量及主键在 `formal-technical-validation.json` / 原始最终比较中。

另外使用精确PG镜像、只读挂载且无网络，从已经校验并用于成功恢复的官方逻辑备份离线提取新增行：全部新经济行归属新Canonical账户4，全部回测行关联本轮backtestRunId，新策略分别关联正式发布记录的target_strategy_definition_id和新SIM，成交/风险关联新订单、ledger_events关联新账务分录；原账户仅有已解释观测。`formal-controlled-additions-proof.json` 为实际逐行主键/父键证明，不只按表名推测新增来源，没有重启数据库。

## 升级后完整产品链与新版安装器接管

使用原口令真实认证，原账户、历史research/backtest/replay/continuous读取成功。经正式API新建research和backtest、绑定冻结数据及原策略版本、执行回测/评估/发布，并创建非空canonicalAccountId的新SIM；只调用PAPER_MATCHING正式调度入口，没有真实provider或真实交易。

本轮新Canonical SIM为 `ptr-1ba597fd-bbb4-4077-b7de-1a492eb7e336`、canonicalAccountId=4，实际 **1 Order / 1 Trade / 6 Ledger Entries / 1 Position**；持仓数量0.00121998。订单关联真实Risk ALLOW，订单/成交/账务身份及账务幂等键无重复。既有canonical analysis和performance接口读取通过，未新建事实源或绕过409。产品过程及ID在 `formal-product.json`，只记录路径/状态和业务结果，不保存token/密码。

正式v1.0.1安装器实际执行status、doctor、stop、start、restart、backup、restore，**7/7 PASS**；不依赖Bridge完成普通启动，不产生首次管理员初始化或重置旧密码。重启及恢复后重新用旧密码登录，并重新读取新SIM，完整关键事实SHA保持 `58467605d1af707098f848162f60b43620c0aa57f39fbf26624546651750905e`。

新版备份为 `installation/backups/20261024T130325352Z-7b35d4a5`，Manifest SHA256 `ff2308a1bcd463ddf06e01de082bd143f241ac486407235a98a525aabac8050e`。备份后经正式API创建研究 `rcf-1493d5c8-183c-479c-81d7-976e4a84e96b` 并确认存在；实际官方restore后该记录消失、备份时32表MATCH、旧登录和受限身份仍通过。不是只对未变化的空操作运行restore。证据为 `formal-installer-handoff.json`、各 `formal-installer-*.log/.result.json`、`formal-post-backup-created-fact.json` 及重启/恢复比较。

收尾只通过新版官方安装器stop停止本任务三个容器，数据、备份、事务和失败证据全部保留；其他项目未用于验证或被停止。最终容器状态见 `formal-final-container-state.json`。

## 平台、补丁与未覆盖范围

真实平台为Windows 11 Pro amd64、OS10.0.26200、Docker29.9.2、Compose5.6.1、Docker Linux Containers。仅本平台隔离技术验证PASS；Linux/macOS未做原生正式验证，均NOT_QUALIFIED。

全局Python3.14.2保持原样，不授予其完整补丁资格。实际所有Bridge调用改用任务自有PSF Python **3.14.9** amd64 / OpenSSL3.5.9。归档SHA `3c01d7cfc666f746a6f064f60fb2156ab0ec464d3d738ab796371a46a9bb24a2` 与PSF官方发布页一致，33个EXE/DLL/PYD的Authenticode有效（PSF及两份Microsoft vcruntime）；未进行离线Sigstore签名验证，不宣称全宿主安全闭包。证据为 `private-python-verification.json`、`private-python-native-signatures.json`。

既有安全资格报告 `NQ_V101_JDK_SECURITY_REMEDIATION.md` 中递归插件/工具闭包、完整宿主PowerShell/.NET维护、SBOM实际覆盖及可重现性等UNVERIFIED边界继续保留；不把新包或技术测试PASS当成这些项的安全证明。本轮补充正式源码/制品身份、Gitleaks归档重新校验及真实扫描、新镜像JDK链行为、实际Bridge解释器和升级/恢复证据；没有重启全技术栈扫描、重新生成全栈SBOM或重复历史GateAUDIT。CVE-2026-91403仍为VERIFIED_MITIGATION，不声称Adoptium vendor-patched。

Bridge19、Release Source29、Package Builder6及先前独立候选审查在实现/合同未变的范围内复用，未重复全Maven/Python/前端单测或完整历史故障矩阵。没有本轮源码修复，因此无需PENDING_GIT_DELIVERY。

本轮执行纠错均限于外部harness并保留事实：首个CHECK请求因Windows文本默认CRLF被原安装器正确拒绝，改为合同要求的ASCII/LF后通过；初始只读观测器误将所有tenant_id元数据表视为RLS表，按冻结的canonical角色/ACL合同核对后纠正（没有改数据库）；新增观测提取首次误用id列，改用原基线真实主键sample_id；离线新增策略归属首次使用推测字段名，按实际target_strategy_definition_id完成绑定。下载urllib超时和只接受PSF签名而误拒合法Microsoft vcruntime的尝试也保留原记录。未修改产品规则或削弱原比较器制造PASS。

## 独立验收门禁与最小后续事项

| Gate | 结果 |
| --- | --- |
| IMMUTABLE_SOURCE / SECRET_SCAN | PASS / PASS |
| OFFICIAL_PACKAGE / BRIDGE_IN_PACKAGE | PASS / PASS（固定正式来源候选，不授予发行资格） |
| TRANSITION_HASH | LOCAL_VERIFIED |
| TRANSITION_TRUST | BLOCKED_FOR_FORMAL_RELEASE；隔离测试有显式豁免 |
| OFFICIAL_V100_INSTALL / FORMAL_BRIDGE_UPGRADE | PASS / PASS_WITH_ISOLATED_TRUST_EXCEPTION |
| POSTGRES_IMAGE_TRANSITION / ROLE_PROVISIONING | PASS / PASS |
| FLYWAY_V47 / RLS / SECURITY_DEFINER | PASS / PASS / PASS |
| HISTORICAL_FACTS | PASS / 原始DIFFERENT按上述比较对象保留，旧键缺失0/旧字段变化0 |
| CANONICAL_SIM / V101_INSTALLER_HANDOFF | PASS / PASS_7_OF_7 |
| BACKUP_RESTORE / FAILURE_RECOVERY | PASS / PASS（本轮正式候选真实中断恢复；旧故障矩阵明确复用） |
| FORMAL_RELEASE_TRUST | NOT_QUALIFIED |
| RELEASE_CANDIDATE_QUALIFICATION | BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING |

唯一尚未解除的本任务正式发行门禁为独立信任锚：正式发布前，由独立可信渠道对固定过渡清单及精确摘要建立可验证背书，并复核其Bridge/源码/Manifest/TAR/安装身份绑定。若清单或安装身份改变，须使用新的摘要与对应信任证据；不能沿用本次豁免。本报告不创建新Gate、不请求重新运行输入未变的全部测试，也不将宿主/递归闭包未覆盖范围伪装成通过。

## 最终输出

```text
Task: NQ-V101-FORMAL-BRIDGE-RELEASE-QUALIFICATION

Baseline:
Source Commit: 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
Source Tree: c323207865b8ef6a978746dd2ae63f15fdcc3ca4

Release Source:
Manifest SHA256: 6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1
Release Source Hash: 58da58497d748e913ccfd5a28f2dc62fd1a5afdf6ef346ee94da4e0f4ac779f8
Secret Scan: PASS

Official Package:
Version: 1.0.1
Schema: 47
Backend Image: sha256:3812ebf7b020445e5c3698e712ff8470d0ffd5b87831b9f81301d1c63f5c5f29
Frontend Image: sha256:014300bbbc2c7966d5b7c8360ee0aaa757472b900dcc6e7dbbe47de8c78ee738
PostgreSQL Image: sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97
Package Manifest SHA256: c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d
Archive SHA256: 477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2 (images-amd64.tar)
Outer ZIP SHA256: 0af219635ab681d254895b7163c6b45c3850732a8b17e1c78b887f94d188c274

Bridge:
Bridge SHA256: 555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8
Baseline Tool SHA256: 523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c
Transition Manifest SHA256: c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56
TRANSITION_HASH: LOCAL_VERIFIED
INDEPENDENT_TRUST_SOURCE: NOT_ESTABLISHED
TRUST_EXCEPTION: APPROVED_FOR_ISOLATED_VALIDATION_ONLY
Independent Trust Anchor: NOT_ESTABLISHED
Preflight: PASS
Execution: COMMITTED / ISOLATED_TRUST_EXCEPTION
Transaction Receipt: 20261024T125624000Z-6775b954 / receipt.json

Upgrade:
Official v1.0.0: PASS
PostgreSQL Transition: PASS / XZ_SECURITY_UPDATE_PRESERVED
V34 → V47: PASS / HISTORICAL_FLYWAY_PREFIX_MATCH
Runtime Role: nq_runtime / RESTRICTED
RLS: PASS
Security Definer: PASS / RESTRICTED_NOLOGIN_OWNER
Historical Facts: PRESERVED_WITH_EXPLAINED_ADDITIONS / RAW_DIFFERENT_RETAINED / MISSING_OLD_KEYS_0 / CHANGED_OLD_FIELDS_0
Canonical SIM: PASS / 1_ORDER_1_TRADE_6_LEDGER_ENTRIES
Installer Handoff: PASS / 7_OF_7
Backup: PASS / 20261024T130325352Z-7b35d4a5
Restore: PASS / FACT_SET_MATCH / POST_BACKUP_FACT_ABSENT
Failure Recovery: PASS / V47_INTERRUPTION_RECOVERED_TO_V34

Platform:
Windows 11 amd64: TECHNICAL_PASS_WITH_ISOLATED_TRUST_EXCEPTION / FORMAL_RELEASE_NOT_QUALIFIED
Bridge Python: TASK_PRIVATE_PSF_3.14.9 / OFFICIAL_ARCHIVE_AND_AUTHENTICODE_VERIFIED
Linux: NOT_QUALIFIED
macOS: NOT_QUALIFIED

Security:
CVE-2026-91403: VERIFIED_MITIGATION / NOT_VENDOR_PATCHED
Remaining Coverage: EXISTING_NON_BLOCKING_HOST_TOOL_RECURSIVE_CLOSURE_AND_REPRODUCIBILITY_LIMITS_RETAINED

Findings:
P0: 0_DEMONSTRATED_WITHIN_SCOPE
P1: 1_FORMAL_RELEASE_GATE / INDEPENDENT_TRUST_ANCHOR_MISSING / NO_NEW_CODE_FINDING
P2: 0_NEW_CODE_FINDINGS / EXISTING_COVERAGE_LIMITS_RETAINED

TECHNICAL_BRIDGE_VALIDATION: PASS
FORMAL_RELEASE_TRUST: NOT_QUALIFIED
RELEASE_CANDIDATE_QUALIFICATION: BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING
Decision: BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING
Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
