# GateZ boundary statement

GateZ 冻结只覆盖 OKX Spot 单策略、冻结公开行情、Backtest/Evaluation/Publish 到 Continuous SIM 和 canonical 经济事实。Java Control Plane 是 Risk、Order、Trade、Ledger 的 owner；Python 仅离线 research，不拥有运行时交易权限。

- `SIM` 是 GateZ 自动经济闭环的唯一运行环境。
- `LIVE` 既不是当前默认值，也没有从 archive 获得启动权限。
- OKX private account-facts 历史只读资格仅证明当时限定的 GET 与安全终态。
- Public replay 的历史规则是采集时观察的冻结假设，并非历史窗口规则证明。
- Paper Detail summary PnL 读取 canonical `/facts`；Position 行不独立披露 PnL。

冻结不表示 LIVE 自动交易、真实 provider、private trading、第二策略、多策略、portfolio allocation、Factor Library、VectorBT/Freqtrade、Binance、futures/leverage/options/on-chain、AI/DH、MCP trading、Shadow expansion、隔离 execution worker 或复杂统计平台已完成。这些均 `OUT OF GATEZ`，由后续 V1 规划分别选择。

安全状态按当前 STATUS：`LIVE=DISABLED`、`kill_switch=ENGAGED`、`shadow_trading=NOT_ENABLED`、`real_provider=NOT_IMPLEMENTED`、`private_trading=NOT_IMPLEMENTED`。本 archive 不授权真实 PLACE/CANCEL、资金移动、解除 kill、生产部署、凭证使用或任何交易 mutation。

只读账户事实的历史 DIVERGED/UNKNOWN 与 Paper SIM 的 canonical 经济事实属于不同环境、所有权及时间口径。不得用冻结结论修复、改判或推断真实账户余额匹配。
