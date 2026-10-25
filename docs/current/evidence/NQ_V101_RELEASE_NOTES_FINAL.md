# NexusQuant v1.0.1

本文件为正式 Release Notes 定稿，供所有者批准后的 GitHub Release 使用。当前尚未发布；产品发布授权待所有者明确决定，Tag 与 GitHub Release 均未创建。

## 正式支持范围与安装

本次发行 NexusQuant Self-Hosted，正式支持范围为 **Windows 11 amd64 / 已安装且正在运行的 Docker Desktop / Linux Containers / Docker Compose v2**，数据库为 PostgreSQL 16.15、Flyway Schema V47。资格状态为 `QUALIFIED_FOR_VERIFIED_SCOPE`。只承诺已验证的现货研究与 SIM 范围。

下载 `NexusQuant-1.0.1-windows-amd64.zip` 和同名 `.sha256`，校验后完整解压，按包内 `INSTALL.md` 使用 Windows 安装入口。GitHub 自动生成的源码归档不能替代包含冻结镜像的安装包。全新安装和常规运维无需宿主 Java、Maven、Node、PostgreSQL 或 Python；Legacy Upgrade Bridge 另需 Python 3.12 或更新版本。

## Features

- Self-Hosted 提供研究、回测、策略发布、Continuous SIM 与运行监控，风险、订单、成交和账务由 Java Control Plane 统一管理。Canonical SIM 使用实际 canonical 账户及既有分析接口，缺少可信身份或事实时不返回虚假零值。
- 多租户基础设施包含 Tenant 隔离、NQ_LOCAL 历史数据归属、注册限流及任务资源配额，并配合受限数据库运行身份。
- 版本信息、What's New 与版本历史已实现；阅读标记保存在当前浏览器。更新默认 `AUTO_UPDATE=OFF`，经宿主官方安装器和用户明确确认执行。
- Platform Admin 已实现只读租户绩效、用户启停、Session 撤销及停用后的执行阻断。这些属于 Public Demo 发行边界；本次 Self-Hosted 镜像排除 Demo 模块，拒绝公开注册和平台入口，不承诺 Self-Hosted 平台管理服务。
- Public Demo 的注册、邮箱验证、Tenant 初始化与幂等模拟资金能力已实现，**尚未取得公网部署资格，不包含在本次发行中**。

## Security

- 包含已验证范围内的 Java、npm、Python 和 Docker 安全补丁；PostgreSQL JDBC 为 **42.7.14**。Python research 保持隔离，不进入产品运行时；Bridge 的解释器由宿主另行提供。
- PostgreSQL 业务身份为 `nq_runtime`，无 SUPERUSER / BYPASSRLS / CREATEDB / CREATEROLE / REPLICATION；`nq_schema_owner` 为受限 NOLOGIN owner。维护与业务口令分离，保留 RLS / FORCE RLS 和 SECURITY DEFINER 权限边界。
- **CVE-2026-91403: VERIFIED_MITIGATION**。构建 JDK 与最终 JRE 的证书阻断策略已有实际问题链、编码变体、正常可信链和 HTTPS 正控制证据。本候选通过相同运行层及关键 JRE 文件字节证明继承。此结论不表示 Adoptium 已提供厂商修复二进制或厂商支持承诺。
- 固定 Git 来源导出和源码 Secret Scan 已通过。宿主 PowerShell/.NET 维护、全部递归工具依赖安全闭包、SBOM 完整覆盖及完全可复现构建仍保留未验证边界，不声明全栈或全宿主无漏洞。

## Upgrade

**v1.0.0 → v1.0.1 的首次跨协议升级，不能直接使用旧版或目标包新版安装器的普通 `update`。** PostgreSQL 镜像身份和数据库角色协议发生变化，原 v1.0.0 安装器也不包含新版 maintenance 路径。`UPDATE_INELIGIBLE` 是正确的安全拒绝，不得修改 Manifest、配置或旧安装器绕过。

必须使用包内 **Legacy Upgrade Bridge**，并具备 Python 3.12+、Docker Linux Containers、Docker Compose v2、完整可信过渡清单、**当前安装实例的独立可信授权**以及用户明确确认。`targetArchiveSha256` 和 `--target-archive` 对应目标包 `images-amd64.tar`，不能填写外层 ZIP 的摘要或路径。

Bridge 先停止业务写入，保留逻辑备份与完整集群副本，验证 PostgreSQL 镜像转换及历史事实，再正向迁移 V34→V47、配置角色并持久提交交接。旧用户认证、账户和经济事实保持已有技术证明；新版安装器重启、备份恢复和数据库变化后的中断恢复已有真实执行证据。未知事务或失败时保持停机，通过已绑定的恢复路径处理，不手工移除 pending 标记，也不让旧镜像直接接管升级后的数据库。

