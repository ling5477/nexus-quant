# NQ Frontend Design System v1

> 任务来源：NQ-FRONTEND-DESIGN-SYSTEM-V1-AND-TRADING-UI-REFACTOR（2026-06-13）。
> 定位：专业量化策略运营控制台 + Paper Trading 运行监控台。深色优先、高信息密度、小圆角、低阴影、1px 分割线、数字等宽。
> 边界：本文件只描述前端视觉系统与组件约定，不涉及后端、AI、DH、LIVE 能力（均未开启 / 未集成）。

## 1. Token 体系

### 1.1 双侧同源（强约束）

| 文件 | 用途 |
| --- | --- |
| `frontend/src/styles/tokens.css` | CSS variables（`--nq-*`），供全局样式与自研组件 CSS 使用 |
| `frontend/src/theme/tokens.ts` | TS 常量（`nqColor` / `nqSpace` / `nqRadius` / `nqFont` / `nqShadow` / `nqMotion`），供 AntD ConfigProvider 与图表主题使用 |

两个文件互为镜像（命名一致、取值一致）。**修改任一侧必须同步另一侧**。选择镜像而非运行时读取，是为了避免样式表加载时序导致主题取值不稳定。

### 1.2 颜色语义

| 语义 | Token | 取值 | 用途 |
| --- | --- | --- | --- |
| primary | `--nq-color-primary` | `#4f7cf7` | 品牌强调，替代 AntD 默认 `#1677ff` |
| success | `--nq-color-success` | `#3dd68c` | RUNNING / ACTIVE / SUCCEEDED |
| info | `--nq-color-info` | `#54a9ff` | PENDING / CREATED |
| neutral | `--nq-color-neutral` | `#8b98ab` | PAUSED / SKIPPED / 普通信息 |
| warning | `--nq-color-warning` | `#e8b339` | WARNING / DEGRADED / 风险提示 |
| danger | `--nq-color-danger` | `#e5484d` | FAILED / BLOCKED / REJECTED / 熔断 / 强平 |
| disabled | `--nq-color-disabled` | `#49566a` | 不可用状态 |
| up | `--nq-color-up` | `#f23645` | 上涨 / 盈利（国内习惯红涨） |
| down | `--nq-color-down` | `#089981` | 下跌 / 亏损（绿跌） |
| paper | `--nq-color-paper` | `#54a9ff` | SIM / PAPER 环境标识 |
| demo | `--nq-color-demo` | `#9d7bff` | DEMO 环境标识 |
| live | `--nq-color-live` | `#e5484d` | LIVE 环境强警示（本阶段 LIVE 能力 disabled，仅视觉预留） |

背景分层：`bg-page #0d1219` → `bg-panel #131a23`（卡片）→ `bg-elevated #18212c`（弹层）→ `bg-sunken #0a0e14`（侧栏）。
分割线：`border #263141`（主）/ `border-subtle #1d2734`（次），统一 1px。

### 1.3 其余 token

- spacing：4 / 8 / 12 / 16 / 24 / 32（`--nq-space-*`）。
- radius：2 / 4 / 6（小圆角，AntD borderRadius=4）。
- typography：正文 13px；mono 字体栈 `JetBrains Mono / Cascadia Mono / Consolas / ui-monospace`。
- shadow：`--nq-shadow-low`（卡片）/ `--nq-shadow-overlay`（弹层），低阴影。
- motion：0.1s / 0.2s / 0.3s + 标准缓动，无装饰性动画。
- z-index：header 100 / overlay 1000 / toast 2000。

## 2. AntD ConfigProvider

主题入口：`frontend/src/theme/antd-theme.ts`（`nqAntdTheme`），由 `AppProviders` 注入。

- `theme.darkAlgorithm` + 上述 token 锚定，覆盖默认蓝、大圆角、大留白。
- 高密度：`fontSize 13`、`controlHeight 30`、Table cell padding 10/12（small 6/8）、Form `itemMarginBottom 12`。
- 组件级覆盖：Layout / Menu / Table / Card / Tabs / Tag / Modal / Descriptions / Button / Alert。

## 3. 图表主题

- 入口：`frontend/src/theme/chart-theme.ts`（`buildNqLineChartBaseOption` + `nqChartSeriesPalette`）。
- ECharts 按需注册统一收口在 `frontend/src/components/nq/charts/echarts-core.ts`，业务图表不得自行全量 import echarts。
- 图表与 UI 同源取色，禁止图表内自定义第二套颜色。
- Lightweight Charts（K 线）本轮未引入依赖，待 Backtest Detail 可视化轮次接入时同样必须读取本 token 体系。

