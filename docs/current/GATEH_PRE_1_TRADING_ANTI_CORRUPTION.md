# GATEH_PRE_1_TRADING_ANTI_CORRUPTION

当前状态：**implemented**

## 目标

把 `nq-core` 的 trading application 从 `adapter-api` request/ack/query 契约中脱开，建立内部统一 `TradingVenueGateway` 边界。

## 已落地

- `nq-core` 新增内部契约：
  - `com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway`
  - `TradingPlaceGatewayResult`
  - `TradingCancelGatewayResult`
  - `TradingOrderStatusSnapshot`
  - `TradingGatewayFailure`
  - `TradingGatewayResultCategory`
- `OrderCommandService` / `OrderCommandWriteService` 已改为只消费内部 gateway 语义。
- `AdapterBackedTradingVenueGateway` 已下沉到 `nq-scheduler`，负责：
  - venue 路由
  - adapter request/query 组装
  - adapter result -> internal result 映射
  - 远端异常降级为 `REMOTE_UNAVAILABLE`
- `PaperMatchingService` 已改为通过 `TradingVenueGateway` 查单状态，不再直接依赖 adapter query 契约。
- `nq-app` 的 `TradingRuntimeConfiguration` 已移除：
  - `JdbcTradingQueryFacade` concrete import
  - `SchedulerTradingMaintenanceService` concrete import
  - `RecoveryService` 显式桥接 Bean
- `nq-infra` 新增 `TradingInfraConfiguration`，收口 `TradingQueryFacade` Bean 创建。
- `SchedulerTradingMaintenanceService` 已升级为组件，由 scheduler 自己承接装配。
- `nq-core/pom.xml` 已移除 `nq-adapter-api` 依赖。

## 护栏

`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/ModuleBoundaryArchTest.java` 已新增/更新：

- `trading_application_should_not_depend_on_adapter_api`
- `trading_application_should_not_depend_on_runtime_concrete`
- `app_trading_configuration_should_not_depend_on_trading_runtime_concrete`

## 验收口径

- `nq-core/src/main/java` 不再 import `com.guidinglight.nexusquant.adapter.api..`
- trading application 不再直接依赖 scheduler / infra concrete
- PRE-1 相关测试已通过：
  - `OrderCommandServiceTest`
  - `PaperMatchingServiceTest`
  - `ModuleBoundaryArchTest`
  - `PackageBoundaryArchTest`
