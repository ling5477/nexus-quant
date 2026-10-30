# Roadmap

当前阶段和安全状态由 [STATUS.md](STATUS.md) 的机器区块唯一拥有。本文件解释当前迭代及历史发布动作，不授予提交、公开发行或部署权限。

## 已发布 v1.0.2 与下一阶段

v1.0.0、v1.0.1、v1.0.2 均已发布。v1.0.2 的 V102-01～05（数据集、现货策略、实验编排、Walk-Forward、Backtest/Continuous SIM 比较）已完成；固定 RC3 正式资格及最终准入已接受，[GitHub Release 427705563](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.2) 于 2026-10-29T10:15:34Z 公开。

发行 Source 为 `e4eca0b23c769602999043b3b95b8777a33bd285`，无父 Snapshot 为 `6b5ddcfac9f9ac02e343975ffe2d061df9785318`，Tag 为 `v1.0.2`，schema V49。完整固定身份以 [STATUS](STATUS.md) 为准；[TESTING](TESTING.md) 和 [WORKLOG](WORKLOG.md) 追加最终资格及发布证据，原[构包前准备报告](evidence/NQ_V102_RELEASE_READINESS.md) 保持历史时点。

Windows 11 amd64 Self-Hosted 新安装、v1.0.1 普通更新、历史数据保留、包级备份恢复、主动回滚与失败更新自动恢复均通过。本轮只同步发行事实，不重跑资格，不重构 RC3。Linux 仅低内存安装与 600 秒短时运行 SCOPED_PASS；正式 Linux 支持和长期稳定性仍未验证。既存低内存断言差异是有证据的非阻断残留，原失败保留，未来版本可定向调整预期。

v1.0.3 第一项功能 NQ-V103-01 有界长历史研究已完成实现和功能候选验证，准确接受 pair 由 [STATUS](STATUS.md) 拥有。原研究页面支持 OKX SPOT BTC/ETH 1h、1～180完整UTC日、最多4320根；分页接入保留每次3000根预算与显式续传；长输入使用不可变V2版本/分块，旧V1继续兼容；原回测/参数实验与90/180日预设Walk-Forward消费同一冻结版本。长实验最多4候选，不扩大Continuous SIM边界。[实施证据](evidence/NQ_V103_01_HISTORICAL_RESEARCH_EXPANSION.md)记录本地真实PG/浏览器、资源及PR交付适用范围。开发V51不改变正式v1.0.2/V49发行。

下一任务为 NQ-V103-02 前向 SIM 观察与复盘，NOT_STARTED，等待后续实施指令；随后才是最小离线因子研究03。本任务不自动执行02/03。保留 canonical Order/Trade/Ledger、LIVE=DISABLED、Kill Switch=ENGAGED、历史 C4 NOT_ACCEPTED / EVIDENCE_GAPS 与用户例外；不重跑72小时。Public Demo、其他正式平台及v1.0.0直接普通升级不属于本次发行。

下文为2026-10-20发布准备历史，保留原始身份与结论，不作为本轮待办。

## 历史状态

截至 2026-10-20，dev 集成源码为 `565764cce5862ecc02ca7d1ce18d56fc382d8841`，tree=`39d472ade2d678e0e053875fcd425611f991614f`；PR #64 已合并，同一 head 的 CI `39373837941` 为 completed/success、9/9 SUCCESS。该CI只绑定集成基线，新文档与Snapshot提交的远端CI必须单独获得。

C1/C2/C3 已完成；下一阶段是解决 Release 发布门槛，再进入版本迭代。C4 的实际原始终态为 **72小时观察完成 / NOT_ACCEPTED / EVIDENCE_GAPS / SAFE_STOP_CONFIRMED**：候选 `064b6801f08486943c6e4691537de641dcd25c4d`、release=`nq-064b6801f084-04b6ffbe6403e926`，缺 T+12/18/36/60h 定点 UI 证据。当前候选的撮合、记账、调度与 schema 差异已按正式白名单逐项绑定已有集成/CI证据及39项定向PG/HTTP/跨JVM证明；原C4不承担新增代码的正确性证明。旧 V58 不作为新 V34 的运行升级来源，正式安装仍要求空库。用户随后明确决定：`C4_OWNER_DECISION=ACCEPT_WITH_EVIDENCE_EXCEPTION`、`C4_72H_RERUN=WAIVED`，接受历史定点 UI 证据缺口并豁免72小时重跑；原审查仍为 `NOT_ACCEPTED / EVIDENCE_GAPS`，没有后续独立改判报告。这是单独的 owner 决定，不追认缺失证据、不改写历史审查；新增运行路径另由本次定向证据证明。

