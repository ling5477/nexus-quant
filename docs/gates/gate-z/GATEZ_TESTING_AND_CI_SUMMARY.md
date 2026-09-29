# GateZ testing and CI summary

本表均为 historical accepted evidence，不代表 closeout 重跑。PR #50–#56 的合并后 `dev` `NQ CI Baseline` 运行分别为 `36381549433`、`36393818232`、`36454109855`、`36457727922`、`36515289047`、`36523326924`、`36531952514`，GitHub readback 均 `completed/success/push`。

- GateZ-1：PR #24 `dev` exact-head CI `36111701008`。
- 公开 replay：PR #27 `dev` exact-head CI `36141910575`。
- 72-bar 全窗口：PR #48 `dev` exact-head CI `36370231759`。
- 账户只读事实：PR #40 `dev` exact-head CI `36295009039`，真实资格与代码 CI 分开。
- 对账语义：PR #46 `dev` exact-head CI `36326961228`，未重新观察真实 OKX。

PostgreSQL 16 与 Flyway V53–V58 贯穿公开行情身份、账户快照来源、Continuous SIM 游标、调度控制和 SIM 账户桥接。PR #55 在 PG16.15 上完成 V1→V58 migrate/validate、并发及 LIVE 回归；backend Maven 724 tests、0 failures、173 conditional skips。PR #56 在隔离 PG16.15/V58 上核对 canonical DB、`/facts` 与四个 Paper GET，含空的第二 run 与普通 Paper 回归。

Continuous SIM 的已接受测试覆盖 closed bar freshness、缺口/修订拒绝、重启与并发去重。PR #52 的隔离 PG16 `StrategySimPostgresIntegrationTest` 20/20 包含自动 ACCEPTED→FILLED、Trade=1、精确 Ledger accounting、动态启停和重启控制；这是自然后台经济观察的测试身份，不宣称长期服务器 soak。Scheduler 的管理和 JWT actor 使用真实 Spring Boot、V57 以及 OFF→ON→OFF、RUN_ONCE 的 actor/audit 验证。PR #56 前端 40/40 unit、TypeScript/Vite build 和目标 Paper Detail Playwright E2E 通过。

72-bar 独立 A/B 回放各 22 Order、22 Trade、90 Ledger，最终 cash `100.96886500`、position `0E-8`、PnL `0.968865000`；反事实未来 bar 不改变早先经济事实。自然后台 order/matching 与 restart 观察只按原运行记录解释。此 closeout 只运行文档、authority、archive、stage 与 release 门禁；无重新 PG qualification、全量 Maven 或公开/私有 OKX 请求。
