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
GateV-1 durable review fact model implementation
```

## 下一允许动作

1. 用户复核并提交 [GATEV_PLAN.md](GATEV_PLAN.md) 及本轮最小 current authority 同步。
2. 等待该提交对应 `NQ CI Baseline / completed / success`。
3. CI 成功后，直接进入 `NQ-GATEV-1-DURABLE-REVIEW-FACT-MODEL-MIGRATION-AND-REPOSITORY-IMPLEMENTATION`。
4. 不再增加 GateV plan review、plan freeze 或 planning addendum；当前 GateV 仍为 `PLAN / NOT IMPLEMENTED`。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- 本轮不新增 read model、API、migration、frontend page、scheduler、runner、Python runtime 或交易能力；下一轮只按 GateV-1 allowlist 实现 durable review fact model。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
