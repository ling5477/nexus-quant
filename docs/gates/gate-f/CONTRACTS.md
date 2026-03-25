# GateF 输入 / 输出 / 接口口径

本文档冻结 GateF-DOC-1、GateF-1、GateF-2、GateF-3、GateF-4 当前已落地边界。

---

## 1. 当前 HTTP 入口

- `POST /__gated/backtest-runs`
- `POST /__gated/backtest-runs/{backtestRunId}/start`
- `POST /__gated/backtest-runs/{backtestRunId}/evaluate`
- `GET /__gated/backtest-runs/{backtestRunId}`
- `GET /__gated/backtest-runs`
- `GET /__gated/backtest-runs/{backtestRunId}/orders`
- `GET /__gated/backtest-runs/{backtestRunId}/trades`
- `GET /__gated/backtest-runs/{backtestRunId}/positions`
- `GET /__gated/backtest-runs/{backtestRunId}/pnl`
- `GET /__gated/backtest-runs/{backtestRunId}/evaluation`

---

## 2. 响应分层

- run detail / run list
  - 暴露 run execution summary
  - 暴露 evaluation summary
- sim_* 明细
  - 走独立 `/orders /trades /positions /pnl`
- evaluation detail
  - 走独立 `/evaluation`

---

## 3. 当前最小对象

- `BacktestRun`
- `SimOrder`
- `SimTrade`
- `SimPosition`
- `SimPnlSnapshot`
- `BacktestEvaluationReport`
- `EvaluationSummary`
