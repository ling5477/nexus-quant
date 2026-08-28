# Roadmap

本文件只定义下一允许动作。current Gate、freeze tag、work batch、LIVE 与 kill switch 必须解析 [STATUS.md](STATUS.md) 的 `nq-current-authority` 区块。

## 当前路线

```text
GateY FROZEN / ACCEPTED / TAGGED
  ↓
GateAUDIT-0C R2 independent review ACCEPTED
  ↓
GateAUDIT-0C R3 final independent review ACCEPTED / READY_TO_COMMIT
  ↓
NQ-GATEAUDIT-0C-R3-COMMIT
```

## 下一允许动作

- 唯一下一动作：`NQ-GATEAUDIT-0C-R3-COMMIT`。
- R3 final independent review 已接受 current post-R3 final candidate，decision=`PASS / REVIEW_ACCEPTED / READY_TO_COMMIT`，P0=0、P1=0；不得再次启动 review 或 remediation。
- commit 尚未创建，exact-head CI 尚未运行；Phase 0 尚未完成，不得 push、PR、tag 或进入 Phase 1 Inventory。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；不得再次 pilot、PLACE、CANCEL、transfer、withdraw 或触达 credential/生产服务器/生产数据库。
- GateY frozen archive 与 `nq-gatey-freeze` 不可改写。
- 后续全仓 Inventory 必须由 machine policy 的 `audit.bootstrapCharter` 所声明的唯一 Charter 驱动，默认只读且禁止自动整改；字段或目标无效时 fail-closed。