本次用户已授权Release Admission裁定；现有合同不禁止在保留原审查的前提下单列历史证据例外。历史定点UI缺口按 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 准入，新增行为由独立定向审查与上述证据覆盖，未降低或修改validator。`C4_REVIEW=NOT_ACCEPTED/EVIDENCE_GAPS`、`C4_72H_RERUN=WAIVED` 均保留。新文档提交及独立Release Snapshot仍须完成其准确SHA的远端CI，不能用565764cc旧CI代替；本地包冻结与公开发布分开记录。

本地包 `C:/nq-v1-final-20261020-565764cc/package-amd64-final` 的 Release ID 为 `53dc4bd18e2797cfb1327a1c04c3ce374d7565d7db7c5b03d90b517b608f1949`，Windows 11 amd64 / 已运行 Docker Desktop 的隔离全新安装、PG16.15/V34、备份恢复和恢复后重启已通过，范围为 `QUALIFIED_LOCAL_SCOPED`，不等于公开发布或 C4 接受。运行入口 http://127.0.0.1:18580；保持 Web 请求/回执人工交接，不授予 Web 宿主执行权限。严格更新版本条件不满足，保留 `UPGRADE_NOT_VERIFIED`。

保留四项外部数据限制：`ACCOUNT-PERFORMANCE` 普通账户缺可信权益来源时不编造收益；`ACCOUNT-FEE-SOURCE` 不编造真实手续费；`PRICE-EVENTS` 不从 OHLC 编造实际发生时点；`LEGACY-VALUATION` 不更改旧 Paper 未知币种权益。`LIVE=DISABLED`、kill=`ENGAGED`，真实 provider/client/交易所写入关闭。

清理结果为 `NQ_V1_LOCAL_CLEANUP_PARTIAL / EXACT_RETAINED_RESOURCES`，不是全部完成。以 `E:/nq-v1-cleanup-20261020-565764cc/NQ_V1_FINAL_CLEANUP_REPORT.md` 为准；18580、18099 Demo、正式包、必要备份、C4与恢复证据保留。本轮未清理资产。

本轮正式清单内产品文档随新的本地开发提交、独立白名单Snapshot和新目录发行包共同冻结；来源及最终摘要见 `C:/nq-v1-freeze-20261020-565764cc/NQ_V1_FINAL_RELEASE_ADMISSION_AND_PACKAGE_FREEZE_REPORT.md`。旧包不修改、不复用旧摘要代表新内容。开发文档不进入Release，精确白名单不扩大。当前机器work batch指向尚未公开发布的新候选；远端exact-head CI未经执行，不声明已通过。

## 历史下一允许工作

1. 保留已接受的历史UI证据例外和重跑豁免，复用已完成的39项定向证明及独立审查；后续发布前取得新SOURCE_SHA和Release Snapshot准确身份的远端CI，不重跑72小时。
2. 本轮已授权文档本地提交及新包冻结；最终源码、包与安装恢复身份由本轮最终报告记录，原包保持不变。
3. release/1.0.0采用独立白名单Snapshot历史；包SOURCE_SHA绑定完整开发提交，Snapshot只作精确投影，映射见最终报告。远端CI和发布动作仍须明确授权；没有Tag或GitHub Release。
4. 发布后版本迭代继续保留 canonical Java 交易 authority；多交易所、完整研究框架、AI/DH、LIVE 自动执行及历史兼容列清理另行规划。

C1 空库主链、C2 benchmark/OOS、C3 同源 UX 不再列为下一实现任务。历史工作包定义见 [V1计划](NQ_V1_CLOSURE_PLAN.md)，其原始计划语句不覆盖本页。

## 证据索引

- C4 原始终态（本地证据，非Git文件）：`E:/Project/nexus-quant/artifacts/runtime/v1-c4-064b6801-20261006/attempt-02/evidence/final-rc-report.md`；JSON、final-evidence-index、controlled-restart 与 final-facts-review-confirmed 在同目录，均为只读历史。
- 本地发行：`C:/nq-v1-final-20261020-565764cc/NQ_V1_FINAL_LOCAL_RELEASE_REPORT.md`。
- 清理：`E:/nq-v1-cleanup-20261020-565764cc/NQ_V1_FINAL_CLEANUP_REPORT.md`。
- 本轮闭包/文档报告：`E:/nq-v1-release-closure-20261020-565764cc/` 下两份 NQ_V1_*_REPORT.md；机器证据在其 evidence 目录，独立 Git-blob 快照为 release-source-565764cc。
