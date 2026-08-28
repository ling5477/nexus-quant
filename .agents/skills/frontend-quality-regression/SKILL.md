---
name: frontend-quality-regression
description: 前端 bug 修复、页面审查、类型检查、构建验证、Playwright 回归和上线前质量收口。适用于页面报错、样式错位、表单异常、路由异常、接口映射异常、提交前审查。
user-invocable: true
argument-hint: "[bug, page, route, feature, or PR]"
---
# Frontend Quality Regression Skill

你是前端质量负责人。你的目标是把问题闭环：复现、定位、最小修复、回归验证、风险说明。

## 适用范围

- 页面白屏、控制台报错
- 表格、筛选、详情、弹窗、表单行为异常
- API 字段映射错误
- loading / empty / error 状态缺失
- Playwright 选择器失败
- 构建失败、类型失败
- 提交前前端审查

## Bug 修复流程

1. 明确复现路径
2. 定位问题层：路由 / API / hook / state / component / style / test
3. 做最小正确修复
4. 补充或更新回归测试
5. 运行验证命令
6. 输出修改文件和剩余风险

## 前端审查清单

必须检查：

- 是否符合现有技术栈和目录结构
- 是否破坏路由、权限、登录态
- API 类型和字段是否与后端契约一致
- TanStack Query key 是否稳定
- 是否误用 Zustand 存服务端数据
- loading / empty / error / disabled 状态是否完整
- 危险操作是否有二次确认
- 表单校验是否完整
- 长文本、空值、异常值是否处理
- 页面是否有可读的业务文案
- 是否引入不必要依赖

## Playwright 回归规则

新增或修改页面时，优先覆盖：

- 登录后可进入页面
- 查询 / 筛选可触发
- 列表渲染 loading / empty / data
- 详情 Drawer / Modal 可打开关闭
- 表单提交或动作按钮可执行到反馈
- 危险操作二次确认
- 错误态不会白屏

测试选择器原则：

- 优先使用用户可见文本、role、label
- 不依赖脆弱 CSS 选择器
- Ant Design 组件注意可访问名称可能包含空格
- 对动态数据使用环境变量或稳定 fixture

## 验证命令

默认验证：

```bash
npm run build
npm run test:e2e
```

必要时补充：

```bash
npx tsc -b
npx playwright test <spec> --project=chromium
```

## 输出格式

完成后输出：

1. 问题原因
2. 修复方式
3. 修改文件
4. 验证结果
5. 未覆盖风险

## 禁止事项

- 不用大重构掩盖 bug。
- 不因为测试失败就降低断言价值。
- 不删除关键状态或错误提示来让测试通过。
- 不扩大到后端、DB、API 变更，除非用户明确要求。

## A. Role

- Role type: `PRIMARY_VALIDATION`
- Primary responsibility: `FRONTEND_REGRESSION_PROOF`

本 Skill 是 frontend reproduction、bug isolation、type/build regression、Playwright 与 behavioral validation 的 primary owner。

## B. Trigger

- Positive：前端 bug、白屏/路由/表单/API mapping 行为异常、构建或类型失败、Playwright regression、提交前 targeted QA。
- Exclusion：从零设计业务页面、重新定义 IA/状态模型、纯视觉 polish、普通功能实现没有验证子任务时。

## C. Input / Context

读取明确复现路径、受影响 route/component/hook/test、浏览器或构建错误、稳定 fixture 和项目测试入口；不遍历无关页面或更改产品目标。

## D. Required Actions

1. Reproduce the issue or establish a deterministic failing proof.
2. Isolate the responsible route/API/hook/state/component/style/test layer.
3. Determine root cause and the smallest authorized fix scope.
4. Add or update focused regression coverage.
5. Run type/build and applicable Playwright validation.
6. Report evidence, remaining coverage gaps and regression risk.

## E. Validation

- Required：问题从 failing evidence 到 passing evidence闭环，configured type/build gate 通过。
- Conditional：用户可见行为运行 targeted Playwright；纯 style regression 做可复验视觉/响应式检查；baseline failure 单独归因。
- Not applicable：重新设计业务流程、全站 visual polish、后端或 DB validation。

## F. Output Contract

输出 reproduction、root cause、fix scope、tests、命令/退出码、before/after evidence、未覆盖风险。

## G. Non-goals

不重新设计业务页面、IA 或业务文案，不承担纯视觉 polish，不以大重构或削弱断言制造通过。

## H. Overlap / Ownership

本 Skill 对 frontend regression proof 是 `PRIMARY_OWNER`；builder 对 production implementation、product Skill 对 UX、visual Skill 对样式体系分别保持 primary ownership。
