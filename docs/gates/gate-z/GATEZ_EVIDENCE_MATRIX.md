# GateZ capability evidence matrix

以下为历史已接受能力的真实合并提交与合并后 `dev` push CI；本 closeout 不重跑技术测试。PR 与 CI 是不同对象，冻结候选须取得自己的 exact-head CI。

| Capability | Technical identity | Historical exact-head CI | Outcome |
| --- | --- | --- | --- |
| GateZ-1 单策略 Backtest→SIM | PR #24 / `09b7e1cd9c68033b54b75cf361cdda70b584f3a4` | `36111701008` | ACCEPTED |
| 公开行情 replay | PR #27 / `b253dc19124d26cd8b2aab5685d773adc03e3687` | `36141910575` | ACCEPTED |
| 72-bar full-window replay | PR #48 / `1ace052903b6bf70af41f1cf77c72b5157e3c6c8` | `36370231759` | ACCEPTED |
| OKX 账户只读事实 | PR #40 / `e9df23f079f0aaec1eff5b38269f4b18c7e9dae3` | `36295009039` | ACCEPTED；当时 DIVERGED |
| 账户对账语义 | PR #46 / `dc0b01e5653d6718a00b0d667bc2440c47b49b55` | `36326961228` | ACCEPTED；不追认旧 UNKNOWN |
| Continuous SIM | PR #50 / `40bb687246bdeabfbfc1cdc249bcdbe916b5f381` | `36381549433` | ACCEPTED |
| Continuous SIM 启动修正 | PR #51 / `9a5d60eb9e580a54fb01ab6ba9f4ef6c67025ddc` | `36393818232` | ACCEPTED |
| Scheduler Control Plane | PR #52 / `454441d2975440e5295489d838a04bf655919588` | `36454109855` | ACCEPTED |
| Natural SIM economic closure | PR #52 / `StrategySimPostgresIntegrationTest` 20/20，自动 ACCEPTED→FILLED、Trade=1、Ledger exact | `36454109855` | PASS；隔离 PG16 真实后台测试 |
| Scheduler management UI | PR #53 / `4b2830c9cde9b61541ce7471afa073ac8a967764` | `36457727922` | ACCEPTED |
| Scheduler JWT actor | PR #54 / `fddbadeab83914f9e95504e602d1123b16bd4525` | `36515289047` | ACCEPTED |
| SIM account bridge | PR #55 / `078cbdb9020ef7ec469b7c94e1f09b723b5dcd10` | `36523326924` | ACCEPTED |
| Paper canonical projection | PR #56 / `fd2ef8a9d3a7307f127bea8b55e2149c2407a590` | `36531952514` | ACCEPTED |

自然 Continuous SIM 经济观察及 restart exactly-once 依赖既有运行证据；本次没有生成新的观察 identity，也没有把 PR #56 读侧修复写成经济 mutation。PR #42 的根因检查原结果仍是 `UNKNOWN / BALANCE_SEMANTIC_MISMATCH` 且 collector `BLOCKED`；它不是此表中的重新资格 PASS。
