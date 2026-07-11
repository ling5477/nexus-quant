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
```

## 下一允许动作

- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation commit `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33` 已包含于 exact-HEAD CI run `29154489746`，默认配置仍关闭。
- 唯一下一任务：`NQ-GATEV-3-POST-CI-ACTIVE-AUTHORITY-SYNC`。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- 本轮只升级 authority schema、checker 与 current 状态文档，不修改 GateV-3 scheduler 业务实现。
- GateV-3 因误提交而先于专项 review 进入 CI；本次 review 已补齐独立接受证据，不要求回退。下一任务只同步 accepted/work authority，不新增实现，也不得把 scheduler acceptance 写成生产启用。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
