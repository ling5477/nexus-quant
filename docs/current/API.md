# Current API

当前 API 文档以代码实际 controller 为准。本文记录当前 API 分类和已完成 GateH API 事实；GateI-PLAN 只新增规划入口，不实现接口。

## API 分类

- Auth API：登录、当前用户、token 相关接口。
- Account API：用户账户、交易账户、默认账户上下文、凭证写侧接口。
- Trading API：订单、成交、持仓、交易工作台相关接口。
- Strategy API：策略配置、策略查询与策略运行前置数据。
- Schedule API：调度配置与调度状态。
- Run API：运行记录、执行状态、运行详情。
- Research API：研究配置与研究任务。
- Backtest API：回测配置、回测执行、回测结果。
- Evaluation API：评估任务、评估结果。
- Publish API：发布候选、发布状态。
- Instrument API：交易标的、交易所、市场类型、symbol catalog。
- Marketdata API：行情基础 ingest/query 能力。
- Adapter Readiness API：只读查询 OKX / Binance / Noop 各能力当前 readiness（no-real / fail-closed），供前端展示当前不可实盘及原因。
- Actuator / Health：Spring Boot actuator、健康检查。

## 当前边界

- 正式 HTTP API 统一使用 `/api/**`。
- 旧 `/__gated/**` 只允许出现在历史文档说明和归档证据中，不属于当前可执行 API；AUDIT-FIX 后 `scripts/gated_okx_dome_verify.ps1` 仅保留阻断 stub，不再保留旧验收调用。
- AI 自动交易 API 当前不存在，也不允许在本次任务新增。
- GateH-1 只收口 Trading Workspace，不新增行情接入、dataset 绑定或 AI 自动交易接口。
- GateH-2 只新增 OKX / Binance SPOT 历史 OHLCV K 线接入、接入任务与运行记录 API；不新增 dataset/backtest 绑定接口，不新增 AI 接口。
- GateH-3 新增 marketdata dataset、quality refresh、backtest config dataset binding 与 backtest run dataset snapshot API；不新增 AI 接口。
- GateI-1 新增策略版本与发布版本绑定 API；不接 AI。
- GateI-2 增强 backtest config、backtest run 和 evaluation report 追溯 API；不进入 GateI-3/4，不接 AI。
- GateM-5A 新增只读 adapter readiness status API；只读静态 readiness 决策，no-real / fail-closed，不接 AI、不接真实交易所、不读 credential、不启用 LIVE。

## Adapter Readiness API

GateM-5A 新增的只读 adapter readiness 状态查询入口：

- `GET /api/adapters/readiness`：只读返回当前各 venue × capability 的 readiness 快照，供前端展示 OKX / Binance / Noop 当前不可实盘及原因。需要认证（bearerAuth），归属 `/api/**` 受保护路由。
  - 响应：`{ generatedAt, items[] }`；每个 item 含 `venue / capability / status / allowed / liveAuthorized / reasons[] / message`。
  - 覆盖 venue：`NOOP / PAPER / SIM / OKX / BINANCE`；覆盖 capability：`PUBLIC_MARKETDATA / SUBSCRIBE_BARS / SUBSCRIBE_TRADES / SUBSCRIBE_ORDERBOOK / PLACE_ORDER / CANCEL_ORDER / QUERY_ORDER / ACCOUNT_BALANCE / PERMISSION_PROBE`（5 × 9 = 45 条）。
  - 当前 baseline（no-real / LIVE disabled）行为：NOOP/PAPER/SIM → `status=NO_REAL`；OKX/BINANCE → `status=NOT_READY`；所有条目 `allowed=false`、`liveAuthorized=false`，无 `READY`；PLACE_ORDER/CANCEL_ORDER 带 `LIVE_DISABLED` 原因，PERMISSION_PROBE 带 `REAL_PROVIDER_NOT_IMPLEMENTED` 原因。
  - 边界：只读静态 readiness 决策；不触达 adapter delegate、不发起 HTTP/socket、不读取 env/credential、不触发下单/撤单/行情订阅；响应不含 secret/apiKey/token/signature/passphrase 或 raw payload。

