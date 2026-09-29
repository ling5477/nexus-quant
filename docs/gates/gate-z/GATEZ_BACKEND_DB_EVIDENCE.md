# GateZ backend and database evidence

GateZ 的价值是同一策略版本的研究、SIM 决策和 canonical 经济事实闭环；migration 只是持久化与来源约束。既有 V53–V58 SQL 均保留历史内容，本 closeout 的 migration diff 必须为零。

| Migration | Business meaning |
| --- | --- |
| V53 `public_market_capture` | 冻结响应、bar、时间可见性、规则 digest 与 dataset 身份，供可重放公开行情输入 |
| V54 rule response identity | 冻结公开 instrument 规则原始响应及 digest，不把当前规则追认为历史时点规则 |
| V55 account snapshot provenance | `trade_env`、balance basis/scope、`recorded_at`；历史 NULL/UNKNOWN 不回填猜测 |
| V56 continuous SIM progress | 每个 Paper run 的游标、已观察 bar、停滞与失败状态，支持 closed-bar 及重启去重 |
| V57 scheduled job controls | 固定 job key、enable、delay、run/actor/audit 状态；默认关闭不授予自动执行 |
| V58 SIM account identity bridge | ACTIVE SIM 的确定性 legacy account 对接；保留 OKX/LIVE 不可变绑定保护 |

Backend 主链以 Strategy SIM 服务调用 canonical Risk/Execution；`PAPER_MATCHING` 消费 canonical SIM Order 并写 Trade/Ledger/Position/Cash。PR #56 的 Paper Detail 修复属于读侧投影，不增加经济 mutation。账户对账只比较有证明归属、同义且新鲜的 NQ managed LIVE 事实，whole-venue 余额仍是外部上下文。
