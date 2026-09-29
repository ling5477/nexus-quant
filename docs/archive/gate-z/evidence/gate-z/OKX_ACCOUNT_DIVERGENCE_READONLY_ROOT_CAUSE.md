# OKX 账户差异只读根因检查（2026-09-27）

## 判定

`ROOT_CAUSE=UNKNOWN / BALANCE_SEMANTIC_MISMATCH`。本轮确认了当前 OKX 账户与 NQ 已保存事实的若干存在性差异，但**不能把历史 `divergence=DIVERGED` 归因于某一个余额数值差**，也不能将本轮提升为 `ACCOUNT_DIVERGENCE=EXPLAINED`。一次比较内，`account_snapshots` 无交易环境字段，而映射的 legacy account `1` 含 SIM trade；比较器因无法证明本地余额与真实 OKX LIVE 余额同义，返回 `UNKNOWN`。这符合禁止不同余额口径硬比较的边界。

补充只读证据确认：OKX 当次返回 7 种正余额资产；DOGE、ETH、OKB、PEPE、TRUMP 在本地账户快照集合中没有对应资产行。BTC 为正且低于当次公开 BTC-USDT 最小数量 `0.00001`，仍保留为正余额事实。本地 BTC/USDT 快照已超过 31 天；这证明比较窗口不成立，**不证明时间差必然造成金额差**。这些补充结论来自同一已保存的私有观察及其后内容未变化的 canonical 表，不是第二次 OKX 请求，也不替代 typed report 的 `UNKNOWN`。

## 候选与执行身份

