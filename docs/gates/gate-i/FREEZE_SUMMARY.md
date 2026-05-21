# GateI Freeze Summary

## 完成范围

GateI 是虚拟币量化 V1 完整闭环阶段，包含 4 个子阶段：

| 子阶段 | 范围 |
|--------|------|
| GateI-1-WO | 策略版本与发布记录绑定 |
| GateI-2-WO | 回测追溯与评估指标增强 |
| GateI-3-WO + GateI-3-FIX | SIM/Paper Trading 运行闭环 |
| GateI-4-WO + GateI-4-FIX | 风控回写、资金曲线、持仓曲线、交易复盘、异常停机 |

## 新增 Flyway Migration

| 版本 | 文件 | 内容 |
|------|------|------|
| V19 | `V19__gate_i1_strategy_versions.sql` | strategy_versions 表 |
| V20 | `V20__gate_i2_backtest_traceability.sql` | 回测追溯与评估指标增强 |
| V21 | `V21__gate_i3_paper_trading.sql` | paper_trading_runs/orders/trades/positions |
| V22 | `V22__gate_i4_paper_trading_monitor.sql` | paper_risk_check_results/equity_curve_snapshots/position_curve_snapshots/trade_replay_records/emergency_stop_events |

## 主要 API

GateI-1/2:
- Strategy Version CRUD
- Publish Record CRUD
- Backtest Config 增强（dataset binding）
- Evaluation Report 增强（metrics）

GateI-3:
- `POST /api/paper-trading/runs` — 创建 Paper run
- `POST /api/paper-trading/runs/{id}/start` — 启动
- `POST /api/paper-trading/runs/{id}/stop` — 停止
- `GET /api/paper-trading/runs/{id}/orders` — 订单
- `GET /api/paper-trading/runs/{id}/trades` — 成交
- `GET /api/paper-trading/runs/{id}/positions` — 持仓

GateI-4:
- `GET /api/paper-trading/runs/{id}/risk-results` — 风控结果
- `POST /api/paper-trading/runs/{id}/risk-results/run-once` — 执行风控
- `GET /api/paper-trading/runs/{id}/equity-curve` — 资金曲线
- `GET /api/paper-trading/runs/{id}/position-curve` — 持仓曲线
- `GET /api/paper-trading/runs/{id}/replay` — 交易复盘
- `POST /api/paper-trading/runs/{id}/emergency-stop` — 紧急停机
- `GET /api/paper-trading/runs/{id}/emergency-stops` — 停机事件

## 主要 DB 表

GateI-1: `strategy_versions`
GateI-2: `backtest_configs` 增强、`backtest_eval_reports` 增强
GateI-3: `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions`
GateI-4: `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`

## 前端能力

- `/paper-trading` 页面：列表、创建、启动、停止、详情抽屉。
- 详情抽屉 Tab：订单、成交、持仓、快照、风控结果、资金曲线、持仓曲线、交易复盘、异常停机。
- 策略版本管理、发布记录管理、回测追溯增强、评估指标增强。

## 验证结果

| 命令 | 结果 |
|------|------|
| `mvn -f backend/pom.xml test` | 35 tests / 0 failures |
| `npm run build` | passed |
| `npm run test:e2e` | 19 passed / 1 skipped |

唯一 skipped：`E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路，不影响 GateI。

## 剩余风险

- `npm audit` 仍有 4 个既有依赖漏洞。
- Vite chunk > 500 kB 警告仍存在。
- Ant Design React 19 compatibility / `Card.bordered` / `Modal.destroyOnClose` deprecation warning 仍存在。
- GateJ 尚未开始。
- AI 仍未开始。

## 结论

- **GateI completed。**
- Next: GateJ-PLAN（Paper Trading 稳定运行）。
- GateJ 不是 AI 阶段。AI 最早 GateK 才允许进入信号层。
