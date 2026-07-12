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
GateV-4 ACCEPTED / CI GREEN
```

## 下一允许动作

- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33`，acceptance head `b209c416e0daf402216140b62785726f5fd116b6`，CI run `29155396719` 为 `completed / success`；scheduler 默认关闭。
- GateV-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit / acceptance head `d7da91a662be1f0fc0bbf64df70ea57318773697`，`NQ CI Baseline` run `29180664320` 为 `completed / success`。
- GateV-FREEZE 尚未初始化；Python manifest preview 继续为 No-file residual。
- 唯一下一任务：`NQ-GATEV-4-POST-CI-ACTIVE-AUTHORITY-SYNC`。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- GateV-4 只复用 GateV-2 已接受的 review API，在既有 `/strategies/validation` route 落地；未新增 backend endpoint、migration、scheduler、状态机或交易能力。batch acceptance 不表示 GateV accepted、frozen、tagged 或 trading authorization。
- GateV-3 因误提交而先于专项 review 进入 CI，现已由独立 acceptance head 与 CI 证据接受；不得把 scheduler acceptance 写成生产启用或 trading authorization。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
