# GateY-6E first real order prerequisite implementation — attempt-01

## 任务分类与结论

- Task classification：`CODE_CHANGE / REAL_PROVIDER_PREREQUISITE / TRUSTED_OBSERVATION / PRIVATE_TRADING_BOUNDARY`；NQ-only、高风险实现任务。
- Final decision：`BLOCKED / MINIMUM_ORDER_VALUE_SOURCE_UNRESOLVED / NO_FABRICATED_PREREQUISITE_FACT / NO_OKX_CALL / NO_EXCHANGE_MUTATION / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED`（阻断 / minimum order value 来源未解决 / 不伪造 prerequisite fact / 未调用 OKX API / 无交易所 mutation / 第一笔真实订单未授权 / LIVE 关闭）。
- Hard gate 在任何 Java 实现前失败；本轮没有实现 trusted observation authority 或 real provider transport，也没有修改 V40/V41。

## Baseline

```text
branch=dev
worktree=clean
staged=0
HEAD=origin/dev=e7230161d30a2332473a84dd5eb58ccf1dd0ac9a
commit_subject=docs(gatey): accept GateY-6D and initialize GateY-6E
CI=31946090565 / completed / success
CI_headSha=e7230161d30a2332473a84dd5eb58ccf1dd0ac9a

accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6E
work_batch_status=NOT_STARTED
next_action=NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION
live=DISABLED
kill_switch=ENGAGED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
```

GitHub Actions run `31946090565` 已只读核验为 `NQ CI Baseline / completed / success`，head SHA 精确匹配。

## Bounded OKX official contract verification

