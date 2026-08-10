# NQ Console Design System — B0 (Design Tokens v2)

> 批次 **B0**(READY_NOW)。对应 `docs/current/frontend/NQ_DESIGN_TOKENS_V2.md` 规范的代码落地。
> 范围:仅基础层。**不含** AI/Agent/DH 页面、真实 WebSocket、交易所 adapter、LIVE 能力、后端改造、业务页面重构。

## 包含

```text
tokens/nq-tokens.ts       唯一来源(颜色/字体/间距/圆角,已过 AA 对比度)
tokens/nq-css-vars.ts     生成 + 注入 CSS 变量,含行情惯例切换(applyNqCssVars)
tokens/nq-tokens.css      静态 :root 兜底(默认 INTL_CRYPTO)+ 数字/CJK 规范(全局 import 延后)
theme/nqAntdTheme.ts      AntD 5 ConfigProvider 主题
theme/nqEchartsTheme.ts   ECharts 主题(registerTheme('nq'),从 echarts/core 注册)
theme/nqLwcOptions.ts     Lightweight Charts 选项 + K 线涨跌色(随惯例翻转)
charts/NqKlineChart.tsx   K-line 基础组件(调用方传入 bar,组件内不取数)
charts/NqVolumeChart.tsx  成交量基础组件(调用方传入 bar,组件内不取数)
status/StatusTag.tsx          实体状态
status/EnvironmentBadge.tsx   环境(LIVE 高危样式区分)
status/RiskBanner.tsx         页级阻断/警报
status/DataFreshness.tsx      数据源新鲜度(intelligence gap)
shell/AppShell.tsx        固定产品壳(侧导航 + Top Bar + 页头 + 内容)
index.ts                  统一导出
```

## 单一来源原则

颜色只在 `nq-tokens.ts` 定义。AntD 读它、CSS 变量由它生成、ECharts/Lightweight Charts 由它派生。
业务自定义组件只读 `var(--nq-*)`,不得私配 hex。NQ 业务 UI 固定 `up=红色上涨/正收益`、`down=绿色下跌/负收益`；`up/down` 永远独立于 `success/danger`。

## B0 接线范围(scoped to demo route)

本仓库已有 GateJ 冻结的 **Design System v1**(`@/theme/*`、`--nq-color-*` CSS 变量,驱动 ~20 个线上页面)。
v2 与 v1 **CSS 变量命名空间不冲突**(v2=`--nq-*`,v1=`--nq-color-*`)。唯一全局冲突点是单一的全局
AntD `ConfigProvider` 主题。本切片按"作用域限定到演示路由"接线:

- v2 `ConfigProvider`(`nqAntdTheme`)、`applyNqCssVars()`、`registerNqEchartsTheme()` 仅在
  自检路由 `/dev/design-system` 内激活,**不改全局 `AppProviders`,不动 v1 页面**。
- `applyNqCssVars()` 以 JS 向 `:root` 注入 `--nq-*`(additive,与 v1 不冲突);因此当前阶段
  **不全局 import `nq-tokens.css`**(避免其 `body` / `:lang(zh)` 全局规则影响 v1 页面)。
- 全局采用 v2、迁移既有页面到 v2 token,是后续单独切片。

## 验证

```text
npm run build            # tsc -b && vite build
浏览器自检 /dev/design-system:
  - 暗色对比度
  - LIVE 与 PAPER 样式明显不同(EnvironmentBadge)
  - 中文 14px / 数字 tabular-nums 等宽
  - 行情惯例开关(CN_STOCK/INTL_CRYPTO)涨跌色与图表同步翻转
  - B0.4 K-line / volume 静态 mock 渲染、loading / empty / error / stale 状态
```
