---
name: fix-ui-bug
description: 定位并修复 React 19 + TypeScript + React Router + TanStack Query + Axios + Zustand + Ant Design 项目中的页面、状态、交互、路由、请求映射和组件行为问题。适用于页面报错、样式错位、表单异常、弹窗异常、列表异常、路由异常等场景。
---

# 目标

对前端问题执行“可复现、可定位、可验证”的最小正确修复。

# 适用范围

适用于：

- React 渲染异常
- 页面空白
- 组件报错
- 样式错位
- 表单校验异常
- Antd Modal / Drawer / Form / Table / Select 行为异常
- React Router 路由参数或跳转异常
- TanStack Query 缓存、失效、重复请求异常
- Axios 参数映射异常
- Zustand 状态同步异常
- 权限显示异常

不适用于：

- 新功能开发
- 完整页面重写
- 架构级改造

# 必须遵守

1. 先复现，再定位，再修复，再验证。
2. 必须说明根因，不能只改表象。
3. 优先最小正确修改，禁止顺手大重构。
4. 必须说明影响范围。
5. 必须给出验证路径。
6. 无法完整复现时，也必须给出当前证据链和最可能根因。
7. 修 bug 时必须按当前技术栈实际链路排查，不允许跳步乱猜。

# 固定排查顺序

遇到前端 bug 时，默认按以下顺序排查：

1. React 组件状态和渲染分支
2. props 传递链与条件渲染
3. React Router 路由参数、嵌套路由、导航行为
4. TanStack Query：
    - query key 是否稳定
    - query 是否误重复执行
    - mutation 后是否正确失效刷新
    - staleTime / enabled / select / placeholderData 是否用错
5. Axios：
    - 请求参数是否映射正确
    - 拦截器是否影响响应
    - 响应结构是否解析正确
6. Zustand：
    - store 是否存了不该存的服务端主数据
    - selector 是否导致多余渲染
    - 状态更新是否丢失
7. Ant Design：
    - 组件 props 是否正确
    - 受控/非受控是否混用
    - Form 回填是否正确
    - Modal / Drawer open 状态是否正确
8. 样式、布局、容器尺寸和 className 冲突

# 执行步骤

## 第一步：复现问题

明确：

- 问题页面
- 操作步骤
- 预期结果
- 实际结果
- 是否必现
- 是否和路由参数、接口数据、缓存状态、窗口尺寸有关

## 第二步：收集证据

优先查看：

- 浏览器控制台
- 网络请求
- React 渲染分支
- query 状态
- mutation 状态
- store 状态
- React Router 参数
- Antd Form 初始值与 setFieldsValue 流程
- Modal / Drawer / Select / Table 的关键 props

## 第三步：确认根因

根因分类必须明确，例如：

- query key 错误导致缓存串用
- mutation 后未 invalidation
- Axios 响应结构解析错误
- 路由参数解析错误
- Zustand 状态覆盖
- Antd Form 回填时机错误
- Modal open 受控状态错误
- 空值未保护
- 条件渲染漏分支
- CSS 冲突

## 第四步：最小修复

修复时遵循：

1. 优先改根因点。
2. 不改无关逻辑。
3. 保持现有技术栈和项目风格一致。
4. 只在必要时补 query key、helper、selector、guard。

## 第五步：验证闭环

至少验证：

- 原问题是否消失
- 相关页面正常路径是否不受影响
- query / mutation 链路是否正常
- 路由跳转是否正常
- Antd 组件交互是否正常

# 输出格式

每次使用本 skill，输出必须按下面结构组织：

1. 问题现象
2. 复现条件
3. 根因分析
4. 修改文件
5. 修复说明
6. 影响范围
7. 验证步骤
8. 风险说明

# 质量标准

合格修复必须满足：

- 根因明确
- 修改聚焦
- 与技术栈边界一致
- 验证路径清晰
- 不引入明显新问题

# 禁止事项

- 禁止只说“已修复”不解释根因
- 禁止把 query 数据搬进 Zustand 作为临时修复
- 禁止大量加 `setTimeout`、`wait` 类补丁逻辑
- 禁止为了修一个点大改整体结构
- 禁止不写验证步骤