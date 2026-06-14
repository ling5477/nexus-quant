# NQ Console Design Tokens v2 + Runtime Baseline

> 任务分类: `FRONTEND_UI` (primary) + `PRODUCT_DESIGN`
> 适用范围: NQ Console 单一设计系统。DH 不建独立前端;AI / DH 页面是 NQ Console 消费 DH API 的页面。
> Gate 边界: GateJ completed → GateK-PLAN。本文件只定基础系统(token / shell / status / 图表 / 表格 / 实时数据规范),不实现 AI / Agent / DH 业务页面。
> 状态: 立即锁死(第一批施工对象)。

这是"立即施工图"的基础层。它把研究报告里散落的视觉断言,收敛成一套**经过对比度验证、单一来源、可直接落代码**的 token 系统。

---

## 0. 核心原则:单一来源,四处消费

所有颜色/字号/间距只在一处定义(`nq-tokens.ts`),由它派生四个消费层。**禁止任何页面、任何图表私配颜色。**

```text
nq-tokens.ts  (唯一来源)
   ├── nqAntdTheme.ts        → AntD 5 ConfigProvider
   ├── nq-tokens.css (生成)  → 业务自定义组件 / TanStack Table / 原生 DOM
   ├── nqEchartsTheme.ts     → ECharts (权益/PnL/回撤/监控)
   └── nqLwcOptions.ts       → Lightweight Charts (K 线/行情主图)
```

不要手抄 CSS 变量;CSS 变量从 TS 生成,避免两份值漂移。

---

## 1. 颜色 token(已过 WCAG AA 校验)

下表的对比度是用脚本对每个背景层算出来的,不是估的。`OK` = 过 AA 正文 4.5:1;`lg` = 过 AA 大文本/UI 3:1(disabled 文本本就豁免,符合)。

### 1.1 背景层级(整站深色,靠明度差分层,不靠渐变)

| token | 值 | 用途 |
|---|---|---|
| `--nq-bg-app` | `#070f1c` | 最外层 body / 导航底 |
| `--nq-bg-canvas` | `#0b1322` | 内容画布 |
| `--nq-bg-panel` | `#0f1b2d` | 卡片 / 表格 / 分区 |
| `--nq-bg-elevated` | `#16263d` | 浮层 / 表头 / hover |

### 1.2 文字(层级靠明度,不靠字号乱跳)

| token | 值 | app | canvas | panel | elevated |
|---|---|---|---|---|---|
| `--nq-text-primary` | `#eef3ff` | 17.3 OK | 16.7 OK | 15.6 OK | 13.7 OK |
| `--nq-text-secondary` | `#bcc8de` | 11.4 OK | 11.0 OK | 10.3 OK | 9.0 OK |
| `--nq-text-tertiary` | `#93a1ba` | 7.4 OK | 7.1 OK | 6.6 OK | 5.8 OK |
| `--nq-text-disabled` | `#6b7990` | 4.4 lg | 4.2 lg | 3.9 lg | 3.5 lg |

> 这里修了研究报告的两个问题:报告的 `text-tertiary #8291ab` / `disabled #5f6d86` 在深色面板上偏弱,已上调到上面这组并逐层验证。

### 1.3 系统语义色(状态、操作,全部走语义,页面不得私配)

| token | 值 | 含义 | panel 对比度 |
|---|---|---|---|
| `--nq-primary` | `#5b8cff` | 主操作 / 选中 / 链接 | 5.5 OK |
| `--nq-success` | `#3ad29f` | 系统成功 / 健康 / 任务完成 | 9.0 OK |
| `--nq-warning` | `#fbbf3f` | 告警 / 降级 / 需关注 | 10.4 OK |
| `--nq-danger` | `#ff6166` | 危险操作 / 失败 / 阻断 / 熔断 | 5.9 OK |
| `--nq-info` | `#56c7f5` | 中性信息 / 提示 | 9.0 OK |

> 主色离开了 AntD 默认蓝 `#1677ff`,避免"一眼中后台模板"。

### 1.4 行情方向色(独立 token + 惯例开关)—— 关键修正

`up`/`down` **不复用** `success`/`danger`。它们是独立语义,因为:盈利的"红"和危险操作的"红"必须能分别改色;而 NQ 是数字货币系统,用户在 A 股(红涨绿跌)和 Binance/OKX(绿涨红跌)之间分裂。做成一个开关,一处切换:

| token | CN 惯例(默认) | INTL 惯例 | 含义 |
|---|---|---|---|
| `--nq-up` | `#ff5c6c`(红) | `#33d6a6`(绿) | 上涨 / 盈利 |
| `--nq-down` | `#33d6a6`(绿) | `#ff5c6c`(红) | 下跌 / 亏损 |
| `--nq-flat` | `#93a1ba` | `#93a1ba` | 持平 |

