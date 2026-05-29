# Skills Merge Map

原始包里共有 38 个 skills。本次合并为 8 个 active skills。

## 1. frontend-product-ui-design

合并来源：

- `shape`
- `onboard`
- `clarify`
- `distill`
- `critique` 的 UX / 信息架构部分
- `harden` 的状态、错误、边界情况部分
- 原建议中的 product-page-ux、trading-business-ux、state-and-empty-design、frontend-copywriting 思路

负责：产品目标、页面结构、业务流程、状态语义、空态/错误态、风险操作提示、业务文案。

## 2. ui-visual-system-polish

合并来源：

- `impeccable`
- `anthropic-frontend-design`
- `arrange`
- `typeset`
- `colorize`
- `bolder`
- `quieter`
- `delight`
- `animate`
- `adapt`
- `normalize`
- `polish`
- `extract` 的设计系统部分
- `audit` 的视觉/可访问性部分
- `optimize` 的 UI 性能部分
- `overdrive` 降级为“仅在明确要求 wow/动效时启用”
- `ui-ux-pro-max` 的设计参考能力

负责：视觉层级、布局、排版、色彩、动效、响应式、设计系统一致性、精修审查。

## 3. frontend-antd-page-builder

合并来源：

- `build-page-from-api`
- `wire-api-module`
- `scaffold-component`
- `extract` 的组件抽取部分

负责：React + TypeScript + Ant Design 页面实现、API 接入、组件骨架、详情抽屉、表格、筛选、操作区。

## 4. frontend-quality-regression

合并来源：

- `fix-ui-bug`
- `frontend-review`
- `e2e-regression`
- `audit` 的提交前质量检查部分
- `harden` 的生产边界检查部分
- `optimize` 的性能回归检查部分

负责：前端 bug 闭环、类型检查、构建、Playwright、提交前审查。

## 5. java-backend-maintenance

合并来源：

- `fix-prod-bug-java`
- `refactor-service-layer-java`
- `spring-boot-module-review`

负责：Java 后端 bug 修复、Service 层职责收口、Spring Boot 模块边界审查。

## 6. java-backend-regression-tests

合并来源：

- `write-junit-and-golden-tests`
- `integration-regression-java`

负责：JUnit、golden case、Controller/Service/Repository 集成回归。

## 7. db-schema-migration-review

合并来源：

- `review-ddl-and-migration`

负责：DDL、migration、索引、约束、默认值、注释、回填脚本审查。

## 8. python-ops-tooling

合并来源：

- `build-batch-script-python`
- `write-pytest-regression`

负责：Python 批处理、数据清洗、导入导出、运维辅助、pytest 回归。

## 不建议默认启用

`shadcn` 不建议放入 active skills，因为 NexusQuant / Decision Hub 当前主栈是 Ant Design。需要做 shadcn 项目时，再单独加入。
