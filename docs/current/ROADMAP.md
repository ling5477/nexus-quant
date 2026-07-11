# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateU FROZEN / ACCEPTED / TAGGED
  ↓
docs governance dynamic-authority/checker fix
  ↓
governance fix commit CI success
  ↓
GateV planning may start in a separately authorized task
```

## 下一允许动作

1. 用户复核并提交本次 governance fix。
2. 等待该提交对应 `NQ CI Baseline / completed / success`。
3. CI 成功后，GateV 可进入单独授权的 planning 任务。
4. GateV implementation 仍为 `NOT STARTED`（未开始），不得由本路线自动启动。

## 路线边界

- 本文件不重新定义 current Gate；若与 `STATUS.md` 冲突，以 `STATUS.md` 为准并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
- GateU tag 已完成，不再保留创建或推送 `nq-gateu-freeze` 的待办步骤。
- 不新增 read model、API、migration、frontend page、scheduler、runner、Python runtime 或交易能力。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 状态由 `STATUS.md` 统一定义。
