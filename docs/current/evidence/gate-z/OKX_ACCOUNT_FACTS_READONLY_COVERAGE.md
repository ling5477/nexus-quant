# OKX 模式 2 与当前公开规则只读资格

分类：`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。本文是 2026-09-27 的固定执行记录；[STATUS](../../STATUS.md) 仍是唯一 current authority。

结论：`PASS / OKX_ACCOUNT_MODE_2_POSITION_FACTS_OBSERVED / PUBLIC_INSTRUMENT_RULE_IDENTITY_OBSERVED / ACCOUNT_DIVERGENCE_CLASSIFIED / REAL_ACCOUNT_READONLY_FACTS_QUALIFIED / NO_TRADING_MUTATION / NO_SECRET_EXPOSURE / LIVE_DISABLED / KILL_ENGAGED`。完整 divergence 为 `DIVERGED`，不需要修改生产数据取得 MATCH。GateZ 保持 `IN_PROGRESS / NOT_FROZEN`。

## 候选与技术交付

| 项目 | 固定证据 |
| --- | --- |
| Starting dev | `79e106b971409881a2337a30693c5a7b397bc0d8` |
| 实现 | `b15406019be0b9d5158a7acc83cf80a4bce8f2ed` |
| 已审 compatibility identity 登记修复 | `02c514c12b14aae7b91c48b2268640ebe8d8ea28` |
| 技术 PR | [#40](https://github.com/ling5477/nexus-quant/pull/40) |
| 候选 PR CI | [36294588029](https://github.com/ling5477/nexus-quant/actions/runs/36294588029)，9/9 SUCCESS |
| 技术 merge / sourceCommit | `e9df23f079f0aaec1eff5b38269f4b18c7e9dae3` |
| 合并后 dev exact-head CI | [36295009039](https://github.com/ling5477/nexus-quant/actions/runs/36295009039)，attempt 1，push/dev，NQ CI Baseline，9/9 SUCCESS |

首次 PR CI `36294213109` 的 content-pinned stage registration 摘要失效原样保留。经独立审查的 lifecycle proposal 只更新两个受影响源码摘要，另一 caller tuple 列表仅排序、集合未变；没有扩大 guard 或权限。随后候选及 merge-head CI 均通过。文档同步提交不替代上述技术接受 pair，其 PR 与最终 CI 由后续 Git 交付记录表示。

## 实现与验证

新增 typed `PRIVATE_ACCOUNT_POSITIONS_READ / OKX_ACCOUNT_POSITIONS_READ`，无参 `accountPositions()` 固定 `GET /api/v5/account/positions`。调用者不能选择 host、method、path、任意 query 或 body；继续复用固定 `https://openapi.okx.com`、signer、超时、256 KiB 响应上限、单并发和无应用重试边界。

`OkxPrivatePositionFact` 只保存 instrument、margin mode、side、quantity、适用币种及 provider 更新时间；`positions` 改为 typed list，兼容性见 [API](../../API.md)。parser 拒绝缺字段、非法枚举/数值/时间、重复身份、内部方向矛盾、重复 JSON key、尾随 JSON 和 partial response。最多 100 条，101 条为 `UNKNOWN / POSITION_RESPONSE_OVER_LIMIT`，禁止截断。合法非现货类别包含 MARGIN、SWAP、FUTURES、OPTION、EVENTS。

模式 1 保持 NOT_APPLICABLE 且不发送 positions GET；模式 2 完整空集或全零为 OBSERVED / NO_ACTIVE_POSITION，非零非现货仓位为 DIVERGED / EXTERNAL_NON_SPOT_POSITION_PRESENT。`spotBtcExposure` 只表达 BTC account balance，不与衍生品数量相加。模式 3/4 保持 UNKNOWN / ACCOUNT_MODE_NOT_YET_QUALIFIED。

模式 2 的分类要求所需外部事实与本地 canonical 余额/订单完整；依次检查非现货仓位、额外正资产、全账户 LIVE 活跃订单、BTC/USDT total/available/frozen。订单比较身份、venue、symbol、side、type、quantity、fill 和 limit price，SIM 不参与；本地最新余额按发布 `snapshot_id DESC` 选择。JDBC 比较后再次检查 TTL 和 kill identity，缺失或过期保持 UNKNOWN，只有完整相等才为 MATCH。

当前公开规则由人工 observation 触发固定无凭证 `GET /api/v5/public/instruments?instType=SPOT&instId=BTC-USDT`；复用 `OkxVenueRuleFactsReader`、`VenueRuleChecksumCalculator`、`OkxVenueRuleContract`，不读写 catalog。connect timeout=3s、完整响应 timeout=5s、stream cap=64 KiB、单并发、无 retry/redirect；constructor 不联网。精确 BTC/USDT/SPOT/live 与完整数值 contract 通过后形成 24h identity；不能表示历史回放时点规则。

