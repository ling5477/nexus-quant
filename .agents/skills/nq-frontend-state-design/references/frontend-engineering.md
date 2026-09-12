# Frontend engineering

按变更读取相关小节；CSS 不要求 API/状态流程分析。版本和命令查当前 [package.json](../../../../frontend/package.json)，视觉 token 查 [FRONTEND_DESIGN_SYSTEM](../../../../docs/current/FRONTEND_DESIGN_SYSTEM.md)。

## API / types / state

- 沿用现有 React/TypeScript/Vite/Ant Design 与目录，API module、types、query keys、hooks、route 和组件各守职责。HTTP 走既有 Axios 实例，不在页面散放 URL；显式类型与后端字段、分页、过滤、排序和错误契约一致，避免用 `any` 掩盖未知数据。
- 服务端状态由 TanStack Query 管理，query key 集中、稳定、可复用，包含实际影响结果的账户/环境/查询参数；更新后的缓存失效/刷新与动作结果一致。Zustand 仅放必要客户端 UI 状态，不能保存一份平行的服务端事实。
- 保持路由、登录态、权限与表单校验；处理 null、异常值、长文本和未知字段。不能临时 mock 成功以掩盖未联调契约，不能为前端任务擅改后端能力或 migration。

## 状态、操作与视觉

- 页面表达当前业务对象/环境、状态、可用动作及后果；危险动作确认影响范围，LIVE 提示强于 SIM，权限不足不伪装空态，失败/风控拒绝/心跳超时/恢复失败/过期不可隐藏。
- 动作从前提、提交、等待到成功/拒绝/失败形成反馈；disabled 说明原因，stale 显示更新时间/刷新入口。保留适用的 traceId/requestId/runId/orderId/strategyCode/paperRunId 和脱敏错误排障信息。
- 业务标识、状态和核心字段按决策优先级排列；技术追踪细节可分组折叠但不能删除。布局依任务决定，不强制 PageHero、卡片数量、列序或 Drawer 模板。
- 沿用 token 和明确可复用组件，保持语义颜色、排版、间距、对齐、密度、响应式、小屏可操作性、对比度、焦点、键盘和语义标签。覆盖溢出、长字段、空值、异常数据与适用的 i18n；不只靠颜色表达风险。动效仅服务反馈和理解，不妨碍交易/风控阅读。
- 不因 polish 换 UI 框架或引入营销页大渐变/插画/夸张标题；图表复用已有库，新增图表库需用户明确要求，不为视觉统一删除业务关键字段。

## 验证

- bug 保留复现路径及失败→修复后的证据，定位 route/API/hook/state/component/style/test 的责任层，不能靠大重构或削弱断言消除失败。
- 类型/API/路由/行为变化运行已有 type/build 入口与受影响交互检查；Playwright 按适用链路覆盖登录后进入、筛选/查询、loading/empty/data、详情开关、表单/动作反馈、危险确认、错误态不白屏。
- 使用 role/label/用户可见文本，注意 Ant Design accessible name 空格；动态数据使用稳定 fixture，避免脆弱 CSS 和测试顺序依赖。纯视觉变化做代表视口/焦点/可读性检查，不默认全站 E2E。
- 写明未联调接口、环境阻塞与既存失败，不能用 mock 通过代替真实后端联调结论。完成以本次契约、状态与交互的可复验证据为准。
