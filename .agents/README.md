# Optimized Agent Skills

这是从原 38 个 skills 合并后的精简版。目标是减少触发冲突、降低上下文噪声，并把零散技能合并成“可执行工作流”。

## 当前 active skills

| Skill | 用途 |
|---|---|
| `frontend-product-ui-design` | 页面产品化、业务 UX、信息架构、状态/空态/风险提示 |
| `ui-visual-system-polish` | 视觉层级、排版、色彩、响应式、动效、设计系统一致性 |
| `frontend-antd-page-builder` | React + Ant Design 页面、组件、API 接入落地 |
| `frontend-quality-regression` | 前端 bug 修复、审查、Playwright 回归 |
| `java-backend-maintenance` | Java 后端 bug 修复、Service 收口、Spring Boot 模块审查 |
| `java-backend-regression-tests` | JUnit、golden case、集成回归 |
| `db-schema-migration-review` | DDL、Flyway/Liquibase migration、索引/约束/注释审查 |
| `python-ops-tooling` | Python 批处理脚本、数据处理脚本、pytest 回归 |

## 使用原则

1. 前端页面从需求到上线，优先使用：
   - `frontend-product-ui-design`
   - `frontend-antd-page-builder`
   - `frontend-quality-regression`

2. 只做视觉提升时，使用：
   - `ui-visual-system-polish`

3. Java 后端问题闭环，使用：
   - `java-backend-maintenance`
   - `java-backend-regression-tests`

4. 数据库结构变更，使用：
   - `db-schema-migration-review`

5. Python 工具和批处理，使用：
   - `python-ops-tooling`

## 对 NexusQuant / Decision Hub 的默认约束

- 前端默认走专业企业后台 / 金融科技后台风格，不走营销页风格。
- 默认技术栈：React 19 + TypeScript + Vite + React Router + TanStack Query + Axios + Zustand + Ant Design + Playwright。
- 服务端数据放 TanStack Query，不把服务端数据塞进 Zustand。
- 不为了 UI 优化新增后端 API、migration 或业务能力。
- 涉及交易、风控、恢复、停止、发布、撤单等操作时，必须展示影响范围并做二次确认。
