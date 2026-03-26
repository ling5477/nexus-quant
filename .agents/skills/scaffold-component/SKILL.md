---
name: scaffold-component
description: 为当前 React 19 + TypeScript + Ant Design 项目生成符合既有规范的组件骨架，自动补齐目录、主组件、类型、导出、测试占位和使用说明。适用于新建业务组件、弹窗组件、筛选组件、表格子组件、详情块组件等场景。
---

# 目标

在当前 React + TypeScript + Ant Design 项目中，生成一个可以直接进入业务开发的标准组件骨架。

# 固定技术栈约束

1. 默认生成 `.tsx` 组件。
2. 类型必须使用 TypeScript 显式定义。
3. 优先生成业务包装组件，不重复造通用基础组件。
4. 优先复用 Ant Design 组件能力，不重新发明 Button、Form、Table、Modal、Drawer、Select 等基础能力。
5. 组件导出方式必须与项目既有习惯一致。

# 适用范围

适用于：

- 新建业务组件
- 新建弹窗组件
- 新建抽屉组件
- 新建筛选组件
- 新建表格列渲染组件
- 新建详情展示组件
- 新建页面局部组合块

不适用于：

- 完整页面搭建
- 全局 store 设计
- 接口接入主流程
- 大规模重构

# 必须遵守

1. 新建组件前先找 2 到 3 个相似组件作参考。
2. 组件目录、命名、导出必须与项目一致。
3. 必须补齐导出入口。
4. 必须补齐 props 类型。
5. 必须预留测试占位文件或最小测试文件。
6. 必须给出最小使用示例。
7. props 只保留业务必要项，不预埋大量未来参数。
8. 组件职责必须单一。
9. 页面级请求、query、路由跳转、复杂业务编排禁止塞进业务组件骨架。
10. 涉及 Antd Form、Modal、Drawer、Table 时，必须遵守其受控/非受控使用方式，不做反模式包装。

# 执行步骤

## 第一步：扫描参考组件

重点确认：

- 文件命名
- 目录结构
- 类型文件位置
- index 导出方式
- 是否有样式文件
- 是否有测试文件
- 是否有 Antd 包装层
- props 命名风格

## 第二步：确认组件边界

在生成前明确：

- 组件负责什么
- 外部输入是什么
- 输出事件是什么
- 是否接收 children
- 是否依赖 Antd Form 实例、Modal open、Drawer open、Table columns 等常见模式
- 哪些状态由父组件控制

## 第三步：生成骨架

优先生成如下结构：

- `ComponentName.tsx`
- `ComponentName.types.ts`
- `index.ts`
- `ComponentName.test.tsx`

如项目确有文档文件习惯，可补 `ComponentName.md`。

## 第四步：补齐最小可用实现

组件骨架至少具备：

- 明确的 props 类型
- 最小可渲染结构
- 关键事件占位
- 合理的 className / data-testid 挂点（必要时）
- 与 Antd 组合使用的最小示例

# 推荐输出结构

默认优先：

- ComponentName/
  - index.ts
  - ComponentName.tsx
  - ComponentName.types.ts
  - ComponentName.test.tsx

# 输出格式

每次使用本 skill，输出必须按下面结构组织：

1. 组件职责
2. 参考组件
3. 目录结构
4. 新增文件
5. props 设计
6. 组件边界说明
7. 最小使用示例
8. 后续接入建议

# 质量标准

合格组件骨架必须满足：

- 职责单一
- 类型明确
- 结构与项目一致
- 复用 Antd 能力
- 不把页面逻辑塞进组件
- 能直接进入业务开发

# 禁止事项

- 禁止重复造基础 UI 组件
- 禁止 props 设计过度膨胀
- 禁止把 query、mutation、路由跳转直接塞进通用业务组件
- 禁止只生成空文件
- 禁止为了通用而过度抽象