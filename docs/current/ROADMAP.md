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
commit 99c976306fb4c645251847c35ecf8c09f194b05d
  ↓
exact-head CI 33164682651 FAILED
  ↓
previous P1-01/P1-02 CLOSED_BY_LINUX_CI
  ↓
doc-link hidden-root remediation REVIEW_ACCEPTED / READY_TO_COMMIT
  ↓
commit 40e1077e1fe735a3d250f094caaa24e437e8ea3f
  ↓
exact-head CI 33306024232 SUCCESS / 11 of 11 jobs
  ↓
GateAUDIT Phase 0 ACCEPTED / CI_GREEN / COMPLETE
  ↓
GateAUDIT Phase 1 FULL FIRST-PARTY REPOSITORY INVENTORY
```

## 下一允许动作

- 唯一下一动作：`NQ-GATEAUDIT-PHASE1-REPOSITORY-AUDIT-INVENTORY`（matcher count=`1`，type=`AUDIT`）。
- 历史链保持：`ae396d3aa4a88878ec0e5284af63b21773e6a868 → 33147280950 / failure`；`99c976306fb4c645251847c35ecf8c09f194b05d → 33164682651 / failure`；`40e1077e1fe735a3d250f094caaa24e437e8ea3f → 33306024232 / success`。
- Phase 0 immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`；P1-01、P1-02 与 doc-link Linux hidden-root finding 均为 `CLOSED_BY_LINUX_CI`。
- 本 closeout 不启动 Phase 1，不得再次启动 Phase 0 review 或 exact-head CI，也不得把 docs-only closeout commit 写入 `work_batch_commit`。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；不得再次 pilot、PLACE、CANCEL、transfer、withdraw 或触达 credential/生产服务器/生产数据库。
- GateY frozen archive 与 `nq-gatey-freeze` 不可改写。
- Phase 1 全仓 Inventory 必须由 machine policy 的 `audit.bootstrapCharter` 所声明的唯一 Charter 驱动，默认只读且禁止自动整改；字段或目标无效时 fail-closed。
