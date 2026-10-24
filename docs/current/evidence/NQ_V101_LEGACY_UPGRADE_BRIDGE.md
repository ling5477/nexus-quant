# NQ-V101-LEGACY-UPGRADE-BRIDGE

日期：2026-10-24（Asia/Shanghai）。结论：**IMPLEMENTED / TARGETED_VALIDATION_PASS / PENDING_GIT_DELIVERY**。

完成独立桥接入口、定向测试和一次限定独立正确性审查。在任务隔离安装中实际完成官方 v1.0.0 → 既有官方来源 v1.0.1 候选的 PG 镜像、角色和 V34→V47 过渡，并验证真实恢复、研究/回测/Canonical SIM、新安装器接管。桥接代码尚未提交，过渡清单明确标记 `bridgeSourceCommit=UNCOMMITTED`；**这不是正式不可变发行资格**。既有目标包未修改，也不被称为包含本次修复的新正式包。

## 候选与保护

| 身份 | 实际值 |
| --- | --- |
| 原分支 / 原 HEAD | `codex/nq-v101-release-manifest-delivery` / `885b76ae92f0d3f26cb3d31a15bd2d607d799faf` |
| fetch 后 origin/dev | `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb` |
| 原 HEAD / dev code tree | 均为 `2145e55070afad4fcdfe0227c11e893a81fba19e` |
| Starting Staged Tree | `9a14dbc564468820846425d81a676777f2ce4878` |
| 新分支 / HEAD | `codex/nq-v101-legacy-upgrade-bridge` / `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb` |
| 本报告加入前的6文件 Staged Tree | `d48495e45e89f7356fc53e1a122b3754b5cc3dde` |
| Final Tree / Changed File Hashes | 外部 `final-candidate.json`，避免本报告自引用 |
| 桥接实现 SHA256 | `555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8` |

仅在确认两基线 tree 相同、无未暂存 tracked 修改后切换；暂存 tree 切换前后保持一致。原暂存阻断报告 blob `634f300c5b8eeb35f4cef3bd89e3329fd5ecbfe6` 保留。未 reset、clean、stash、cherry-pick 或改写历史。

原两份未跟踪报告仍未跟踪，且原字节保持：

- `NQ_V101_FULL_STACK_SECURITY_PATCH_CLOSEOUT.md`：SHA256 `9cd418d744ce0c90257b6bf086c657cd34c282a56be3b98c696f1fcf62fa61e6`。
- `NQ_V101_RELEASE_SOURCE_AND_FORMAL_UPGRADE_QUALIFICATION_RESUMED.md`：SHA256 `912d48c9c1cacee7cd04e642f3b0b2fec6982c2bb05c2b54cb70c95ca92fcbaf`。

## 升级断点与实施

原 Windows/POSIX 安装器要求 PG config digest 不变，且不会生成独立运行口令、执行新 maintenance。两者都是正确的历史限制，本轮均未放宽。原版18字段 Manifest 的严格解析规则不变。复用前轮同一源/目标包身份的真实原安装器 CHECK 回执：`CHECKED / UPDATE_INELIGIBLE`，见 `E:/nq-v101-pg-compatibility-20261024/actual-v100-check-receipt.json`；未伪造直接升级资格。

新增 `release/installers/legacy_upgrade_bridge.py`，提供 `preflight / execute / status / recover`。Python 3.12+ 为该独立工具的明确运行前提。Windows 使用原 `operation.lock` 的独占打开语义；POSIX 同时保留原目录锁协议。只回收同过渡清单、已证明进程退出的桥接锁，未知/存活 owner 均拒绝。

独立 `NQ_LEGACY_BRIDGE_V1` 清单固定绑定桥接来源 Commit、脚本 SHA、32表工具 SHA、安装配置 SHA、项目身份、原/目标 Manifest SHA、目标 ZIP SHA、目标源码 Commit 和架构。操作人须在执行任何脚本之前，用 OS SHA256 工具对照独立可信来源核验；脚本自身算出的摘要不能自行成为信任锚。源码未提交时，只有显式 `--local-candidate` 才接受 `UNCOMMITTED`，不得作为正式来源资格。正式外部发布清单/信任锚尚未签发。

