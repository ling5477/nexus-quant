---
name: frontend-review
description: 对 React 19 + TypeScript + Vite 8 + React Router + TanStack Query + Axios + Zustand + Ant Design + Playwright 项目的前端改动做结构、规范、类型、状态覆盖、技术栈边界和回归准备审查。适用于页面开发完成后、自测前、提交前、PR 前的质量收口。
---

# 目标

对前端改动做一次聚焦于“是否符合当前技术栈约束、是否可维护、是否遗漏关键状态”的审查收口。

# 适用范围

适用于：

- 页面开发完成后的自审
- PR 前检查
- 较大前端功能开发后的收口
- 重构后的质量检查
- bug 修复后的变更审查

不适用于：

- 替代全部测试
- 纯视觉设计评审
- 纯性能专项分析
- 纯可访问性专项分析

# 必须遵守

1. 先基于当前项目和技术栈规则审查，不凭个人偏好乱改。
2. 必须区分：
   - 必改问题
   - 建议优化
   - 可暂缓项
3. 必须覆盖结构、复用、类型、状态、技术栈边界、回归准备。
4. 每个问题尽量落到具体文件和具体改动点。
5. 结论必须明确。

# 审查维度

## 1. React 组件与分层

检查：

- 页面层、组件层、hooks、api 层是否职责清晰
- 是否出现巨型页面或巨型组件
- 是否把 query / mutation / 路由跳转 / 展示逻辑全部揉在一个文件里
- 是否存在明显重复逻辑未抽取

## 2. React Router 使用

检查：

- 路由组织是否合理
- 路由参数读取是否清晰
- 嵌套路由是否被正确使用
- 页面跳转是否稳定
- 是否存在魔法路径字符串泛滥

## 3. TanStack Query 使用

检查：

- 服务端数据是否正确走 query / mutation
- query key 是否稳定、可读、可失效
- 是否在 mutation 成功后正确 invalidation
- 是否错误使用 placeholderData / select / enabled / staleTime
- 是否重复请求或缓存串用

## 4. Axios 使用

检查：

- 是否统一走 Axios 实例
- 是否绕过拦截器
- 请求参数与响应结构映射是否清晰
- 错误处理是否统一

## 5. Zustand 使用边界

检查：

- Zustand 是否只承载 UI 状态或轻量跨组件状态
- 是否被滥用于保存服务端主数据
- selector 是否过粗导致多余渲染
- store 是否职责清晰

## 6. Ant Design 复用情况

检查：

- 是否优先复用 Antd 基础组件
- 是否重复封装 Button、Modal、Drawer、Form、Table、Select 等基础能力
- Form、Modal、Drawer 是否按受控方式合理使用
- Table、Pagination、message、notification 是否使用稳定

## 7. TypeScript 类型质量

检查：

- props 类型是否明确
- query / mutation 数据类型是否明确
- 是否存在明显 any 滥用
- DTO、表单值、展示值、请求值是否混乱
- 空值保护是否完整

## 8. 页面状态覆盖

检查：

- loading
- empty
- error
- submitting
- disabled
- no-permission
- modal / drawer open-close
- success / failure message
- query reset

是否完整覆盖。

## 9. Vite 与工程约束

检查：

- 路径别名是否统一
- 环境变量是否合理
- 动态导入和懒加载是否合理
- 是否引入与 Vite 当前体系冲突的写法

## 10. 测试与回归准备

检查：

- 是否给出验证步骤
- 是否需要补 Playwright
- 选择器是否稳定
- 改动是否影响现有关键回归路径

# 执行步骤

## 第一步：看改动范围

明确：

- 新增了什么
- 修改了什么
- 影响哪些页面、路由、query、store、组件

## 第二步：按维度逐项检查

每个结论尽量落到具体文件。

## 第三步：输出分级结论

输出必须分级：

- 必改问题
- 建议优化
- 可暂缓项

# 输出格式

每次使用本 skill，输出必须按下面结构组织：

1. 本次审查范围
2. 总体结论
3. 必改问题
4. 建议优化
5. 可暂缓项
6. 风险点
7. 合并前检查项

# 审查结论标准

- 可合并
- 修正后可合并
- 不建议合并

结论必须三选一。

# 质量标准

合格审查必须满足：

- 问题具体
- 与当前技术栈强相关
- 不把个人偏好当阻塞项
- 结论明确
- 具备可执行性

# 禁止事项

- 禁止只挑格式问题不看真实技术问题
- 禁止忽略 TanStack Query / Zustand 的边界
- 禁止忽略 Antd 复用情况
- 禁止不给结论