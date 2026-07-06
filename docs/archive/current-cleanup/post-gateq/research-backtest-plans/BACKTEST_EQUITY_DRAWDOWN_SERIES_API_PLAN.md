# Backtest Equity / Drawdown Series API Plan

> 任务类型：BACKEND_API_PLANNING + DATA_CONTRACT_REVIEW + TEST_BASELINE_REVIEW + DOCUMENTATION
> 状态：**planning only**。本文不实现代码、不新增 migration、不新增 API。
> 阶段口径：GateJ completed → GateK-PLAN；AI not started；DH not integrated；LIVE disabled；real exchange adapter not implemented。
> 日期：2026-06-15

## 0. 结论(先说要点)

**后端不缺端点。** 回测权益/PnL 时间序列**已存在并已暴露**:

- 表 `sim_pnl_snapshots`(`V8__gate_f3_simulated_execution_facts.sql`),按 `backtest_run_id` 存储、按 `snapshot_time` 排序,含 `equity / cash_balance / position_market_value / realized_pnl / unrealized_pnl / total_fee / total_slippage / net_pnl`,并有索引 `idx_sim_pnl_snapshots_run_snapshot_time (backtest_run_id, snapshot_time)`。
- 端点 **`GET /api/backtest-runs/{runId}/pnl-snapshots`**(`BacktestRunController`)→ `List<SimPnlSnapshotResponse>`,即权益/PnL 序列。
- 评估侧(`DrawdownCalculator` / `EvaluationMetricCalculator`,通过 `SimPnlSnapshotQueryRepository.listByBacktestRunId`)**已基于该 equity 序列计算 maxDrawdown 等指标**。

因此 **B1 回测详情页的权益/回撤曲线显示 "unavailable" 是前端未接线既有端点,不是后端缺口。** 本轮(planning)的产出是:确认无需新增后端 API/表/migration,并给出一个**前端跟进切片**(B1.1)消费既有端点 + 客户端派生回撤。回撤序列后端不返回,但可由 equity 序列零成本派生(与后端 `DrawdownCalculator` 同口径);如未来多消费方需要,再考虑后端派生端点(可选,非必需)。

## 1. 当前数据现状(Q1:是否已有可稳定抽取的时间序列)

**有,且稳定。** 真实序列源是 `sim_pnl_snapshots`,不是 `reportJson` / `metricsJson`:

| 维度 | 事实 |
|---|---|
| 表 | `sim_pnl_snapshots`(V8 gate_f3),FK `fk_sim_pnl_snapshots_run` 关联 run,索引 `(backtest_run_id, snapshot_time)` |
| 键 | `backtest_run_id`(run 级,不是 config 级) |
| 排序 | `ORDER BY snapshot_time ASC, sim_pnl_snapshot_id ASC`(Repository 已固定) |
| 字段 | `snapshot_time, cash_balance, position_market_value, realized_pnl, unrealized_pnl, total_fee, total_slippage, equity, net_pnl, created_at` |
| 写入 | `BacktestExecutionService` 回测执行时落库;`SimPnlSnapshotRepository.insert` |
| 读取(已用) | `BacktestFactQueryService.listPnlSnapshots` + eval 的 `SimPnlSnapshotQueryRepository.listByBacktestRunId` |

`reportJson` / `metricsJson`:口径不透明、不保证含序列,B1 已对其做防御式解析且解析不到 → 不应作为序列来源。**序列来源固定为 `sim_pnl_snapshots`。**

## 2. 是否新增 API(Q2)

**不新增。** 既有端点已满足:

```
GET /api/backtest-runs/{runId}/pnl-snapshots   →  List<SimPnlSnapshotResponse>
```

- 路径是 **run 级 `{runId}`**(与既有 `/api/backtest-runs/{runId}`、`/evaluation`、`/sim-orders`、`/sim-trades`、`/sim-positions`、`/publish` 一致),**不是** config 级。
- 不建议新增 `/equity-curve` 别名端点(会与 `pnl-snapshots` 重复);前端直接消费 `pnl-snapshots`。
- 该端点目前**未在 `API.md` 记录**(连同 sim-orders/trades/positions),属文档遗漏,已在本轮补记事实(见 `API.md`)。

## 3. 是否同时返回 drawdown series(Q3)

**后端当前不返回 drawdown 序列,也不必新增。** 回撤可由 equity 序列零成本派生,与后端 `DrawdownCalculator` 同口径:

```
peak_i      = max(equity_0..i)
drawdown_i  = peak_i - equity_i            (绝对回撤,≥0;画图可取负)
drawdownRate_i = peak_i<=0 ? 0 : (peak_i - equity_i)/peak_i
```

- 推荐 **B1.1 前端从 equity 序列派生 drawdown**(单次 O(n) 扫描),无后端改动。
- 可选(未来,非必需):若 drawdown 序列需多端复用或大数据量服务端下采样,再加后端派生端点/字段。

## 4. x 轴字段(Q4)

**使用 `snapshotTime`(eventTime,`Instant`)。**

- 不用 `barTime`:快照是执行/PnL 事件,不等价于 K 线 bar;`sim_pnl_snapshots` 无 barTime。
- 不用 `sequenceIndex` 作为主 x:无该字段;`sim_pnl_snapshot_id` 仅作为 `snapshot_time` 相等时的稳定 tiebreak(Repository 已 `ORDER BY snapshot_time, sim_pnl_snapshot_id`)。
- 前端 x 轴用 `snapshotTime`;若多点时间戳相同,保持后端返回顺序即可。

