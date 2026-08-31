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
commit 95b859ee61a8e7f0a725e29877e7303ea4453b1a
  ↓
exact-head CI 33347091147 SUCCESS / 11 of 11 jobs
  ↓
F-001 ACCEPTED / CI_GREEN
  ↓
F-004 TRADE / LEDGER / POSITION CONVERGENCE IMPLEMENTED / PENDING_REVIEW
  ↓
Independent Review Attempt-01 FAIL / CHANGES_REQUIRED
  ↓
P1-01 / P1-02 / P1-03 REMEDIATED
  ↓
Independent Review Attempt-02 PASS / REVIEW_ACCEPTED
  ↓
commit 18efc06c380d2b411ba7d5f651e7e441247a1b96
  ↓
exact-head CI 33358364678 SUCCESS / 11 of 11 jobs
  ↓
F-004 ACCEPTED / CI_GREEN
  ↓
F-002 RESTART PROOF FOUNDATION IMPLEMENTED / PENDING_REVIEW
  ↓
Independent Review Attempt-01 FAIL / CHANGES_REQUIRED
  ↓
P1-01 / P1-02 REMEDIATED
  ↓
Independent Review Attempt-02 PASS / REVIEW_ACCEPTED
  ↓
NQ-GATEAUDIT-PHASE4-F002-COMMIT
```

## 下一允许动作

- 唯一下一动作：`NQ-GATEAUDIT-PHASE4-F002-COMMIT`（matcher count=`1`，type=`COMMIT`）。
- 历史链保持：`ae396d3aa4a88878ec0e5284af63b21773e6a868 → 33147280950 / failure`；`99c976306fb4c645251847c35ecf8c09f194b05d → 33164682651 / failure`；`40e1077e1fe735a3d250f094caaa24e437e8ea3f → 33306024232 / success`。
- Phase 0 immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`；P1-01、P1-02 与 doc-link Linux hidden-root finding 均为 `CLOSED_BY_LINUX_CI`。
- Phase 1～3 是 audit/analysis/disposition facts；Phase 3 findings=`P0 0 / P1 4 / P2 8 / P3 1`。
- F-001 immutable pair=`95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147`；F-004 immutable pair=`18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678`，均 `ACCEPTED / CI_GREEN`。F-002 Attempt-02 已接受，P1-01/P1-02=`CLOSED`，当前 `REVIEW_ACCEPTED / READY_TO_COMMIT`；F-003 保持 open。

## Persistent boundary

- `LIVE=DISABLED`、kill switch=`ENGAGED`；不得再次 pilot、PLACE、CANCEL、transfer、withdraw 或触达 credential/生产服务器/生产数据库。
- GateY frozen archive 与 `nq-gatey-freeze` 不可改写。
- 本次 commit/exact-head CI 只接受 F-002 Phase4 restart foundation，不扩展 accepted-timeout、cancel/fill race、kill in-flight、multi-instance、部署或 Phase6 qualification。
