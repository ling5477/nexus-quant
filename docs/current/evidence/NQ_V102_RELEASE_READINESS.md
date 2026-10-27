# NQ-V102-RELEASE-READINESS

执行：2026-10-27 至 2026-10-28（Asia/Shanghai）。任务类型：`RELEASE_CANDIDATE_PREPARATION`。

**Decision: SOURCE_CANDIDATE_READY**。该结论只允许进入后续准确候选的 Git 交付与正式包资格验证，不是发布许可或包级 PASS。

## 候选与支持范围

| 项目 | 结果 |
| --- | --- |
| Repository | `ling5477/nexus-quant` |
| Starting HEAD / Source HEAD | `73604dd0543bee0c536bd5a6065b8646afa4e346`；另有本报告所述未提交 delta，不把基线 HEAD 称为 v1.0.2 正式源码身份 |
| Branch | `codex/nq-v102-release-readiness`，从本次重新 fetch 后的 `origin/dev` 创建；起始工作区/index 干净 |
| Candidate Version / Schema Target | `1.0.2` / `V49` |
| Final Staged Tree | 精确暂存后由 `git write-tree` 记录于最终交付回执及外部 `staged-identity.json`；报告不自引用其所属 tree |
| Distribution / Host | `NQ_SELF_HOSTED` / Windows 11 amd64 / 已运行 Docker Desktop Linux Containers / PostgreSQL 16 |
| Source Manifest | 工作区静态检查通过；2,208 个唯一且存在的文件，runtime 2,150，migration 49；新增 27 路径，移除 1 个已重命名的失效旧路径 |
| Manifest SHA256 | `bf5430469117ad16e5f606f70b87fbf0558a5c22daeb3ec157ee05a575780a90`；正式 export hash 尚不存在 |
| Immutable Export / Package | `NOT_RUN`；没有为未提交源码制造 Git Commit、`.release-source.json` 或候选 Package Manifest |
| LIVE / Real Exchange Write / Auto Strategy Publish | `DISABLED / DISABLED / DISABLED` |
| Canonical Order / Trade / Ledger | `UNCHANGED`；未改动交易事实、风控或记账算法 |

不包含 Public Demo 公网、Linux/macOS 资格、ARM64、v1.0.0 直接升级、真实交易、新交易功能或历史全量审计。没有重跑 72 小时观察。

## 已发布事实与历史资格

通过 GitHub 连接器只读确认 [v1.0.1 Release](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.1)：Release ID `423414604`，2026-10-25T13:19:00Z 发布，draft=false、prerelease=false。核对正式原始 ZIP、原包 21 个文件、TAR 和不可变导出 2,182 个文件。

