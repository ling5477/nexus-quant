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
GateV-3 IMPLEMENTED / PENDING REVIEW
```

## 下一允许动作

- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3：`IMPLEMENTED / PENDING REVIEW`（已实现 / 待复核）；仅存在于当前工作区，尚未 review、commit 或取得自身 CI，默认配置仍关闭。
- 唯一下一任务：`NQ-GATEV-3-CONTROLLED-READONLY-SCHEDULER-REVIEW`。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- 本轮只升级 authority schema、checker 与 current 状态文档，不修改 GateV-3 scheduler 业务实现。
- GateV-3 review 只能在本治理变更由用户 commit/push 且取得 exact-HEAD CI success 后执行；review 通过前不得暂存或提交 GateV-3 代码，不得把工作区实现写成 accepted 或生产启用。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