## Account Credential API

当前已实现的账户凭证写侧与生命周期入口：

- `GET /api/exchange-accounts/{accountId}/credentials/active`：读取当前 active credential 摘要，支持可选查询参数 `credentialType` 显式选择 `OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`；未指定 `credentialType` 且同一 account 存在多个 ACTIVE credential type 时返回 `409 STATE_CONFLICT`，不再按更新时间静默选择。响应只包含 `credentialId`、`exchangeAccountId`、`credentialType`、`maskedAccessKey`、`credentialStatus`、`verificationStatus`、`isActive`、`revokedAt`、`rotatedFromCredentialId`、`rotatedAt`、`lastVerifiedAt`、`lastVerificationError`、`updatedAt` 等非敏感字段。
- `POST /api/exchange-accounts/{accountId}/credentials`：新增 credential 版本；旧 active 版本仅写为 `credential_status='ROTATED'` 且 `is_active=false`，不再把轮换旧版本混同为不可恢复 `REVOKED`。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate`：显式轮换指定 ACTIVE credential；`credentialType` 从旧 credential 派生，请求体必须包含新 credential material 和 rotate reason；同事务内旧 credential 标记 `ROTATED`、新 credential 创建为 `ACTIVE`，并追加旧 `ROTATED` / 新 `CREATED` audit log。
- `POST /api/exchange-accounts/{accountId}/credentials/verify`：对当前 active credential 做结构性校验，支持可选查询参数 `credentialType` 显式选择；未指定 `credentialType` 且同一 account 存在多个 ACTIVE credential type 时返回 `409 STATE_CONFLICT`。该接口只处理 `credential_status='ACTIVE'` 且 `is_active=true` 的 active material。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/revoke`：不可恢复撤销 credential，写入 `credential_status='REVOKED'`、`revokedAt` 和 append-only `credential_audit_logs` 事件；重复 revoke 幂等返回当前摘要。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/disable`：临时禁用 credential，写入 `credential_status='DISABLED'` 和 append-only audit 事件。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`：重新启用临时禁用的 credential；只允许 `credential_status='DISABLED' AND is_active=false` 的 credential 经本地结构性校验后恢复为 `ACTIVE`，拒绝 `ACTIVE / REVOKED / ROTATED / EXPIRED`，同事务内检查同 account + credentialType 无其他 ACTIVE，写入 `ENABLED` audit log。请求体只包含必填 `reason`，`credentialType` 从 credentialId 派生；不调用真实交易所，不返回或记录敏感材料。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe`：触发最小 permission probe 编排；请求体只允许 `reason / dryRun / mode / paperSafetyConfirmed` 等非敏感字段，拒绝 `apiKey / secret / signature / headers` 等未知字段；`credentialType` 与 actor 均由服务端派生。Service 先做 owner/account/credential、ACTIVE、Paper safety、LIVE disabled、`withdraw_enabled=false` 和 IN_PROGRESS gate，再调用独立 `ExchangeCredentialPermissionProbePort`。本轮默认 port 为 no-real-exchange fake，只返回脱敏 `SKIPPED`，不访问真实交易所。
- `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest`：读取 latest permission probe summary；只读 `permission_probe_status / permission_scope / ip_allowlist_probe_status / failed_auth_count / last_permission_probe_at / last_permission_probe_error` 等脱敏字段，不触发 adapter，不读取 credential material。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/expire`：标记 credential 过期，写入 `credential_status='EXPIRED'` 和 append-only audit 事件。

Credential API 固定边界：