对比度(行情数字一律 bold + `tabular-nums`,按大文本 3:1 评估,实测仍过 4.5):up 5.8 OK / down 9.3 OK on panel。

**待你定:** 默认惯例是 `CN` 还是 `INTL`?我的建议是**提供个人偏好开关**(像交易所一样),默认随主要用户群定;不要在代码里写死。

### 1.5 环境 / 审计色(全局统一,长期可见)

| token | 值 | 含义 |
|---|---|---|
| `--nq-env-paper` | `#3b82f6` | PAPER |
| `--nq-env-demo` | `#7c5cff` | DEMO |
| `--nq-env-live` | `#ff4d4f` | LIVE(高危,样式必须与 Paper 区分) |
| `--nq-env-readonly` | `#667085` | READONLY |
| `--nq-env-audited` | `#3ad29f` | AUDITED |

### 1.6 边框

| token | 值 | 用途 |
|---|---|---|
| `--nq-border-subtle` | `#22324a` | 卡片/表格/分区的 1px 秩序线 |
| `--nq-border-strong` | `#3a4d6e` | 强分隔 / 聚焦 |

> 深色系层级靠边框+背景分层建立,阴影只留两档给浮层。

---

## 2. 字体与 CJK 策略

```text
UI 字体栈: Inter, "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif
数字/ID 栈: "JetBrains Mono", "Roboto Mono", ui-monospace, monospace  (强制 font-variant-numeric: tabular-nums)
```

字号(高密度优先,但对 CJK 做了抬高):

| 角色 | 拉丁 | CJK 处理 | 说明 |
|---|---|---|---|
| 正文/表格 | 13px | 渲染中文标签时用 **14px**(`:lang(zh)` 或容器类抬一档) | 13px CJK 长时间读偏累 |
| 次级/caption | 12px | 12px | 元信息、单位 |
| 数字(价格/盈亏/ID) | 13px mono | 同左 | 必须 `tabular-nums`,小数点对齐、跳动不抖 |
| 标题 | 16 / 20 / 24px | 同左 | 页头/分区 |

> 这条修了研究报告"全站 13px"对中文偏小的问题:基线 13,**中文内容抬到 14**,数字保持等宽 13。

---

## 3. 间距 / 圆角 / 阴影 / 密度

```text
间距:   4 / 8 / 12 / 16 / 24 / 32     页面 gutter 24,卡片 padding 16,密集面板 gap 12
圆角:   4(输入框/Tag) · 6(Card/Drawer) · 大圆角禁用
阴影:   两档轻阴影,仅用于浮层;不做玻璃态、不做悬浮投影
表格行高: 主表 32 · 次级事实表 28 · 摘要表 36   数字右对齐 / 实体左对齐 / 状态与动作不换行
空单元格: 统一显示 "-"
```

---

## 4. 状态系统(Status System)——四个常驻组件

把"系统现在可不可信"前置,是这套系统区别于普通后台的核心。固定四类承载,任何页面复用同一套:

| 组件 | 职责 | 嵌入位置 |
|---|---|---|
| `StatusTag` | 实体状态(订单/Run/策略/发布…) | 表格、卡片、列表、页头 |
| `EnvironmentBadge` | 环境 / 读写边界(PAPER/DEMO/LIVE/READONLY) | Top Bar 常驻 + 操作区 |
| `RiskBanner` | 页级阻断 / 熔断 / 警报 | 页头下方,不可藏角落 |
| `DataFreshness` | 数据源新鲜度与"看不见什么" | 监控页、数据源旁、Top Bar 汇总 |

`DataFreshness` 是从 World Monitor 吸收的(intelligence gap):数据源断了**不能静默隐藏**,要显式标状态。固定状态枚举:

```text
fresh · stale · very_stale · delayed · degraded · no_data · error · disabled
示例:
  OKX Market Data      fresh      2s ago
  Binance Kline        stale      3m ago
  Risk Engine          healthy    120ms
  Strategy Scheduler   delayed    last heartbeat 45s
  DH Agent Feedback    disabled
```

---

## 5. 实时数据 + 虚拟化(补研究报告缺的架构层)

监控 / 工作台类页面不能只定视觉。固定规范:

**数据传输**

```text
当前阶段(GateK 前): 一律 TanStack Query polling
  - 监控类轮询间隔 3–5s,可手动刷新;窗口失焦自动降频/暂停
  - 所有请求带 traceId;失败进入 DataFreshness 的 error/degraded,不静默
后期: 行情 / 订单 / 持仓 / Agent 进度切换 SSE 或 WebSocket
  - 传输层抽象成 useLiveQuery,页面不感知 polling 还是 socket
```

