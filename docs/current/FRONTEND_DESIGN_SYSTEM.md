# NQ 前端视觉系统

> 当前实现说明，不是 Phase 验收或发布授权。视觉参考用于风格，页面内容以现有路由和业务数据为准。

## 统一入口

- `frontend/src/theme/tokens.ts` 与 `frontend/src/styles/tokens.css` 是 TS / CSS 镜像，修改取值须同步。
- `frontend/src/theme/antd-theme.ts` 由全局 AppProviders 注入；`nq-design-system/theme/nqAntdTheme.ts` 只做兼容导出，不维护第二套主题。
- `nq-design-system/tokens/nq-tokens.ts` 从主 tokens 派生；旧 `--nq-*` 变量由全局导入的兼容 CSS 映射到 `--nq-color-*`。
- 独立页不再另设 ConfigProvider 或注入另一套全局配色。诊断页切换行情 convention 后，离开时恢复默认值。

## 视觉基线

| 项目 | 当前值 |
| --- | --- |
| 页面 / 面板 | `#040d17` / `#071827` |
| 弹层 / 侧栏 | `#0d2134` / `#05121f` |
| 主边框 / 次边框 | `#254863` / `#173247`，1px |
| 主色 / 强调色 | `#0878fa` / `#00bcf2` |
| 主文字 / 次文字 | `#edf4fc` / `#a6bfd7` |
| 圆角 | 4 / 6 / 8px；登录品牌卡片单独为 12px |
| 间距 | 4 / 8 / 12 / 16 / 24 / 32px |
| 字号 | 正文 13px，页面标题 20px；数字 tabular-nums |

保持 success / warning / danger 与涨跌颜色独立。业务 UI 默认 CN_STOCK（红涨绿跌）；INTL_CRYPTO 仅由支持该参数的图表/诊断显式选择。不要把登录背景装饰 K 线的蓝红配色用作业务颜色语义。

## 产品壳与页面容器

`ConsoleLayout` 使用唯一 `nq-design-system/shell/AppShell`，保留鉴权、路由 Outlet、菜单和面包屑。
桌面侧栏宽 240px、收起宽 72px；窄屏使用可滚动 Drawer，选中导航后关闭。
Header 保留真实账户上下文、运行环境标签、角色、语言和退出；账户长名称视觉省略，完整值保留于 title / aria-label。
没有新增搜索、通知、实时运行指示等不存在的能力。

新增受保护页面使用 `NqPageScaffold`，不要再创建 AppShell 或 ConfigProvider。沿用已有 primitives：

- `components/nq/NqPageHeader`：语义 heading、描述、操作和风险提示。
- AntD Card 的 `page-card` / `page-section`：统一面板。
- `NqFilterBar`、`NqDataTable`、`NqMetricCard`：查询、列表和指标。
- `NqEmptyState` / `NqErrorState` / `NqLoadingState`：保持空、失败、加载的区别。
- `NqStatusTag`、`NqEnvironmentBadge`、`NqRiskBanner`、`DataFreshness`：复用实体语义，不能用主题决定权限或健康状态。
- `PageHero` 仍是兼容适配入口；不要复制状态映射。

```tsx
import {Card} from 'antd';
import {NqPageScaffold} from '@/nq-design-system/shell/NqPageScaffold';
import {NqPageHeader} from '@/components/nq';

// title、description 和内容由页面现有 i18n / 查询提供。
<NqPageScaffold>
  <Card className="page-card" bordered={false}>
    <NqPageHeader title={title} description={description}/>
  </Card>
  <Card className="page-section" bordered={false}>{content}</Card>
</NqPageScaffold>
```

## 当前页面覆盖