`preflight` 只读：不下载/加载镜像、不创建目录、不改库。`execute` 必须显式确认 `BRIDGE 1.0.0 TO 1.0.1`；恢复必须指定原事务 ID、同一可信清单及 `RECOVER 1.0.0`。比较工具执行刚校验的源码字节，不使用未绑定 pyc 缓存。

数据库变化前持久化原合同的 `pending-update` 与 `pending-restore.env`；独立桥接回执不进入旧 `last-update`。双标记令原版所有命令（包括 rollback/restore）安全拒绝。建立/解除都在持锁期间进行，先移除 update 标记、最后移除 restore 标记。失败保留稳定事务与停机保护，不能以旧镜像接管已迁移库。

状态路径为 `VERIFIED → QUIESCED → BACKUP_VERIFIED → TARGET_STAGED → DB_TRANSITION_STARTED → ROLES_AND_SCHEMA_VERIFIED → TARGET_RUNTIME_VERIFIED → COMMITTED`。异常按数据库是否可能改变区分 `FAILED_BEFORE_DATABASE_CHANGE / FAILED_AFTER_DATABASE_CHANGE`，保留 checkpoint 与 `RECOVERY_REQUIRED`；恢复失败停应用/PG，保留 `FAILED_RECOVERY`。已经持久 COMMITTED 的事务只能补完交接回执，不能重新判为允许回退旧库。

先停业务写入，生成原合同逻辑备份，再停 PG 备份完整集群。在隔离副本验证旧/新镜像真实启动、集群 system identifier、数据库 locale/collation、扩展、认证身份、32表与索引；拒绝外部 tablespace/数据目录链接。恢复使用完整集群副本，包括集群角色口令与归属，替换前数据树另名保留，不依赖新角色修补旧库。

角色/Flyway 全部复用 `InstallationDatabaseMaintenanceMain`，无第二套迁移系统。维护账号口令不变；独立运行口令生成并持久保存，格式使用新版安装器的 `DB_ROLE_FORMAT=2`。Java 业务容器只接收 `nq_runtime` 凭据。未修改 maintenance、Tenant、Order、Ledger、Risk、历史 migration 或 JDK 策略。

修改范围为桥接脚本、定向测试、`INSTALL.md`、builder 的比较工具复制和 source manifest 的两个明确路径。清单当前2182文件、必需 runtime 2124文件、47 migrations，缺失路径0。这里只是候选静态覆盖检查，没有从工作区制造正式导出或发行包。

## 实际材料与隔离环境

| 身份 | 官方 v1.0.0 | 既有 v1.0.1 候选 |
| --- | --- | --- |
| Package Manifest SHA256 | `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1` | `7d2e42e45f4a18f7e0b7e16253ca1faee078bb96e92a4bfdbe5df0955a1315b3` |
| ZIP SHA256 | `37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931` | `bada1788e4c813cff9a9898950028e567342bb3adcb34f99f007e250f9ca2701` |
| PG native image | `sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d` | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| PG config digest | `sha256:275447c94b11b151decd1f29877965301d5a77032037c46de93d990f739f00a9` | `sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b` |

正式材料来源/归档/层证明复用前轮同一不可变身份；本轮 preflight 再核验包 Manifest、归档、实际安装资产、镜像身份和架构。没有回退 XZ 更新：实际系统包差异仍仅 `liblzma5`、`xz-utils`，`5.8.1-1+deb13u1 → 5.8.1-1+deb13u2`。两图实际 PG `16.15`；不是仅据1087文件摘要相同认定兼容。本轮隔离恢复副本、locale/collation、扩展、索引和数据读取构成额外实际证据。

任务根 `E:/nq-v101-legacy-bridge-20261024/`；新安装根 `installation/`；项目 `nq-legacy-bridge-20261024`；端口仅 loopback 19380/19388。用未修改的官方 v1.0.0 PS 安装器创建，再用原版恢复既有业务基线。未借用共享安装或改动原始备份。Docker Desktop 启动时已有服务按其 restart 策略恢复；未使用它们执行本任务验证。收尾已停止本任务三个容器，保留全部数据和备份。

## 定向验证与恢复

