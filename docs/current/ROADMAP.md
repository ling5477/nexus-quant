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
GateW-3 COMMITTED / CI GREEN / CONTINUE REQUIRED
```

## 下一允许动作

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag 为 `nq-gatev-freeze`，历史证据入口为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；candidate/acceptance head 为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，CI run `29191014596` 为 `completed / success`。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，CI run `29199785253`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；acceptance head `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，CI run `29219687588`。
- GateW-2：`ACCEPTED / CI GREEN`；implementation/acceptance head `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，CI run `29230512781`。`REAL_SMOKE=NOT_RUN`，不表示远端 permission、LIVE 或交易授权。
- GateW-3 venue-rule facts：implementation commit 为 `8b54adc6952775dc1a939aad7b0ae849f20f42cf`，migration conformance review 已通过；CI blocker fix commit `fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28` 的 exact-head CI run `29260881801` 已 `completed / success`。LIMIT-only internal preview implementation commit `eff79d7c7ea1b034de4e77c7ec64974c247027f5` 的 exact-head run `29308652349` 为 `completed / failure`；acceptance head `abc5230c21ad37b3d01bc7df2cc825579bd3f7dc` 的 exact-head run `29319269424` 为 `completed / success`，当前为 `COMMITTED|CI_GREEN|CONTINUE_REQUIRED`，GateW-3 尚未整体 accepted。
- GateW-3 read-only reconciliation：implementation/acceptance head `71e1ded5a9896996717549d2a96068356dea7288`，exact-head CI run `29324600871 / completed / success`，10/10 jobs success；当前为 `COMMITTED|CI_GREEN|CONTINUE_REQUIRED`，GateW-3 尚未整体 accepted。
- 当前治理动作：`NQ-GATEW-3-RISK-PREFLIGHT-SECURITY-RISK-REVIEW-ATTEMPT-01`。只允许独立 security/risk review；不得执行真实 OKX HTTP、读取真实 credential、repair 或初始化 GateW-4/Freeze。

## 路线边界

- GateV tag 是历史 release 事实；不得重打、移动、覆盖或 force update `nq-gatev-freeze`。
- GateW-2 只接受两个冻结的 OKX private read-only typed operation；禁止 raw path、mutating/funds movement、自动 credential 访问、startup/background probe、migration 和把 mock/CI 写成真实 smoke。LIVE、交易授权与订单写侧继续关闭。
- GateW-3 venue-rule facts commit 仍只覆盖 public metadata 的显式、最多 3 个 OKX Spot symbol 同步和 `instrument_catalog` migration；本轮 preview 仅增加 bounded local read 与 pure diagnostic，不扩大同步范围。当前不得初始化 GateW-4 或 Freeze。
- GateW-3 preview 只允许本地 deterministic diagnostics；其 acceptance-head exact-head CI 已成功，但这不等于交易授权或 GateW-3 整体接受。禁止 `TradingAdapter`、order command/write/lifecycle、credential/private transport、实时 network、任何 preview persistence，以及通过 `dryRun=true` 复用真实下单链。
- GateW-3 reconciliation 只允许 OKX Spot、最多 3 symbols、每类每 symbol 1 page/100 records、24h window 的显式 typed `Read` snapshot；无 controller/scheduler/repair/persistence，默认不装配。即使全量 matched，也仅表示 `SNAPSHOT_MATCHED_AT_EVALUATION_TIME`，`executionReadiness=BLOCKED`。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime、real provider 与 private trading 的状态由 `STATUS.md` 统一定义。
