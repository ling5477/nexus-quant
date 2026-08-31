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
GateAUDIT Phase 1 INVENTORY COMPLETE
  ↓
GateAUDIT Phase 2 AS-IS ANALYSIS COMPLETE
  ↓
GateAUDIT Phase 3 FINDINGS / DISPOSITIONS COMPLETE
  ↓
GateAUDIT Phase 4A F-001 L3 PROOF FOUNDATION IMPLEMENTED
  ↓
Independent Review Attempt-01 FAIL / CHANGES_REQUIRED
  ↓
Independent Review Attempt-02 PASS / REVIEW_ACCEPTED
  ↓
NQ-GATEAUDIT-PHASE4-L3-COMMIT
```

## 下一允许动作

- 唯一下一动作：`NQ-GATEAUDIT-PHASE4-L3-COMMIT`（matcher count=`1`，type=`COMMIT`）。
- 历史链保持：`ae396d3aa4a88878ec0e5284af63b21773e6a868 → 33147280950 / failure`；`99c976306fb4c645251847c35ecf8c09f194b05d → 33164682651 / failure`；`40e1077e1fe735a3d250f094caaa24e437e8ea3f → 33306024232 / success`。
- Phase 0 immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`；P1-01、P1-02 与 doc-link Linux hidden-root finding 均为 `CLOSED_BY_LINUX_CI`。
- Phase 1～3 是 audit/analysis/disposition facts；Phase 3 findings=`P0 0 / P1 4 / P2 8 / P3 1`。
- Phase 4A F-001 L3 proof 已由 Independent Review Attempt-02 接受，Attempt-01 的 blocking P1 已关闭；当前状态为 `INDEPENDENT_REVIEW_ACCEPTED / CI_PENDING_AFTER_COMMIT`。仅在 final canonical regression 通过后提交并取得新 exact-head CI；F-001 尚未关闭，F-002/F-003/F-004 未实现。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；不得再次 pilot、PLACE、CANCEL、transfer、withdraw 或触达 credential/生产服务器/生产数据库。
- GateY frozen archive 与 `nq-gatey-freeze` 不可改写。
- 本次 commit/CI 只验证已接受的 F-001 candidate，不得扩展到模块重构、F-004 repair、CI workflow 修改、部署或供应链。
