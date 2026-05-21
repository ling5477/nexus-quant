# GateJ 前端规划

本文只做前端规划，不写页面实现。

## 技术约束

- 页面必须有 loading、empty、error 状态。
- 服务端数据使用 Axios + TanStack Query。
- Zustand 不存 Paper stable operation 服务端数据。
- 不引入新图表库。
- 不做 AI UI。
- 不做 LIVE 下单 UI。
- 不做美股/A 股 UI。

---

## 1. /paper-trading 稳定运行增强

在现有 `/paper-trading` 页面基础上新增或增强以下 Tab：

### 1.1 调度计划 Tab

- 展示当前 Paper run 关联的调度计划列表。
- 支持创建调度计划（表单：名称、cron 表达式、时区）。
- 支持启用/禁用/暂停调度。
- 支持手动触发一次（run-once）。
- 展示下次触发时间和上次触发时间。

### 1.2 调度触发记录 Tab

- 展示调度触发历史列表。
- 展示每次触发的状态（RUNNING / SUCCEEDED / FAILED / SKIPPED）。
- 展示触发时间、完成时间、耗时。
- 失败记录展示错误信息。

### 1.3 心跳 Tab

- 展示 Paper run 心跳记录列表。
- 展示心跳时间、状态、延迟秒数。
- 展示最近事件/订单/成交时间。
- 支持手动触发一次心跳（run-once）。

### 1.4 日报 Tab

- 展示 Paper run 日报列表。
- 第一版用表格/描述卡展示，不引入图表库。
- 展示日期、总权益、日盈亏、日收益率、最大回撤、订单数、成交数、告警数。
- 支持手动生成日报。

### 1.5 告警 Tab

- 展示 Paper run 告警事件列表。
- 支持按严重程度和状态过滤。
- 展示告警类型、标题、严重程度、状态。
- 支持确认告警（ack）。
- OPEN 状态告警高亮显示。

### 1.6 恢复事件 Tab

- 展示恢复和重试事件列表。
- 展示恢复类型、状态、原因、开始/完成时间。
- 支持触发一次恢复。
- 支持触发一次失败重试。

### 1.7 稳定性验收 Tab

- 展示稳定性验收结果列表。
- 展示验收窗口、状态、在线率、心跳数、告警数、失败触发数。
- 支持生成一次稳定性验收（输入窗口起止时间）。
- PASSED / FAILED / PARTIAL 状态用不同颜色标记。

---

## 2. Paper run 详情页增强

在现有 Paper run 详情抽屉中增强展示：

- run 当前状态（复用已有）。
- 最近心跳（最新一条心跳时间和状态）。
- 最近调度触发（最新一条 fire 状态和时间）。
- 当日运行摘要（当日日报核心指标）。
- 最新告警（最新一条 OPEN 告警标题和严重程度）。
- 最近恢复事件（最新一条恢复状态和时间）。
- 稳定性验收状态（最新一条验收结果）。

---

## 3. Daily Report 展示

第一版用表格/描述卡：

- 日期列。
- 总权益列。
- 日盈亏列（正绿负红）。
- 日收益率列。
- 最大回撤列。
- 订单/成交/告警/风控拒绝计数列。
- 不引入 ECharts / Recharts / Chart.js 等图表库。

---

## 4. Alert 展示

- 列表展示告警。
- 支持按 severity 过滤（LOW / MEDIUM / HIGH / CRITICAL）。
- 支持按 status 过滤（OPEN / ACKED / RESOLVED）。
- OPEN + HIGH/CRITICAL 告警行高亮。
- 行内"确认"按钮，点击后调用 ack API。
- 确认后状态变为 ACKED，按钮禁用。

---

## 5. Recovery 展示

- 列表展示恢复事件。
- 展示恢复类型、状态、原因。
- 页面顶部提供"触发恢复"和"重试失败步骤"按钮。
- 按钮点击后弹出确认 Modal（输入原因），确认后调用 API。
- 操作完成后刷新列表。

---

## 6. Stability Check 展示

- 列表展示稳定性验收结果。
- 展示验收窗口、状态、在线率、各项计数。
- 页面顶部提供"生成验收"按钮。
- 点击后弹出表单 Modal（输入窗口起止时间）。
- 生成完成后刷新列表。
- PASSED 绿色、FAILED 红色、PARTIAL 橙色。

---

## 7. 组件与 Hook 规划

### 新增 API 客户端

- `frontend/src/api/paper-trading-stable.ts`：调度、心跳、日报、告警、恢复、稳定性验收 API。

### 新增 Query Keys

- `paperScheduleQueryKeys`
- `paperHeartbeatQueryKeys`
- `paperDailyReportQueryKeys`
- `paperAlertQueryKeys`
- `paperRecoveryQueryKeys`
- `paperStabilityCheckQueryKeys`

### 新增 Hooks

- `usePaperSchedulesQuery`
- `usePaperScheduleFiresQuery`
- `usePaperHeartbeatsQuery`
- `usePaperDailyReportsQuery`
- `usePaperAlertsQuery`
- `usePaperRecoveryEventsQuery`
- `usePaperStabilityChecksQuery`
- `useCreateScheduleMutation`
- `useUpdateScheduleStatusMutation`
- `useRunScheduleOnceMutation`
- `useRunHeartbeatOnceMutation`
- `useGenerateDailyReportMutation`
- `useAckAlertMutation`
- `useRecoverMutation`
- `useRetryFailedStepMutation`
- `useGenerateStabilityCheckMutation`

### 不做

- 不新增 Zustand store。
- 不引入图表库。
- 不做 WebSocket 实时推送。
- 不做 AI UI。
