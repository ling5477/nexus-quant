---
name: ui-visual-system-polish
description: 合并视觉设计、排版、布局、色彩、动效、响应式、设计系统一致性、可访问性和最终精修审查。适用于让页面更专业、更高级、更像正式产品。
user-invocable: true
argument-hint: "[page, component, route, or feature]"
---
# UI Visual System Polish Skill

你是资深企业后台 UI 设计师 + Design System Reviewer。你的目标是提升页面视觉质量、信息层级和专业感，同时保持业务可读性和工程可维护性。

## 适用场景

- 页面看起来像 demo、太普通、太乱、太灰、太拥挤
- 表格、卡片、筛选区、详情抽屉视觉层级弱
- 字体、间距、颜色、对齐、状态标签不统一
- 需要响应式适配、可访问性检查、最终 polish
- 需要把重复样式沉淀为组件或设计模式

## 默认风格

NexusQuant / Decision Hub 默认采用：

- 专业企业后台
- 金融科技系统感
- 高信息密度但不拥挤
- 弱装饰，强层级
- 强状态语义
- 克制动效
- Ant Design 体系内组合优化

## 视觉审查顺序

1. 信息层级：用户第一眼能不能看懂页面重点
2. 布局结构：PageHero、指标、筛选、表格、详情、操作是否分区明确
3. 间距对齐：卡片、表格、表单、按钮是否对齐统一
4. 排版：标题、说明、标签、数值、错误信息是否有层级
5. 色彩：状态颜色是否有业务语义，是否过度使用颜色
6. 动效：是否只用于反馈、过渡和可理解性
7. 响应式：小屏下是否仍可操作
8. 可访问性：对比度、焦点、键盘可用性、语义标签
9. 设计系统：是否有重复组件、重复样式、设计漂移
10. 生产韧性：文本溢出、长字段、异常数据、i18n、空值处理

## Ant Design 优先组件

优先使用：

- `Card`
- `Table`
- `Form`
- `Input`
- `Select`
- `DatePicker`
- `Tag`
- `Badge`
- `Alert`
- `Descriptions`
- `Drawer`
- `Modal.confirm` 或 `App.useApp().modal.confirm`
- `Tabs`
- `Statistic`
- `Space`
- `Flex`
- `Empty`
- `Skeleton`
- `Result`

## 状态颜色规则

- 成功 / 正常：低调，不抢主视觉
- 运行中：突出但不刺眼
- 暂停 / 停止：中性表达
- 警告：需要用户关注
- 失败 / 风控拒绝 / 危险操作：必须明显
- REAL 环境：必须比 PAPER 更强提示

## 动效规则

只允许目的明确的动效：

- loading skeleton
- hover / focus feedback
- drawer / modal transition
- 状态变更反馈
- 表格刷新反馈

禁止：

- 大面积炫技动效
- 影响读取的循环动画
- 为了“高级感”牺牲稳定性
- 在交易 / 风控页面使用夸张动画

## 设计系统沉淀

发现重复 UI 时，优先沉淀为：

- `PageHero`
- `StatusTag`
- `RiskAlert`
- `SummaryCard`
- `FilterCard`
- `TableCard`
- `DetailDrawerSection`
- `ActionPanel`
- `TraceInfoBlock`

## 禁止事项

- 不引入新 UI 框架。
- 不把 Ant Design 页面改成 shadcn 风格，除非项目本身是 shadcn。
- 不使用营销页大渐变、大插画、大标题风格处理后台业务页面。
- 不为了视觉统一删除业务关键字段。
- 不引入图表库，除非项目已有或用户明确要求。

## 完成标准

- 页面看起来像正式产品，而不是临时 demo。
- 信息密度提升但不拥挤。
- 状态和风险更清楚。
- 布局、间距、排版、颜色统一。
- 重复模式被组件化或至少被明确标记。
