# NQ Console Design System — B0 基线

> 批次: **B0**(READY_NOW)。对应 `NQ_DESIGN_TOKENS_V2.md` 规范的代码落地。
> 范围: 仅基础层。**不含** AI/Agent/DH 页面、真实 WebSocket、交易所 adapter、LIVE 能力、后端改造、业务页面。

## 包含

```text
tokens/nq-tokens.ts       唯一来源(颜色/字体/间距/圆角,已过 AA 对比度)
tokens/nq-css-vars.ts     生成 + 注入 CSS 变量,含行情惯例切换
tokens/nq-tokens.css      静态 :root 兜底(默认 INTL_CRYPTO)+ 数字/CJK 规范
theme/nqAntdTheme.ts      AntD 5 ConfigProvider 主题
theme/nqEchartsTheme.ts   ECharts 主题(registerTheme('nq'))
theme/nqLwcOptions.ts     Lightweight Charts 选项 + K 线涨跌色
status/StatusTag.tsx          实体状态
status/EnvironmentBadge.tsx   环境(LIVE 高危样式区分)
status/RiskBanner.tsx         页级阻断/警报
status/DataFreshness.tsx      数据源新鲜度(intelligence gap)
shell/AppShell.tsx        固定产品壳(侧导航 + Top Bar + 页头 + 内容)
index.ts                  统一导出
```

## 接线(应用入口)

```tsx
import { ConfigProvider } from 'antd';
import {
  nqAntdTheme, applyNqCssVars, registerNqEchartsTheme,
  AppShell, EnvironmentBadge, DataFreshness,
} from '@/nq-design-system';
import '@/nq-design-system/tokens/nq-tokens.css';

// 1) 注入 CSS 变量(默认 INTL_CRYPTO;用户切换时传 'CN_STOCK')
applyNqCssVars(userPrefersCnStock ? 'CN_STOCK' : 'INTL_CRYPTO');
// 2) 注册图表主题(同惯例)
registerNqEchartsTheme(userPrefersCnStock ? 'CN_STOCK' : 'INTL_CRYPTO');

export function Root() {
  return (
    <ConfigProvider theme={nqAntdTheme}>
      <AppShell
        nav={/* 导航项 */ null}
        topRight={<><EnvironmentBadge env="PAPER" /><DataFreshness source="OKX Market Data" state="fresh" detail="2s ago" inline /></>}
        pageHeader={/* 标题 / 实体 ID / 状态 / 操作 */ null}
      >
        {/* 页面内容 */}
      </AppShell>
    </ConfigProvider>
  );
}
```

## 单一来源原则

颜色只在 `nq-tokens.ts` 定义。AntD 读它、CSS 变量由它生成、ECharts/Lightweight Charts 由它派生。业务自定义组件只读 `var(--nq-*)`,不得私配 hex。`up/down` 永远独立于 `success/danger`。

## 验证(在你的仓库执行)

```text
本环境无法 npm build(无 NQ 前端源码、网络禁用)。对比度已在生成时校验通过。
落地后请执行: npm run build; npm run test:e2e;并用浏览器核对暗色对比度与 LIVE/Paper 样式区分。
```

## B0 下一切片(本基线之后)

```text
- 登录页 + 异常页(403/无权限/错误/空初始化)按新 token 重做
- 表格密度封装(主表 32 / 次级 28 / 摘要 36;数字右对齐 tabular-nums 列工具)
- useLiveQuery 抽象(当前 TanStack Query polling;后期 SSE/WebSocket 不改调用方)
```
