# NexusQuant

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘等底座扩展到美股和 A 股。

当前事实入口以 `docs/current/` 为准。`docs/gates/` 只保存已完成 Gate 的冻结卷宗，`docs/archive/` 只作历史归档参考。

## 当前状态

- GateH completed
- GateI completed
- GateJ-PLAN completed
- GateJ-1-WO completed
- GateJ-2-WO completed
- GateJ-3-WO completed
- Next: GateJ-FREEZE（1h/24h/7d 连续运行验收 + 冻结）
- AI not started
- GateK not started

GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。当前不允许接入 AI。

## 当前能力摘要

- 交易工作台已完成（GateH）。
- OKX / Binance SPOT 历史 OHLCV K 线接入已完成（GateH）。
- marketdata dataset 与 backtest config 绑定已完成（GateH）。
- `strategy_versions` 与 publish workflow 已完成（GateI）。
- backtest config / evaluation / traceability 增强已完成（GateI）。
- SIM / Paper Trading 运行闭环已完成（GateI）。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构已完成（GateI）。
- Paper Trading 调度 / 心跳 / 日报 / 告警 / 恢复事件 / 稳定性验收结构 / HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库已完成（GateJ-1/2/3）。

## 当前明确不做

- AI / AI 信号 / AI 自动交易 / AI Paper Trading
- 真实 LIVE 下单与真实交易所下单接口调用
- 美股 / A 股
- 合约全量
- 高频
- 复杂因子平台
- 外部通知（邮件 / Slack / 钉钉 / 企业微信 / Telegram / Webhook / 短信）
- 自动恢复策略引擎

## 当前文档入口

- `docs/current/README.md`：当前事实入口索引
- `docs/current/STATUS.md`：当前项目状态
- `docs/current/ROADMAP.md`：总路线
- `docs/current/PLAN_GATEJ.md`：GateJ 规划
- `docs/current/GATEJ_WORK_ORDER.md`：GateJ 工作单（含 GateJ-FREEZE 范围）
- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`
- `docs/current/DOC_CLEAN_REPORT.md`：最近一次文档清理报告
- GateH 冻结卷宗：`docs/gates/gate-h/`
- GateI 冻结卷宗：`docs/gates/gate-i/`

## 当前验证基线

后端：

```powershell
mvn -f backend/pom.xml test
```

前端：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Python：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

详细验证记录见 `docs/current/TESTING.md`。

## 剩余已知风险

- `npm audit` 仍有既有告警。
- Vite chunk > 500 kB 警告仍存在。
- Ant Design React 19 compatibility / deprecation warning（`Card.bordered`、`Modal.destroyOnClose`）仍存在。
- E2E 仍有 1 个 skipped（`E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路），与 GateJ 主链无关。