- API response 不返回 `encryptedPayload`、`decryptedPayloadJson`、`apiKey`、`secretKey`、`token`、`privateKeyPem`、`passphrase` 或任何明文 credential material。
- Permission probe response 只返回 `accountId`、`credentialId`、`credentialType`、`exchange`、`permissionProbeStatus`、`permissionScope`、`withdrawEnabled`、`ipAllowlistProbeStatus`、`failedAuthCount`、`lastPermissionProbeAt`、`sanitizedErrorCategory`、`requestId`、`traceId`；不返回 raw response、headers、signature、request body、encrypted/decrypted payload、API key、secret、private key 或 passphrase。
- Permission probe `requestId` 与 `traceId` 是两类不同审计字段：`requestId` 标识本次 probe result，`traceId` 标识调用链路。默认 NoReal port 使用本地脱敏 requestId，不复用 traceId，也不包含 credential material。
- revoke / disable / expire lifecycle command request body 只接收 `reason`；enable command request body 只接收必填 `reason`；rotate command request body 接收新 credential material 和必填 `reason`。应用层限制 reason 长度并拒绝明显包含 token、API key、secret、private key、password、助记词、密钥等敏感材料的原因。
- `DISABLED / REVOKED / EXPIRED / ROTATED` 均不会进入 active material 查询；`REVOKED / ROTATED` 不允许通过本轮接口改写为 `DISABLED / EXPIRED`。
- Batch 5-E-B 后，active summary / active material 无 `credentialType` 路径只在候选唯一时返回；多 ACTIVE credential type 必须显式选择或返回 409。`permission_scope=NULL` 仍表示权限尚未由代码确认，enable 不把 `permission_scope=NULL` 解释为 `TRADE`，本轮不把 `permission_scope` 作为交易权限判断。
- 当前 no-real-exchange permission probe 后端编排已冻结为 guarded baseline；默认 port 仍为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。未接真实 OKX/Binance/Bybit/Gate adapter，未新增 AI / DH / Agent credential 调用、LIVE 交易或真实下单路径；future real adapter 必须另起任务并重新安全审查。

## GateH-1 Trading Workspace API

当前已实现的 GateH-1 交易工作台读写入口：

- `GET /api/trading/orders`：按正式 `exchangeAccountId` 账户上下文查询订单列表，支持 `orderId`、`symbol`、`status`、`environment`、分页筛选。
- `GET /api/trading/orders/{orderId}`：查询单笔订单详情。
- `GET /api/trading/orders/{orderId}/trade`：查询订单最近一笔成交事实。
- `GET /api/trading/accounts/{accountId}`：查询账户余额快照；`accountId` 仍由后端兼容映射到 legacy trading account。
- `GET /api/trading/positions/{accountId}/{symbol}`：查询账户和交易对维度持仓快照。
- `POST /api/trading/orders`：触发既有下单编排，仍走服务端风控与状态机。
- `POST /api/trading/orders/cancel`：触发既有撤单编排。
- `POST /api/trading/reconciliation/run-once`：触发既有对账维护动作。
- `POST /api/trading/recovery/run-once`：触发既有恢复维护动作。

GateH-1 不新增历史行情抓取、marketdata ingestion、dataset 绑定、AI 下单或策略自动交易接口。

## GateH-2 Marketdata Ingestion API

当前已实现的 GateH-2 行情接入入口：

- `GET /api/marketdata/bars`：按 `exchangeCode`、`marketType`、`symbol`、`interval`、`startTime`、`endTime`、`page`、`size` 查询 `marketdata_bars`。
- `POST /api/marketdata/ingestion-jobs`：创建 SPOT 历史 K 线接入任务。
- `GET /api/marketdata/ingestion-jobs`：查询最近接入任务列表。
- `GET /api/marketdata/ingestion-jobs/{jobId}`：查询接入任务详情。
- `GET /api/marketdata/ingestion-jobs/{jobId}/runs`：查询任务运行记录。
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`：执行一次接入任务，返回 `runId`、`status`、`fetchedBars`、`insertedBars`、`updatedBars`、`skippedBars`、`startedAt`、`finishedAt`、`errorMessage`。

GateH-2 固定范围：

- `exchangeCode`：`OKX`、`BINANCE`。
- `marketType`：仅 `SPOT`。
- `symbol`：`BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- `interval`：`1m`、`5m`、`15m`、`1h`、`4h`、`1d`。
- 数据类型：OHLCV K 线。

