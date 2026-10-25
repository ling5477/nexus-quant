# NQ v1.0.1 Release Notes — 候选

**尚未发布。当前决定：PENDING_GIT_DELIVERY。** 本稿可供项目所有者审阅，包内安装说明修复、新候选身份确认及单独产品发布授权完成前，不得作为已发布公告。

本轮核对的正式候选来源为 `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71`，Tree为 `c323207865b8ef6a978746dd2ae63f15fdcc3ca4`。下述技术资格绑定该候选；当前仅有安装说明修复待交付，未创建替代包。最终发布时须使用实际验收的新来源和制品身份，不能把现有隔离签名挪作新候选或产品发布授权。

## Features

- Self-Hosted维持研究、回测、策略发布、Continuous SIM及统一风险、订单、成交、账务路径；Canonical SIM分析沿用真实canonical账户和既有分析接口，缺少身份或事实时不返回虚构零值。
- 多租户基础设施包含Tenant隔离、NQ_LOCAL旧数据归属、资源配额及执行边界；数据库业务进程使用受限角色。
- 源码实现Self-Hosted / Public Demo双发行物，包含Demo注册、邮箱验证、Tenant初始化和幂等模拟资金配置。**本次限定Self-Hosted候选；Public Demo尚未公网正式部署。** Self-Hosted正式镜像排除Demo模块并拒绝公开注册及平台入口。
- Platform Admin相关源码实现只读租户绩效、用户启停、Session撤销和禁用后执行阻断；该能力不代表Self-Hosted新增公开平台入口，也不代表Public Demo具备公网运行资格。
- 版本信息、What's New及版本历史已实现；更新通过宿主官方安装器和用户明确确认，`AUTO_UPDATE=OFF`。阅读标记仅在当前浏览器保存。

## Security

- 本候选包含已验证范围内的Java、npm、Python与Docker依赖补丁；PostgreSQL JDBC为42.7.14，定向回归沿用既有证据。
- CVE-2026-91403采用经过实际Java信任链测试的证书阻断缓解：问题证书变体和完整链拒绝，正常可信链及正常HTTPS接受；覆盖正式构建JDK和发行包最终JRE。
- 结论为 **VERIFIED_MITIGATION**。这不是Adoptium已提供厂商修复二进制或厂商支持的声明。
- PostgreSQL运行时分离迁移/备份身份、`nq_runtime`受限业务身份和`nq_schema_owner`不可登录owner；保留RLS及SECURITY DEFINER权限约束，业务容器不接收维护口令。
- 已完成固定来源导出、完整性校验及Secret Scan。宿主维护、全部递归工具安全闭包和完全可复现构建仍有明确未验证边界；本公告不宣称全平台或全依赖无漏洞。

## Upgrade

- v1.0.0 Schema34正向迁移至v1.0.1 Schema47（V35–V47）；历史Flyway校验和保持。原用户认证、账户及经济事实在已验证实例中保持。
- 本候选同时改变PostgreSQL镜像身份和数据库运行角色协议。**旧版或目标包新版安装器的普通update均不能直接完成这一转换。** `UPDATE_INELIGIBLE`是正确的安全拒绝，不要修改Manifest、运行配置或历史安装器绕过。
- 一次性Legacy Upgrade Bridge及32表比较工具随正式包提供。Bridge需Python3.12或更新版本、Docker Linux Containers和Compose v2；全新安装及常规运维无需宿主Python。
- 执行前必须独立验证Bridge、比较工具、源码、原/目标Manifest、镜像归档及**当前安装实例**的可信过渡授权。`targetArchiveSha256`与`--target-archive`绑定目标包的`images-amd64.tar`，不是外层ZIP。
- Bridge停止业务写入，保留逻辑备份及完整集群副本，验证PostgreSQL镜像过渡及数据库事实后执行角色配置和迁移。事务持久提交后，由新版官方安装器接管常规启动、备份恢复。
- 已验证正式包真实中断后恢复至v1.0.0，以及升级后新版安装器的重启和备份恢复。不要让旧镜像直接接管已升级数据库；失败或未知事务保持停机，通过已绑定的恢复流程处理，不手工移除pending标记。
- **当前可信签名只授权项目 `nq-v101-formal-bridge-20261024`、amd64的固定隔离v1.0.0→v1.0.1实例。** Attestation59432473为所有者签名授权，不是GitHub重新构建发行包的provenance，也不是产品发布许可。
- 一般用户的安装不会自动继承该清单。其他实例需维护者另行核对并通过可信渠道提供绑定自身配置摘要、项目、架构与精确原/目标制品的授权，再完整验签；现有签发工作流固定本实例，不是自动通用签发服务。没有这种授权时不要升级。原始runtime.env、口令或密钥不得上传。

## Qualification and supported scope

当前原候选在 **Windows11 amd64 / Docker Desktop / Linux Containers** 下取得限定技术资格：正式包空库安装、强制改密与重启通过；同包真实Bridge升级、受限角色、RLS、研究/回测/策略发布、Canonical SIM及官方恢复证据通过。新Canonical SIM实际产生1订单、1成交、6条Ledger分录。

这不扩展v1.0.0历史支持矩阵。包内安装说明仍待本轮最小修复的Git交付，正式发行资格尚未完成；最终以修复后候选验收及项目所有者单独决定为准。

## Known Limitations

- **LIVE disabled**；不授予真实交易所写入、真实provider/client或真实资金权限。
- **Public Demo not ready**；真实邮件、HTTPS/TLS、容量及公共行情再展示许可等公网条件未取得正式资格。
- **Linux/macOS not qualified**；ARM64构建能力不等于宿主原生资格。Docker缺失时自动安装及daemon恢复未扩展为官方支持范围。
- **General public legacy upgrade not automatically qualified**；当前隔离实例授权不可复用于其他安装。
- 原始32表严格比较保持`DIFFERENT`：旧主键缺失0、旧业务字段变化0，新增权益观测及定向产品验证事实已逐项记录。`PRESERVED_WITH_EXPLAINED_ADDITIONS`不把原始结果改成MATCH；重启和恢复比较为MATCH。
- 普通账户缺少可信权益来源时不显示虚构收益；真实手续费需要可信来源，OHLC不能提供真实事件发生时点，旧Paper未知币种权益限制保留。
- 历史长时运行观察的定点UI证据缺口按既有所有者例外保留；本次不改写原审查结论，不授予跨平台或无限时运行保证。

许可证为Apache-2.0；使用前阅读随包LICENSE、第三方声明及DISCLAIMER。NexusQuant不提供投资建议。

最终决策与实际证据见 [NQ_V101_FINAL_RELEASE_DECISION.md](NQ_V101_FINAL_RELEASE_DECISION.md)。本稿不创建Tag、Release或部署。
