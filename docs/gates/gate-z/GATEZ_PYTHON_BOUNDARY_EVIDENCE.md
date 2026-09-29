# GateZ Python boundary evidence

Python 保持离线 research 域，用于隔离实验和研究辅助；GateZ 的公开行情回放、策略版本、Backtest/Evaluation/Publish、Continuous SIM、Scheduler、Risk、Order、Trade、Ledger 与 Paper Detail 主运行链由 Java Control Plane 承担。

- Python 研究输出不能直接建立 canonical Order。
- Python 研究输出不能写 canonical Trade 或 Ledger。
- Java 的冻结 StrategyVersion 是运行输入的稳定业务身份。
- Java Risk/Execution 是 SIM 交易 mutation 的正式入口。
- `research/py` 不是后台 scheduler 的 owner。
- 本 closeout 不启动 research 实验或导入外部策略框架。

Python 没有直接 LIVE 交易权限，也不是 canonical Order/Trade/Ledger 第二事实源。研究结果只有经过 Java 的正式版本、评价、发布及风险/执行路径，才进入 GateZ 已接受的 SIM 链。

本冻结不宣称 Python runtime trading、VectorBT/Freqtrade 集成、ML/AI 自动交易或 DH integration。相关方向仍为 `OUT OF GATEZ`。本 closeout 不改 Python 源码、依赖、模型或实验数据。