GateH-2 不新增 AI 自动交易、AI 信号接入、dataset/backtest 绑定、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配或复杂因子平台 API。

## GateH-3 Dataset and Backtest Binding API

当前已实现的 GateH-3 数据集与回测绑定入口：

- `GET /api/marketdata/datasets`：查询 marketdata dataset 列表，支持按 `exchangeCode`、`marketType`、`symbol`、`interval` 过滤。
- `POST /api/marketdata/datasets`：创建 dataset，并立即基于 `marketdata_bars` 计算覆盖范围与质量状态。
- `GET /api/marketdata/datasets/{datasetId}`：查询 dataset 详情。
- `POST /api/marketdata/datasets/{datasetId}/refresh-quality`：重新计算 dataset 覆盖率、缺口数、异常 bar 数和质量状态。
- `PATCH /api/backtest-configs/{configId}/dataset`：把 dataset 绑定到 backtest config，并保存 `dataset_snapshot_json`。
- `GET /api/backtest-configs/{configId}`：返回 `datasetId` 和 `datasetSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 创建时固化的 `datasetSnapshotJson`。

GateH-3 固定范围：dataset 来源仅为 GateH-2 的 `marketdata_bars`；仅支持 `OKX` / `BINANCE`、`SPOT`、`BTC-USDT` / `ETH-USDT` / `SOL-USDT`、`1m` / `5m` / `15m` / `1h` / `4h` / `1d`。

GateH-3 不新增 AI 自动交易、AI 信号接入、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配、复杂因子平台或高频交易 API。

## GateI-1 Strategy Version and Publish API

当前已实现的 GateI-1 策略版本与发布链路入口：

- `GET /api/strategies/{strategyCode}`：按 `strategyCode` 查询策略定义详情。
- `PATCH /api/strategies/{strategyCode}/status`：按 `strategyCode` 启用或停用策略定义。
- `GET /api/strategies/{strategyCode}/versions`：查询策略版本列表。
- `POST /api/strategies/{strategyCode}/versions`：创建策略版本，固化 `paramSnapshotJson`、`configSnapshotJson`、`sourceSnapshotJson` 和 `checksum`。
- `GET /api/strategies/{strategyCode}/versions/{versionId}`：查询策略版本详情，并校验版本归属策略编码。
- `GET /api/publishes`：查询发布记录列表，可按 `strategyVersionId` 过滤。
- `GET /api/publishes/{publishId}`：查询发布记录详情。
- `POST /api/publishes?backtestRunId={runId}`：发布回测结果，可选绑定 `strategyVersionId`。
- `POST /api/backtest-runs/{runId}/publish`：兼容既有发布入口，可选传入 `strategyVersionId`。
- `GET /api/backtest-runs/{runId}/publish`：返回发布结果，并包含策略版本绑定与 `versionSnapshotJson`。

GateI-1 固定范围：

- 策略版本状态：`DRAFT`、`ACTIVE`、`ARCHIVED`。
- 发布绑定只接受存在且 `ACTIVE` 的策略版本。
- 发布时固化 `versionSnapshotJson`，后续策略版本变化不会改写历史发布记录。
- 不修改策略核心算法，不启动回测，不进入 Paper Trading。

GateI-1 不新增 AI API，不新增 AI 自动交易接口，不新增美股/A 股、合约全量、高频或复杂因子平台接口。

## GateI-2 Backtest Traceability and Evaluation API

当前已实现的 GateI-2 回测配置、运行追溯与评估报告入口：

- `GET /api/research-configs`：返回默认业务可见的研究配置列表；Batch 4-A 后默认不包含 `status=ARCHIVED`，`status=DISABLED` 仍可见。
- `GET /api/research-configs/{configId}`：返回单条研究配置详情；允许读取 `status=ARCHIVED` 的配置，用于历史追溯。
- `POST /api/research-configs/{configId}/archive`：把研究配置标记为 `ARCHIVED`，写入 `archivedAt / archivedBy / archiveReason / updatedAt`；请求体 `archiveReason` 可空，不得包含密钥、token、API secret、私钥、助记词等敏感信息；重复归档幂等返回当前详情。
- `GET /api/backtest-configs`：返回回测配置列表，包含 `strategyVersionId`、`strategyVersionSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`、`datasetId`、`datasetSnapshotJson`。
- `POST /api/backtest-configs`：创建回测配置，并初始化参数快照、配置快照；不启动回测。
- `GET /api/backtest-configs/{configId}`：返回单条回测配置详情，包含 strategy version、dataset、参数和配置快照。
- `POST /api/backtest-configs/{configId}/archive`：把回测配置标记为 `ARCHIVED`，写入 `archivedAt / archivedBy / archiveReason / updatedAt`；请求体 `archiveReason` 可空，不得包含密钥、token、API secret、私钥、助记词等敏感信息；重复归档幂等返回当前详情。
- `PATCH /api/backtest-configs/{configId}/strategy-version`：绑定已存在的 strategy version，后端从 `strategy_versions` 读取并固化版本快照和参数快照；请求体只允许传 `strategyVersionId`。
- `PATCH /api/backtest-configs/{configId}/dataset`：复用 GateH-3 dataset 绑定入口，后端固化 dataset snapshot。
- `POST /api/backtest-runs`：根据回测配置创建 run，创建时固化 `strategyVersionId`、`strategyVersionSnapshotJson`、`datasetSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 详情和完整追溯快照；后续配置重新绑定不会改写历史 run。
- `GET /api/evaluations`：查询已生成评估报告列表，返回 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON 等核心指标。
- `GET /api/evaluations/{evaluationId}`：按 `evalReportId` 查询评估报告详情。
- `GET /api/backtest-runs/{runId}/evaluation`：返回该 run 的评估报告。
- `GET /api/backtest-runs/{runId}/sim-orders`：返回 run 模拟订单事实列表（GateF-3 sim facts）。
- `GET /api/backtest-runs/{runId}/sim-trades`：返回 run 模拟成交事实列表。
- `GET /api/backtest-runs/{runId}/sim-positions`：返回 run 模拟持仓事实列表。
- `GET /api/backtest-runs/{runId}/pnl-snapshots`：返回 run 权益/PnL 快照序列（来源表 `sim_pnl_snapshots`，按 `snapshot_time` 升序：`equity / cashBalance / positionMarketValue / realizedPnl / unrealizedPnl / totalFee / totalSlippage / netPnl`）。**回测权益/回撤曲线的时间序列来源即此既有端点。**