技术 PR [#42](https://github.com/ling5477/nexus-quant/pull/42) 已合并至 `dev=f7a5faa782e55a87f263f7fefba4b717599396d9`。该候选的定向独立正确性审查为 PASS，剩余 P0/P1/P2/P3 均为 0；本地 81 项定向测试通过（account service 27、公开规则 10、隔离 PG16/V54 集成 33、scoped 配置 9、production context 2）。合并后 [exact-head CI run 36302559099](https://github.com/ling5477/nexus-quant/actions/runs/36302559099) 为 `push/dev`、同一 head、attempt 1、9/9 job success；详见 [CI JSON 换行规范化副本](okx-account-divergence-root-cause-20260927/exact-head-ci.json)。原始 JSON 与发布副本的 SHA-256 分别保留于 [release 身份](okx-account-divergence-root-cause-20260927/release-identity.json)。

本轮 canonical release 为 `nq-f7a5faa782e5-20670623964ad074`，manifest SHA-256 为 `f364e593c450303c0da8b9ebaa2633875c46cdeeb5c55037611c4a3c845fb070`，外部 `EXACT_HEAD_CI` admission SHA-256 为 `4334ae3433eb5853f89c7baddf51028b102095d63eb6fb3a58620bf0474539df`。候选、manifest、admission、source tree 和 CI run 已精确绑定，见 [release 身份](okx-account-divergence-root-cause-20260927/release-identity.json)。[生产预检](okx-account-divergence-root-cause-20260927/production-precheck.json) 确认旧 release、PG16/V54、失败迁移 0、kill engaged、owner 绑定唯一、Java 进程 0。[部署结果](okx-account-divergence-root-cause-20260927/deployment-result.json) 确认普通 install/activate、只读 Flyway validate、migration 执行数 0、activation `COMPLETED`，当前指针精确为本轮 release。

首轮部署归档的 helper 把加密 `.cred` 按明文读取，在安装前停止；[失败原件](okx-account-divergence-root-cause-20260927/first-deployment-failure.json) 记录 `completedSteps=[]`、旧指针未变、runtime 未启动。第二轮使用独立 root 和归档，以既有 `systemd-creds --name=db-password --newline=no` 在内存中取得数据库认证材料；未轮换、复制到 evidence 或输出密码。第二轮修正了只读 Flyway helper 的固定目录，并绑定新 helper 摘要。第一次 Windows 打包传错前端目录，生成与 admission 不符的 release；该包没有部署，按 CI `artifacts/dist` 参数重建后，releaseId 与 manifest 摘要均精确匹配。

## 同一次观察的字段事实

正式 run 在 `2026-09-27T08:24:49.895284597Z` 取得 account facts，`2026-09-27T08:24:51.283943312Z` 完成 canonical 比较。permission probe 为 `SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED`，年龄约 `0.155s`。mode=`2`，非 SPOT positions=`OBSERVED / NO_ACTIVE_POSITION / 0`，OKX SPOT open orders=`OBSERVED / 0`，BTC、USDT 与全部返回余额、费率、交易所时间及当前公开规则均已观察。[脱敏结果](okx-account-divergence-root-cause-20260927/qualification-summary.json) 绑定 observationId `6ab8313a-bbcf-424a-9413-130837cb8355` 和受限原件 SHA-256 `6b43744bcff6f2af92c63cdffb65739c9cf55b9a3095885772c2dc14fbb2114b`。

| 维度 | 只读结论 | 证据边界 |
| --- | --- | --- |
| 余额口径 | typed report=`UNKNOWN / BALANCE_SEMANTIC_MISMATCH` | 本地账户 1 有 SIM trade 1、LIVE trade 0；`account_snapshots` 无环境字段。禁止把 OKX total/available/frozen 与本地投影直接判为相等或不等。 |
| 资产集合 | OKX 返回 BTC、USDT、DOGE、ETH、OKB、PEPE、TRUMP，7 项 total 均为正；本地快照只有 BTC、USDT | 额外 5 项是确定的存在性差异；是否属于 NQ 应纳入的 canonical 业务范围需单独裁定。未导出私有金额。 |
| BTC 小额余额 | BTC 为正，低于当次公开 `minSz=0.00001` | 仅说明低于当前公开最小下单数量；不置零，也不据此推出本地 BTC 金额差。 |
| 订单 | OKX SPOT open orders 0；本地活跃 LIVE orders 0 | 本地另有活跃 SIM orders 2，不参与真实账户比较。未执行取消或认领。 |
| 仓位 | OKX 非 SPOT position 0；本地旧 SPOT position 行 1、非零行 1 | 两者不是相同产品维度，不能相加或互相抵消；该本地行时间为 8 月 26 日。 |
| 时效 | 外部各必需私有字段在 `comparisonAt` 新鲜；本地 BTC/USDT 最新快照为 `2026-08-26T17:06:52.602+08:00` | 本地快照明显超出 60 秒比较窗口，未证明时间差的因果方向。旧 snapshot 不作为当前实时事实。 |

[资产存在性与公开最小数量](okx-account-divergence-root-cause-20260927/balance-presence-summary.json)、[canonical 语义计数](okx-account-divergence-root-cause-20260927/canonical-semantic-diagnostic.json)及[订单/仓位计数](okx-account-divergence-root-cause-20260927/canonical-dimensions-diagnostic.json)均不含私有金额。补充诊断晚于正式观察；[内容连续性核对](okx-account-divergence-root-cause-20260927/canonical-continuity.json)显示 `account_snapshots`、`orders`、`positions`、`trades`、`ledger_entries` 与观察终态的完整表摘要一致。[事后分析](okx-account-divergence-root-cause-20260927/post-run-analysis.json)仅确认各外部必需字段在**当时**的比较时刻新鲜，不声称现在仍新鲜，也不追认本轮 qualification PASS。

typed report 只有一项 `BALANCE / UNKNOWN / BALANCE_SEMANTIC_MISMATCH`，aggregate=`UNKNOWN`，不把上述存在性差异伪装成已完成的余额数值分类。报告绑定 observationId、canonicalFactIdentity=`15ce35ff749c2f5e0722acd1295f924665dda2eeb275a8adcd51ccea0ddcf93d` 与当次 publicRuleIdentity；`externalFactIdentity=null` 是实际输出，受限原件摘要另行绑定观察身份。BTC/USDT 的外部 total/available/frozen 数值及本地余额数值仅留在受限原件，本公开证据不导出，因口径未获证明也不列出数值差。

## 原始失败与副作用边界

正式采集器在处理 typed `divergence=UNKNOWN` 时，对无 `expiresAt` 的字段执行时间解析，触发 `AttributeError`；原始 decision 保留为 `QUALIFICATION_BLOCKED_COLLECTOR_ATTRIBUTEERROR`。该错误发生在唯一一次 account-facts observation 已完成之后。受限原件、采集器 SHA-256 `d7abce89da4e6c7e4c17ddc008126d172a3df616149398250fa78c51d114f886`、出错行及终态均保留；事后分析不会覆盖失败记录。此缺陷只影响 collector 的验收包装；typed report 本身已明确返回 `UNKNOWN`，因此即使包装修复，本轮也不能宣称精确余额根因已识别。

JDK request 日志实际计得固定 HTTPS GET 10 次：config 2、balance 1、trade-fee 1、orders-pending 1、positions 1、public time 3、public instruments 1；其他 endpoint 0，provider 非 GET 0。`PLACE/CANCEL/AMEND/TRANSFER/WITHDRAW/ACCOUNT_CONFIG_MUTATION=0`。Order 3、ExecutionIntent 1、ExecutionReceipt 1、Trade 1、Ledger 4 在前后均为相同数量及内容摘要；全部非允许业务表无变化。credential material/identity 和 owner 密码未变，已知敏感值未出现在日志；临时 runtime secrets 文件已移除。最终 Java/MainPID=`0`、canonical service inactive、LIVE=`DISABLED`、kill=`ENGAGED`。上述为安全终态证明，不是 root-cause qualification PASS。

## 后续边界

保留此前已接受的 `REAL_ACCOUNT_READONLY_FACTS_QUALIFIED` 历史资格。本轮 root-cause work batch 为 `BLOCKED`；只有单独解决账户快照 LIVE/SIM 语义和时效来源、再用新的候选与一次独立只读观察，才能决定 BTC/USDT 金额差或把 `ACCOUNT_DIVERGENCE` 标为 `EXPLAINED`。本证据不授权账户修复、自动对账、交易、LIVE 启用、解除 kill switch 或凭证变更。
