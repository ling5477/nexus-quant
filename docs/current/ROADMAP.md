# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateU FROZEN / ACCEPTED / TAGGED
  ↓
GateV-0 PLAN / NOT IMPLEMENTED
  ↓
GateV planning commit CI success
  ↓
GateV-1 ACCEPTED / CI GREEN
  ↓
GateV-2 ACCEPTED / CI GREEN
  ↓
GateV-3A ACCEPTED / CI GREEN
  ↓
GateV-3 ACCEPTED / CI GREEN
  ↓
GateV-4 NOT STARTED
```

## 下一允许动作

- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33`，acceptance head `b209c416e0daf402216140b62785726f5fd116b6`，CI run `29155396719` 为 `completed / success`；scheduler 默认关闭。
- GateV-4：`NOT STARTED`（未开始），是唯一下一 batch；正式任务 ID 为 `NQ-GATEV-4-REVIEW-WORKBENCH-IMPLEMENTATION`，仅允许复用既有 GateV-2 API 实现 Review Workbench，不表示已经开始实现。
- 唯一下一任务：`NQ-GATEV-4-REVIEW-WORKBENCH-IMPLEMENTATION`。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- GateV-4 已初始化为 `NOT STARTED` work batch；只有本 authority sync 由用户提交、push 并取得 exact-HEAD green CI 后，才允许启动其正式 implementation task。
- GateV-3 因误提交而先于专项 review 进入 CI，现已由独立 acceptance head 与 CI 证据接受；不得把 scheduler acceptance 写成生产启用或 trading authorization。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
