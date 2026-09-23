# GateAUDIT Phase7 final baseline lineage

本文件是 Phase7-D 为 strict archive 建立的 controlled reconstructed baseline。canonical planning artifact 仍为 [GateAUDIT Phase7 Final Baseline Plan](../../audit/evidence/GATEAUDIT_PHASE7_FINAL_BASELINE_PLAN.md)。本文件不是 byte-for-byte copy，不声明 `HASH_PRESERVING_COPY`。

## Source lineage

```text
source_path=docs/audit/evidence/GATEAUDIT_PHASE7_FINAL_BASELINE_PLAN.md
source_last_commit=8868edb248b614e360377317c9c17e8f1d7d8404
source_git_blob=5f3009bdf2d8acc9d9bfe23185fbfc5f779dad62
source_file_sha256=7a6eeb286355f2dac5b95874ec5178f3c67b856de40205946e52d26b0f4d7b02
archive_strategy=CONTROLLED_RECONSTRUCTED_BASELINE
```

原计划中的 `Phase7 NOT_STARTED`、planning-time HEAD、next action Phase7-A 等内容均是 `HISTORICAL_SNAPSHOT`。当前 lifecycle 只能由 current STATUS、archive closeout 以及实际 Git/tag/CI 事实解释，不能从规划时陈述倒推。

## Phase7 sequence

1. Phase7-A：固定 21 行 final acceptance matrix、17 行 residual、owner 与 release-boundary input；已接受。
2. Taxonomy normalization：将 B–F 规范为唯一可分类 action；已接受。
3. Phase7-B：只读 historical projection baseline verification；已接受，repair obligation 关闭且不需要 repair。
4. Phase7-C：独立 governance/release-control readiness review；已接受并只授权 Phase7-D。
5. Phase7-D：创建 strict canonical archive、pre-tag closeout、exact-head CI 与 authority acceptance；本 archive 所属阶段。
6. Phase7-E：候选进入 `dev` 后重新绑定实际 freeze candidate、tree、exact-head CI、annotated/remote tag；尚未执行。
7. Phase7-F：tag 后 authority synchronization；尚未执行。

## Ownership and state semantics

- `STATUS.md` machine block 是 current stage、安全、accepted/work batch 与 next action 的唯一 machine authority。
- 本 archive 的 evidence matrix 是 final accepted evidence locator，不复制 raw evidence，不覆盖 canonical accepted documents。
- `COMPLETE / PRETAG / TAG_PENDING` 仅表示 capability/governance closeout；不等于 `FROZEN`、`TAGGED` 或 `RELEASED`。
- `FROZEN` 必须由 `dev` 上实际 freeze commit、annotated tag object、remote tag、peeled target 和同一 commit 的 exact-head CI 共同证明。
- 若 promotion/merge 生成新 commit，新 commit 才是 freeze candidate；pre-merge audit SHA 不能冒充 release identity。

## Archive contract

GateAUDIT 使用 default strict policy：8 mandatory roles 加 5 conditional roles，合计 13。各 role 为 substantive summary，canonical evidence reference-in-place；不创建 raw corpus copy，也不修改 manifest/checker/governance/release contract。tag 后 archive 才能作为 immutable frozen volume，当前仍为 pre-tag candidate。

## Safety and stop conditions

Phase7-D 不运行 Full Maven、frontend E2E、L5/L6、historical oracle、restore 或真实 provider，除非 archive/contract delta 产生新风险。出现 authority conflict、reopened mandatory closure、新 P0/P1、owner ambiguity、strict checker 必须修改、unexpected tag、secret/raw data 或 user-owned path 混入时立即停止。
