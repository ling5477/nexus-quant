# RC1-7 Package Mapping

> 历史迁移映射，不代表当前包事实。
> 本文件仅用于追溯 RC1-7 包迁移过程；当前模块 owner、包结构与依赖方向以 `docs/current/MODULES.md` 和最新源码为准。

当前状态：`RC1-7-A / RC1-7-B`

说明：

- 本文件是 RC1-7 的迁移映射表，后续 `RC1-7-B/C/D` 只按本表推进。
- 列固定为：`当前 FQCN | 所在模块 | 目标包 | 业务域 | 层级 | 本轮是否移动 | 不移动原因`
- `本轮是否移动` 取值：`是 / 否`

## Shared / Hold

| 当前 FQCN | 所在模块 | 目标包 | 业务域 | 层级 | 本轮是否移动 | 不移动原因 |
| --- | --- | --- | --- | --- | --- | --- |
| `com.guidinglight.nexusquant.api.web.ApiErrorResponse` | `nq-api` | `com.guidinglight.nexusquant.api.shared.web` | `shared` | `api` | `否` | 共享错误响应，不强行并入单一业务域 |
| `com.guidinglight.nexusquant.api.web.ApiFieldError` | `nq-api` | `com.guidinglight.nexusquant.api.shared.web` | `shared` | `api` | `否` | 共享错误字段模型，不属于六个业务域中的单域 |
| `com.guidinglight.nexusquant.api.web.ApiExceptionHandler` | `nq-api` | `com.guidinglight.nexusquant.api.shared.web` | `shared` | `api` | `否` | 共享异常处理器，本轮只修引用 |
| `com.guidinglight.nexusquant.api.web.OpenApiSecurityConfiguration` | `nq-api` | `com.guidinglight.nexusquant.api.shared.config` | `shared` | `api` | `否` | OpenAPI 支撑配置，非单域实现 |
| `com.guidinglight.nexusquant.app.NexusQuantApplication` | `nq-app` | `com.guidinglight.nexusquant.app` | `app-bootstrap` | `bootstrap` | `否` | 顶层 Spring Boot 启动入口，保留 |
| `com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration` | `nq-app` | `com.guidinglight.nexusquant.app.config` | `cross-domain` | `bootstrap` | `否` | 交易所 adapter 顶层装配，非本轮主线 |
| `com.guidinglight.nexusquant.app.config.LocalTestFallbackConfiguration` | `nq-app` | `com.guidinglight.nexusquant.app.config` | `cross-domain` | `bootstrap` | `否` | local/test fallback 仍需作为跨域 fallback 入口 |
| `com.guidinglight.nexusquant.infra.config.InfraModuleConfiguration` | `nq-infra` | `com.guidinglight.nexusquant.infra.config` | `cross-domain` | `infra` | `否` | 基础设施模块聚合配置，先保留 |

## Account / Auth