> 上述 run-fact 端点(sim-orders / sim-trades / sim-positions / pnl-snapshots)早已在 `BacktestRunController` 实现,此前 `API.md` 漏记,本轮补记为事实。回测权益/回撤曲线后端契约与前端对接计划见 [BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md](./BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md):`pnl-snapshots` 端点与 `sim_pnl_snapshots` 表已存在,**无需新增后端 API / 表 / migration**;前端消费(B1.1,equity 曲线 + 派生 drawdown)为 **planning,尚未实现**。

GateI-2 固定范围：

- 只增强现有 backtest / evaluation 链路。
- 归档命令是配置生命周期命令，不是删除接口；不会删除或隐藏已经产生的 backtest runs、evaluations 或 publish records。
- 不新增 `includeArchived` HTTP 查询参数；默认列表隐藏 `ARCHIVED`，详情按 ID 仍可读取。
- 不修改回测核心算法，不修改策略核心算法，不修改交易核心状态机。
- 不做 SIM/Paper Trading 运行闭环，不进入 GateI-3/4。
- 不接 AI，不新增 AI 分析报告、AI 信号、AI 自动交易或 AI Paper Trading。
- 不新增美股/A 股、合约全量、高频或复杂因子平台 API。

## GateI Planning Entry

GateI API 规划入口为 [GATEI_API_PLAN.md](../gates/gate-i/GATEI_API_PLAN.md)。本轮只做规划，不实现接口。

