# Canonical owner 凭据恢复与账户只读观察

日期：2026-09-27。当前机器 authority 由 [STATUS](../../STATUS.md) 拥有。

结论：`CANONICAL_OWNER_CREDENTIAL_PROVISIONED / CANONICAL_OWNER_AUTHENTICATED / OKX_PERMISSION_PROBE_PASS / NO_TRADING_MUTATION / QUALIFICATION_BLOCKED_PARTIAL_ACCOUNT_FACTS`。认证来源阻断已解除，完整真实账户只读资格尚未通过。GateZ 保持 `IN_PROGRESS / NOT_FROZEN`，LIVE=`DISABLED`，kill=`ENGAGED`。

## 技术交付

| 项目 | 已验证事实 |
| --- | --- |
| starting dev | `9ea7b1f538c7785ac7965e3e5ae226fbfa02a79d` |
| 实现 / CI fixture 修复 | `f2c633ce8e2ac011d6f83a1964ed1e3b56099686` / `5833c45d23cc7ced5e01e77071e6fe043eb4f4fb` |
| 技术 PR | [#38](https://github.com/ling5477/nexus-quant/pull/38)，已合并 |
| PR CI | [36289230852](https://github.com/ling5477/nexus-quant/actions/runs/36289230852)，`pull_request / completed / success / 9 of 9` |
| technical merge | `ba43b961c3f5a2ec3579f239f4fdc8c8bcf762c3` |
| dev exact-head CI | [36289608733](https://github.com/ling5477/nexus-quant/actions/runs/36289608733)，`push / completed / success / 9 of 9` |
| 本地目标验证 | 35 tests，0 failures/errors/skips；Linux root 0600 CLI 成功，宽权限、软链接、可写父目录和重放拒绝 |
| 独立认证审查 | `REVIEW_ONLY / PASS / P0-P3=0/0/0/0`，16 文件起止一致、stage=0；独立复现 35 tests |
| 后续差异 | 仅 PostgreSQL fixture 接受 CI 的 localhost；本地先复现失败，再通过 3 项 PostgreSQL 测试。7 个正式源码文件保持审查指纹 |
| 新操作脚本检查 | 独立 `REVIEW_ONLY / PASS`，5 个脚本起止一致、stage=0；静态检查不替代实际运行证据 |

正式入口为 `ExistingUserPasswordRotationMain`，通过 nq-auth service 和窄 repository contract 执行事务内 CAS；只更新已存在目标的 password_hash/updated_at。未新增 migration、公开重置 API、seed 路径或普通启动副作用。

首次 PR CI `36288950123` 的 PostgreSQL fixture 拒绝 localhost，失败历史保留。主工作区陈旧 CRLF migration 字节曾导致本地 release 与 CI admission 不同，该制品未执行；隔离 exact-head checkout 重建后，manifest/admission 完全匹配。

## 两个制品的身份

| 用途 | release / source / manifest |
| --- | --- |
| 一次性认证维护 | `nq-ba43b961c3f5-0b2a03f836672399` / `ba43b961c3f5a2ec3579f239f4fdc8c8bcf762c3` / `37e8157e8824fd9d9927a4ef726d456ec6818471fced66f902c11e8bab52f580` |
| 已安装 scoped runtime | `nq-ca23090af446-a83050fcf51b7299` / `ca23090af446cb2b186138cfa6e656d6a613a924` / `2df3fc79bebe390d0266d703df296224567dfde683b3c8bcd875e9b1a8259781` |

维护制品来自同一 dev CI 的已验 backend/frontend/provenance，服务器再次通过 POSIX release 与 `EXACT_HEAD_CI` admission 校验；admission execution=`36289608733:1`。current 指针和 activation 未变，未重新部署交易 runtime、bootstrap 或运行 Flyway migration。

## 认证更新与登录

`03:02:43Z–03:03:38Z` 的一次性维护成功。目标为现有 canonical user 2，enabled=true，roles=`ADMIN/OPERATOR/VIEWER`；account 1 / credential 2 的 owner 链保持不变。82 张表的前后内容摘要仅 users 变化；排除目标行 password_hash/updated_at 后的 users 内容完全一致，用户数量不变，新 hash 为 BCrypt。其余 81 张表未变。

来源类别为 `SERVER_GENERATED_ROOT_PROTECTED_PERSISTENT_FILE`。高熵密码保留在 root 所有的 0600 持久文件，父目录为 0700；请求和数据库密码的临时副本已删除。旧 soak 登录来源仍为 `MUST_NOT_USE`。密码、完整 hash、token 和 credential material 不进入本证据。

正常 `POST /api/auth/login` 成功；随后 `/api/auth/me` 校验 userId=2、username 与预检目标相同、角色完全一致。启动与登录前后的数据库内容一致。没有手工签发 token 或绕过 controller/security。

## 真实观察与剩余阻断

`03:04:04Z–03:05:34Z` 执行一次 probe 与一次 account-facts observation。runtime 的三种身份、Java 21、唯一 scoped profile、loopback、provider observation=true、trading components=false、mutation runtime=false 和 kill=ENGAGED 均在登录前回读通过。

Permission probe：`SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED / failedAuthCount=0`，时间 `03:05:16.694801Z`，使用时年龄约 0.13 秒。API Key 权限和凭据材料未修改。

Account facts 的 observedAt=`2026-09-27T03:05:16.899230117Z`：

| 事实 | 实际结果 |
| --- | --- |
| 账户模式、BTC/USDT 余额、BTC 现货敞口 | OBSERVED；生产金额仅保留在受限运维证据，不进入 Git |
| SPOT open orders、BTC-USDT maker/taker fee、交易所时间 | OBSERVED |
| 仓位 | UNKNOWN / `ACCOUNT_MODE_REQUIRES_POSITION_OBSERVATION`；实际账户模式为 2，不能按纯现货模式写成 NOT_APPLICABLE |
| account divergence | UNKNOWN / `POSITION_COMPARISON_UNAVAILABLE_FOR_ACCOUNT_MODE`，不能写成 MATCH 或 DIVERGED |
| public instrument rule | UNKNOWN / `PUBLIC_RULE_IDENTITY_UNAVAILABLE`；只读回查 BTC-USDT 目录有 1 行，完整 observedAt/checksum 身份为 0 行 |
| 总体 | UNKNOWN / `PARTIAL_ACCOUNT_FACTS`，不声明 `REAL_ACCOUNT_READONLY_FACTS_QUALIFIED` |

观察时 freshness 成立；private facts 的 TTL 为 1 分钟，后续使用必须重新观察。历史余额更新时间不替代本次 GET 的 observedAt，也不把此 snapshot 当作持续有效的交易前提。

## 零交易 mutation 与采集器偏差

原脚本结果为 `QUALIFICATION_SAFETY_EVIDENCE_FAILED`：脚本错误预期 www.okx.com，实际请求为 openapi.okx.com。原脚本、原结果均保留；未改验收标准重跑或追认该 run 为 PASS。事后核对部署 JAR 的固定 host 常量与同一 source，确认请求命中已部署的 `JdkOkxPrivateReadTransport.GLOBAL_HOST`。

8 次外联均为固定 GET：account/config 2 次、account/balance 1 次、account/trade-fee 1 次、trade/orders-pending 1 次、public/time 3 次。时钟的 3 次读取来自既有固定三样本合同。没有非 GET 或额外路径。

diagnostics 的计数仍为 `NOT_INSTRUMENTED`，不当作 0。零交易 mutation 由部署的固定 GET 路径、request-only 记录、实际 runtime composition 和全表内容摘要共同证明：PLACE/CANCEL/AMEND/TRANSFER/WITHDRAW/ACCOUNT_CONFIG_MUTATION 均为 0。Order/Intent/Receipt/Trade/Ledger 行数为 `3/1/1/1/4`，前后内容完全一致。

只读阶段变化仅为正式 permission-probe contract 的 credential 权限元数据和 credential_audit_logs；credential 材料、身份和 owner 摘要未变。已知密码、token、数据库密码、签名主密钥的运行日志匹配为 false，未发现 credential header；原始日志保持服务器受限访问。

终态 stop 命令退出 0，Java 进程=0、MainPID=0、LIVE=DISABLED、kill=ENGAGED（version 11）。unit 保留 `failed / exit-code`，不声称正常成功退出；临时 runtime secret 文件已删除，唯一持久登录密码来源保留。

下一步是补齐模式 2 的只读仓位/偏差覆盖与正式公开规则身份来源，并修正后续采集器的固定主机预期后，以新候选重新资格验证。该步骤可能需要新的只读 endpoint 和规则入库授权，本次未扩展请求集合或修改目录数据。完整资格仍 BLOCKED，已接受的公开行情 smoke 和历史成功事实保留。
