# GATEH_PRE_2_MARKETDATA_INSTRUMENTS

当前状态：**implemented (minimum viable baseline)**

## 目标

把 `instrument / symbol` 从 adapter cache 提升为正式 catalog，并去掉 backtest 对 BTC fixture 的硬编码默认回退。

## 已落地

### 1. 正式 instrument catalog

新增数据库表：`instrument_catalog`

- migration: `backend/nq-infra/src/main/resources/db/migration/V15__gateh_pre_instrument_catalog.sql`
- 唯一键：
  - `(exchange_code, exchange_symbol)`
  - `(exchange_code, internal_symbol)`
- 主字段：
  - `exchange_code`
  - `instrument_type`
  - `exchange_symbol`
  - `internal_symbol`
  - `base_asset`
  - `quote_asset`
  - `status`
  - `tick_size`
  - `step_size`
  - `min_quantity`
  - `source`
  - `synced_at`

### 2. 后端主链

新增 core 侧正式契约：

- `InstrumentCatalogService`
- `InstrumentCatalogSyncService`
- `InstrumentCatalogSyncResult`
- `InstrumentCatalogUpsertStats`
- `InstrumentCatalogItem`
- `InstrumentCatalogRepository`

新增 infra 实现：

- `JdbcInstrumentCatalogRepository`

新增 scheduler 侧 sync 实现：

- `AdapterInstrumentCatalogSyncService`
- 数据来源：
  - `OkxExchangeAdapter.instrumentsCache()`
  - `BinanceExchangeAdapter.filtersCache()`

新增 API：

- `GET /api/instruments`
- `POST /api/instruments/sync`

### 3. fixture / backtest 去 BTC 默认回退

- `FixtureMarketdataRegistry` 已迁入 `nq-core` 并扩展为多交易对注册表：
  - `BINANCE_BTCUSDT_1M_SAMPLE`
  - `BINANCE_ETHUSDT_1M_SAMPLE`
- `BacktestExecutionService` 已移除固定 `btcusdt_1m_sample.csv` 默认回退。
- 当前规则：
  - `provider=db` 时默认 `resourcePath=marketdata_bars`
  - `provider=fixture` 时必须：
    - 显式提供 `resourcePath`
    - 或 `datasetId` 已在 `FixtureMarketdataRegistry` 中注册
- 新增主资源 fixture：
  - `backend/nq-core/src/main/resources/backtest/fixtures/btcusdt_1m_sample.csv`
  - `backend/nq-core/src/main/resources/backtest/fixtures/ethusdt_1m_sample.csv`

## 当前 owner 口径

- `marketdata` application/domain owner：`nq-core`
- `marketdata` JDBC / fixture port implementation owner：`nq-infra`
- instrument / symbol 主数据 owner：`nq-core + nq-infra`
- adapter 元数据采集与 sync 承接：`nq-scheduler`
- `nq-backtest` 只消费 marketdata port，不再持有平台级 marketdata application/domain 代码。

> 说明：PRE-CLEAN-2 已完成 platform marketdata owner 最小物理收口；后续若要继续平台化，只在该 owner 基线之上增量推进。

## 验收口径

- `marketdata.application/domain` 已归属 `nq-core`，不再挂在 `nq-backtest`
- 系统存在正式 `instrument_catalog`，不再只依赖 adapter cache
- 已有正式 query/sync API，可供前端 selector 与主数据页使用
- backtest 不再因为缺 `resourcePath` 自动回退到 BTC fixture
- 现有多币种最小基线已成立：BTCUSDT + ETHUSDT
- PRE-2 相关测试已通过：
  - `InstrumentCatalogServiceTest`
  - `InstrumentCatalogControllerTest`
  - `MarketdataBarIngestServiceTest`
  - `BacktestExecutionServiceTest`
