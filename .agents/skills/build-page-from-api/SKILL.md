---
name: build-page-from-api
description: 基于 React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design 的既有项目结构，根据业务提示词和后端接口实现完整前端页面。适用于列表页、详情页、表单页、管理页、查询页等页面开发。
---

# 目标

根据以下输入，落地一个可运行、可联调、可维护的前端页面：

- 业务需求或提示词
- 现有项目结构
- 已有组件体系
- 后端接口定义
- 当前前端固定技术栈

本 skill 的目标不是从零设计页面，而是在现有 React 技术体系中，按既有规范实现一个可继续演进的页面。

# 固定技术栈约束

涉及前端页面开发时，必须遵守以下技术边界：

- React 19
- TypeScript
- Vite 8
- React Router
- TanStack Query
- Axios
- Zustand
- Ant Design
- Playwright

## 强制规则

1. 页面和组件必须使用 React + TypeScript，默认使用 `.tsx`。
2. 路由必须接入 React Router，不允许自造路由管理方式。
3. 服务端数据必须通过 Axios 实例 + TanStack Query 管理。
4. 禁止把服务端主数据放入 Zustand。
5. Zustand 只用于 UI 状态、轻量跨组件状态、页面临时状态。
6. UI 基础组件优先复用 Ant Design，不允许重复造 Button、Modal、Drawer、Table、Form、Select、Tabs、DatePicker、Pagination 等基础组件。
7. 页面优先组织为：路由页容器 + 查询区 + 内容区 + 操作区 + 弹窗/抽屉。
8. 不允许无依据新增新的状态库、请求库、UI 库、样式体系。
9. 生成代码时必须优先复用现有 query key、Axios 封装、Zustand store、Antd 业务组件包装层。
10. 页面必须覆盖 loading、empty、error、submitting、disabled、no-permission（存在权限时）等状态。

# 适用范围

适用于：

- 新增列表页
- 新增详情页
- 新增新增/编辑页
- 新增查询筛选页
- 新增后台管理页
- 根据接口快速搭建业务页面

不适用于：

- 纯视觉高保真还原
- 脱离项目结构的 demo 页面
- 大规模组件库重构
- 复杂性能专项治理
- 单纯 bug 修复

# 必须遵守

1. 先读项目，再写代码。
2. 至少找出 2 个最接近目标页面的现有实现作为参考。
3. 优先复用现有页面模式、公共组件、hooks、query key、请求封装、工具函数、类型定义。
4. 列表页优先使用 Antd Table、Form、Pagination、Space、Button 等现有模式。
5. 表单页优先使用 Antd Form、Input、Select、DatePicker、InputNumber、Drawer、Modal 等现有模式。
6. 页面级服务端数据统一走 TanStack Query。
7. 提交动作统一走 mutation，并在成功后处理：
   - 成功提示
   - query 失效刷新
   - 页面回跳、关闭弹窗或局部更新
8. 页面级 UI 状态例如弹窗开关、当前选中行、筛选面板展开状态，可使用本地 state 或 Zustand；服务端主数据禁止放入 Zustand。
9. 不允许页面内直接散写裸 `axios.get/post/put/delete`。
10. 不允许为了省事把查询区、表格区、弹窗区、详情区全部塞进一个超大页面文件。

# 执行步骤

## 第一步：理解现有项目结构

先确认：

- 页面目录结构
- 路由注册方式
- Axios 实例位置
- TanStack Query 使用方式
- query key 组织方式
- Zustand store 组织方式
- Ant Design 组件使用习惯
- 公共 hooks 与工具函数位置
- 页面级权限控制方式
- 测试目录与 Playwright 结构

至少找出 2 个现有页面作为对齐依据。

## 第二步：理解页面职责与接口

根据接口定义确认：

- 页面是列表页、详情页还是表单页
- 初始加载用哪些 query
- 提交动作用哪些 mutation
- 分页、筛选、排序是否后端驱动
- 字段含义、状态展示、枚举展示方式
- 页面是否有弹窗、抽屉、批量操作、详情跳转

## 第三步：先做页面拆分

建议按职责拆分：

- 页面路由容器
- 查询表单区
- 表格区 / 详情区 / 表单区
- 操作栏
- 弹窗 / 抽屉
- query hooks
- api/service 模块
- types 模块
- 页面级 Zustand store（仅在确有必要时）

拆分原则：

1. 页面编排放页面层。
2. 服务端数据逻辑放 query/mutation hooks。
3. 请求逻辑放 api/service。
4. 纯展示块再拆子组件。
5. 不做无意义拆分。

## 第四步：落地代码

实现时必须做到：

- 路由可进入
- 页面可加载
- 接口可联调
- 提交可执行
- query / mutation 语义清晰
- Antd 组件使用稳定
- 错误提示清晰
- 成功反馈清晰
- 基本状态完整

## 第五步：自检

生成结果后逐项检查：

- 是否走了 React Router
- 是否走了 Axios 实例 + TanStack Query
- 是否误把服务端主数据放进 Zustand
- 是否优先复用了 Antd 和现有业务组件
- 是否补齐了 loading / empty / error / submitting
- 是否存在明显硬编码
- 是否文件过大、职责混乱

# 输出格式

每次使用本 skill，输出必须按下面结构组织：

1. 页面定位
2. 参考页面
3. 页面拆分方案
4. 新增文件
5. 修改文件
6. 路由与状态设计
7. 接口接入说明
8. 基础状态覆盖说明
9. 风险与缺失项
10. 验证步骤

# 质量标准

合格结果必须满足：

- 能进入现有 React 项目体系
- 不是静态壳子
- 不是脱离项目风格的孤岛代码
- 服务端数据由 TanStack Query 管理
- Axios、React Router、Antd、Zustand 使用边界正确
- 页面具备基础联调能力

# 禁止事项

- 禁止在页面内直接裸写 axios 请求
- 禁止把服务端主数据放入 Zustand
- 禁止重复造基础 UI 组件
- 禁止只给伪代码
- 禁止只描述思路不落地实现