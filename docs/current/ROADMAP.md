# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 区块。

## 当前路线

```text
GateX FROZEN / ACCEPTED / TAGGED
  ↓
GateY-1～6F ACCEPTED / CI GREEN
  ↓
GateY minimal live pilot VERIFIED
  ↓
GateY-FREEZE ACCEPTED / CI GREEN / FREEZE READY
  ↓
GateY FROZEN / ACCEPTED / TAGGED
  ↓
GateAUDIT-PLAN NOT STARTED
  ↓
NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION
```

## 下一允许动作

- 当前唯一治理动作是 `NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION`。
- 当前状态：`GateAUDIT-PLAN=NOT STARTED`（未开始）；下一独立任务必须先做全仓只读 inventory/audit，再形成最小、可审查、可回滚的 consolidation 批次。
- 禁止：直接删除代码、批量重构、进入 GateZ、继续扩真实交易、controller 再执行 pilot、PLACE、CANCEL、Attempt-02、transfer/withdraw、修改生产事实或重新部署 pilot runtime。

## Freeze 后路线

GateY tag 与 post-tag authority sync 已完成。不进入 GateZ，也不继续扩真实交易；唯一下一阶段为：

```text
NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION
```

该阶段当前只初始化为 `NOT STARTED`（未开始）；本 post-tag sync 不执行全仓 audit。后续范围包括 inventory、死代码/重复实现/Gate 临时代码/架构/Spring composition/DB-model/tests/fixtures/scripts/deploy/docs/evidence/CI/frontend 结构债审计与收口。

## Persistent boundary

- GateY 只证明单账户、单 credential、OKX Spot BTC-USDT BUY LIMIT、`<= 10 USDT`、人工受控 exactly-one PLACE 与完整 reconciliation。
- 不证明通用 LIVE、自动策略实盘、多订单、多账户、多交易所、合约/杠杆、HA、长期 soak、AI/DH trading 或 transfer/withdraw。
- `NO_SECOND_REAL_PILOT` 永久成立；任何新真实交易必须属于未来独立阶段、独立 exact scope 与独立用户授权。