## 4. 数字排版规范

- 数字字段（价格/数量/收益率/回撤/评分/成交量/仓位/延迟/滑点）：`.nq-num`（`tabular-nums` + `tnum`）。
- 表格数字列：`nqNumericColumn()` 注入右对齐 + `.nq-col-num`。
- ID / hash / traceId：`.nq-mono` 等宽字体。
- 收益率/盈亏带正负号（`signed`），按正负着色 `colorBySign`（正=up 红，负=down 绿）。
- 千分位 + 小数位统一：价格 4 位、金额/数量 2 位、百分比 2 位（`formatNqNumber`）。
- 后端 `drawdown` / `dailyReturn` / `uptimeRatio` 为比例值（见 `DrawdownCalculator`），展示用 `NqPercentText ratio` 换算百分比。

## 5. Nq 组件清单（`frontend/src/components/nq/`）

| 组件 | 用途 |
| --- | --- |
| `NqPageHeader` | 页面头部；标题必须保持 heading 语义（E2E 依赖 getByRole heading） |
| `NqMetricCard` | 指标卡（tone：success/warning/danger/up/down/muted） |
| `NqStatusTag` | 状态标签，内置状态→色调映射，语义冲突用 `tone` 覆盖；文本保持后端原值 |
| `NqEnvironmentBadge` | SIM/PAPER 蓝、DEMO 紫、LIVE 红强警示 |
| `NqRiskBanner` | 安全/风险横幅，禁止隐藏失败与风险信息 |
| `NqFilterBar` | 查询区容器（标题默认「查询区」） |
| `NqDataTable` / `nqNumericColumn` | 高密度表格包装 + 数字列 helper |
| `NqPriceText` / `NqAmountText` / `NqPercentText` / `formatNqNumber` | 数字排版组件族 |
| `NqEmptyState` / `NqErrorState` / `NqLoadingState` | 空/错/载入三态，空态文案由调用方保持业务口径 |
| `NqDangerConfirmButton` | 危险操作二次确认（仅防误触，不承载权限/风控判断） |
| `NqEquityCurveChart` / `NqDrawdownChart` | ECharts 权益/回撤曲线，读取 Design System token |

兼容层：`components/page/PageHero` 已收敛为 `NqPageHeader` 薄适配，存量页面自动统一头部；新页面直接使用 `NqPageHeader`。

待补组件（下一轮）：`NqKlineChart`（依赖 lightweight-charts，回测详情轮次引入）、`NqAlertPanel` / `NqHeartbeatPanel` / `NqScheduleFirePanel` / `NqRecoveryPanel` / `NqStabilityCheckPanel`（Paper Trading 控制台深化轮次）。

## 6. 组件边界

- AntD 负责 Form / Input / Select / DatePicker / Modal / Drawer / Tabs / Tooltip / Popover / Dropdown / Menu / Pagination / Notification / Message。
- ProComponents 只允许用于 admin / config / debug 页面；核心交易、Paper Trading、回测详情页不直接套 ProTable / ProForm。
- 禁止引入 AG Grid、MUI / Chakra / Mantine / Element Plus / Naive UI / shadcn/ui、Tailwind 全量重写。
- TanStack Table 仅在确有 headless 需求时再评估，当前未引入。

## 7. 页面视觉守则

必须：深色背景、紧凑卡片、清晰分区、状态色统一、数字右对齐、危险操作强提示、低阴影、小圆角、1px 分割线。
禁止：大面积渐变、毛玻璃、3D 粒子、营销风首页、花哨动画、过度留白、各页面自定义配色。

## 8. 本轮落地范围（2026-06-13）

- Design System v1 全量落地（tokens / AntD 主题 / 图表主题 / 全局样式重写）。
- Dashboard 重构为安全总览（系统健康横幅、Paper Run 汇总、焦点 run 绩效、最近事件、业务入口）。
- Paper Trading 控制台视觉重构（状态摘要条、权益/回撤 ECharts、Nq 组件换装），E2E 文案选择器全部保留。
- 其余页面（Backtest Detail、Strategy Center、Risk Center、Operation Center、Trading Workbench）通过 PageHero 适配层与全局主题获得基础换肤，深度重构留待后续轮次按优先级推进。
