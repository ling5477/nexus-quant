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
