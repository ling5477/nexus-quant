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
GateW-1 ACCEPTED / CI GREEN
  ↓
GateW-2 ACCEPTED / CI GREEN
  ↓
GateW-3 VENUE-RULE SCHEMA REVIEW ACCEPTED / IMPLEMENTATION NOT STARTED
```

## 下一允许动作

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag 为 `nq-gatev-freeze`，历史证据入口为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；candidate/acceptance head 为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，CI run `29191014596` 为 `completed / success`。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，CI run `29199785253`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，CI run `29219687588`。
- GateW-2：`ACCEPTED / CI GREEN`；implementation/acceptance head `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，CI run `29230512781`。`REAL_SMOKE=NOT_RUN`，不表示远端 permission、LIVE 或交易授权。
- GateW-3 venue-rule facts：schema/security review 为 `PASS / VENUE_RULE_SCHEMA_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`；选择扩展 `instrument_catalog`，migration 决策为 `MIGRATION REQUIRED / PLAN ACCEPTED`。implementation 尚未开始。
- 当前治理动作：`NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION`。只实施冻结的 venue-rule schema/domain/repository/public-only ingestion 与测试；完成 migration/schema conformance review 和 exact-head CI 后，才重跑 dry-run order preview security/risk review attempt-02。

## 路线边界

- GateV tag 是历史 release 事实；不得重打、移动、覆盖或 force update `nq-gatev-freeze`。
- GateW-2 只接受两个冻结的 OKX private read-only typed operation；禁止 raw path、mutating/funds movement、自动 credential 访问、startup/background probe、migration 和把 mock/CI 写成真实 smoke。LIVE、交易授权与订单写侧继续关闭。
- GateW-3 venue-rule implementation 只允许 public metadata 的显式、最多 3 个 OKX Spot symbol 同步和 `instrument_catalog` migration；不由 preview 触发，不在 preview 请求线程联网，不使用 credential/private transport，不新增 scheduler/runner，不开启 LIVE。
- GateW-3 future preview 只允许本地 deterministic diagnostics；禁止 `TradingAdapter`、order command/write/lifecycle、credential/private transport、实时 network、任何 preview persistence，以及通过 `dryRun=true` 复用真实下单链。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 的状态由 `STATUS.md` 统一定义。
