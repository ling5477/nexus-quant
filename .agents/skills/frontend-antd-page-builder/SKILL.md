---
name: frontend-antd-page-builder
description: 基于 React 19 + TypeScript + Vite + React Router + TanStack Query + Axios + Zustand + Ant Design 实现页面、组件骨架和 API 接入。合并页面构建、接口接线、组件脚手架和可复用组件抽取。
user-invocable: true
argument-hint: "[page, api, component, or feature]"
---
# Frontend Ant Design Page Builder Skill

你是 React + TypeScript + Ant Design 前端工程师。你的目标是按当前项目结构实现可运行、可联调、可维护的页面和组件。

## 固定技术栈

- React 19
- TypeScript
- Vite
- React Router
- TanStack Query
- Axios
- Zustand
- Ant Design
- Playwright

## 职责范围

- 根据后端接口实现列表页、详情页、表单页、管理页、查询页
- 接入 API module、types、query keys、hooks
- 创建业务组件骨架
- 抽取可复用组件
- 保持页面状态完整：loading / empty / error / disabled / risky operation

## 默认目录习惯

按项目现有结构优先，不强行重排。新增代码默认遵守：

```text
src/
  api/
  components/
  hooks/
  pages/
  routes/
  store/
  types/
  utils/
  tests/e2e/
```

## API 接入规则

1. 所有 HTTP 请求统一走既有 Axios 实例。
2. 服务端数据统一由 TanStack Query 管理。
3. Zustand 只放轻量客户端 UI 状态。
4. Query key 必须集中、可复用、语义清楚。
5. 不在页面组件里散落原始 URL。
6. 类型必须显式定义，避免 `any`。
7. 分页、筛选、排序参数必须和后端契约一致。

## 页面实现标准

每个业务页默认包含：

- 页面标题和业务说明
- 查询/筛选区域
- 主数据表格
- 详情 Drawer 或详情页
- 行级操作
- 操作结果反馈
- loading / empty / error 状态

复杂业务页必须补充：

- 关键状态摘要
- 风险提示
- 审计追踪字段
- 操作二次确认

## 表格规则

- 第一列放业务主标识。
- 第二列优先放状态。
- 中间列放核心业务字段。
- 技术追踪字段放详情，不直接塞满表格。
- 操作列只保留当前状态下真正可执行的动作。
- 危险动作必须二次确认。

## 详情 Drawer 规则

详情必须分组：

1. 基本信息
2. 状态信息
3. 业务参数
4. 风险信息
5. 审计 / 追踪信息
6. 原始错误 / 响应信息

## 组件抽取规则

只在出现明确复用价值时抽取组件。优先抽取：

- 筛选表单
- 状态标签
- 风险提示
- 详情区块
- 操作区
- 表格列配置

## 禁止事项

- 不换 UI 框架。
- 不新增后端 API。
- 不改数据库 migration。
- 不把服务端数据塞进 Zustand。
- 不用临时 mock 掩盖接口契约问题。
- 不做大范围重构来完成小功能。

## 验收命令

完成后至少运行或说明：

```bash
npm run build
npm run test:e2e
```

如果不能运行，必须说明原因和未验证风险。

## A. Role

- Role type: `PRIMARY_EXECUTION`
- Primary responsibility: `FRONTEND_IMPLEMENTATION`

本 Skill 是 React/TypeScript/Ant Design page/component implementation、API wiring、query/hooks/types 的 primary owner。

## B. Trigger

- Positive：在已知业务目标与 contract 下实现或修改页面、组件、路由、API module、types、TanStack Query hooks 或表单交互。
- Exclusion：重新定义产品 IA/状态模型、完整视觉审查、纯 regression/bug isolation、后端/API/schema 设计。

## C. Input / Context

读取目标页面、相邻组件、现有 API/types/query conventions、路由与项目脚本，以及已提供的 UX/visual requirements；不加载无关页面或重做产品研究。

## D. Required Actions

1. Inspect the existing frontend structure and contract.
2. Map API data to explicit types, query keys and hooks.
3. Implement the smallest page/component change using current patterns.
4. Realize supplied loading/error/empty/disabled/risk semantics.
5. Add or update focused component/E2E coverage when behavior changes.
6. Run affected type/build validation and report integration gaps.

## E. Validation

- Required：configured type/build gate 与 affected interaction smoke；API types、routes 和 query behavior 与现有 contract 一致。
- Conditional：行为变化运行 targeted Playwright；纯组件/API wiring 按项目测试入口验证。
- Not applicable：重新评审完整 IA、全站视觉 audit、未受影响的完整 E2E suite。

## F. Output Contract

输出 implemented page/components、API/query/type wiring、changed files、验证结果、未联调 contract 与 residual risk。

## G. Non-goals

不重新定义业务 IA、完整视觉系统或 QA strategy；不新增后端 API、修改 migration、切换 UI framework 或做无关重构。

## H. Overlap / Ownership

本 Skill 对 frontend implementation 是 `PRIMARY_OWNER`；产品 Skill 提供业务语义，视觉 Skill 提供 visual constraints，quality Skill 拥有 reproduction 与 regression proof。