| 当前 FQCN | 所在模块 | 目标包 | 业务域 | 层级 | 本轮是否移动 | 不移动原因 |
| --- | --- | --- | --- | --- | --- | --- |
| `com.guidinglight.nexusquant.api.web.ExchangeAccountController` | `nq-api` | `com.guidinglight.nexusquant.account.api.web` | `account` | `api` | `是` | 当前位于全局 `api.web`，需按业务域归位 |
| `com.guidinglight.nexusquant.api.web.ExchangeAccountResponse` | `nq-api` | `com.guidinglight.nexusquant.account.api.dto` | `account` | `api` | `是` | 账户查询响应 DTO |
| `com.guidinglight.nexusquant.core.account.application.ExchangeAccountQueryService` | `nq-core` | `com.guidinglight.nexusquant.account.application` | `account` | `application` | `是` | 当前已半成型，统一去掉 `core` 根包 |
| `com.guidinglight.nexusquant.core.account.application.port.ExchangeAccountRepository` | `nq-core` | `com.guidinglight.nexusquant.account.domain.port` | `account` | `domain` | `是` | repository port 归 domain |
| `com.guidinglight.nexusquant.core.account.domain.ExchangeAccountSummary` | `nq-core` | `com.guidinglight.nexusquant.account.domain` | `account` | `domain` | `是` | 账户摘要 domain model |
| `com.guidinglight.nexusquant.infra.account.jdbc.JdbcExchangeAccountRepository` | `nq-infra` | `com.guidinglight.nexusquant.account.infra.jdbc` | `account` | `infra` | `是` | JDBC 实现归域内 infra |
| `com.guidinglight.nexusquant.app.config.AccountModuleConfiguration` | `nq-app` | `com.guidinglight.nexusquant.account.infra.config` | `account` | `infra` | `是` | 纯账户 Bean 拆出后，原类删除或拆散 |
| `com.guidinglight.nexusquant.api.web.AuthController` | `nq-api` | `com.guidinglight.nexusquant.auth.api.web` | `auth` | `api` | `是` | 正式认证 controller 归 auth 域 |
| `com.guidinglight.nexusquant.api.web.AuthLoginRequestBody` | `nq-api` | `com.guidinglight.nexusquant.auth.api.dto` | `auth` | `api` | `是` | 登录请求 DTO |
| `com.guidinglight.nexusquant.api.web.AuthLoginResponse` | `nq-api` | `com.guidinglight.nexusquant.auth.api.dto` | `auth` | `api` | `是` | 登录响应 DTO |
| `com.guidinglight.nexusquant.api.web.CurrentUserResponse` | `nq-api` | `com.guidinglight.nexusquant.auth.api.dto` | `auth` | `api` | `是` | `/api/auth/me` 响应 DTO |
| `com.guidinglight.nexusquant.auth.application.DbAuthService` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | 正式 DB-backed auth 用例服务 |
| `com.guidinglight.nexusquant.auth.application.CurrentUserProfileService` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | 当前用户 profile 查询用例 |
| `com.guidinglight.nexusquant.auth.application.AuthSeedService` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | 认证 seed 用例 |
| `com.guidinglight.nexusquant.auth.application.AdminNotInitializedException` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | auth 用例异常 |
| `com.guidinglight.nexusquant.auth.application.SeedUserCommand` | `nq-auth` | `com.guidinglight.nexusquant.auth.application.command` | `auth` | `application` | `是` | seed 输入命令对象 |
| `com.guidinglight.nexusquant.auth.service.AuthService` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | controller 面向的应用服务接口 |
| `com.guidinglight.nexusquant.auth.service.LocalAuthService` | `nq-auth` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | local fallback auth 实现 |
| `com.guidinglight.nexusquant.auth.dto.LoginRequest` | `nq-auth` | `com.guidinglight.nexusquant.auth.application.command` | `auth` | `application` | `是` | 内部登录命令 |
| `com.guidinglight.nexusquant.auth.dto.LoginResponse` | `nq-auth` | `com.guidinglight.nexusquant.auth.application.result` | `auth` | `application` | `是` | 内部登录结果 |
| `com.guidinglight.nexusquant.auth.domain.AuthUserProfile` | `nq-auth` | `com.guidinglight.nexusquant.auth.domain` | `auth` | `domain` | `是` | auth domain model |
| `com.guidinglight.nexusquant.auth.model.LocalUserAccount` | `nq-auth` | `com.guidinglight.nexusquant.auth.domain` | `auth` | `domain` | `是` | local auth 账户模型 |
| `com.guidinglight.nexusquant.auth.application.port.AuthUserRepository` | `nq-auth` | `com.guidinglight.nexusquant.auth.domain.port` | `auth` | `domain` | `是` | repository port 归 domain |
| `com.guidinglight.nexusquant.security.model.TokenClaims` | `nq-security` | `com.guidinglight.nexusquant.auth.domain` | `auth` | `domain` | `是` | token claims 归 auth 域 |
| `com.guidinglight.nexusquant.security.service.TokenService` | `nq-security` | `com.guidinglight.nexusquant.auth.domain.port` | `auth` | `domain` | `是` | token port 归 domain |
| `com.guidinglight.nexusquant.security.service.JwtTokenSettings` | `nq-security` | `com.guidinglight.nexusquant.auth.infra.token` | `auth` | `infra` | `是` | JWT 配置实现 |
| `com.guidinglight.nexusquant.security.service.JwtTokenService` | `nq-security` | `com.guidinglight.nexusquant.auth.infra.token` | `auth` | `infra` | `是` | JWT 实现归 auth infra |
| `com.guidinglight.nexusquant.security.web.JwtAuthenticationFilter` | `nq-security` | `com.guidinglight.nexusquant.auth.api.web` | `auth` | `api` | `是` | 鉴权 filter 属于入站 web adapter |
| `com.guidinglight.nexusquant.gateway.model.GatewayRequestContext` | `nq-gateway` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | 认证态读取上下文对象 |
| `com.guidinglight.nexusquant.gateway.service.GatewayAuthFacade` | `nq-gateway` | `com.guidinglight.nexusquant.auth.application` | `auth` | `application` | `是` | 当前认证用户读取接口 |
| `com.guidinglight.nexusquant.gateway.service.SecurityContextGatewayAuthFacade` | `nq-gateway` | `com.guidinglight.nexusquant.auth.infra.gateway` | `auth` | `infra` | `是` | SecurityContext 读取实现 |
| `com.guidinglight.nexusquant.infra.auth.jdbc.JdbcAuthUserRepository` | `nq-infra` | `com.guidinglight.nexusquant.auth.infra.jdbc` | `auth` | `infra` | `是` | auth JDBC 实现 |
| `com.guidinglight.nexusquant.app.security.ApiSecurityErrorWriter` | `nq-app` | `com.guidinglight.nexusquant.auth.api.web` | `auth` | `api` | `是` | 401/403 输出是 auth web 支撑 |
| `com.guidinglight.nexusquant.app.config.AuthModuleConfiguration` | `nq-app` | `com.guidinglight.nexusquant.auth.infra.config` | `auth` | `infra` | `是` | auth 域配置迁入域内 |
| `com.guidinglight.nexusquant.app.config.SecurityConfiguration` | `nq-app` | `com.guidinglight.nexusquant.auth.infra.config` | `auth` | `infra` | `是` | 安全链装配迁入 auth 域 |
| `com.guidinglight.nexusquant.app.config.SecurityRuntimeProperties` | `nq-app` | `com.guidinglight.nexusquant.auth.infra.config` | `auth` | `infra` | `是` | auth runtime properties |
| `com.guidinglight.nexusquant.app.config.AuthSeedConfiguration` | `nq-app` | `com.guidinglight.nexusquant.auth.infra.config` | `auth` | `infra` | `是` | auth seed 配置 |
| `com.guidinglight.nexusquant.app.config.AuthBootstrapAdminConfiguration` | `nq-app` | `com.guidinglight.nexusquant.auth.infra.config` | `auth` | `infra` | `是` | auth bootstrap 配置 |