| 检查 | 实际结果 / 证据 |
| --- | --- |
| 桥接 unittest | 19项 PASS，含8类编排故障子例；`bridge-unit-final.log` |
| source工具回归 | 29项 PASS；因本轮新增2个正式输入运行，不重新审查原79路径；`source-tests.log` |
| 原 PS/SH 双 pending | 各12命令拒绝，共24/24 PASS；真实脚本、合成安装，非真实POSIX平台资格 |
| 原操作锁 | Windows独占锁、Bash原生PID锁2/2拒绝；`review-locks/operation-lock-refusal.json` |
| 迁移前失败 | 首轮 collation 误判后实际恢复V34，原库未进入目标迁移；`recovery-attempt1.log` |
| 数据库已改变后失败 | 真实目标 maintenance 到V47、受限角色/RLS检查通过后注入失败，完整集群恢复V34；r5事务 `20261024T055856000Z-48187be1` |
| 提交前进程中断 | 在 `TARGET_RUNTIME_VERIFIED` 注入 `os._exit(77)`，外层PowerShell返回1；持久checkpoint保留，死锁owner可回收；r7事务 `20261024T060819000Z-48cfb178` 实际恢复V34 |
| 中断前后业务事实 | 恢复前32表原始 MATCH；恢复后与下一次源基线 MATCH；`after-kill-r7-comparison.json`、`qualification-results.json` |
| 最终正常升级 | r8，native exit0，事务 `20261024T061211000Z-da7492fc` COMMITTED；`bridge-r8-normal-exit.json` |
| 角色与RLS | 真实 maintenance验证通过；无租户上下文 `nq_runtime` 读取 accounts=0，旧账户均NQ_LOCAL；没有伪造正向Tenant上下文 |
| 原认证字段 | 旧用户、角色、用户角色原主键/原字段摘要保持，原用户实际登录 PASS；新角色定义/新空列不冒充旧字段变化 |
| 正向租户/Definer | 原用户产品HTTP查询及新研究、回测、SIM成功，实际受限身份通过Runtime Guard；maintenance检查Definer Owner安全属性 |
| Canonical SIM | 新run的canonicalAccountId非空；真实1 Order / 1 Trade / 6 Ledger entries，停止后重启及恢复仍可读取 |
| Self-Hosted边界 | 注册/平台HTTP均403，符合既有denyAll；同一后端镜像既有JAR证据为NQ_SELF_HOSTED、无Demo JAR，不将403单独冒充类缺失证明 |
| 新安装器接管 | 正式目标PS restart、backup、restore均exit0；重启/恢复前后32表各自原始 MATCH；原用户登录与持久SIM复读PASS |
| JDK缓解 | 复用同一后端镜像既有48变体/14链拒绝、正常链接受证据；`VERIFIED_MITIGATION`，无Adoptium vendor-patched声明 |

编排合成故障覆盖停止应用、备份、镜像切换、Role Provisioning、Flyway、应用启动、提交前、回执写入；其他负例覆盖清单、脚本摘要、镜像身份、取消、稳定事务和凭据环境。真实PG恢复覆盖三类要求，不用这些mock替代。POSIX本机仅Git Bash合同证明；Linux/macOS正式资格 NOT_RUN。

保留的开发失败：首轮将 default collation 的合法空 catalog version 误判，改为核对数据库实际版本；下一轮 dpkg-query shell变量展开错误，改为直接参数；认证比较先后误含新增空列/新角色定义，改为核验全部旧主键与旧列；版本接口需认证导致401，桥接不索取业务密码，改为运行容器完整身份与匿名health，产品认证接口另行真实测试。外部产品夹具首轮误期望404，核对现有denyAll后按真实403验证，原失败日志保留。未修改产品认证规则或业务算法来消除这些失败。

## 32表严格结果

最终桥接自己的 V34 源 checkpoint → V47 启动后：**MATCH**。提交前强杀的恢复前比较：**MATCH**。新版安装器重启、备份后实际恢复比较：**MATCH**。

最早2026-10-21历史参考 → 本轮最终桥接前源库：**原始 DIFFERENT**。仅新增两条 `account_equity_measurements`：`b882eefa-ab47-4f7a-9857-9c71a80c4bfc`（05:45:38 UTC，桶1992023）、`41490b18-e246-418d-a4e4-d27f7c5b16d2`（06:03:18 UTC，桶1992024）。二者均在最终桥接前的旧运行阶段产生，状态NO_BALANCE、equity=NULL、SIM、fundingEntryCount=0，属于已有十五分钟采样合同；不是新账务分录或虚构收益。