核验日期：2026-08-16。只读取 [OKX API v5 官方文档](https://www.okx.com/docs-v5/en/)；未调用任何 `/api/v5/**` API endpoint，未读取 credential。

| Candidate endpoint | 官方可回读字段 | 可满足的 V40 fact | 缺口 |
| --- | --- | --- | --- |
| [`GET /api/v5/account/instruments`](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-instruments) | SPOT 相关字段包括 `instType`、`instId`、`baseCcy`、`quoteCcy`、`tickSz`、`lotSz`、`minSz`、`state`、`tradeQuoteCcyList` 等；完整 response table 共 54 个字段 | `state`→trading status；`tickSz`→tick size；`lotSz`→lot size；`minSz`→minimum order size | 没有 minimum order value/notional response field |
| [`GET /api/v5/account/trade-fee`](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-fee-rates) | `level`、`feeGroup`、`maker`、`taker`、`makerUSDC`、`takerUSDC`、`ts` 等 | fee tier/group/rates 与 observation time | 不提供 instrument minimum order value |
| [`GET /api/v5/account/balance`](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-balance) | `details[].ccy`、`details[].availBal`、`cashBal`、`uTime` 等 | USDT available balance 与 balance observation time | 不提供 instrument minimum order value |
| [`GET /api/v5/public/time`](https://www.okx.com/docs-v5/en/#public-data-rest-api-get-system-time) | `ts` | server time / clock observation | 不提供 instrument minimum order value |

官方对 SPOT/MARGIN `minSz` 的定义是“quantity in base currency”的 minimum order size，不是 quote-currency minimum notional。不得将 `minSz × ticker` 改写为 venue-authored minimum order value。

官方页面全文仅有两个 `minNotional` 命中，均位于 RFQ 错误码模板：`{nonSpotMinNotional}` 与 `{spotMinNotional}`。它们不是上述四个 candidate endpoint 的 response field，没有 per-instrument typed value、currency、observation identity 或回读 contract，不能作为 V40 trusted fact 来源。

## V40 expected semantic 与 exact mismatch

V40 / current domain 要求：

- `minimumOrderValue` 是独立 `BigDecimal`，必须 `> 0`；
- `minimumOrderValueCurrency` 必须精确为 `USDT`；
- 值进入 instrument observation canonical payload/digest，并作为 session-bound append-only fact 持久化；
- 该值必须来自可回读、可审计、venue-authored trusted observation，而不是不可逆 hash、经验常量或计算替代。

Exact mismatch：

```text
official account/instruments = minSz(base quantity), no minimum notional field
V40 instrument observation   = minimumOrderValue(quote value) + currency=USDT
typed exact mapping          = IMPOSSIBLE_WITH_CURRENT_CANDIDATE_ENDPOINTS
```

禁止替代方案均未采用：固定 `5 USDT`=`0`；历史公告值=`0`；`minSz × ticker`=`0`；经验推导=`0`；test fixture 充当 production fact=`0`。

## Conditional implementation disposition

由于 hard gate 未成立，以下工作均未开始：

- `OkxPilotPrerequisiteObservationAuthority` 或等价 production authority：`NOT_IMPLEMENTED`。
- Real typed `OkxSpotProviderTransport` capability：`NOT_IMPLEMENTED`。
- Credential/JIT callback、signer、bounded GET transport 接线：`NOT_TOUCHED`。
- PLACE_LIMIT / QUERY_ORDER / CANCEL_ORDER / READ_ORDER / READ_FILLS production transport：`NOT_IMPLEMENTED`。
- UNKNOWN→QUERY_BY_CLIENT_ORDER_ID、cancel query-first production实现：`NOT_IMPLEMENTED_IN_THIS_TASK`；GateY-6B contract未修改。
- Runtime wiring：default NoReal/unbound 保持不变，real mutation runtime caller=`0`。

## Secret、network 与 mutation boundary

```text
real credential read=0
OKX API call=0
real PilotScope materialization=0
real OperatorApproval=0
ExecutionIntent=0
ExecutionReceipt=0
PLACE=0
CANCEL=0
TRANSFER=0
WITHDRAW=0
worker start=0
LIVE enable=0
kill disengage=0
```

访问 `www.okx.com/docs-v5/en/` 仅为公开官方文档读取，不是 OKX trading/account API 调用。未读取 `.env`，未接触任何 secret、raw provider response 或生产数据。

## Architecture 与 findings

- 产品代码、module dependency、V40/V41、schema、migration、runtime wiring、provider contract、credential path均未修改，现有 architecture boundary保持原状。
- P0=`0`。
- P1=`1`：`MINIMUM_ORDER_VALUE_SOURCE_UNRESOLVED`；若继续实现会迫使系统伪造或错误推导交易 prerequisite fact，因此阻断。
- P2=`0`。
- P3=`0`。

## Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| Git baseline | PASS（通过） | `dev` clean；staged=`0`；`HEAD == origin/dev == e7230161...` |
| `gh run view 31946090565 --json ...` | PASS（通过） | exact-head `completed / success`；只读 |
| OKX official docs fetch | PASS（通过） | HTTP 200；四个 endpoint/path与response tables已解析；无 `/api/v5/**` 调用 |
| response-field extraction | PASS（通过） | instruments 54 fields、fee 24、balance 69、time 1；首个 helper 名触发 PowerShell parser error，改用无歧义 helper 后成功，未产生写操作 |
| V40/domain semantic comparison | BLOCKED（阻断） | mandatory positive USDT minimum order value 无 official typed source |
| Maven/focused tests | NOT RUN（未运行） | hard gate 要求在其余代码前立即停止；产品代码 diff=`0`，不伪造 implementation verification |
| authority/docs/diff checks | PASS（通过） | 最终验证见本任务 TESTING/WORKLOG 记录 |

## Authority after 与 exact changed files

Authority 保持不变：

```text
accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6E
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION
live=DISABLED
kill_switch=ENGAGED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
```

Exact changed files：

```text
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
docs/current/evidence/gate-y/NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION.attempt-01.md
```

## 回滚、提交与下一步

- 本轮不 stage、commit、push、deploy；STATUS/ROADMAP/README 与所有产品代码保持不变。
- 回滚：提交前只反向应用上述 4 个 evidence/ledger 文件 diff；禁止整仓 reset/restore。
- 建议 commit：`docs(gatey): record GateY-6E minimum order value blocker`。成功路径的 `feat(gatey): implement first real order prerequisites` 当前不适用。
- 下一具体动作：由用户/产品方提供当前官方、venue-authored、per-instrument typed minimum order value source，或另行显式授权独立 V40/V41 contract/schema remediation；在此之前重跑同一 implementation仍必须 fail closed。