**表格虚拟化阈值**

```text
< 200 行:        TanStack Table,不虚拟化
200 – 5,000 行:  TanStack Table + 行虚拟化(@tanstack/react-virtual)
实时高频/> 5,000: 才评估 AG Grid(社区版优先);在此之前一律不引入
```

> 与上轮一致:核心高密度表用 TanStack(headless,逃离 AntD 视觉);ProComponents 只用于 Settings 这类低风险运营页;AG Grid 继续后置。

---

## 6. 代码:单一来源与四个派生层

### 6.1 `nq-tokens.ts`(唯一来源)

```ts
// 所有 NQ Console 颜色/排版的唯一来源。CSS 变量、AntD 主题、图表主题都从这里派生。
export const nqTokens = {
  bg:     { app: '#070f1c', canvas: '#0b1322', panel: '#0f1b2d', elevated: '#16263d' },
  text:   { primary: '#eef3ff', secondary: '#bcc8de', tertiary: '#93a1ba', disabled: '#6b7990' },
  border: { subtle: '#22324a', strong: '#3a4d6e' },
  semantic: { primary: '#5b8cff', success: '#3ad29f', warning: '#fbbf3f', danger: '#ff6166', info: '#56c7f5' },
  // 行情方向:与 success/danger 解耦,由 convention 决定具体 hex
  market: {
    CN:   { up: '#ff5c6c', down: '#33d6a6', flat: '#93a1ba' }, // 红涨绿跌
    INTL: { up: '#33d6a6', down: '#ff5c6c', flat: '#93a1ba' }, // 绿涨红跌(Binance/OKX 默认)
  },
  env: { PAPER: '#3b82f6', DEMO: '#7c5cff', LIVE: '#ff4d4f', READONLY: '#667085', AUDITED: '#3ad29f' },
  radius: { sm: 4, md: 6 },
  font: {
    ui: "'Inter','HarmonyOS Sans SC','PingFang SC','Microsoft YaHei',system-ui,sans-serif",
    mono: "'JetBrains Mono','Roboto Mono',ui-monospace,monospace",
    sizeBase: 13, sizeCJK: 14,
  },
  space: [4, 8, 12, 16, 24, 32] as const,
} as const;

export type MarketConvention = 'CN' | 'INTL';
export const marketColors = (c: MarketConvention = 'CN') => nqTokens.market[c];
```

### 6.2 `nq-css-vars.ts`(生成 CSS 变量,避免手抄漂移)

```ts
import { nqTokens, type MarketConvention } from './nq-tokens';

export function nqCssVars(convention: MarketConvention = 'CN'): Record<string, string> {
  const m = nqTokens.market[convention];
  return {
    '--nq-bg-app': nqTokens.bg.app, '--nq-bg-canvas': nqTokens.bg.canvas,
    '--nq-bg-panel': nqTokens.bg.panel, '--nq-bg-elevated': nqTokens.bg.elevated,
    '--nq-text-primary': nqTokens.text.primary, '--nq-text-secondary': nqTokens.text.secondary,
    '--nq-text-tertiary': nqTokens.text.tertiary, '--nq-text-disabled': nqTokens.text.disabled,
    '--nq-border-subtle': nqTokens.border.subtle, '--nq-border-strong': nqTokens.border.strong,
    '--nq-primary': nqTokens.semantic.primary, '--nq-success': nqTokens.semantic.success,
    '--nq-warning': nqTokens.semantic.warning, '--nq-danger': nqTokens.semantic.danger,
    '--nq-info': nqTokens.semantic.info,
    '--nq-up': m.up, '--nq-down': m.down, '--nq-flat': m.flat,
    '--nq-env-paper': nqTokens.env.PAPER, '--nq-env-demo': nqTokens.env.DEMO,
    '--nq-env-live': nqTokens.env.LIVE, '--nq-env-readonly': nqTokens.env.READONLY,
    '--nq-env-audited': nqTokens.env.AUDITED,
    '--nq-font-ui': nqTokens.font.ui, '--nq-font-mono': nqTokens.font.mono,
  };
}

// 应用到 :root(惯例切换时重设即可)
export function applyNqCssVars(convention: MarketConvention = 'CN') {
  const vars = nqCssVars(convention);
  for (const [k, v] of Object.entries(vars)) document.documentElement.style.setProperty(k, v);
}
```

### 6.3 `nqAntdTheme.ts`(AntD 5 ConfigProvider)