GateI 规划 API 分类：

- Strategy Version API。
- Publish Version API。
- Backtest Config Enhanced API。
- Evaluation Report API。
- Paper Trading Run API。
- Risk Result API。
- Equity Curve API。
- Position Curve API。
- Trade Replay API。
- Emergency Stop API。

GateI 后续规划不改变当前事实：AI、AI 信号、AI 自动交易和 AI Paper Trading 仍未开始。

## GateI-3 Paper Trading Run API

当前已实现的 GateI-3 SIM/Paper Trading 运行闭环入口：

- `GET /api/paper-trading/runs`：查询 Paper Trading run 列表，可按 `publishId`、`status` 过滤。
- `POST /api/paper-trading/runs`：基于 `publishId` 创建 Paper Trading run，固化 publish/strategy version/dataset/param/config 快照。
- `GET /api/paper-trading/runs/{paperRunId}`：查询 Paper Trading run 详情。
- `GET /api/paper-trading/runs/{paperRunId}/summary`：只读聚合该 Paper run 的运行结果复盘、异常原因诊断、运行事件时间线与关键计数（counts/latest/resultReview/diagnoses/timeline/safety），供前端详情区优先消费；不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启。
- `POST /api/paper-trading/runs/{paperRunId}/start`：启动 Paper run（CREATED → RUNNING）。
- `POST /api/paper-trading/runs/{paperRunId}/stop`：停止 Paper run（RUNNING → STOPPED）。
- `GET /api/paper-trading/runs/{paperRunId}/orders`：查询 Paper run 订单事实列表。
- `GET /api/paper-trading/runs/{paperRunId}/trades`：查询 Paper run 成交事实列表。
- `GET /api/paper-trading/runs/{paperRunId}/positions`：查询 Paper run 持仓事实列表。

GateI-3 固定范围：

- 只做 SIM/Paper，不接 LIVE 自动交易。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 不新增美股/A 股、合约全量、高频或复杂因子平台 API。
- Paper run 状态流转：CREATED → RUNNING → STOPPED；CREATED/RUNNING → FAILED。
- Paper run 创建时固化 publish snapshot、strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- 第一版 orders/trades/positions 为空列表，由后续 GateI-4 风控回写和撮合填充。

## GateI-4 Paper Trading Monitor API

当前已实现的 GateI-4 风控回写、资金曲线、持仓曲线、交易复盘与异常停机入口：

- `GET /api/paper-trading/runs/{paperRunId}/risk-results`：查询 Paper run 风控检查结果列表。
- `POST /api/paper-trading/runs/{paperRunId}/risk-results/run-once`：触发一次最小 BASIC_HEALTH_CHECK 风控检查并写入结果。
- `GET /api/paper-trading/runs/{paperRunId}/equity-curve`：查询 Paper run 资金曲线快照列表（按时间倒序）。
- `GET /api/paper-trading/runs/{paperRunId}/position-curve`：查询 Paper run 持仓曲线快照列表（按时间倒序）。
- `GET /api/paper-trading/runs/{paperRunId}/replay`：查询 Paper run 交易复盘事件记录列表（按时间倒序）。
- `POST /api/paper-trading/runs/{paperRunId}/emergency-stop`：触发异常停机；当 run 处于 RUNNING 时调用 stop 状态机并返回 APPLIED，否则返回 FAILED 并记录原因。
- `GET /api/paper-trading/runs/{paperRunId}/emergency-stops`：查询 Paper run 异常停机事件列表。

