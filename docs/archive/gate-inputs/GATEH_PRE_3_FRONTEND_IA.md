# GATEH_PRE_3_FRONTEND_IA

当前状态：**implemented (route + page-domain baseline)**

## 目标

先冻结正式控制台的信息架构与页面域，停止继续在 `trade-validation` / GateG 平铺壳子上叠页面。

## 已落地

### 1. 路由与导航

- `frontend/src/router/routes.tsx` 已调整为正式入口：
  - `/dashboard`
  - `/accounts`
  - `/trading`
  - `/instruments`
  - `/marketdata`
  - `/strategies`
  - `/schedules`
  - `/runs`
  - `/research`
  - `/backtests`
  - `/evaluations`
  - `/publishes`
- 兼容路由：`/trade-validation -> /trading`
- `frontend/src/router/navigation.tsx` 已按正式 IA 分组：
  - 概览
  - 账户与交易
  - 市场与主数据
  - 策略运行
  - 研究与回测
- `frontend/src/components/layout/AppSiderMenu.tsx` 已改为按 section 分组展示。

### 2. 账户上下文主链

- `AppHeader` 已支持在 Header 中切换当前 `exchangeAccountId` 上下文。
- `account-context-store` 继续以 `selectedExchangeAccountId` 为正式主键。
- `TradingVerificationController` 已新增 `exchangeAccountId -> legacyAccountId` 的 controller 内兼容解析；
  前端可正式传 `exchangeAccountId`，不再默认暴露 `legacyAccountId`。

### 3. 页面域

新增正式页面域：

- `frontend/src/pages/trading/TradingWorkbenchPage.tsx`
- `frontend/src/pages/instruments/InstrumentsPage.tsx`
- `frontend/src/pages/marketdata/MarketdataPage.tsx`

新增前端 API / 类型：

- `frontend/src/api/instruments.ts`
- `frontend/src/api/marketdata.ts`
- `frontend/src/types/instruments.ts`
- `frontend/src/types/marketdata.ts`

### 4. 交易工作台

- 原 `TradeValidationPage` 已按正式工作台口径收口：
  - 标题改为“交易工作台”
  - 默认账户来源改为 `selectedExchangeAccountId`
  - 不再以手工输入 `legacyAccountId` 为默认模式
  - 详情标题与上下文提示同步更新

## 当前 IA 口径

- 账户与交易：`accounts` / `trading`
- 市场与主数据：`instruments` / `marketdata`
- 策略运行：`strategies` / `schedules` / `runs`
- 研究与回测：`research` / `backtests` / `evaluations` / `publishes`

## 后续约束

- 后续新页面不再新增平级“验证页”路由
- 与交易对选择相关的页面必须优先接 `instrument catalog`
- 页面默认上下文一律从 `exchangeAccountId` 出发，不再把 `legacyAccountId` 暴露为主入口
