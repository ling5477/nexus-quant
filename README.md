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
- DOC-CLEAN-2 completed
- PRE-FREEZE-CODE-AUDIT second pass completed（无 P0；E2E 与 Python 基线已由 Codex 实际重跑通过，详见 `docs/current/PRE_FREEZE_AUDIT_REPORT.md`）
- GateJ-FREEZE-FIX second pass completed（详见 `docs/current/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`）
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed
- GateJ completed（详见 `docs/current/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`）
- Next: GateK-PLAN
- AI not started
- DH integration not started / not connected to NQ
- Multi-exchange expansion not started

GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。GateJ-FREEZE 稳定性验收已通过；AI、AI 信号、AI 自动交易、AI Paper Trading、DH integration、多交易所扩展仍未开始。当前 Next 只是 GateK-PLAN，不代表 GateK 实现已启动。

## 当前能力摘要

- 交易工作台已完成（GateH）。
- OKX / Binance SPOT 历史 OHLCV K 线接入已完成（GateH）。
- marketdata dataset 与 backtest config 绑定已完成（GateH）。
- `strategy_versions` 与 publish workflow 已完成（GateI）。
- backtest config / evaluation / traceability 增强已完成（GateI）。
- SIM / Paper Trading 运行闭环已完成（GateI）。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构已完成（GateI）。
- Paper Trading 调度 / 心跳 / 日报 / 告警 / 恢复事件 / 稳定性验收结构 / HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库已完成（GateJ-1/2/3）。
- GateJ-FREEZE 30m / 1h / 24h / 7d 连续运行验收已通过，GateJ completed。

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
- `docs/current/PRE_FREEZE_AUDIT_REPORT.md`：GateJ-FREEZE 前置代码 / 文档 / 实现真实性审查报告
- `docs/current/PRE_FREEZE_AUDIT_FIX_PLAN.md`：PRE-FREEZE-CODE-AUDIT 修复计划与 GateJ-FREEZE 入场条件
- `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`：GateJ-FREEZE 1h/24h/7d 验收记录模板
- `docs/current/GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`：GateJ-FREEZE 最终验收报告
- `docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`
- `docs/current/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`：credential active material selection Batch 5-E-A 只读审计报告
- `docs/current/DOC_CLEAN_REPORT.md`：最近一次文档清理报告
- GateH 冻结卷宗：`docs/gates/gate-h/`
- GateI 冻结卷宗：`docs/gates/gate-i/`
- GateJ 冻结卷宗：`docs/gates/gate-j/`

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
- E2E 本轮二次审查实际结果为 24 passed / 1 skipped；唯一 skipped 为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路，与 GateJ 主链无关。
- Python 本轮二次审查实际结果为 pytest 2 passed、mypy success、ruff all checks passed；默认 WindowsApps `python` alias 不可用，人工复跑需使用真实 Python 解释器或修正 PATH。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed；本轮最终文档阶段未重新执行 build/deploy/restart。
- UI/UX smoke review 结论为 Functional stability PASS、UI/UX professionalism FAIL；该项登记为 post-freeze remediation，不能宣称 UI/UX 专业化已完成。
- 当前不应描述为面向公开用户的生产就绪。
