# Roadmap

本文件只定义下一允许动作和路线。当前 Gate、release tag 与安全状态必须读取 [STATUS.md](STATUS.md) 的 `nq-current-authority` 机器可读区块。

## 当前路线

```text
GateV FROZEN / ACCEPTED / TAGGED
  ↓
GateW FROZEN / ACCEPTED / TAGGED
  ↓
GateW-ATTEMPT-13-168H-ACCEPTANCE ACCEPTED / CI GREEN
  ↓
GateW strict archive + freeze exact-head CI + annotated tag VERIFIED
  ↓
GateX IN PROGRESS / NOT FROZEN
  ↓
GateX-PLAN BASELINE ESTABLISHED / READY TO COMMIT
  ↓
GateX-0A ACCEPTED / CI GREEN
  ↓
GateX-0B ACCEPTED / CI GREEN
  ↓
GateX-0C ACCEPTED / CI GREEN
  ↓
GateX-0D ACCEPTED / CI GREEN
  ↓
GateX-0E AUDITED / IMPLEMENTATION NOT REQUIRED
  ↓
GateX-1 NOT STARTED
```

## 下一允许动作

- GateW：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；durable archive 为 [../gates/gate-w/README.md](../gates/gate-w/README.md)，release tag 为 `nq-gatew-freeze`。
- GateW freeze commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`；exact-head CI run=`31299729114 / completed / success / 10 jobs / bad=0`。
- GateW Attempt-13=`COMPLETED / ACCEPTED`（已完成 / 已接受）；production deployment=`STOPPED`；production soak=`COMPLETED`。656 条样本与 hash chain 已接受并 sealed，worker 已停止。
- GateW acceptance batch：`GateW-ATTEMPT-13-168H-ACCEPTANCE / ACCEPTED / CI GREEN`；acceptance head=`20cf7970dfb414868da3e42dddaefc5965246570`，CI run=`31295184056`。
- GateX：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateX-0A/0B/0C/0D 已接受，GateX-0E 条件项已审计且无需实施；GateX-1 尚未开始，不表示 GateX production capability 已完成。
- GateX-PLAN：`BASELINE ESTABLISHED / READY TO COMMIT`（基线已建立 / 可进入提交前复核）；实施基线见 [GATEX_PLAN.md](GATEX_PLAN.md)。
- GateX-0A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；Strategy↔Trading 与 audit port ownership P1 已关闭，merge acceptance head 的 exact-head CI 已成功。
- GateX-0B：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head=`108a14d14906d6fa354349c66d35a2ae6967cebf`，exact-head CI run=`31321821962 / completed / success`。
- GateX-0C：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head=`46392213495652f6a09005148cc160fd2882adb9`，exact-head CI run=`31325824949 / completed / success`。
- GateX-0D：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head=`885ed23375d0d8a58d9d10d2c4768f390322af93`，exact-head CI run=`31344357225 / completed / success / 10 jobs`。
- GateX-0E：`AUDITED / IMPLEMENTATION NOT REQUIRED`（已审计 / 无需实施）；未发现 GateX-1 Query/cache/config blocker，条件项不进入 machine lifecycle，未新增 skip 状态。
- GateX-1：`NOT STARTED`（未开始）；本 authority transition 不实现 Strategy Release service、artifact verifier、migration、persistence 或 admission。
- 当前唯一治理动作是 `NQ-GATEX-1-STRATEGY-RELEASE-ARTIFACT-PRODUCTIONIZATION-IMPLEMENTATION`。

## GateW 已冻结边界

- 168h OKX read-only soak 已完成并接受，但 `read-only soak ≠ 真实资金交易授权`。
- LIVE=`DISABLED`，kill switch=`ENGAGED`；order/cancel/transfer/withdraw/LIVE execution=`0/0/0/0/0`。
- P2 known limitation 保留：maximum gap=`1797s / sequence 1→2`。
- Attempt-09 的拒绝、Attempt-10/11/12 的失败终态与全部 remediation evidence 均已进入 GateW archive，不得删除、覆盖或复用。
- `nq-gatew-freeze` 不得删除、移动、覆盖或 force update；问题只能通过 forward remediation 或 superseding tag 处理。

## GateX implementation 边界

- 唯一实施顺序为 0A → 0B → 0C → 0D → 条件性 0E；GateX-0 总量控制在 3～5 个代码任务、3～7 工程日。
- GateX-0 只处理 P1 与 GateX 触达区域的低风险 P2；不得演化为 Maven 全量拆分或全仓架构重构。
- GateX-1～5 与 FREEZE 的前置、范围和验收以 [GATEX_PLAN.md](GATEX_PLAN.md) 为准；计划不得被解释为 capability 已实现。
- 不得把 GateW diagnostic/read-only/soak 证据推导为远端交易 permission、账户健康、余额充分、private trading 或 unattended execution readiness。
- 不得开启 LIVE、真实下单/撤单、转账/提现、AI trading、DH runtime、Integration runtime、RealClient 或 real provider。
- NQ-only 任务不得修改或声明 DH current authority；DH/Integration 状态继续只表达 NQ 侧 no-real 边界。
