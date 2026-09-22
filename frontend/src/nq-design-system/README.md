# NQ Console Design System

统一规范见 [前端视觉系统](../../../docs/current/FRONTEND_DESIGN_SYSTEM.md)。

## 入口与兼容

- 主 tokens：`@/theme/tokens`，与 `@/styles/tokens.css` 镜像。
- `tokens/nq-tokens.ts` 派生兼容 API；`tokens/nq-tokens.css` 是全局别名，不再维护独立调色板。
- `theme/nqAntdTheme.ts` 兼容导出全局主题。不得在页面另设一套 ConfigProvider。
- `shell/AppShell.tsx`：共享桌面侧栏 / 窄屏 Drawer / header / content。
- `shell/NqPageScaffold.tsx`：页面分区容器，统一间距和最小宽度；不拥有路由、权限或数据。
- `brand/BrandLockup.tsx`：用户确认的蓝色丝带图标与 NEXUS QUANT 字标，覆盖侧栏和登录页；组件 / favicon 使用同一原图的 128px / 32px 衍生 PNG，见[品牌素材记录](../assets/brand/README.md)。
- `brand/ExchangeBadge.tsx`：本地官方 OKX / BINANCE 图标配合真实文字身份；PAPER 与未知代码保留通用占位，不表达可交易或已授权。

## 页面组合

新页面放在现有 ConsoleLayout 下，用 NqPageScaffold 组织已有 NqPageHeader、NqFilterBar、AntD Card（page-card / page-section）和 NqDataTable。
策略定义、回测配置、账户、交易工作台和行情是实际使用示例。不要复制主题、壳或为相同用途重复创建组件。

状态、环境和新鲜度分别复用 status/ 下的组件；存量 components/nq/ 的实体映射保留其业务语义。
未知数据、权限不足和请求失败不能为了外观显示成成功或零。

## 图表与行情惯例

`NqKlineChart` / `NqVolumeChart` 接收调用方数据，主题由 `nqLwcOptions` 派生。
ECharts 通过 `registerNqEchartsTheme` 注册；格式化与表格密度 API 保持兼容。
默认 CN_STOCK（红涨绿跌），INTL_CRYPTO 是显式诊断/兼容参数；涨跌与 success/danger 永远独立。
`/dev/design-system` 提供状态、表格和图表样例；切换 convention 后离开会恢复默认值。样例不是运行证据。

## 验证

`npm run build` 包含现有单元测试。行为修改再运行相关 E2E；视觉修改检查桌面和窄屏、中英文、长账户名、菜单收起/展开、焦点和错误信息可见性。
新增生成素材必须先经用户确认。登录背景、蓝色丝带图标均已确认并接入，favicon 与品牌组件共享同一素材；官方交易所图标来源见 [exchange assets](../assets/exchanges/README.md)。
