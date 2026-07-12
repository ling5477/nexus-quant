# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateU FROZEN / ACCEPTED / TAGGED
  ↓
GateV FROZEN / ACCEPTED / TAGGED
  ↓
GateW IN PROGRESS / NOT FROZEN
  ↓
GateW-PLAN NOT STARTED
```

## 下一允许动作

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag 为 `nq-gatev-freeze`，历史证据入口为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；candidate/acceptance head 为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，CI run `29191014596` 为 `completed / success`。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW-PLAN：`NOT STARTED`（未开始）；`NONE / NOT_RUN`。
- 唯一下一任务：`NQ-GATEW-PLAN-IMPLEMENTATION`。该名称使用现有 checker 可识别的 `IMPLEMENTATION` 分类 token，但 scope 仅限 GateW planning；不得开始 GateW 业务实现。

## 路线边界

- GateV tag 是历史 release 事实；不得重打、移动、覆盖或 force update `nq-gatev-freeze`。
- GateW planning 前不新增业务代码、migration、scheduler、runner、真实 provider、RealClient 或交易能力。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 的状态由 `STATUS.md` 统一定义。