## Trading / Strategy

| 当前 FQCN | 所在模块 | 目标包 | 业务域 | 层级 | 本轮是否移动 | 不移动原因 |
| --- | --- | --- | --- | --- | --- | --- |
| `com.guidinglight.nexusquant.api.service.TradingQueryFacade` | `nq-api` | `com.guidinglight.nexusquant.trading.application.query` | `trading` | `application` | `是` | 交易只读查询入口不应留在全局 api.service |
| `com.guidinglight.nexusquant.app.trading.query.JdbcTradingQueryFacade` | `nq-app` | `com.guidinglight.nexusquant.trading.infra.query` | `trading` | `infra` | `是` | 交易查询 SQL 实现归 trading infra |
| `com.guidinglight.nexusquant.api.model.AccountBalanceView` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易查询视图 DTO |
| `com.guidinglight.nexusquant.api.model.AccountView` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易查询视图 DTO |
| `com.guidinglight.nexusquant.api.model.OrderView` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易查询视图 DTO |
| `com.guidinglight.nexusquant.api.model.PositionView` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易查询视图 DTO |
| `com.guidinglight.nexusquant.api.model.TradeView` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易查询视图 DTO |
| `com.guidinglight.nexusquant.api.web.TradingVerificationController` | `nq-api` | `com.guidinglight.nexusquant.trading.api.web` | `trading` | `api` | `是` | trading controller 归域 |
| `com.guidinglight.nexusquant.api.web.OrderSubmitRequest` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 下单请求 DTO |
| `com.guidinglight.nexusquant.api.web.OrderCancelRequestBody` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 撤单请求 DTO |
| `com.guidinglight.nexusquant.api.web.ReconcileRunOnceRequest` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | reconcile 请求 DTO |
| `com.guidinglight.nexusquant.api.web.RecoveryRunOnceRequest` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | recovery 请求 DTO |
| `com.guidinglight.nexusquant.api.web.OperationTriggerResponse` | `nq-api` | `com.guidinglight.nexusquant.trading.api.dto` | `trading` | `api` | `是` | 交易操作响应 DTO |
| `com.guidinglight.nexusquant.core.model.OrderAggregate` | `nq-core` | `com.guidinglight.nexusquant.trading.domain` | `trading` | `domain` | `是` | 订单聚合根 |
| `com.guidinglight.nexusquant.core.model.OrderRecord` | `nq-core` | `com.guidinglight.nexusquant.trading.domain` | `trading` | `domain` | `是` | 订单记录 domain model |
| `com.guidinglight.nexusquant.core.recovery.RecoveryReport` | `nq-core` | `com.guidinglight.nexusquant.trading.domain` | `trading` | `domain` | `是` | recovery 结果模型 |
| `com.guidinglight.nexusquant.core.recovery.RecoveryService` | `nq-core` | `com.guidinglight.nexusquant.trading.application` | `trading` | `application` | `是` | recovery 用例接口 |
| `com.guidinglight.nexusquant.core.state.OrderStateMachine` | `nq-core` | `com.guidinglight.nexusquant.trading.domain.state` | `trading` | `domain` | `是` | 订单状态机接口 |
| `com.guidinglight.nexusquant.core.state.InMemoryOrderStateMachine` | `nq-core` | `com.guidinglight.nexusquant.trading.domain.state` | `trading` | `domain` | `是` | 纯内存状态机实现，非 infra |
| `com.guidinglight.nexusquant.core.execution.AdapterRouter` | `nq-core` | `com.guidinglight.nexusquant.trading.application.routing` | `trading` | `application` | `是` | 交易 adapter 路由逻辑 |
| `com.guidinglight.nexusquant.core.service.CancelOrderRequest` | `nq-core` | `com.guidinglight.nexusquant.trading.application.command` | `trading` | `application` | `是` | trading command object |
| `com.guidinglight.nexusquant.core.service.CancelOrderResult` | `nq-core` | `com.guidinglight.nexusquant.trading.application.result` | `trading` | `application` | `是` | trading result object |
| `com.guidinglight.nexusquant.core.service.PlaceOrderRequest` | `nq-core` | `com.guidinglight.nexusquant.trading.application.command` | `trading` | `application` | `是` | trading command object |
| `com.guidinglight.nexusquant.core.service.PlaceOrderResult` | `nq-core` | `com.guidinglight.nexusquant.trading.application.result` | `trading` | `application` | `是` | trading result object |
| `com.guidinglight.nexusquant.core.service.ExecutionCommandMapper` | `nq-core` | `com.guidinglight.nexusquant.trading.application` | `trading` | `application` | `是` | trading 编排映射器 |
| `com.guidinglight.nexusquant.core.service.OrderCommandService` | `nq-core` | `com.guidinglight.nexusquant.trading.application` | `trading` | `application` | `是` | trading 主命令服务 |
| `com.guidinglight.nexusquant.core.service.OrderCommandWriteService` | `nq-core` | `com.guidinglight.nexusquant.trading.application` | `trading` | `application` | `是` | trading 写服务 |
| `com.guidinglight.nexusquant.core.service.OrderLifecycleService` | `nq-core` | `com.guidinglight.nexusquant.trading.application` | `trading` | `application` | `是` | trading 生命周期服务 |
| `com.guidinglight.nexusquant.core.service.TradingMaintenanceService` | `nq-core` | `com.guidinglight.nexusquant.trading.application.maintenance` | `trading` | `application` | `是` | trading maintenance 用例接口 |
| `com.guidinglight.nexusquant.core.service.OrderCommandStrategyExecutionGateway` | `nq-core` | `com.guidinglight.nexusquant.trading.application.port` | `trading` | `application` | `是` | trading 到 strategy 执行网关 |
| `com.guidinglight.nexusquant.core.service.port.OrderRepository` | `nq-core` | `com.guidinglight.nexusquant.trading.domain.port` | `trading` | `domain` | `是` | repository port 归 domain |
| `com.guidinglight.nexusquant.core.service.port.AuditLogRepository` | `nq-core` | `com.guidinglight.nexusquant.trading.domain.port` | `trading` | `domain` | `是` | trading 审计 port |
| `com.guidinglight.nexusquant.core.service.port.RiskEventRepository` | `nq-core` | `com.guidinglight.nexusquant.trading.domain.risk.port` | `trading` | `domain` | `是` | risk event 作为 trading 子域 port |
| `com.guidinglight.nexusquant.infra.trading.JdbcOrderRepository` | `nq-infra` | `com.guidinglight.nexusquant.trading.infra.jdbc` | `trading` | `infra` | `是` | trading JDBC 实现 |
| `com.guidinglight.nexusquant.risk.model.RiskContext` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk 归 trading 子域 |
| `com.guidinglight.nexusquant.risk.model.RiskDecisionResult` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk 归 trading 子域 |
| `com.guidinglight.nexusquant.risk.service.RiskRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk 规则接口归 domain |
| `com.guidinglight.nexusquant.risk.service.RiskRuleRegistry` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk 规则注册归 domain |
| `com.guidinglight.nexusquant.risk.service.PreTradeRiskService` | `nq-risk` | `com.guidinglight.nexusquant.trading.application.risk` | `trading` | `application` | `是` | 风控编排服务 |
| `com.guidinglight.nexusquant.risk.service.RiskGate` | `nq-risk` | `com.guidinglight.nexusquant.trading.application.risk` | `trading` | `application` | `是` | 风控用例接口 |
| `com.guidinglight.nexusquant.risk.service.PreTradeRiskSettings` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk 默认设置对象 |
| `com.guidinglight.nexusquant.risk.service.AccountTradingEnabledRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.DuplicateRequestRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.KillSwitchRiskRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.KillSwitchService` | `nq-risk` | `com.guidinglight.nexusquant.trading.application.risk` | `trading` | `application` | `是` | 风控状态服务 |
| `com.guidinglight.nexusquant.risk.service.MaxOrderAmountRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.MinNotionalRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.OrderPrecisionRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.RateLimitRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.SymbolEnabledRule` | `nq-risk` | `com.guidinglight.nexusquant.trading.domain.risk` | `trading` | `domain` | `是` | risk rule |
| `com.guidinglight.nexusquant.risk.service.NoopRiskGate` | `nq-risk` | `com.guidinglight.nexusquant.trading.application.risk` | `trading` | `application` | `是` | fallback risk gate |
| `com.guidinglight.nexusquant.infra.risk.JdbcRiskEventRepository` | `nq-infra` | `com.guidinglight.nexusquant.trading.infra.risk.jdbc` | `trading` | `infra` | `是` | trading risk JDBC 实现 |
| `com.guidinglight.nexusquant.scheduler.service.SchedulerTradingMaintenanceService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.scheduler` | `trading` | `infra` | `是` | scheduler concrete maintenance 实现 |
| `com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.scheduler` | `trading` | `infra` | `是` | scheduler concrete recovery 实现 |
| `com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.scheduler` | `trading` | `infra` | `是` | scheduler concrete recovery 实现 |
| `com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.scheduler` | `trading` | `infra` | `是` | scheduler concrete reconcile 实现 |
| `com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.scheduler` | `trading` | `infra` | `是` | scheduler concrete reconcile 实现 |
| `com.guidinglight.nexusquant.scheduler.service.PaperMatchingService` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.execution` | `trading` | `infra` | `是` | paper trading 执行实现 |
| `com.guidinglight.nexusquant.scheduler.service.PaperTradingAdapter` | `nq-scheduler` | `com.guidinglight.nexusquant.trading.infra.execution` | `trading` | `infra` | `是` | local fallback trading adapter |
| `com.guidinglight.nexusquant.core.model.StrategyDefinition` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyDefinitionStatus` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy enum |
| `com.guidinglight.nexusquant.core.model.StrategyRun` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyRunDetail` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyRunExecutionResult` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyRunOrderSummary` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyRunStatus` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy enum |
| `com.guidinglight.nexusquant.core.model.StrategyRunSummary` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyRunTradeSummary` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategySchedule` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy domain model |
| `com.guidinglight.nexusquant.core.model.StrategyScheduleStatus` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain` | `strategy` | `domain` | `是` | strategy enum |
| `com.guidinglight.nexusquant.core.service.StrategyDefinitionCreateRequest` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.command` | `strategy` | `application` | `是` | strategy command object |
| `com.guidinglight.nexusquant.core.service.StrategyDefinitionService` | `nq-core` | `com.guidinglight.nexusquant.strategy.application` | `strategy` | `application` | `是` | strategy 应用服务 |
| `com.guidinglight.nexusquant.core.service.StrategyManualTriggerRequest` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.command` | `strategy` | `application` | `是` | strategy command object |
| `com.guidinglight.nexusquant.core.service.StrategyManualTriggerResult` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.result` | `strategy` | `application` | `是` | strategy result object |
| `com.guidinglight.nexusquant.core.service.StrategyManualTriggerService` | `nq-core` | `com.guidinglight.nexusquant.strategy.application` | `strategy` | `application` | `是` | strategy 应用服务 |
| `com.guidinglight.nexusquant.core.service.StrategyRunQueryService` | `nq-core` | `com.guidinglight.nexusquant.strategy.application` | `strategy` | `application` | `是` | strategy 查询服务 |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleCreateRequest` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.command` | `strategy` | `application` | `是` | strategy command object |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleScanBatchResult` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.result` | `strategy` | `application` | `是` | strategy result object |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleScanOutcome` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.result` | `strategy` | `application` | `是` | strategy result object |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleScanResult` | `nq-core` | `com.guidinglight.nexusquant.strategy.application.result` | `strategy` | `application` | `是` | strategy result object |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleScanService` | `nq-core` | `com.guidinglight.nexusquant.strategy.application` | `strategy` | `application` | `是` | strategy 应用服务 |
| `com.guidinglight.nexusquant.core.service.StrategyScheduleService` | `nq-core` | `com.guidinglight.nexusquant.strategy.application` | `strategy` | `application` | `是` | strategy 应用服务 |
| `com.guidinglight.nexusquant.core.service.port.StrategyDefinitionRepository` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain.port` | `strategy` | `domain` | `是` | strategy port |
| `com.guidinglight.nexusquant.core.service.port.StrategyRunRepository` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain.port` | `strategy` | `domain` | `是` | strategy port |
| `com.guidinglight.nexusquant.core.service.port.StrategyRunQueryRepository` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain.port` | `strategy` | `domain` | `是` | strategy port |
| `com.guidinglight.nexusquant.core.service.port.StrategyScheduleRepository` | `nq-core` | `com.guidinglight.nexusquant.strategy.domain.port` | `strategy` | `domain` | `是` | strategy port |
| `com.guidinglight.nexusquant.infra.strategy.JdbcStrategyDefinitionRepository` | `nq-infra` | `com.guidinglight.nexusquant.strategy.infra.jdbc` | `strategy` | `infra` | `是` | strategy JDBC 实现 |
| `com.guidinglight.nexusquant.infra.strategy.JdbcStrategyRunRepository` | `nq-infra` | `com.guidinglight.nexusquant.strategy.infra.jdbc` | `strategy` | `infra` | `是` | strategy JDBC 实现 |
| `com.guidinglight.nexusquant.infra.strategy.JdbcStrategyRunQueryRepository` | `nq-infra` | `com.guidinglight.nexusquant.strategy.infra.jdbc` | `strategy` | `infra` | `是` | strategy JDBC 实现 |
| `com.guidinglight.nexusquant.infra.strategy.JdbcStrategyScheduleRepository` | `nq-infra` | `com.guidinglight.nexusquant.strategy.infra.jdbc` | `strategy` | `infra` | `是` | strategy JDBC 实现 |