该比较 **Missing Original Keys=0 / Changed Original Business Fields=0 / Changed Historical Economic Facts=0**。单独注明 `PRESERVED_WITH_EXPLAINED_ADDITION`（实际2条）；没有把原始DIFFERENT改成MATCH，没有删除新增记录或改变比较器。最终正常升级后的研究/回测/SIM新增事实另属本轮明确产品验证，不混入桥接前后严格比较。

## 一次独立审查与限制

`/root/bridge_lock_review` 先进行独立原锁路径调查，随后完成本次实现的一次限定候选审查；没有review-of-review。发现并在同次审查内关闭：恢复回执失败后未停业务P1、遗留原子写残片阻断恢复P2、已验证死owner缺PID无法回收P2。最终10项有界检查通过，未解决P0/P1/P2=0/0/0。最终审查绑定本报告所列实现SHA及其余4个实现/测试文件，收据 `review-locks/final-candidate-review.json` SHA256 `efa9374226125dc123634e32d3e369b54b40fa8c53039f107a9567eaa48408f8`。这些合成检查不替代上述真实PG与业务执行。

没有新增Commit/Push/PR/Merge/Tag/Release；没有正式新包、全量依赖扫描、全Maven/Python/前端测试或历史GateAUDIT。正式桥接来源Commit、独立对外过渡清单、新Commit的正式导出/扫描/构建和正式升级资格均等待Git交付。既有Host/CPython、递归工具闭包、Gitleaks归档来源及不可变发行包供应链证据缺口仍按原报告保留，未在本轮关闭。

## 验收

```text
Task: NQ-V101-LEGACY-UPGRADE-BRIDGE
Baseline: origin/dev 15ffbc58e15adcb11d97ba296dc4ce4b882b0beb
Branch: codex/nq-v101-legacy-upgrade-bridge
HEAD: 15ffbc58e15adcb11d97ba296dc4ce4b882b0beb
Starting Tree: 9a14dbc564468820846425d81a676777f2ce4878
Final Tree: SEE_EXTERNAL_FINAL_CANDIDATE_JSON

Bridge:
Implementation: IMPLEMENTED
Trust Anchor: LOCAL_ARTIFACT_VERIFIED / FORMAL_SOURCE_PENDING_GIT_DELIVERY
Original Installer Compatibility: UNCHANGED / DIRECT_UPDATE_INELIGIBLE
Authorization: EXPLICIT_LOCAL_ISOLATED_EXECUTION
Transaction State Machine: VERIFIED

PostgreSQL:
Old Image: sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d
New Image: sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97
Image Transition: QUALIFIED_LOCAL_CANDIDATE
Role Provisioning: PASS
Schema Ownership: RESTRICTED_NOLOGIN
Runtime Identity: nq_runtime / RESTRICTED
Flyway: V34_TO_V47_PASS

Upgrade:
v1.0.0 → v1.0.1: LOCAL_BRIDGE_PASS
Original Data: PRESERVED / RAW_HISTORICAL_DIFFERENT_EXPLAINED
Original Credentials: PRESERVED / LOGIN_PASS
Canonical SIM: PASS
RLS: PASS
Security Definer: PASS

Recovery:
Pre-DB Failure: PASS
Post-DB Failure: PASS
Interrupted Transaction: PASS
Restore Verification: PASS

Verification:
Unit: 19_PASS
Installer Contract: PASS_WITH_POSIX_PLATFORM_LIMIT
Real PostgreSQL: PASS
Docker: PASS / TASK_CONTAINERS_STOPPED_AFTER_VALIDATION
HTTP: PASS
CI Compatibility: LOCAL_SOURCE_TESTS_29_PASS / EXACT_HEAD_CI_NOT_RUN

Findings:
P0: 0_UNRESOLVED_WITHIN_SCOPE
P1: 0_UNRESOLVED_WITHIN_SCOPE
P2: 0_UNRESOLVED_WITHIN_SCOPE

Decision: IMPLEMENTED / TARGETED_VALIDATION_PASS / PENDING_GIT_DELIVERY
Formal Package Qualification: NOT_RUN
Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
