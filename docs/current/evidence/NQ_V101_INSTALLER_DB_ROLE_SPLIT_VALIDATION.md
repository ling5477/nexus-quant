# NQ v1.0.1 安装器数据库角色拆分：实现验证

日期：2026-10-23。任务：`NQ-V101-INSTALLER-DB-ROLE-SPLIT-UPGRADE-UNBLOCK`。

结论：安装器修复已实现，定向 **IMPLEMENTATION_VALIDATION PASS**；正式不可变包升级 **NOT_RUN**。本报告不授予发布、Public Demo 公网部署或 LIVE 权限。

## 候选与前置交付

- 当前分支：`codex/nq-v101-installer-db-role-split`；HEAD / origin/dev：`04b19f444cc5ddb8bfcdbdaa58f00d2ed4e8a69f`。
- UX 已单独交付 [PR #74](https://github.com/ling5477/nexus-quant/pull/74)：候选 `445a8de004995607ba39dd87af04fb1fc81ca2cd`，tree `b6bdc0d7dec59bba020a0b67a1b600c95c895766`；PR CI `39888330030` 9/9 SUCCESS；合并后的上述 dev SHA，push CI `39889068927` 9/9 SUCCESS。本轮没有重新审查前端。
- 安装器修复仍是未提交工作区；逐文件 SHA256 见[脱敏索引](NQ_V101_INSTALLER_DB_ROLE_SPLIT_VALIDATION.index.json)。未提交、未推送、未创建本轮 PR/Tag/Release。
- v1.0.0 原正式包只读：source `5231266d366680ed5c6c1e38ab84adb33ffcd644`，manifest `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1`，收尾复核不变。旧业务备份在任务独立安装中恢复，未操作原安装数据库。

## 实现

保留 `nexusquant` 维护身份及原 DB_PASSWORD；新增不可登录、无 SUPERUSER/BYPASSRLS 的 `nq_schema_owner` 与独立 `nq_runtime`。运行身份无 CREATEDB/CREATEROLE、Schema CREATE 或 Owner 成员关系。业务容器只取得运行口令；维护秘密仅进入 PG 与一次性维护进程。

维护入口不启动 Spring/HTTP/调度：角色事务与对象归属准备 → 原 Flyway migrate/validate → 原空库管理员初始化 → ACL 重建 → 原 Tenant 运行守卫。跨连接会话锁覆盖该序列；失败不启动业务。对 public 中应用表、序列、函数、视图、enum/domain 定向处理，未使用全局 REASSIGN OWNED。未改历史 migration、Tenant 守卫、Order/Ledger 或 LIVE 设置。

恢复显式使用 `--no-owner --no-privileges`，由匹配版本的维护入口恢复 Owner、撤销 PUBLIC 的函数权限并重建受限 EXECUTE；内部工作/平台控制表仍不可写，平台管理函数不授予 Self-Hosted 运行身份。备份保存独立运行凭据。DB_ROLE_FORMAT=2 与旧格式分开；普通 start 缺失运行凭据拒绝，不任意重置。

Windows/POSIX 保留原可信 manifest、镜像摘要、确认、备份和事务 journal。v1.0.0 首次进入新角色合同须用**可信目标包内新版脚本**调用原 update；旧发布脚本进程不知道新维护步骤。具体命令见 [INSTALL.md](../../../INSTALL.md)。失败与显式 rollback 使用事务保存的旧资产、旧配置和完整数据库备份。

## 实际运行与结果

本地 PG16.15，Windows 11 amd64 / Docker Desktop 29.9.2、Compose 5.6.0。所有修改与故障注入均在本任务资源内。POSIX 使用 Ubuntu 22.04 容器宿主、真实 Docker Unix socket 和同路径挂载的任务卷；无模拟 Docker、无伪装 OS。它不构成原生 Ubuntu/macOS 宿主资格。

| 验证 | 实际结果 |
| --- | --- |
| Windows 新装、默认管理员强制改密、重跑 | PASS；用户原字段与配置不变 |
| Windows 官方 v1.0.0 安装及既有业务备份恢复 | PASS；原包 V34，维护账号密码不变 |
| 新角色准备及原 V34→V47 Flyway | PASS；旧 34 条校验和与 success 保持，validate 成功 |
| 认证保存 | PASS；旧用户原字段、角色保持；新 email/email_verified_at 单列断言 NULL；首次新版本登录前旧 Session 摘要一致 |
| NQ_LOCAL 与资金 | PASS；旧 Membership 均 NQ_LOCAL，tenant_sim_accounts=0，未补发 Demo 资金 |
| RLS / Owner / SECURITY DEFINER | PASS；98 表 RLS+FORCE，危险 Definer owner=0，完整 Tenant 启动检查通过 |
| 角色限制 | PASS；SUPERUSER/BYPASSRLS/CREATEDB/CREATEROLE/Schema CREATE/Owner Membership 均 false；SET ROLE Owner、内部表与平台函数被拒绝 |
| 新装与升级后的研究/回测/SIM | PASS；分别通过真实产品 HTTP、研究评价、冻结回测、发布及 canonical 历史回放，每次取得 1 Order / 1 Trade / 6 Ledger entries |
| 成交后服务重启 | PASS；升级实例 Order/Trade/Ledger 摘要 MATCH；未把历史回放成交描述为自然 Continuous SIM 成交 |
| Windows 与 POSIX backup/restore | PASS；API 创建备份后 marker，正式恢复后 marker 消失、运行守卫与登录恢复 |
| 角色初始化中断 | PASS；真实维护容器在归属事务锁等待时被终止，事务回滚，正式 start 重试成功 |
| 真实 Flyway 失败与自动回滚 | PASS；仅故障夹具内新增 V48 SELECT 1/0，PG 日志实证 division by zero；业务未启动到失败目标，自动恢复旧 V34/health；32 表 MATCH |
| 更新进程中断 | PASS；真实 DB_MAY_CHANGE journal 后终止 PowerShell 进程树；pending 保留，普通 start 拒绝；显式 rollback 还原 V34，32 表 MATCH，再升级正确 r2 成功 |
| POSIX 实际安装、doctor、恢复及研究/回测/SIM | PASS；运行目录700、秘密600、静态runtime.yml644；POSIX update/rollback 另有模拟合同回归，未声称实际 POSIX 跨版本升级通过 |
| Self-Hosted 发行身份 | PASS（实现夹具）；JAR 无 nq-public-demo；注册/平台路径为403/404拒绝；无注册 Controller 发行模块 |
| LIVE / private provider | DISABLED；kill switch ENGAGED，无真实账户、真实下单或外部邮件 |

### 32 表原合同

`upgrade-compare-r2.log` 与升级后重启比较均严格 **DIFFERENT**：仅 `account_equity_measurements` 新增 `3328884d-5372-4f57-b84e-d00b4d5cf604`，旧主键缺失=0、原字段改变=0。没有关闭比较器、改变精度规则或把 DIFFERENT 写成 MATCH；该条启动后新增观测单独解释。后续新建回测和 SIM 的业务写入发生在保存比较后，不并入升级前后历史保存证明。

两次恢复边界比较（真实 Flyway 失败、真实进程中断）均为 **MATCH / 32 tables**，没有旧订单或资金丢失。最初收集器把新增 nullable 认证列算进旧字段比较，以及把 baseline SHA 写入旧包 before 元数据；原失败保留，r2 已从真实安装配置重取正确来源 SHA，重新捕获并比较。正常登录新增 Session 不被伪装为迁移差异，也未手工调整数据库匹配摘要。

## 测试、独立检查和证据边界

- PG16 维护测试最终 3 PASS：fresh/幂等、V34旧认证/校验和、危险角色/checksum拒绝与重试；新增正确 Tenant/epoch 守卫成功及有效身份下错误目标 Tenant 拒绝。
- 安装器原合同 30 PASS / 1 SKIP 的有效证据复用（跳过项是 Git Bash 无法证明 POSIX mode）；本轮补充2项新角色合同 PASS，修改模拟维护分支后3项受影响旧合同 PASS。真实 POSIX 安装另行证明权限，未将模拟结果当宿主资格。
- source exporter tests 29 PASS；source/schema contract tests 16 PASS；32表比较器 tests 7 PASS；PowerShell parse errors=0，sh -n PASS；stage assets / semantic guard errors=0；链接检查 errors=0，123条既有历史链接 warning 保留。
- 一次限定范围真正独立检查，随后只补审经济守卫授权修复及过渡命令；最终未发现产品权限扩张或剩余可复现 P0/P1/P2。测试负例被建议明确 Tenant 后已修正并重跑。未启动全量安全审查或重审已交付前端。
- 首轮实际 SIM 失败为经济触发器缺少 nq_require_tenant_execution EXECUTE；已补入精确白名单并以 r2 新镜像、新 manifest、PG测试及真实成交重验。首轮失败日志保留，不追认为通过。

## 制品与正式资格

原本地 distribution Docker build 因 auth.docker.io 连接超时失败，原日志保留。实现验证使用本地已验证 JRE/NGINX base、实际 Maven fat JAR、实际 Docker build/save，保留独立 task-only 标签、镜像 native/config身份、archive摘要及安装器/runtime文件摘要。前端未改，复用已验证 Production dist。所有原 v1.0.0 镜像和包保留，无 registry push。

这批包是明确标记 sourceDirty=true、sourceCommitIsBaselineOnly=true 的 **IMPLEMENTATION_VALIDATION_ONLY** 夹具；独立固定测试 manifest 用于验证安装器的校验和事务流程，不能当作正式发布者真实性证据。r2身份见索引。测试故障 SQL 仅注入另一个任务镜像的 classpath，不在仓库或正式制品中。第一种故障夹具未实证 SQL 执行，只证明失败目标回滚；第二种在 nested nq-infra 中注入 V48 后取得 PG 错误，单独保留并作为实际 Flyway 失败证据。

正式 v1.0.1 仍须在另行 Git 授权后从不可变 commit、经审阅且与目标 VERSION 一致的完整来源清单执行原 export/build-package，再做正式包升级。本轮只加入维护入口的精确来源条目，未把现有 v1.0.0 来源清单重新策划成完整 v1.0.1 清单；探索性全工作区 coverage 显示既有新功能未进入正式清单，未报 coverage PASS，也未放宽该拒绝。本轮未取得正式包，Formal Immutable Package Upgrade=NOT_RUN。

## 证据保留及清理

脱敏索引只保留必要日志摘要、SHA256、候选与比较结果。原始运行日志、dump、认证材料及业务响应保留在受限 `C:/nq-v101-installer-roles-20261023/`，不进入 Git。失败日志、后续成功及收集器修正分开保留。

任务 Windows/POSIX/PG证明容器与网络已移除；独立安装数据、备份、验证镜像及 `nq-installer-posix-20261023` 卷保留以复核恢复证据。清理前后的37个其他容器身份保持，未清理用户原数据或原环境。

## 最终结果

```text
Task: NQ-V101-INSTALLER-DB-ROLE-SPLIT-UPGRADE-UNBLOCK
Implementation:
Role Provisioning: IMPLEMENTED
Schema Owner: IMPLEMENTED
Runtime Role: IMPLEMENTED
Flyway Separation: IMPLEMENTED
Windows Installer: IMPLEMENTED
POSIX Installer: IMPLEMENTED
Backup: IMPLEMENTED
Restore: IMPLEMENTED
Rollback: IMPLEMENTED
Verification:
Fresh Installation: PASS
Existing v1.0.0 Migration: PASS (IMPLEMENTATION_VALIDATION)
Role Privileges: PASS
RLS: PASS
SECURITY DEFINER: PASS
Historical Data: OLD_ROWS_PRESERVED; STRICT_DIFFERENT / one added observation
Self-Hosted Login: PASS
SIM Regression: PASS (historical replay)
Findings:
P0: NONE
P1: 1 FIXED / missing economic guard EXECUTE; remaining in scope NONE
P2: NONE in scope; native POSIX/macOS qualification NOT_RUN
Installer Fix: IMPLEMENTED
Targeted Qualification: PASS (IMPLEMENTATION_VALIDATION_ONLY)
Formal Immutable Package Upgrade: NOT_RUN
Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
```
