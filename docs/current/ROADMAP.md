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
commit ae396d3aa4a88878ec0e5284af63b21773e6a868
  ↓
exact-head CI 33147280950 FAILED
  ↓
GateAUDIT-0C R3 CI failure remediation REVIEW_ACCEPTED / READY_TO_COMMIT
  ↓
NQ-GATEAUDIT-0C-R3-COMMIT
```

## 下一允许动作

- 唯一下一动作：`NQ-GATEAUDIT-0C-R3-COMMIT`。
- Remediation independent review decision=`PASS / REVIEW_ACCEPTED / READY_TO_COMMIT`，P0=0、P1=0，P1-01/P1-02=`CLOSED`，candidate modified by review=`NO`。
- 失败 commit=`ae396d3aa4a88878ec0e5284af63b21773e6a868` 与 exact-head CI=`33147280950 / completed / failure` 保持不变；新 remediation commit 与 CI 尚未创建。
- 不得再次启动 review、修改 remediation candidate、amend failed commit、弱化 CI、push、tag、deploy 或进入 Phase 1 Inventory。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；不得再次 pilot、PLACE、CANCEL、transfer、withdraw 或触达 credential/生产服务器/生产数据库。
- GateY frozen archive 与 `nq-gatey-freeze` 不可改写。
- 后续全仓 Inventory 必须由 machine policy 的 `audit.bootstrapCharter` 所声明的唯一 Charter 驱动，默认只读且禁止自动整改；字段或目标无效时 fail-closed。
