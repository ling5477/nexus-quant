# NQ-V101-SELF-HOSTED-UPGRADE-AND-RELEASE-UX

执行日期：2026-10-23（Asia/Shanghai）。结论：`BLOCKED`；更新体验已实现并完成定向验证，正式跨版本升级尚未执行，不能接受为 Release Candidate。

## 基线与候选

- Branch：`codex/nq-v101-self-hosted-upgrade-release-ux`。
- HEAD / 最新 fetch 后的 origin/dev：`137ab44cadaa56c6740a0d085aa2902027a22b1d`。
- 前置 PR [#73](https://github.com/ling5477/nexus-quant/pull/73) 已合并；Post-merge CI `39815272057`，同一 head、completed/success。该历史 CI 不替代本工作区候选 CI。
- 产品目标 `VERSION=1.0.1`；源码清单目标同步为 1.0.1。当前变更未提交，无独立候选 Git SHA。
- 当前工作区逐文件摘要见本机 `C:/nq-v101-upgrade-20261023/evidence/candidate-worktree-final.json`；脱敏证据索引见 [JSON](NQ_V101_SELF_HOSTED_UPGRADE_AND_RELEASE_UX.index.json)。没有新 Tag、Release 或公网部署。

## 实际实现

1. 结构化双语版本记录包含 Version、Release Date、Features、Improvements、Bug Fixes、Breaking Changes、Known Issues、Release URL。1.0.0 指向真实已发布 Release；1.0.1 标记 CANDIDATE，日期和 Release URL 为空。候选功能对应已合并代码，当前更新体验在说明中明确仍待候选资格，不算作已发布功能。
2. 修正 README、INSTALL、CHANGELOG 中 1.0.0 尚未发布的旧描述。GitHub 只读复核：publishedAt=`2026-10-20T17:13:02Z`，非 draft、非 prerelease；文档发布日期明确采用 UTC。没有改写历史证据或 STATUS 中旧机器 authority。
3. 设置 → 版本与更新可随时查看版本历史。可信清单交接后展示对应版本说明；未知版本明确提示从可信发行渠道阅读，不把内置候选记录作为“发现可更新”的证据。
4. 复用既有 manifest SHA256 校验、宿主 CHECK 回执、明确确认、下载原 UPDATE 请求及宿主安装器流程。未新增 Web 执行宿主命令权限。只有原 CHECKED / UPDATE_ELIGIBLE 回执能开启确认；失败、未知回执不能显示成功，成功仍需既有当前运行版本及健康核对。
5. 首次登录的 What's New 读取真实 `/system/version` 安装身份；未知身份、未知版本、临时 userId=0、强制改密或版本读取失败时不提示。阅读键绑定用户、安装包摘要与安装版本；同一键确认后刷新/登录不重复，切换用户或实际安装版本分别处理。阅读状态保存在当前浏览器，不是跨设备同步或安装成功凭证。存储失败明确提示并允许本次关闭。
6. Public Demo 不渲染本地安装步骤、交接表单或 Self-Hosted 登录更新提示。
7. 发布源码验证器不再硬编码 1.0.0，要求目标为受限三段版本号并与同一 commit 的 VERSION 一致。完整 40 位 commit、Git blobs only、逐文件来源、摘要及制品完整性合同保留；新增两个正式前端文件精确加入清单。

## 定向验证

| 项目 | 真实结果与边界 |
| --- | --- |
| Frontend Unit Tests | 89 文件 / 682 项通过；另有独立审查复现三个目标文件 12/12 |
| 更新 UI Playwright | Microsoft Edge，5/5；中英文历史/提示、同用户去重、不同用户及版本重新提示、未知版本/读取失败、存储写入失败、取消确认、失败回执不报成功；全部为明确 API/清单界面夹具，不是实际安装证明 |
| Self-Hosted Production Build | `tsc -b && vite build` 通过；发行标记 `NQ_SELF_HOSTED`，现有构建插件对 Demo/platform 模块泄漏的拒绝检查通过；保留既有大 chunk warning |
| 发布源码测试 | 29/29；同提交版本匹配/错配、不可变 Git 身份、路径/覆盖/法律与依赖边界 |
| 32 表比较工具回归 | 7/7；缺失/新增/变更、金额精度、Flyway checksum/失败记录等反例 |
| 阶段资产检查 | `scanned=2676 reviewed_exceptions=87 errors=0`；只同步已审测试文件的一条规范化 SHA，87 条例外及规则未改变 |
| diff 检查 | `git diff --check` 通过；未暂存 |
| 新候选 CI | NOT_RUN；未提交/推送 |

定向独立审查只读复核了来源版本校验、更新信任边界、Public Demo 操作隔离、What's New 身份绑定、说明真实性及本报告的升级资格边界。未发现已实现 UI/来源改动的 P0/P1；另确认下述官方运行角色 P1。摘要提案曾被误判过期，复核原 guard 的去注释规范化规则后撤销：proposal.candidate 的 raw SHA 与文件匹配、proposal.policy 的规范化 SHA 与 guard.inspect 匹配，完整 propose 重建相等；没有据误判修改规则。

## v1.0.0 正式安装与原备份恢复实证

原正式包只读：`C:/nq-v1-freeze-20261020-565764cc/package-amd64-frozen`。按正式 provenance 逐文件核验全部一致。

| 身份 | 值 |
| --- | --- |
| v1.0.0 snapshot | `be816342581c8ab09a2c9b70dd8e9d1c88dcec8c` |
| 正式包 SOURCE_SHA | `5231266d366680ed5c6c1e38ab84adb33ffcd644` |
| 正式包 manifest SHA256 | `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1` |
| 既有隔离备份 | `C:/nq-v101-usage-20261021/installation/backups/20261021T033822453Z-787c414a` |
| Backup manifest SHA256 | `647475bda0120321ba75ac41e715bf258d475bc40e94bd6d8268841a0f82b6dc` |
| database.dump SHA256 | `d2e95cb40d3b64017cbd6eddadfb3ba0fdf3326e63aac29e61bb0554ff5b35c2` |
| 新任务独立安装 | `C:/nq-v101-upgrade-20261023/installation` |
| Compose project / HTTP | `nq-v101-upgrade-20261023` / UI 18980 / API 18988 |

执行原正式安装器 install → inspect Compose project/PG mount 确认任务所有权 → restore 原备份 → 32 表 capture/compare → 旧用户正式登录 → 原 restart → 再捕获比较及登录。没有连接真实用户数据库，也没有手工迁移数据。

- PG16，Flyway V1–V34。恢复后与旧基线 32 表严格 `MATCH / exit0`。
- 重启后原主键缺失=0、原字段摘要变更=0、Flyway 历史相同；users 表只保留指纹，重启前后相同，旧认证可登录。
- 严格重启比较保留 `DIFFERENT / exit1`：`account_equity_measurements` 新增 sample=`b98a42ff-cb10-49e3-9425-10c2af5af5fe`、bucket=`1991907`、SIM exchangeAccountId=1、USDT、NO_BALANCE、equity=NULL。原观测 bucket=1991725。新时间桶观测不是资金分录；不删除新行、不过滤差异或将严格结果改为 MATCH。
- 原安装器实际 check-update 同一正式包返回 `updateAvailable=False; AUTO_UPDATE=OFF`；update 同版本被拒绝（exit1），pending-update 不存在、transactions 目录内事务数=0。该证据只覆盖旧安装器更新前拒绝边界，不能冒充 v1.0.1 失败恢复资格。
- 新任务容器及网络已通过原 uninstall（不 purge）清理；独立数据、备份与证据保留。未删除用户已有资源。

## 未完成的真实升级与阻断

### BLOCKING：没有合规的 v1.0.1 制品来源身份

现有 `release/build-package.ps1:18` 只接受已核验的 immutable release-source export；`scripts/release/release_source.py` 只导出明确 commit 的 Git blobs。用户明确要求不自动 Commit，而实现仍在工作区。

已实际调用工作区打包器，按预期 exit1：`Build from a verified immutable release-source export`；输出目录未创建。未伪造 SOURCE_SHA、未拼包、未绕过校验，也未创建独立 snapshot commit。

因此以下全部为 NOT_RUN / BLOCKED：v1.0.1 候选包构建、正式安装器跨版本更新、V34→当前 V47 正向迁移、NQ_LOCAL 旧数据归属、升级后旧用户/订单/成交/Ledger/资金与32表旧字段保持、新 Schema 使用、候选包 Controller 排除检查、升级后研究/回测/SIM、候选更新失败/回滚及恢复。旧版本恢复 MATCH 和 UI fixture 不能替代这些证明。

### P1：官方 installer/runtime 角色与新 Tenant guard 不兼容

`release/runtime/compose.yml` 将 `POSTGRES_USER=nexusquant` 同时作为 `NQ_PROD_DB_USER`；runtime.yml 开启 Flyway，但安装器没有独立迁移身份、受限 NOLOGIN owner 与应用运行角色转换路径。

在本任务恢复的 PG16 中只读实测此用户：SUPERUSER=true、BYPASSRLS=true、CREATEROLE=true、CREATEDB=true、public schema CREATE=true。当前 `TenantPersistenceConfiguration.tenantRuntimeRoleGuard` 与 `TenantTransactionManager.requireRestrictedRole` 明确拒绝上述角色。独立审查确认配置与守卫冲突；这是实际配置属性加静态启动合同证明，**没有宣称已经执行并观察到 v1.0.1 升级失败**。

该兼容缺口未修复。不能弱化 Tenant guard、通过手工改测试库或复用上一 Docker 资格夹具的受限角色来掩盖正式安装器缺少转换路径。修复及其 forward ownership、迁移/运行分离、旧认证/业务事实保存和事务回滚验证需要合规候选包才能在正式路径下完成。

P0：未发现。P2：What's New 阅读标记仅限当前浏览器；其他平台资格与既有外部数据限制保留。Public Demo 的真实邮件、TLS、容量及行情再展示授权仍未取得公网资格。

## 最终验收

```text
VERSION_TARGET: v1.0.1
SELF_HOSTED_UPGRADE: BLOCKED
HISTORICAL_DATA_PRESERVATION: BLOCKED
FLYWAY_UPGRADE: BLOCKED
RELEASE_NOTES: IMPLEMENTED
UPDATE_CONFIRMATION: IMPLEMENTED (REUSED_HOST_INSTALLER_HANDOFF)
WHATS_NEW: IMPLEMENTED
VERSION_HISTORY: IMPLEMENTED
AUTO_INSTALL: DISABLED
PUBLIC_DEMO_DEPLOYMENT: NOT_READY
LIVE: DISABLED
Decision: BLOCKED / immutable candidate identity unavailable; official installer runtime role incompatible with Tenant guard
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
```

修改范围：README/INSTALL/CHANGELOG/VERSION；两个既有版本面板、ConsoleLayout、双语资源；新增结构化版本记录及版本历史/提示组件；对应 unit/E2E；精确源码清单与版本校验及反例；一条必要测试摘要同步；本报告与脱敏证据索引。没有修改历史 Flyway、Tenant/Registration/SIM/Ledger 实现或正式 v1.0.0 制品。