有效目标测试共 **134**：adapter 56、account service 27、public rule 10、隔离 PostgreSQL 20、scoped composition/security 21；均 0 failures/errors/skips。PostgreSQL 使用独立 PG16、完整 Flyway V1–V54 和随机 schema，覆盖 MATCH / DIVERGED / UNKNOWN、发布顺序、订单余额比较与 SIM/LIVE 隔离；跨 TTL 和 kill 状态/版本变化由 account service 单元测试及独立反例重放验证。无 migration 或替身业务表。真正 Spring composition 证明启动 credential/decrypt/private/public-rule HTTP 为 0，TradingAdapter/SpotExecutionProviderPort 为 0，mutation 不可达。

独立代码审查初轮发现 P1 两项（快照排序 false-MATCH、JDBC 跨 TTL）和 P2 一项（JDBC 期间 kill 变化）；全部修复并独立重放。最终 fingerprint=`cd0a3859859884f8ee04f68362de72ff547ef21776b5782b3c07b109b89e5571`，起止相同、stage=0，剩余 P0/P1/P2/P3=`0/0/0/0`。审查者初轮 109 项、修复后 47 项测试通过，不把重复运行相加为新覆盖数量。

运维采集器的终态异常会跳过剩余证据问题已修复并独立补审，13 项离线测试通过；缺失证据必须 BLOCKED，已有结果不能覆盖。实包预检另发现迁移实际位于唯一 `nq-infra` 嵌套 JAR，原部署准备在解包前停止，未切换生产；原包保留。修复后的 77 库/54 迁移原字节与 inventory 一致，17 个负例全部在输出前拒绝，独立复放其中 6 个关键负例通过。部署 helper SHA=`8c1e3fe81326e1404c957a30752191f5dc1d734be6b600fe14e07e7b0ea82ede`；所有定向审查剩余 finding 均为 0。

## Canonical 生产部署

| 项目 | 实际值 |
| --- | --- |
| releaseId | `nq-e9df23f079f0-5a401cd983263e6d` |
| sourceCommit | `e9df23f079f0aaec1eff5b38269f4b18c7e9dae3` |
| release manifest SHA-256 | `bf7f51fd967ca3364bcfc42c8ff8b5f2995c0414d270916275c0aa36caaf5da9` |
| EXACT_HEAD_CI admission SHA-256 | `9c9e39fcef7ea8133b55186a9da25a6601e4c034daf7082595354251b61b134b` |
| admission execution | `36295009039:1` |
| JAR SHA-256 | `94ab61361808252adacfc50ceef95e8e4c536f8658c8778d22b53b724c98fcdd` |
| migration inventory SHA-256 | `1824d55741b8b7c1d4c56877edbbe63963466f7e798c392132eda6934cb5e955` |
| 前任 release | `nq-ca23090af446-a83050fcf51b7299` |
| activation | ACTIVATE / COMPLETED / generation 3 / atomicReplace=true |
| activation transaction | `0cfb2450a7124844bca3689a87028686` |
| 部署完成 | `2026-09-27T05:23:41.267225Z` |

从合并后 exact-head CI 下载三组制品并匹配 GitHub archive SHA-256，在固定技术提交的干净 worktree 重建 canonical release，与 CI admission 的 manifest 完全相同。服务器普通 install/verify/observe-database/activate/preflight 全部通过；PG16、V54、Flyway validate=true、pending=0、failed=0、migrationsExecuted=0，使用只读数据库连接。未再次 bootstrap、未 migrate/repair、未维护 owner/roles/credential ownership。

## 两次独立正式 run

首轮目录 `20260927-coverage-unblock`：`05:24:22.177514Z–05:26:03.481745Z`。owner 登录及权限 probe 成功，但 collector 将阶段预算误写为 probe 2 / observation 8；源码实际为 probe config 1，全部 3 次 time 请求归 observation。该轮在账户观察前停止，decision=`QUALIFICATION_BLOCKED_EXACT_EXTERNAL_GET_BUDGET_UNVERIFIED`，实际仅 1 次 config GET。原 collector SHA=`e04aa014f1b98015332e494b2995ed9e29071d99d45807742761232853c6a46c`，受限结果 SHA=`5eb71771c62e0348baf737c87db231f85937c414144c12f620dbc736f9080c06`，原文件/失败结论不变。

成功轮目录 `20260927-coverage-unblock-attempt2`：`05:36:55.941816Z–05:38:43.340142Z`。修正阶段归属并使用新 ROOT、unit、runId，只有五行变化；总 GET 预算、endpoint allowlist、事实和终态验收标准不变。源码绑定正负例及原终态回归共 16/16 通过，独立补审起止指纹相同、stage=0。collector SHA=`ec96b3f3ab0aa18e246e966663b1a12874b7dfcddf7927e9d32c8c1584341d66`，受限结果 SHA=`788ef009dee61dca8f26ed8bc03f3e9f116a0a59ccb1c8ea40d78eb6188441b6`。每轮运行期间 collector、代码和标准均冻结；没有原地改写重跑或拼接两轮取得 PASS。

## 新账户事实

owner 的正常登录及 `/api/auth/me` 绑定通过；复用现有 root 0600 密码来源，前后 fingerprint 一致，不输出密码或 fingerprint。permission probe=`SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED`，时间 `2026-09-27T05:38:23.204943Z`，进入账户观察时 age=0.255298s。

