# Current Modules（GateG）

## `frontend`

- GateG 当前主改对象
- 负责 React + TypeScript + Vite 前端工程骨架
- 负责登录页、路由守卫、基础布局与菜单
- 负责策略、调度、运行、研究、回测、评估、发布、交易验证页面
- 负责统一 API client、认证态管理、页面级 loading / error 状态
- 负责 Playwright e2e 用例与回归脚本

## `nq-api`

- 负责 HTTP API 层
- 承接 `@RestController`
- 承接 request / response DTO
- 承接仅服务于 Controller 的 API service、mapper、assembler
- 对外暴露正式 `/api/**` 路由下的认证、策略、调度、研究、回测、交易验证接口
- 作为 GateG 页面联调的唯一后端入口

## `nq-app`

- 只负责 Spring Boot 启动入口
- 只负责模块装配、profile、bean wiring、运行时配置
- 承担 SecurityFilterChain、JWT 过滤器、trace 过滤器与最终运行装配
- 不承载前端页面逻辑

## `nq-auth`

- 提供配置驱动本地账户认证与登录服务
- 使用 BCrypt 校验 `username / password`
- 为 GateG 登录页提供后端登录能力

## `nq-security`

- 提供 JWT access token 签发、解析、校验
- 提供 Bearer token 认证过滤器
- 为 GateG 路由守卫提供 token 契约基础

## `nq-gateway`

- 提供对 `SecurityContext` 的薄封装，统一读取当前认证主体
- 为 `/api/auth/me` 与受保护接口提供主体语义

## `nq-research`

- 承接 `ResearchConfig / BacktestConfig / BacktestRun / BacktestPublishRecord`
- 作为 GateG 研究与回测页面的数据来源

## `nq-backtest`

- 承接历史数据驱动的模拟执行、`sim_order / sim_trade / sim_position / sim_pnl_snapshot`
- 作为 GateG 回测详情页的数据来源

## `nq-eval`

- 承接回测评估报告与 evaluation 查询
- 作为 GateG evaluation 视图的数据来源

## `nq-core / nq-ledger / nq-risk / nq-scheduler / nq-adapter-*`

- 继续维持 GateD~GateF 已冻结边界
- 仅在前端联调缺口明确时补最小接口或字段

## 本步边界

- 当前覆盖到 GateG-DOC-1
- GateG 当前已冻结页面与联调范围
- 未展开实际前端代码实现
- 未展开数据库大改或执行域重构