| 页面 | 接入情况 |
| --- | --- |
| 登录 | 已确认背景，左侧品牌叙事、右侧表单；窄屏优先表单；认证逻辑不变 |
| Dashboard | 既有查询；指标摘要、绩效/事件与运行边界分栏；无数据时不填充装饰图表 |
| 策略定义、回测配置 | 共享壳/主题与 NqPageScaffold；保留查询、表单和 Drawer |
| 账户、交易工作台、行情 | 使用 NqPageScaffold；账户表小屏内部横向滚动；行情 K 线主图与成交量上下排列，保留真实查询和质量状态 |
| 账户、交易、行情、标的、运行就绪、策略验证/Shadow、调度、运行记录、研究、评估、发布、Paper 子路由 | 共享壳、主题、卡片和页头；不代表每页业务布局已深度重做 |
| 诊断、异常独立页 | 使用全局主题；诊断图表样例不代表真实业务数据 |

风险、监控、设置按现有功能入口表达，不因参考图而新增虚构路由。

Dashboard 不以缺失、失败、刷新中的查询或未知心跳推断健康。运行列表和告警查询失败时，计数显示 `-`，不伪装为零；日报、心跳与事件查询失败使用现有错误展示并保留错误身份。完整的焦点运行证据也只描述查询快照，不代表当前实时或全局健康。

覆盖清单以 `frontend/src/router/routes.tsx` 为准：上述共享壳覆盖全部受保护路由。20 个列表/工作区入口包含四个 Paper 子页；Shadow 与回测详情继承相同壳和主题，未重写详情业务布局。`/dev/design-system` 是诊断样例，不是运行数据；独立异常页使用全局主题，不增加业务导航。

## 图表与数字

ECharts 使用 `theme/chart-theme.ts` / `nq-design-system/theme/nqEchartsTheme.ts`，按需注册。
现有 Lightweight Charts 由 `NqKlineChart` / `NqVolumeChart` 和 `nqLwcOptions` 复用主 tokens；组件接收数据，不发起第二套查询。
数字列沿用 `.nq-num`、`nqNumericColumn`；ID / traceId 用 `.nq-mono`。
既有格式化组件分别保持精度、比例和空值契约，不能仅为视觉统一改变金额或百分比含义。

## 品牌素材与审批边界

`BrandLockup` 使用已确认的蓝色丝带图标与 NEXUS QUANT 字标，覆盖展开/收起侧栏及登录页；favicon 通过 Vite 引用同一素材，不额外引入 PWA。
图标为 `frontend/src/assets/brand/nq-ribbon-icon-approved.png`，来源生成文件 `exec-827d9a1d-2dc0-486c-a7b3-c8075fa96876.png`；保持用户确认的原图，不重新绘制。
`ExchangeBadge` / `ExchangeIcon` 使用本地打包的 OKX、BINANCE 官方站点图标，覆盖账户列表、Header 账户选择和行情元数据；来源与校验值见[素材记录](../../frontend/src/assets/exchanges/README.md)。图标仅作装饰，保留交易所文字身份；PAPER 与未知代码使用通用图标，不推断能力、权限或授权。运行时不访问交易所 CDN，素材来源记录不等于品牌使用许可审查。
任何新增生成背景/图标都必须先展示并获得用户确认，再接入代码。

当前登录素材原图为 `frontend/src/assets/brand/login-earth-kline-realistic-approved.png`，用户已确认自然回调版本；页面使用同尺寸、解码像素一致的无损 WebP。品牌组件和 favicon 使用同一确认原图的 128px / 32px PNG 衍生版本，原图保留，处理细节见[品牌素材记录](../../frontend/src/assets/brand/README.md)。
来源生成文件：`exec-aecc0ce6-83ae-4587-abc1-a3800dc9bdbc.png`。
SHA256：`94F4ABEB142A38AFB1AEB8A707119F5B8ACD626379808871F0C0E7F483E83D46`。
背景是装饰图，不表示行情、收益或实时交易能力。沿用右侧登录卡片，末段 K 线可能被卡片遮挡；窄屏允许裁切背景，不允许裁切凭证输入。

## 验证与限制

修改后按影响验证 build、目标 E2E、中英文及代表视口。浏览器 fixture 只证明前端渲染和交互，不代替真实后端联调。
保持 query keys、API payload、错误 code / traceId、权限、mutation 重试策略不变。
本说明只描述前端设计系统，不作为全站深度重构、品牌资产验收、独立审查或 exact-head CI 的证据。