| 事实 | 成功轮结果 |
| --- | --- |
| aggregate | OBSERVED / PRIVATE_READ_ONLY |
| observedAt | `2026-09-27T05:38:23.820031102Z` |
| account mode / credential / permissions | OBSERVED：2 / ACTIVE / READ+TRADE |
| USDT、BTC、全部返回余额 | 全部 OBSERVED；不导出金额 |
| positions | OBSERVED / NO_ACTIVE_POSITION，完整 typed 空集，count=0 |
| Spot BTC exposure | OBSERVED，仅 account balance scope |
| SPOT open orders | OBSERVED，count=0 |
| BTC-USDT fee | OBSERVED；不导出费率值 |
| exchange time | OBSERVED，`2026-09-27T05:38:24.776Z` |
| public rule | OBSERVED / CURRENTLY_OBSERVED_PUBLIC_RULE |
| divergence | OBSERVED / DIVERGED |

public identity=`OKX:BTC-USDT:aeb0798ee88b36877b4adc5ead2030fc5386945e8dad3dc3c5a4c77b3c48aa02`；source=`OKX_PUBLIC_INSTRUMENTS`；observedAt=`2026-09-27T05:38:25.382878869Z`，expiresAt=`2026-09-28T05:38:25.382878869Z`，TTL=24h。私有事实 TTL=60s，验收时所有字段完整且未过期；后续消费必须重新检查 TTL，不把固定资格证据当作永久新鲜数据。完整 DIVERGED 不表示需要或允许自动修复生产余额/订单。

## 实际网络与副作用

仅从 JDK `REQUEST:` 日志计数，不使用预算值代替实际请求，不计 RESPONSE 行或本机 REST。固定 HTTPS host=`openapi.okx.com`，以下均为 GET。

| Endpoint / 固定 query | 首轮 | 成功轮 probe | 成功轮 observation | 两轮总计 |
| --- | ---: | ---: | ---: | ---: |
| `/api/v5/account/config` | 1 | 1 | 1 | 3 |
| `/api/v5/account/balance` | 0 | 0 | 1 | 1 |
| `/api/v5/account/trade-fee?instType=SPOT&instId=BTC-USDT` | 0 | 0 | 1 | 1 |
| `/api/v5/trade/orders-pending?instType=SPOT&limit=100` | 0 | 0 | 1 | 1 |
| `/api/v5/account/positions` | 0 | 0 | 1 | 1 |
| `/api/v5/public/time` | 0 | 0 | 3 | 3 |
| `/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT` | 0 | 0 | 1 | 1 |
| Total | 1 | 1 | 9 | 11 |

成功轮精确预算 10/10，其他 endpoint=0，启动/登录期间外联=0。PLACE/CANCEL/AMEND/TRANSFER/WITHDRAW/ACCOUNT_CONFIG_MUTATION 全部为 0。首轮的汇总 mutation 枚举因采集未完整而为 null，不能将 null 当 0；其实际网络仅单个只读 config GET，outboundMutationCount=0，canonical 对比也完整无变化。

两轮各自的前后校验均成立：Order 3→3、ExecutionIntent 1→1、ExecutionReceipt 1→1、Trade 1→1、Ledger 4→4，五表内容摘要不变。其他业务表无变化；仅 `exchange_account_credentials` 权限元数据及 `credential_audit_logs` 发生允许的刷新，credential material/identity 摘要不变。未修改 instrument catalog、账户模式、角色、owner、凭证归属或密码。

两轮终态均为 Java=0、MainPID=0、LIVE=DISABLED、kill=ENGAGED/version 11，临时 runtime secrets 文件已移除，stopExit=0。systemd transient unit 真实状态为 `failed / exit-code`，未为美化状态 reset；安全停机证据与该显示状态分别记录。

## 限制与下一动作

生产诊断的 credential metadata/material/decrypt/GET/POST 计数仍为 `NOT_INSTRUMENTED / null`，不是 0；启动零 credential/decrypt/private/public-rule 请求由独立真实 Spring composition 测试支撑，生产日志直接证明启动/登录 HTTP=0。不把未提供的仪表值补成零。

秘密检查确认已知敏感值、JWT/敏感字段和签名 header 不在输出或日志中；完整 typed facts 仅保存在服务器 root 私有目录，原始 private response 未保存。本 Git 文档不含金额、凭证、token、密钥、签名或私有原始 payload。日志检查和代码/测试共同支撑本轮无秘密暴露结论，不把缺失的 decrypt 仪表解释为额外观测。

模式 3/4、历史回放时点规则、非现货交易与 LIVE 均未获得资格。当前 coverage blocker 已关闭，后续动作以 STATUS 的 `next_action=NONE` 为准；新的业务目标、再次运行或任何真实交易仍受既有 authority 与用户授权边界约束。

本机未提交的执行目录分别为 `artifacts/qualification/okx-account-facts-readonly/20260927-coverage-unblock` 与同级 `20260927-coverage-unblock-attempt2`；保存实际 CI JSON、制品 digest、独立审查、部署结果及脱敏 qualification summary。服务器受限根为 `/var/lib/nexus-quant/qualification/` 下对应目录。旧失败及历史接受事实均原位保留。