## Research / Marketdata

| 当前 FQCN | 所在模块 | 目标包 | 业务域 | 层级 | 本轮是否移动 | 不移动原因 |
| --- | --- | --- | --- | --- | --- | --- |
| `com.guidinglight.nexusquant.api.service.BacktestConfigApiService` | `nq-api` | `com.guidinglight.nexusquant.research.application.api` | `research` | `application` | `是` | research API 应用服务 |
| `com.guidinglight.nexusquant.api.service.BacktestRunApiService` | `nq-api` | `com.guidinglight.nexusquant.research.application.api` | `research` | `application` | `是` | research API 应用服务 |
| `com.guidinglight.nexusquant.api.service.ResearchConfigApiService` | `nq-api` | `com.guidinglight.nexusquant.research.application.api` | `research` | `application` | `是` | research API 应用服务 |
| `com.guidinglight.nexusquant.api.web.BacktestConfigController` | `nq-api` | `com.guidinglight.nexusquant.research.api.web` | `research` | `api` | `是` | research controller |
| `com.guidinglight.nexusquant.api.web.BacktestRunController` | `nq-api` | `com.guidinglight.nexusquant.research.api.web` | `research` | `api` | `是` | research controller |
| `com.guidinglight.nexusquant.api.web.ResearchConfigController` | `nq-api` | `com.guidinglight.nexusquant.research.api.web` | `research` | `api` | `是` | research controller |
| `com.guidinglight.nexusquant.api.web.BacktestConfigCreateRequestBody` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestConfigResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestRunStartRequestBody` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestRunResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestEvaluationResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestPublishRequestBody` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.BacktestPublishResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.ResearchConfigCreateRequestBody` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.ResearchConfigResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research DTO |
| `com.guidinglight.nexusquant.api.web.SimOrderResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research/backtest 查询 DTO |
| `com.guidinglight.nexusquant.api.web.SimTradeResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research/backtest 查询 DTO |
| `com.guidinglight.nexusquant.api.web.SimPositionResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research/backtest 查询 DTO |
| `com.guidinglight.nexusquant.api.web.SimPnlSnapshotResponse` | `nq-api` | `com.guidinglight.nexusquant.research.api.dto` | `research` | `api` | `是` | research/backtest 查询 DTO |
| `com.guidinglight.nexusquant.research.model.BacktestConfig` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.BacktestEvaluationView` | `nq-research` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 子域模型 |
| `com.guidinglight.nexusquant.research.model.BacktestPublishRecord` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.BacktestPublishRequest` | `nq-research` | `com.guidinglight.nexusquant.research.application.publish` | `research` | `application` | `是` | publish 用例请求 |
| `com.guidinglight.nexusquant.research.model.BacktestRun` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.BacktestRunStatus` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域枚举 |
| `com.guidinglight.nexusquant.research.model.ExecutionStrategyDefinitionDraft` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.PublishStatus` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域枚举 |
| `com.guidinglight.nexusquant.research.model.PublishSummary` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.ResearchConfig` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.model.SourceStrategySnapshot` | `nq-research` | `com.guidinglight.nexusquant.research.domain` | `research` | `domain` | `是` | research 主域模型 |
| `com.guidinglight.nexusquant.research.port.BacktestConfigRepository` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.port.BacktestRunRepository` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.port.ResearchConfigRepository` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.port.BacktestPublishRecordRepository` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.port.BacktestEvaluationQueryPort` | `nq-research` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.research.port.ExecutionStrategyDefinitionWriter` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.port.SourceStrategySnapshotRepository` | `nq-research` | `com.guidinglight.nexusquant.research.domain.port` | `research` | `domain` | `是` | research port |
| `com.guidinglight.nexusquant.research.service.BacktestConfigService` | `nq-research` | `com.guidinglight.nexusquant.research.application` | `research` | `application` | `是` | research 应用服务 |
| `com.guidinglight.nexusquant.research.service.BacktestConfigCreateRequest` | `nq-research` | `com.guidinglight.nexusquant.research.application.command` | `research` | `application` | `是` | research command |
| `com.guidinglight.nexusquant.research.service.BacktestRunService` | `nq-research` | `com.guidinglight.nexusquant.research.application` | `research` | `application` | `是` | research 应用服务 |
| `com.guidinglight.nexusquant.research.service.BacktestRunStartRequest` | `nq-research` | `com.guidinglight.nexusquant.research.application.command` | `research` | `application` | `是` | research command |
| `com.guidinglight.nexusquant.research.service.BacktestPublishService` | `nq-research` | `com.guidinglight.nexusquant.research.application` | `research` | `application` | `是` | research 应用服务 |
| `com.guidinglight.nexusquant.research.service.ResearchConfigService` | `nq-research` | `com.guidinglight.nexusquant.research.application` | `research` | `application` | `是` | research 应用服务 |
| `com.guidinglight.nexusquant.research.service.ResearchConfigCreateRequest` | `nq-research` | `com.guidinglight.nexusquant.research.application.command` | `research` | `application` | `是` | research command |
| `com.guidinglight.nexusquant.research.service.ResearchToExecutionMapper` | `nq-research` | `com.guidinglight.nexusquant.research.application` | `research` | `application` | `是` | research 到 execution/strategy 映射器 |
| `com.guidinglight.nexusquant.backtest.model.BacktestExecutionContext` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.BacktestExecutionRequest` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.BacktestExecutionResult` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.SimOrder` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.SimOrderStatus` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域枚举 |
| `com.guidinglight.nexusquant.backtest.model.SimTrade` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.SimPosition` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 子域模型 |
| `com.guidinglight.nexusquant.backtest.port.SimOrderRepository` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest.port` | `research` | `domain` | `是` | backtest 子域 port |
| `com.guidinglight.nexusquant.backtest.port.SimTradeRepository` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest.port` | `research` | `domain` | `是` | backtest 子域 port |
| `com.guidinglight.nexusquant.backtest.port.SimPositionRepository` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest.port` | `research` | `domain` | `是` | backtest 子域 port |
| `com.guidinglight.nexusquant.backtest.port.SimPnlSnapshotRepository` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest.port` | `research` | `domain` | `是` | backtest 子域 port |
| `com.guidinglight.nexusquant.backtest.service.BacktestExecutionService` | `nq-backtest` | `com.guidinglight.nexusquant.research.application.backtest` | `research` | `application` | `是` | backtest 子域应用服务 |
| `com.guidinglight.nexusquant.backtest.service.BacktestExecutionPersistenceService` | `nq-backtest` | `com.guidinglight.nexusquant.research.application.backtest` | `research` | `application` | `是` | backtest 子域应用服务 |
| `com.guidinglight.nexusquant.backtest.service.BacktestFactQueryService` | `nq-backtest` | `com.guidinglight.nexusquant.research.application.backtest` | `research` | `application` | `是` | backtest 子域应用服务 |
| `com.guidinglight.nexusquant.backtest.service.BacktestSignalPolicy` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 领域策略 |
| `com.guidinglight.nexusquant.backtest.service.BuiltinFixtureSignalPolicy` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 领域策略 |
| `com.guidinglight.nexusquant.backtest.service.ExecutionPricingPolicy` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 领域策略 |
| `com.guidinglight.nexusquant.backtest.service.FeeModel` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 领域策略 |
| `com.guidinglight.nexusquant.backtest.service.SlippageModel` | `nq-backtest` | `com.guidinglight.nexusquant.research.domain.backtest` | `research` | `domain` | `是` | backtest 领域策略 |
| `com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 子域模型 |
| `com.guidinglight.nexusquant.eval.model.EvaluationStatus` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 子域枚举 |
| `com.guidinglight.nexusquant.eval.model.EvaluationSummary` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 子域模型 |
| `com.guidinglight.nexusquant.eval.port.BacktestEvaluationReportRepository` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.eval.port.SimOrderQueryRepository` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.eval.port.SimTradeQueryRepository` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.eval.port.SimPositionQueryRepository` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.eval.port.SimPnlSnapshotQueryRepository` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval.port` | `research` | `domain` | `是` | eval 子域 port |
| `com.guidinglight.nexusquant.eval.service.BacktestEvaluationService` | `nq-eval` | `com.guidinglight.nexusquant.research.application.eval` | `research` | `application` | `是` | eval 子域应用服务 |
| `com.guidinglight.nexusquant.eval.service.DrawdownCalculator` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 领域计算器 |
| `com.guidinglight.nexusquant.eval.service.EvaluationMetricCalculator` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 领域计算器 |
| `com.guidinglight.nexusquant.eval.service.SharpeCalculator` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 领域计算器 |
| `com.guidinglight.nexusquant.eval.service.TradeOutcomeCalculator` | `nq-eval` | `com.guidinglight.nexusquant.research.domain.eval` | `research` | `domain` | `是` | eval 领域计算器 |
| `com.guidinglight.nexusquant.backtest.model.BarInterval` | `nq-backtest` | `com.guidinglight.nexusquant.marketdata.domain` | `marketdata` | `domain` | `是` | 历史行情基础类型应归 marketdata |
| `com.guidinglight.nexusquant.backtest.model.HistoricalBar` | `nq-backtest` | `com.guidinglight.nexusquant.marketdata.domain` | `marketdata` | `domain` | `是` | 历史行情基础类型应归 marketdata |
| `com.guidinglight.nexusquant.backtest.model.HistoricalDatasetSpec` | `nq-backtest` | `com.guidinglight.nexusquant.marketdata.domain` | `marketdata` | `domain` | `是` | 历史行情查询条件应归 marketdata |
| `com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery` | `nq-backtest` | `com.guidinglight.nexusquant.marketdata.domain` | `marketdata` | `domain` | `是` | 历史行情查询条件应归 marketdata |
| `com.guidinglight.nexusquant.backtest.port.HistoricalMarketDataPort` | `nq-backtest` | `com.guidinglight.nexusquant.marketdata.domain.port` | `marketdata` | `domain` | `是` | 历史行情 port 属于 marketdata 域 |
| `com.guidinglight.nexusquant.api.web.MarketdataController` | `nq-api` | `com.guidinglight.nexusquant.marketdata.api.web` | `marketdata` | `api` | `是` | marketdata controller |
| `com.guidinglight.nexusquant.api.web.MarketdataBarResponse` | `nq-api` | `com.guidinglight.nexusquant.marketdata.api.dto` | `marketdata` | `api` | `是` | marketdata DTO |
| `com.guidinglight.nexusquant.infra.marketdata.jdbc.JdbcHistoricalMarketDataPort` | `nq-infra` | `com.guidinglight.nexusquant.marketdata.infra.jdbc` | `marketdata` | `infra` | `是` | marketdata JDBC 实现 |
| `com.guidinglight.nexusquant.infra.backtest.FixtureHistoricalMarketDataPort` | `nq-infra` | `com.guidinglight.nexusquant.marketdata.infra.fixture` | `marketdata` | `infra` | `是` | fixture 历史行情实现应归 marketdata |
| `com.guidinglight.nexusquant.app.config.MarketdataModuleConfiguration` | `nq-app` | `com.guidinglight.nexusquant.marketdata.infra.config` | `marketdata` | `infra` | `是` | marketdata 域专属配置迁入域内 |
