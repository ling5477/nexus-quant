# OKX scoped readonly runtime 身份修复与生产边界

日期：2026-09-27。当前机器 authority 仍由 [STATUS](../../STATUS.md) 拥有。

结论：`IMPLEMENTATION_ACCEPTED / READONLY_RUNTIME_STARTED / QUALIFICATION_BLOCKED / CANONICAL_OWNER_LOGIN_SOURCE_MISMATCH`。不声明真实账户只读资格、LIVE readiness 或 private trading readiness。

## 技术候选与验证

| 项目 | 已核对事实 |
| --- | --- |
| starting dev | `facb499f72e1afef7bf147f9f1465a3889ff2670` |
| technical commit | `176833609c01abcf978e11de2d07d809304ad9f6` |
| technical PR | [#36](https://github.com/ling5477/nexus-quant/pull/36)，已合并 |
| PR CI | `36232751713 / pull_request / success / 9 of 9` |
| fixed technical dev | `ca23090af446cb2b186138cfa6e656d6a613a924` |
| dev exact-head CI | [36233115450](https://github.com/ling5477/nexus-quant/actions/runs/36233115450)，`push / completed / success / 9 of 9` |
| 本地目标回归 | 88 tests，0 failures/errors/skips；stage semantic/asset checker errors=0；asset 回归 64（2 个既有 skip），semantic 回归 4 通过；diff check 通过 |
| 独立审查 | REVIEW_ONLY，P0/P1/P2/P3=`0/0/0/0`；独立复现 71 tests，0 failures/errors/skips |
| 候选完整性 | 15 个文件起止 hash 一致，stage=0；指纹文件 SHA256=`53f8ed8009ab60167818ede41ac87518ac492046102585ed450bd2a3b50226b4` |

旧 runtime 把 release ID 当作 40 位源码 SHA，并要求两者相等；下游 prerequisite authority 重复施加同一错误要求。当前 scoped runtime 只接受 `nq-[0-9a-f]{12}-[0-9a-f]{16}`，保留独立的 40 位小写 source commit、64 位小写 manifest digest，并验证 12 位源码前缀一致。结构绑定不替代外部 admission/installer 对制品真实性的验证。

诊断现在识别实际 account-facts service、固定 readonly transport 与唯一 credential executor。完整 component scan 证明交易 provider、scheduler、websocket 缺席，启动不访问 credential 或 OKX。历史 exact-pilot 持久合同保留，不授予当前 scoped profile 启用历史转换工厂的能力。installer、admission、activation 合同与 migration 均未修改。

## 新制品与生产回读

| 项目 | 已验证值 |
| --- | --- |
| release ID | `nq-ca23090af446-a83050fcf51b7299` |
| source commit | `ca23090af446cb2b186138cfa6e656d6a613a924` |
| source tree | `468b2c2be4f42b903391618265cf96b5ec103e34` |
| manifest SHA256 | `2df3fc79bebe390d0266d703df296224567dfde683b3c8bcd875e9b1a8259781` |
| admission | 原始 `EXACT_HEAD_CI`，execution=`36233115450:1` |
| admission SHA256 | `644fa417378b6f8c658368a436a7635078879afb250b9201eb146976e9ecc75e` |
| schema | PostgreSQL 16 / V54；strict Flyway validate=true，pending=0，本轮 migrationsExecuted=0 |
| install / activate | 普通 canonical install 与 ACTIVATE 均成功；未执行 install-for-bootstrap/bootstrap-current |
| current | `/opt/nexus-quant/releases/nq-ca23090af446-a83050fcf51b7299` |
| activation | generation=2，transaction=`d9b563e2094f43fbb6e00ce139442923`，COMPLETED；signed journal/head 一致 |
| canonical predecessor | `nq-facb499f72e1-e8de1eb71558a126` |

制品从 fixed dev 的 CI backend/frontend artifact 和干净 exact-head checkout 重建，与原始 CI admission 完全匹配。服务器安装前后均通过 release/admission 校验，POSIX 校验通过。

## 运行边界与真实 blocker

`2026-09-27T01:08:55Z` 回读 health=UP；info 和 readonly diagnostics 的 release/source/manifest 与上述值一致，Java=21，唯一 profile=`scoped-okx-private-readonly`，loopback，provider observation=true，trading components=false，live=false，kill=ENGAGED，mutation runtime=false。

随后本地 owner 登录未通过。只读数据库元数据确认：所用 management 登录用户名不存在于 canonical 数据库；目标账户 owner 存在且启用，enabled admin 存在。该登录来源属于旧 soak 环境，不能作为 canonical owner 的有效身份。未再次尝试密码、未重置账号、未伪造 token，也未修改任何 credential 权限。采集器仅保留了本地 HTTP 失败类别，未保存实际状态码；不将推断的 401 写为实测值。

permission probe=`NOT_RUN`，WITHDRAW/IP/freshness=`NOT_REOBSERVED`；account-facts=`NOT_RUN`，余额、费率、SPOT open orders、position/exposure、exchange time 与 divergence=`UNKNOWN`。没有执行 OKX private 请求。

runtime diagnostics 的读凭证、解密、请求和交易计数仍为 `NOT_INSTRUMENTED`，不能写成 0。补充证据是：运行期未到达 probe/account-facts 调用，request-only HTTP 日志无外联，完整数据库前后指纹无变化，canonical Order/Intent/Receipt/Trade/Ledger 内容一致；已知受控 secret 值的日志匹配为 false。该失败运行未发生交易或资金 mutation，不能据此接受真实账户资格。

runtime 已停止。`2026-09-27T01:10:20Z` 最终回读 Java 进程=0、scoped MainPID=0，unit=failed/exit-code（停止后状态，不是运行中）；LIVE=DISABLED，kill=ENGAGED。不得将 unit 的 failed 状态写成正常成功退出。

## 保留的先前成功与失败

上一轮 PG16 备份、隔离恢复、V47–V54 migration、Flyway validate、canonical install 和 legacy bootstrap 成功事实原样保留；第一次部署的 scoped runtime 未启动、OKX 请求=0 的边界亦保留。

本轮辅助安装脚本第一次因依赖数量上限 30 小于实际 77，在写入安装目标前停止；随后从已验新 JAR 提取依赖执行 validate 并完成部署。scoped 第一次前置检查因旧 soak 数据库名不符而未启动；第二次使用同一实例的既有数据库角色，显式连接已验 canonical 数据库，成功启动后在 owner 登录处停止。所有失败输出和后续结果分开保留，不追认为先前运行成功。

下一动作：取得目标 owner 的有效现有受控登录来源，随后重新核对当前身份和安全状态，继续已授权的 permission probe 与一次账户事实观察。缺少该来源时保持 BLOCKED；不重做 bootstrap，不扩大为交易任务。

脱敏运维证据保存在任务 qualification 目录及服务器受限目录，包括 deployment-result、scoped-qualification-attempt2-result、owner-authentication-metadata 和 final-readback。受限日志、凭证、生产数据及原始 provider 响应不进入 Git。