```ts
import { theme, type ThemeConfig } from 'antd';
import { nqTokens as t } from './nq-tokens';

export const nqAntdTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: t.semantic.primary,
    colorSuccess: t.semantic.success,
    colorWarning: t.semantic.warning,
    colorError: t.semantic.danger,
    colorInfo: t.semantic.info,
    colorBgBase: t.bg.app,
    colorBgContainer: t.bg.panel,
    colorBgElevated: t.bg.elevated,
    colorBorder: t.border.strong,
    colorBorderSecondary: t.border.subtle,
    colorText: t.text.primary,
    colorTextSecondary: t.text.secondary,
    colorTextTertiary: t.text.tertiary,
    colorTextQuaternary: t.text.disabled,
    borderRadius: t.radius.md,
    borderRadiusSM: t.radius.sm,
    borderRadiusLG: t.radius.md,
    fontSize: t.font.sizeBase,
    fontFamily: t.font.ui,
    wireframe: false,
  },
  components: {
    Table: {
      headerBg: t.bg.elevated,
      headerColor: t.text.secondary,
      rowHoverBg: t.bg.elevated,
      borderColor: t.border.subtle,
      cellPaddingBlock: 6,
      cellPaddingInline: 12,
      cellFontSize: t.font.sizeBase,
    },
    Card: { colorBgContainer: t.bg.panel, paddingLG: 16 },
    Layout: { bodyBg: t.bg.canvas, siderBg: t.bg.app, headerBg: t.bg.app },
    Tag: { borderRadiusSM: t.radius.sm },
    Button: { primaryShadow: 'none', defaultShadow: 'none' },
  },
};
```

### 6.4 `nqEchartsTheme.ts`(ECharts:权益/PnL/回撤/监控)

```ts
import { nqTokens as t, marketColors, type MarketConvention } from './nq-tokens';
import * as echarts from 'echarts';

export function registerNqEchartsTheme(convention: MarketConvention = 'CN') {
  const m = marketColors(convention);
  echarts.registerTheme('nq', {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: t.font.ui, color: t.text.secondary },
    // 系列默认色:主色优先,涨跌单列用 market 色
    color: [t.semantic.primary, t.semantic.info, t.semantic.warning, m.up, m.down, '#9b8cff'],
    title: { textStyle: { color: t.text.primary } },
    legend: { textStyle: { color: t.text.tertiary } },
    grid: { borderColor: t.border.subtle },
    categoryAxis: {
      axisLine: { lineStyle: { color: t.border.strong } },
      axisLabel: { color: t.text.tertiary, fontFamily: t.font.mono },
      splitLine: { lineStyle: { color: t.border.subtle } },
    },
    valueAxis: {
      axisLine: { lineStyle: { color: t.border.strong } },
      axisLabel: { color: t.text.tertiary, fontFamily: t.font.mono },
      splitLine: { lineStyle: { color: t.border.subtle } },
    },
    tooltip: {
      backgroundColor: t.bg.elevated,
      borderColor: t.border.strong,
      textStyle: { color: t.text.primary, fontFamily: t.font.mono },
    },
  });
}
// 用法: <ReactECharts theme="nq" ... />
```

### 6.5 `nqLwcOptions.ts`(Lightweight Charts:K 线/行情主图)

```ts
import { nqTokens as t, marketColors, type MarketConvention } from './nq-tokens';

export function nqLwcOptions(convention: MarketConvention = 'CN') {
  return {
    layout: { background: { color: 'transparent' }, textColor: t.text.tertiary, fontFamily: t.font.mono },
    grid: { vertLines: { color: t.border.subtle }, horzLines: { color: t.border.subtle } },
    rightPriceScale: { borderColor: t.border.strong },
    timeScale: { borderColor: t.border.strong },
    crosshair: { vertLine: { color: t.border.strong }, horzLine: { color: t.border.strong } },
  };
}

export function nqCandleColors(convention: MarketConvention = 'CN') {
  const m = marketColors(convention);
  return {
    upColor: m.up, borderUpColor: m.up, wickUpColor: m.up,
    downColor: m.down, borderDownColor: m.down, wickDownColor: m.down,
  };
}
```

---

## 7. 验收(本基础层)

```text
[ ] ConfigProvider 注入 nqAntdTheme,全站离开默认蓝
[ ] applyNqCssVars 注入,业务组件/TanStack Table 只读 var(--nq-*)
[ ] ECharts 与 Lightweight Charts 与界面同色板(惯例切换一处生效)
[ ] up/down 与 success/danger 在代码中是独立 token
[ ] 中文内容 14px / 数字 tabular-nums 等宽
[ ] DataFreshness / RiskBanner / EnvironmentBadge / StatusTag 四件常驻可用
[ ] LIVE 操作样式明显区别于 PAPER
[ ] frontend: npm run build 通过(在你本地仓库执行)
```

> 本仓库环境无法跑 `npm run build`(无 NQ 前端源码、网络禁用)。对比度校验已在生成时跑过并通过;npm 构建与真机视觉校验在你侧完成。
