# NexusQuant

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘等底座扩展到美股和 A 股。

当前事实入口以 `docs/current/` 为准。`docs/gates/` 只保存已完成 Gate 的冻结卷宗，`docs/archive/` 只作历史归档参考。

## 当前状态

- DOC-CLEAN completed
- BASELINE-FIX completed
- GateH completed
- GateI completed
- GateJ-PLAN completed
- GateJ-1-WO completed
- GateJ-2-WO completed
- GateJ-3-WO completed
- Current: GateJ-3-WO completed（异常恢复、失败重试、稳定性验收结构、自动告警最小落库）
- Next: GateJ-FREEZE（1h/24h/7d 连续运行验收 + 冻结）
- AI not started（AI 最早 GateK 才允许进入信号层）

GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。当前不允许接入 AI。

## 当前能力摘要

- 交易工作台已完成。
- OKX / Binance SPOT 历史 OHLCV K 线接入已完成。
- marketdata dataset 与 backtest config 绑定已完成。
- `strategy_versions` 与 publish workflow 已完成。
- backtest config / evaluation / traceability 增强已完成。
- SIM / Paper Trading 运行闭环已完成。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构已完成。

## 当前明确不做

- AI
- AI 信号
- AI 自动交易
- AI Paper Trading
- 美股/A 股
- 合约全量
- 高频
- 复杂因子平台

## 当前文档入口

- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/PLAN_GATEI.md`
- `docs/current/GATEI_WORK_ORDER.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`

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

## 剩余已知风险

- `npm audit` 仍有既有告警。
- Vite chunk > 500 kB 警告仍存在。
- Ant Design React 19 compatibility / deprecated warning 仍存在。
- E2E 仍有少量 skipped，原因见 `docs/current/TESTING.md`。