## 5. value 字段(Q5)

`SimPnlSnapshotResponse` 已返回全部所需字段:

| 字段 | 用途 |
|---|---|
| `equity` | 权益曲线主序列 |
| (派生)drawdown / drawdownRate | 回撤曲线(由 equity 派生) |
| `netPnl` | 可选副序列(累计净盈亏) |
| `cashBalance` / `positionMarketValue` | 可选:现金 vs 持仓市值分解 |
| `realizedPnl` / `unrealizedPnl` | 可选:已实现/未实现分解 |
| `totalFee` / `totalSlippage` | 可选:成本曲线 |

**B1.1 最小切口:`equity` + 派生 `drawdown`。** 其余字段作为后续增强,不在最小切口内强求。

## 6. 数据来源 / 是否新增表(Q6)

- **读现有表 `sim_pnl_snapshots`,不新增表、不新增 migration、不改历史 migration。**
- **不**重构 `reportJson` 结构去塞序列(序列已经有专表,重复存储是反模式)。
- 索引已满足 run 级范围查询(`(backtest_run_id, snapshot_time)`)。

## 7. 对现有 API 的影响(Q7)

**无影响。** `pnl-snapshots` 端点早已存在且独立;backtest config / run / evaluation 既有 API 不变,DTO 不变,DB 不变。本计划只触发**前端**跟进,不触发任何后端改动。

## 8. 分页 / 压缩 / 下采样(Q8)

- 现状:`pnl-snapshots` 返回**全量 list**,无分页。
- 行数取决于回测引擎快照节奏(逐笔/逐 bar/逐事件),**需在 B1.1 用真实数据测一次典型与极端 run 的行数**。
- 推荐策略:
  - B1.1:前端全量取 + **渲染端下采样**(点数 > ~2000 时用 LTTB 或等距抽样),不改后端。
  - 仅当真实行数证明过大(影响传输/内存)时,再考虑**后端可选 query 参数**(`from/to/maxPoints/downsample`),additive、向后兼容;当前不做。
- 不引入压缩协议;JSON + gzip(既有网关)足够。

## 9. 前端 BacktestDetailPage 对接(Q9)

**这是后续实现切片(B1.1),不在本 planning 轮实现。** 计划如下:

1. `frontend/src/api/backtests.ts`:新增 `pnlSnapshots(runId): Promise<SimPnlSnapshotItem[]>` → `GET /backtest-runs/{runId}/pnl-snapshots`;`frontend/src/types/backtests.ts` 加 `SimPnlSnapshotItem`(snapshotTime/equity/cashBalance/positionMarketValue/realized/unrealizedPnl/netPnl/totalFee/totalSlippage)。
2. `BacktestDetailPage`:`runId` 取自当前所选 evaluation 的 `backtestRunId`(B1 已拉 evaluations 并选定一条 → 已含 runId),保证曲线与指标同一 run。
3. 用 **B0.3 `useLiveQuery`**(`pollingIntervalMs=0`,仅手动刷新)拉 `pnl-snapshots`。
4. 映射:equity 点 `{t: snapshotTime, v: equity}`;派生 drawdown 点 `{t, v: equity - runningPeak}`(≤0)。
5. 喂给**既有 `BacktestCurveChart`**(组件零改动);移除/降级 B1 里对 report/metrics JSON 的防御式解析(改为 fallback 或删除)。
6. 空序列(run 无快照)→ 既有 unavailable 占位。
7. 不接 socket、不接 LIVE、不改后端、不全局替换 AppProviders。

## 10. 页面级 E2E fixtures(Q10)

- 后端已存在 `BacktestRunControllerTest`、`ResearchBacktestQueryControllerLocalTest` 覆盖 `pnl-snapshots`,可作为契约基线参考。
- B1.1 页面级 e2e fixture(需后端 local profile,本前端环境后端 `:18888` 未起):
  - seed 一个 backtest config → 一次 run,且 `sim_pnl_snapshots` 有若干行(递增/带回撤的 equity),并生成一条 `SUCCEEDED` evaluation 指向该 run,dataset 已绑定。
  - 断言:`/backtests/:configId` 解析出 run → 拉 pnl-snapshots → 权益曲线渲染 canvas、回撤曲线渲染、指标卡有值、数据集/参数快照可见。
  - 反例 fixture:run 无快照 → 曲线显式 unavailable(不编造)。
- 在无后端环境下,曲线**组件**行为已由 `design-system-backtest-chart-smoke.spec.ts` 覆盖(样本渲染 + unavailable)。

## 风险

- 快照节奏/行数未知 → 大 run 需下采样(见 §8);B1.1 先用真实数据量化。
- runId 选取:config 可能有多 run;B1.1 必须让曲线与指标取**同一 run**(以所选 evaluation.backtestRunId 为准)。无 evaluation 但有 run 的场景,可改用 run 列表选 run(B1.1 评估)。
- 时间戳重复:依赖后端 `(snapshot_time, sim_pnl_snapshot_id)` 稳定排序。
- 单位:`equity` 等为账户币种数值(无单位标注),与 B1 指标口径一致。

## 下一步(planning 之后)

- 开实现切片 **NQ-FRONTEND-BACKTEST-EQUITY-CURVE-WIRING-B1.1**:仅前端消费既有 `pnl-snapshots` 端点 + 派生回撤 + 页面接线 + e2e fixture;**无后端改动**。
- 本轮不实现、不合并实现;仅文档 planning。
