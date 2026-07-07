# GateR Frontend Evidence Index

## Frontend scope

- 门户链路仅覆盖 Shadow Run list / detail / replay 的只读展示。

## Pages / routes

- `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx`（`/strategies/shadow-runs`）
- `frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx`（`/strategies/shadow-runs/:shadowRunId`）

## API client and hooks

- `frontend/src/api/shadow-runs.ts`
- `frontend/src/hooks/useShadowRunQueries.ts`

## Types and boundary constraints

- `frontend/src/types/shadow-runs.ts`
- 无写侧按钮：列表与详情页不提供 start / stop / execute / rerun / approve / trade。

## E2E / smoke evidence

- `frontend/tests/e2e/shadow-run-detail-smoke.spec.ts`（detail/replay 的 no-side-effect smoke 覆盖）

## Frontend boundary closure

- 列表仅展示 run summary、状态、no-side-effect flags、decision/conistency 状态。
- 跳转详情用于只读查看，不触发任何 runner 或交易行为。