| 身份 | v1.0.1 原始值 |
| --- | --- |
| Tag Object | `1670d6a853b8f7b00d0727c818cf372ef7a23532` |
| 独立 Snapshot / Tree | `48094e289436bc8b744bbfeb7488f7419fe00ff3` / `d5c3e2b20777440503339d467a84ccf6e968de33` |
| 包 SOURCE_SHA / 原开发 Tree | `b0433fca6a9ce86d8b9c34abed2be2e48f25a3ae` / `3b77583e0e1e0d2b52ccfa0cade1588d0075bc45` |
| 原 Release Source Hash | `c4787a97cccbe83928f5e05148d13afb084695e4b5989d1234a39c4247d7845c` |
| Package Manifest SHA256 | `f0beba4f37481c85ff1677e8c546f10deaeba57fb5144f69f5d7edcd839ef5db` |
| images-amd64.tar SHA256 | `7248a47823571fb112ea91f317e092cb6c47108b934d90ef52c299adb7c9e006` |
| 官方 ZIP SHA256 | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb`；与 GitHub asset digest、实际原始字节一致 |
| Schema / PG | `V47` / `16.15` |

原源码与包目录为 `E:/nq-v101-final-refreeze-20261025-b0433fca/`，官方 ZIP 位于 `E:/nq-v101-official-github-release-delivery-20261025/assets/`。来源验证使用现有 `release_source.py verify`，不是重新导出或改写历史文件。v1.0.0 与 v1.0.1 的 Tag、Snapshot、包、证明文件均未修改。

`release/support-matrix.json` 保留 v1.0.0 所有权和原始字节，SHA256=`66fc528361e9aa03b600fce3087379ef60a8374f34b225df686c77a39ac2191b`。新候选通过现有 VERSION、逐文件 source manifest、INSTALL 及本报告表达准备状态，不把旧矩阵改为新版本 PASS。历史 C4 原审查 `NOT_ACCEPTED / EVIDENCE_GAPS`、所有者例外及 72 小时重跑豁免独立保留。

## 最小源码准备与构建阻断修复

- VERSION、CHANGELOG、README、INSTALL、前端结构化版本记录同步 v1.0.2 候选及五阶段产品变化；v1.0.1 的已发布事实来自正式 Release，原历史资格报告保持原样。
- source manifest 显式新增已完成业务的 27 个正式文件，包括 V48/V49；失效的 `SpotSmaVersionForm.tsx` 由原 `c76614d…` 提交 R064 重命名为 `SpotTargetVersionForm.tsx`，只移除清单中的旧路径。
- 工作区静态清单检查：所有选中文件存在、VERSION 对应、迁移与运行覆盖零遗漏、法律文件和发行语义/开发引用检查零错误。该结果为 `WORKSPACE_STATIC_ONLY_NOT_FORMAL_EXPORT`；正式导出验证、扫描及干净导出树构建仍须绑定未来准确提交。

发现实际阻断：当前本机 `postgres:16.15` 的 config digest 为 `sha256:275447c94b11b151decd1f29877965301d5a77032037c46de93d990f739f00a9`，与正式 v1.0.1 的 `sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b` 不同。直接凭标签构建不能证明普通更新相容；新 pull 结果也必须重新核验。

最小修复为构建器增加成对的 `-PostgresImage` / `-ExpectedPostgresConfigDigest`。显式输入只接受 immutable SHA256 和单架构，不拉取浮动标签；使用包版本局部别名供 Docker archive 关联，归档验证后对照预期 config，失败发生在生成 Manifest 前。INSTALL 给出复用可信 v1.0.1 原包镜像的准确命令。原安装器、可信校验和角色协议未改动。构建器原默认模式保留，但不作为本次普通更新方案。

真实历史三镜像归档验证通过，config/layers/native index/platform manifest 绑定正确；错误 config、缺少配对参数、浮动输入、多架构复用和工作区构建均拒绝。该归档是 **历史镜像构建合同检查**，没有生成或宣称 v1.0.2 候选镜像/安装包。

## 非空数据库升级与保留

独立容器 `nq-v102-readiness-pg16-20261027`，loopback `55446`，实际 PostgreSQL 16.15；没有操作共享数据库或真实用户安装。源数据为原隔离 v1.0.1 技术资格实例的非空 V47 逻辑备份：

```text
E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/installation/backups/20261024T130325352Z-7b35d4a5/database.dump
SHA256=f7a6edadb08d1f868bc3c4bcc9d641442eda774b2c9c000c37657b2fdac82d16
```

它属于较早 `1dc5ea7e…` 技术候选的测试实例，不冒充已发布 b0433fca 包的新实际升级。V1–V47 共 47 个 SQL 与已发布原包来源逐字节一致；现有 maintenance 源码也与正式发行来源一致。使用当前源码 maintenance 的同一 `prepare` 路径，依次 V47→V48→V49，每步迁移并 validate，再验证运行身份；重复迁移为 0。

| 覆盖 | 实际结果 |
| --- | --- |
| 原始数据未被预处理隐藏 | 另将原 dump 原样恢复至 `nq_readiness_raw47`，不先调用 maintenance；与最终 `nq_readiness_retry2` 的 120 张旧表逐原字段完整比较，全部相同 |
| 用户与密码状态 | users=1、auth_sessions=3；整行比较包含原密码编码、强制改密、认证状态与会话事实；未输出原值或认证摘要 |
| 租户、账户归属 | 租户、membership、账户、exchange account 等整行保持；accounts=4、exchange_accounts=1 |
| 策略与发布 | strategy_versions=1、backtest_publish_records=2；原身份、快照、状态与关联保持 |
| 回测与评价 | backtest_runs=2、backtest_eval_reports=2；原结果完整保持 |
| SIM 与经济事实 | paper runs=3、continuous runs=1、continuous bars=74；canonical orders=2、trades=2、ledger_entries=14；SIM历史交易与144条PnL快照等完整保持 |
| 迁移历史 | V1–V47 checksum/version/success 前缀相同；最终49条成功历史、pending=0；Flyway validate通过、repeat migrate=0 |
| 旧 Owner / RLS / ACL | maintenance 重建源恢复副本的正式权限后，升级前后旧表 Owner、ENABLE/FORCE RLS、ACL 完全一致；runtime 无SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/REPLICATION |
| 新表 | 实验、trial、attempt及验证、step、holdout view共6表，均由`nq_schema_owner`拥有，ENABLE/FORCE RLS；`nq_runtime`有规定CRUD权限，无租户上下文读取为0 |
| 权限负例 | SET ROLE owner、内部工作表读取、租户控制写入、public建表、平台SECURITY DEFINER调用权限共5类拒绝；真实owner/跨租户HTTP负例另由本次PG集成测试覆盖 |
| 允许的Schema变化 | 仅按原V48/V49；旧表比较只排除新增`tenant_background_work.experiment_id`，旧字段全部参与；新增EXPERIMENT扫描游标单独检查恰1条。没有忽略原业务列 |

未执行 Flyway Repair、未修改任何 migration、未删除原始业务数据。恢复副本中的角色/ACL 重建沿用正式 maintenance 合同，不把 `--no-owner --no-acl` 的原始恢复状态冒充正式运行身份。

## 普通更新、备份与恢复合同

**v1.0.1→v1.0.2：源码合同层面可沿用正式普通 update，条件是准确目标包复用可信原PG config及角色协议。实际发行包更新资格为 NOT_RUN。**

现有 PS/POSIX 安装器的 Git blobs、compose/runtime 与正式 v1.0.1 包一致；没有放宽 Manifest 校验。检查顺序仍为可信目标包/资产→加载验证→停止业务写入→完整备份并验证→PREPARED/pending→切换绑定资产/config→DB_MAY_CHANGE→maintenance迁移/授权→runtime身份/schema/health→成功receipt。版本递增、Schema单调及PG config相同均必须成立。

数据库可能改变后的失败沿已绑定事务恢复完整数据库与旧资产；未知journal保持fail-closed。不能只替换旧应用镜像接管V49。正常restore要求相同版本/schema/包身份；rollback只接受已知事务receipt和对应备份。v1.0.0直接升级不属于本合同，历史Bridge不提供新授权。

本次源码层另实际创建升级前V47逻辑备份，恢复至 `nq_readiness_restored47`，通过原maintenance恢复正式角色合同后，120旧表、Owner/ACL/RLS与备份时点一致。这只证明 **SOURCE_LOGICAL_BACKUP_RESTORE_PASS**；没有执行新候选完整安装器备份集、实际更新失败注入、旧应用回滚启动或包级恢复资格。

## 回归、独立审查与证据

原始执行材料保留于 `E:/nq-v102-release-readiness-20261027/`。数据库dump及认证材料不进入Git；报告只保留计数、匹配结论和公开制品身份。

| Validation | 结果与范围 |
| --- | --- |
| Backend | `backend-targeted-retry.log`：11 tests，0 failures/errors/skips，BUILD SUCCESS；maintenance3、比较PG2、绩效比较3、Walk-Forward policy3 |
| PostgreSQL | `migration-upgrade-final.log`及`raw-v47-to-v49-preservation.json`：上述非空升级、120表保留、运行权限与逻辑恢复通过 |
| Frontend | `frontend-build-final.log`：tsc/Vite通过；`frontend-release-tests-final.log`：版本/实际安装身份/中英文/阅读标记4项通过；保留既有bundle提示 |
| Release Source Tests | `release-source-tests-final.log`：29项通过；`transition-tests-final.log`：12项通过，显式UTF-8运行 |
| Installer Contract Suite | `release-contract-tests.log`：116项执行、1项POSIX真实文件权限条件跳过；模拟安装器测试不是Windows真实包资格。GBK子进程读取异常单列保留，唯一受影响handoff测试以显式UTF-8补跑，不用早期日志宣称无异常 |
| Release Build Contract | `package-builder-contract-retry.log`：真实历史镜像归档及错误config拒绝通过；3类非法复用输入和工作区构建拒绝通过；不是候选包构建 |
| Workspace Source Checks | `workspace-static.json`：2,208文件、runtime2150/migration49、零遗漏、零发行语义错误；不是正式export PASS |
| Authority / Links / Diff | 使用当前authority、相关文档链接检查及`git diff --cached --check`，最终结果见外部日志与暂存回执 |
| Core E2E | 本次真实Servlet/PG比较回归通过；整条新浏览器链未重跑。复用下列五阶段及集成基线的有效证据，不转移为新包资格 |
| Baseline CI | [40529901477](https://github.com/ling5477/nexus-quant/actions/runs/40529901477)：push、HEAD=`73604dd…`、completed/success，9/9；只绑定基线，新未提交候选CI=NOT_RUN |

复用链路及限制：

- [Dataset](NQ_V102_01_RESEARCH_DATASET_PRODUCTIZATION.md)：有界接入/质量/冻结消费；[Strategy](NQ_V102_02_STRATEGY_EXECUTION_EXPANSION.md)：SMA/通道、HOLD、冻结输入与SIM重启恢复。
- [Experiment](NQ_V102_03_STRATEGY_EXPERIMENT_ORCHESTRATION.md)：持久试验、尝试/取消、租户权限、真实JVM恢复及浏览器链；[Walk-Forward](NQ_V102_04_WALK_FORWARD_AND_ROBUSTNESS.md)：三折、留出、成本重算、时间反事实和真实JVM恢复。
- [Comparison](NQ_V102_05_BACKTEST_SIM_COMPARISON.md)：原始研究/发布/Continuous SIM身份、只读权限、冻结血缘、费用覆盖和事实不足。五阶段已包含在当前基线，源码准备没有改变上述业务逻辑；原报告中的未提交文字保留各自历史时点身份。
- 数据仍限短样本；已观察的holdout不变成从未观察样本，独立FLAT窗口收益不串接成实际连续账户，缺失经济事实不编造零值。自然无交易、不可比与失败结果保留。

一次独立限定审查覆盖发行白名单、原迁移/安装器/角色身份、更新与恢复控制，新增构建入口后仅补审受影响控制。审查者自行运行负例、独立验证实际历史归档三组件绑定，并只读核验任务PG的V49/六表权限和旧经济计数。无未关闭actionable finding；未构建review-of-review链。审查绑定builder SHA256=`be25e13ca07d28a36ea06c04d47e87565de041feef35f7faa9dbea89579b760c`，test SHA256=`b41517d8a75a660b0844d20e313257ac70b98932081153131abe278d9e1df0b8`。

## 失败保留与发现

已关闭：白名单遗漏及失效改名路径；浮动PG与原包config不兼容的构建输入缺口。未降低原安装器安全条件。

执行夹具历史分别保留：初次恢复角色遗漏NOINHERIT被正确拒绝；初版快照重复建立连接耗尽本机临时端口；初版整行比较把V48新增空字段当数据变化。修正仅涉及本次外部验证helper，使用新数据库重跑，最终另对未经maintenance的原dump副本比较原字段；历史失败不追认PASS。文档机器next-action曾同时匹配RELEASE/REVIEW，已改为唯一COMMIT动作token，最终authority checker通过。

NON_BLOCKING：模拟套件有GBK读取问题及一项POSIX文件系统条件跳过；既有Vite bundle提示；构建合同测试保留局部镜像别名和归档。它们不赋予新OS、包级或LIVE资格。

任务数据库、备份、原包与关键证据保留；本任务PG在验证结束后停止。未删除共享容器、Volume、用户worktree或历史资产，未执行全局清理。该任务未进行Git发布，Post-merge Cleanup不适用，不宣称DELIVERED/CLEANUP_COMPLETE。

## 验收与下一步

```text
Task: NQ-V102-RELEASE-READINESS
Version: 1.0.2
Source HEAD: 73604dd0543bee0c536bd5a6065b8646afa4e346 + UNCOMMITTED_STAGED_DELTA
Schema Target: V49
Source Manifest: WORKSPACE_STATIC_PASS / FORMAL_EXPORT_NOT_RUN
Migration Compatibility: SOURCE_V47_TO_V48_TO_V49_PASS
Existing Data Preservation: PASS / 120 ORIGINAL TABLES
Runtime Permission: PASS / SOURCE_ISOLATED_PG16
Upgrade Contract: SOURCE_COMPATIBLE_WITH_TRUSTED_PG_REUSE
Fresh Install Qualification: NOT_RUN
Actual Package Upgrade Qualification: NOT_RUN
Backup / Restore: SOURCE_LOGICAL_PASS / PACKAGE_NOT_RUN
Rollback: CONTRACT_CHECKED / ACTUAL_PACKAGE_NOT_RUN
Core E2E: TARGETED_PG_HTTP_PASS / PRIOR_EFFECTIVE_EVIDENCE_REUSED / NEW_BROWSER_CHAIN_NOT_RUN
Release Build Contract: TARGETED_PASS / CANDIDATE_PACKAGE_BUILD_NOT_RUN
LIVE Boundary: DISABLED
Blockers: NONE_FOR_SOURCE_PREPARATION
Unverified Items: EXACT_COMMIT_CI, VERIFIED_IMMUTABLE_EXPORT, SECRET_SCAN_OF_EXPORT,
  CLEAN_EXPORT_BUILD, CANDIDATE_IMAGES_AND_MANIFEST, FRESH_INSTALL, ACTUAL_PACKAGE_UPGRADE,
  PACKAGE_BACKUP_RESTORE, FAILURE_RECOVERY_AND_ROLLBACK
Decision: SOURCE_CANDIDATE_READY
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
LIVE: DISABLED
```

停止于源码准备和精确暂存。后续经明确Git交付授权，取得准确提交及其CI、验证不可变导出并扫描/构建，按INSTALL保留可信PG输入生成真实新候选，再开展全新安装、非空v1.0.1普通更新、备份恢复和失败回滚资格。待取得这些证据前不能宣布正式发行就绪，不创建Tag、Release或生产部署。