GateI-4 固定范围：

- 只做 SIM/Paper Trading 监控与异常停机，不接 LIVE 自动交易。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 风控检查第一版仅写入最小 BASIC_HEALTH_CHECK；具体规则与撮合回写在后续 Gate 实现。
- 异常停机仅复用既有 PaperTradingRunService.stop，不引入额外状态。

## GateJ Planning Entry

GateJ API 规划入口为 [GATEJ_API_PLAN.md](./GATEJ_API_PLAN.md)。本轮只做规划，不实现接口。

GateJ 规划 API 分类：

- Paper Run Schedule API（调度计划 CRUD + run-once）。
- Paper Run Heartbeat API（心跳记录 + run-once）。
- Paper Run Daily Report API（日报生成 + 查询）。
- Paper Run Alert API（告警查询 + 确认）。
- Paper Run Recovery API（恢复 + 重试）。
- GateJ Stability Acceptance API（稳定性验收生成 + 查询）。

GateJ 后续规划不改变当前事实：AI、AI 信号、AI 自动交易和 AI Paper Trading 仍未开始。GateJ 不是 AI 阶段。

## GateJ-1 Paper Run Schedule and Heartbeat API

当前已实现的 GateJ-1 调度计划、触发记录与心跳入口：

- `GET /api/paper-trading/schedules`：查询调度计划列表，可按 `paperRunId`、`status` 过滤。
- `POST /api/paper-trading/schedules`：创建调度计划，默认 ENABLED 状态。
- `GET /api/paper-trading/schedules/{scheduleId}`：查询调度计划详情。
- `PATCH /api/paper-trading/schedules/{scheduleId}/status`：更新调度状态（ENABLED / DISABLED / PAUSED）。
- `POST /api/paper-trading/schedules/{scheduleId}/run-once`：手动触发一次调度，写入 fire 记录。
- `GET /api/paper-trading/schedules/{scheduleId}/fires`：查询调度触发记录列表。
- `GET /api/paper-trading/runs/{paperRunId}/heartbeats`：查询 Paper run 心跳记录列表。
- `POST /api/paper-trading/runs/{paperRunId}/heartbeats/run-once`：手动生成一次心跳记录。

GateJ-1 固定范围：

- 只做调度计划、触发记录和心跳。
- 不做日报、告警、恢复、稳定性验收（GateJ-2/3）。
- 不做后台常驻调度器自动触发（第一版只支持 run-once 手动触发）。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。

## GateJ-2 Paper Run Daily Report and Alert API

当前已实现的 GateJ-2 日报与告警入口：

