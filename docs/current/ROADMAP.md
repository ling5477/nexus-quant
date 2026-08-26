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
NQ-GATEY-FREEZE-CLOSEOUT
```

## 下一允许动作

- 当前唯一治理动作是 `NQ-GATEY-FREEZE-CLOSEOUT`。
- 允许：完成 [GateY strict archive candidate](../gates/gate-y/README.md)、运行 archive/authority/link/frozen/secret 验证、精确 commit/push、等待 freeze commit exact-head CI、创建不可移动 annotated tag、再同步 post-tag current authority。
- 禁止：controller 再执行 pilot、PLACE、CANCEL、Attempt-02、第二订单、transfer、withdraw、重新 DISENGAGE kill、修改生产业务事实、credential/OKX 权限或重新部署 pilot runtime。

## Freeze 后路线

GateY tag 与 post-tag authority sync 完成后，不进入 GateZ，也不继续扩真实交易。唯一下一阶段为：

```text
NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION
```

该阶段只在 post-tag authority 中初始化为 `NOT STARTED`（未开始）；本 pre-tag closeout 不执行全仓 audit。后续范围包括 inventory、死代码/重复实现/Gate 临时代码/架构/Spring composition/DB-model/tests/fixtures/scripts/deploy/docs/evidence/CI/frontend 结构债审计与收口。

## Persistent boundary

- GateY 只证明单账户、单 credential、OKX Spot BTC-USDT BUY LIMIT、`<= 10 USDT`、人工受控 exactly-one PLACE 与完整 reconciliation。
- 不证明通用 LIVE、自动策略实盘、多订单、多账户、多交易所、合约/杠杆、HA、长期 soak、AI/DH trading 或 transfer/withdraw。
- `NO_SECOND_REAL_PILOT` 永久成立；任何新真实交易必须属于未来独立阶段、独立 exact scope 与独立用户授权。