当前候选的 Bridge、PG 转换、业务链、Canonical SIM、备份恢复和失败恢复资格为 **`INHERITED_WITH_DELTA_PROOF`**：运行平台 Manifest、config、全部运行层、JAR、109 个前端文件、47 个 SQL、Bridge、安装器和 runtime 均与已实测候选一致。新候选独立完成了全新安装、强制改密、受限角色、RLS、V47、版本身份和重启验证；**没有重新执行新候选 Bridge 升级**。不得把继承资格表述为一次新的实际升级测量。

Attestation **59600294** 仅授权项目 `nq-v101-refreeze-legacy-20261025` 的固定隔离安装实例，签名范围为 `ISOLATED_V100_TO_V101_UPGRADE`，`productReleaseAuthorized=false`、`liveAuthorized=false`。它是实例升级授权，不是产品发布许可或构建 provenance；该登记实例尚未执行本次新 Bridge。

**普通用户不会自动获得 Legacy Upgrade 授权。当前签发工作流固定上述实例，不提供通用实例级签发服务；本次发行不承诺该服务已可用。** 其他安装必须另行取得维护者经可信渠道签发、绑定其自身配置摘要、项目、架构及准确原/目标制品的授权并完整验签。没有有效的自身实例授权时，不得升级。不要上传原始 runtime.env、口令或密钥。发布本版本和下载包均不构成升级授权。

## Known Limitations

- Linux / macOS 为 `NOT_QUALIFIED`，ARM64 未取得正式运行资格；包内其他平台脚本的存在不构成支持承诺。Docker 自动安装和 daemon 恢复不在本次正式支持范围。
- Public Demo 为 `NOT_READY`，真实邮件、HTTPS/TLS、容量和公共行情再展示许可等公网条件未取得正式资格。
- LIVE 为 **DISABLED**，不开放真实 provider/client、真实交易所写入、真实资金操作或自动 LIVE 执行。不包含期货/杠杆、Binance 生产支持、AI 交易或 HFT。
- 原 32 表严格比较保留 `DIFFERENT`：旧主键缺失 0、旧业务字段变化 0；新增权益观测及授权产品验证事实均已解释。`PRESERVED_WITH_EXPLAINED_ADDITIONS` 不把原始结果改为 MATCH；原实测重启和恢复比较为 MATCH。
- 普通账户缺少可信外部权益/收益来源时不虚构收益；缺少可信真实手续费来源时不伪造实际手续费；OHLC 不证明真实事件时点；旧 Paper 未知币种权益保持未知。
- 历史 C4 原审查仍为 `NOT_ACCEPTED / EVIDENCE_GAPS`，缺 T+12/18/36/60h 定点 UI 证据。所有者既有例外 `ACCEPT_WITH_EVIDENCE_EXCEPTION`、72 小时重跑 `WAIVED` 和 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 保留，不宣称 C4 独立 PASS。

源码 Snapshot 中的 `release/support-matrix.json` 记录 v1.0.0 历史资格；运行安装 ZIP 按既有打包合同不包含该源码文件。README / CHANGELOG 中的预发布或旧资格措辞保留冻结时点身份，本 Release Notes 表达 v1.0.1 的限定发行声明，不扩大旧矩阵，也不改写历史失败记录。

## 不可变来源与制品

| 对象 | 冻结身份 |
| --- | --- |
| Release Source Commit / 包 SOURCE_SHA | `b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae` |
| Release Source Tree | `3b77583e0e1e0d2b52ccfa0cade1588d0075bc45` |
| Release Source Hash | `c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c` |
| Package Manifest / Release ID | `f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db` |
| images-amd64.tar SHA256 | `7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006` |
| 下载 ZIP SHA256 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |

签名工作流 Commit 为 `99f63020c62a89926fbe79f9322532a37ce3ed1b`，与发行源码 Commit 分别记录。[PR #81](https://github.com/ling5477/nexus-quant/pull/81) 合并后 [CI 40164710397](https://github.com/ling5477/nexus-quant/actions/runs/40164710397) 为精确该 Commit 的 9/9 SUCCESS；发行源码本身的 [CI 40157573104](https://github.com/ling5477/nexus-quant/actions/runs/40157573104) 与之分开记录。

后续 Annotated Tag `v1.0.1` 应指向独立白名单源码 Snapshot，包含 2,182 个正式文件及唯一生成的 `.release-source.json`，预期 Tree 为 `d5c3e2b20777440503339d467a84ccf6e968de33`；其 metadata 仍绑定上表发行源码 Commit。当前 Snapshot Commit 和 Tag 对象均未创建，后续交付须记录实际对象，不能把签名工作流合并 Commit 作为发行 Snapshot。该计划不改变包 SOURCE_SHA。

许可证为 **Apache-2.0**。使用前阅读随包 `LICENSE`、`THIRD_PARTY_NOTICES.md` 和 `DISCLAIMER.md`。NexusQuant 不提供投资建议或持续盈利承诺。