- `GET /api/paper-trading/runs/{paperRunId}/daily-reports`：查询 Paper run 日报列表（按 report_date 倒序）。
- `POST /api/paper-trading/runs/{paperRunId}/daily-reports/generate`：生成 Paper run 日报；请求体 `reportDate` 可空，空时使用当前 UTC 日期；按 (paper_run_id, report_date) 幂等。
- `GET /api/paper-trading/runs/{paperRunId}/daily-reports/{reportId}`：查询日报详情。
- `GET /api/paper-trading/runs/{paperRunId}/alerts`：查询 Paper run 告警列表（按 created_at 倒序），可按 `status`、`severity` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/alerts`：创建一条告警事件；severity 必须为 LOW / MEDIUM / HIGH / CRITICAL；状态固定 OPEN。
- `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/ack`：确认告警；OPEN → ACKED；幂等；RESOLVED 状态返回 409。
- `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/resolve`：解决告警；任意非 RESOLVED → RESOLVED；幂等。

GateJ-2 固定范围：

- 只做日报与告警。
- 不做恢复、稳定性验收（GateJ-3）。
- 不做外部通知（邮件、Slack、钉钉）。
- 不引入图表库。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。

## GateJ-3 Paper Run Recovery and Stability API

当前已实现的 GateJ-3 恢复、重试、稳定性验收、监控守护入口：

- `GET /api/paper-trading/runs/{paperRunId}/recovery-events`：查询 Paper run 恢复事件列表（按 created_at 倒序），可按 `recoveryType`、`status` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/recover`：触发一次手动恢复（MANUAL_RECOVER）；请求体 `reason / requestJson` 可选；不调用真实交易所下单接口。
- `POST /api/paper-trading/runs/{paperRunId}/retry-failed-step`：触发一次失败步骤重试（RETRY_FAILED_STEP）；请求体 `failedStep / reason / requestJson` 可选；不调用真实交易所下单接口。
- `GET /api/paper-trading/runs/{paperRunId}/stability-checks`：查询稳定性验收列表（按 created_at 倒序），可按 `status` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/stability-checks/generate`：生成 Paper run 稳定性验收；请求体 `checkWindowStart / checkWindowEnd` 必填；按 (paper_run_id, check_window_start, check_window_end) 幂等。
- `GET /api/paper-trading/runs/{paperRunId}/stability-checks/{stabilityCheckId}`：查询稳定性验收详情。
- `POST /api/paper-trading/runs/{paperRunId}/monitor/run-once`：执行一次监控守护；检查 heartbeat lag（默认阈值 300s）并落库 HEARTBEAT_LAG 告警；检查最近 5 分钟内 schedule fire failed 并落库 SCHEDULE_FIRE_FAILED 告警；同一类型在 5 分钟去重窗口内不重复创建；第一版只落库，不外发通知。

GateJ-3 recovery event 状态流转：

- `STARTED → SUCCEEDED / FAILED / SKIPPED`。
- 第一版根据 Paper run 状态映射：RUNNING/CREATED → SUCCEEDED，STOPPED → SKIPPED。
- 每次恢复/重试产生新记录，不幂等（每次产生新的 recovery_event_id）。

GateJ-3 stability check 第一版口径：

- `PASSED`：窗口内 heartbeat_count > 0，且无 CRITICAL 未处理告警，且 failed_fire_count = 0。
- `PARTIAL`：窗口内有心跳但存在普通告警或恢复事件。
- `FAILED`：窗口内无心跳，或存在 CRITICAL 未处理告警，或 failed_fire_count > 0。
- 第一版 `uptime_ratio` 按粗略判定（PASSED=1.0、PARTIAL=0.9、FAILED 有心跳=0.5/无心跳=0）。
- 第一版口径不等于 GateJ-FREEZE 的 1h/24h/7d 最终验收。

GateJ-3 自动告警口径：

- `HEARTBEAT_LAG`：监控守护检测到最近 heartbeat 不存在或 lag_seconds ≥ 300，且 Paper run 状态为 RUNNING；severity = HIGH；source = MONITOR。
- `SCHEDULE_FIRE_FAILED`：监控守护检测到最近 5 分钟内存在 paper_run_schedule_fires.status = FAILED 记录；severity = MEDIUM；source = SCHEDULE。
- 第一版去重：每种 alert_type 在 5 分钟内最多创建 1 条；不做更复杂的策略去重。
- 第一版只落库 paper_run_alerts，不外发通知（邮件 / Slack / 钉钉 / 短信 / Webhook 均不接入）。

GateJ-3 固定范围：

- 只做恢复、重试、稳定性验收、HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库。
- 不做 1h/24h/7d 正式验收归档（GateJ-FREEZE）。
- 不做外部通知（邮件、Slack、钉钉、企业微信、Telegram、Webhook、短信）。
- 不做自动恢复策略引擎。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 不调用真实交易所下单接口。
