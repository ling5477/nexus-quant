-- V14__rc1_schema_comments_backfill.sql
-- Why:
-- 1) RC1 已在 V12 / V13 新增账户、凭证与 marketdata 正式表，但尚未补齐表与字段注释；
-- 2) 使用独立 backfill migration，避免直接修改已生成的版本化 migration 引发 Flyway checksum 漂移；
-- 3) 本批只补表与字段语义说明，不改变任何约束、索引或数据。

COMMENT ON TABLE exchange_accounts IS 'RC1 交易账户主表。保存用户维度下的交易所账户、环境、别名与默认账户口径，是 legacy accounts 向正式账户模型迁移后的主承载表。';
COMMENT ON COLUMN exchange_accounts.exchange_account_id IS '交易账户主键。作为前端账户上下文与后端账户管理的 canonical 身份。';
COMMENT ON COLUMN exchange_accounts.owner_user_id IS '所属用户主键。一个用户可绑定多个交易所账户，所有账户数据归属于该用户。';
COMMENT ON COLUMN exchange_accounts.exchange_code IS '交易所编码，固定使用统一 canonical 口径，例如 OKX / BINANCE。';
COMMENT ON COLUMN exchange_accounts.trade_env IS '交易环境，固定枚举为 SIM / LIVE。legacy DOME / REAL 只允许在导入映射层存在。';
COMMENT ON COLUMN exchange_accounts.account_alias IS '用户在同一交易所与同一环境下区分多个账户的业务别名。';
COMMENT ON COLUMN exchange_accounts.external_account_ref IS '交易所侧账户引用或外部账户标识。可为空；非空时在同一交易所与环境范围内必须唯一。';
COMMENT ON COLUMN exchange_accounts.legacy_account_id IS '兼容期内映射旧 accounts.account_id 的桥接字段，用于 Phase A / B 兼容读写与回填追踪。';
COMMENT ON COLUMN exchange_accounts.is_default IS '是否为该用户在当前交易所与环境下的默认账户。唯一约束保证同一作用域最多只有一条 true。';
COMMENT ON COLUMN exchange_accounts.status IS '账户状态，当前固定口径为 ACTIVE / DISABLED。';
COMMENT ON COLUMN exchange_accounts.created_at IS '交易账户记录创建时间。';
COMMENT ON COLUMN exchange_accounts.updated_at IS '交易账户记录最后更新时间。';

COMMENT ON TABLE exchange_account_credentials IS 'RC1 交易账户凭证版本表。保存账户对应交易所凭证的密文版本、激活状态、校验状态与轮换关系，不再以 env/yml 作为正式主数据源。';
COMMENT ON COLUMN exchange_account_credentials.credential_id IS '账户凭证主键。每次轮换都新增新记录，不覆盖旧版本。';
COMMENT ON COLUMN exchange_account_credentials.exchange_account_id IS '所属交易账户主键。一个交易账户可按 credential_type 拥有多版本凭证。';
COMMENT ON COLUMN exchange_account_credentials.credential_type IS '凭证类型，当前固定支持 OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519。';
COMMENT ON COLUMN exchange_account_credentials.encrypted_payload IS '凭证明文经数据库加密后的密文字节，不在应用层或仓库中以明文持久化。';
COMMENT ON COLUMN exchange_account_credentials.key_version IS '加密主密钥版本号。用于后续轮换、回放与审计定位。';
COMMENT ON COLUMN exchange_account_credentials.cipher_suite IS '加密算法套件，首版固定为 PGP_SYM_AES256。';
COMMENT ON COLUMN exchange_account_credentials.masked_access_key IS '脱敏后的 access key 或主标识，用于前端展示和审计定位，不可用于恢复明文。';
COMMENT ON COLUMN exchange_account_credentials.verification_status IS '最近一次校验状态，固定口径为 PENDING / VERIFIED / FAILED / REVOKED。';
COMMENT ON COLUMN exchange_account_credentials.is_active IS '是否为当前生效版本。唯一约束保证同一账户同一凭证类型仅一条 active。';
COMMENT ON COLUMN exchange_account_credentials.revoked_at IS '凭证被停用或撤销的时间。active 切换后旧版本应记录该时间。';
COMMENT ON COLUMN exchange_account_credentials.rotated_from_credential_id IS '当前版本所继承或轮换自的旧凭证主键，用于构建轮换链和审计血缘。';
COMMENT ON COLUMN exchange_account_credentials.last_verified_at IS '最近一次对当前凭证执行校验的时间。';
COMMENT ON COLUMN exchange_account_credentials.last_verification_error IS '最近一次校验失败的错误摘要；校验成功时可为空。';
COMMENT ON COLUMN exchange_account_credentials.created_at IS '凭证版本记录创建时间。';
COMMENT ON COLUMN exchange_account_credentials.updated_at IS '凭证版本记录最后更新时间。';

COMMENT ON TABLE marketdata_bars IS 'RC1 历史行情 K 线表。保存交易所、交易对、周期和时间窗口维度下的 OHLCV 数据，作为正式 historical marketdata 查询主来源。';
COMMENT ON COLUMN marketdata_bars.marketdata_bar_id IS '历史 K 线主键。';
COMMENT ON COLUMN marketdata_bars.exchange_code IS '交易所编码，使用 canonical 口径。';
COMMENT ON COLUMN marketdata_bars.symbol IS '交易对标识，例如 BTC-USDT。';
COMMENT ON COLUMN marketdata_bars.interval IS 'K 线周期，例如 1m / 5m / 1h。';
COMMENT ON COLUMN marketdata_bars.open_time IS 'K 线开始时间。与 exchange_code + symbol + interval 共同构成唯一时间点。';
COMMENT ON COLUMN marketdata_bars.close_time IS 'K 线结束时间。';
COMMENT ON COLUMN marketdata_bars.open_price IS '开盘价。';
COMMENT ON COLUMN marketdata_bars.high_price IS '最高价。';
COMMENT ON COLUMN marketdata_bars.low_price IS '最低价。';
COMMENT ON COLUMN marketdata_bars.close_price IS '收盘价。';
COMMENT ON COLUMN marketdata_bars.volume IS '该时间窗口内的成交量。';
COMMENT ON COLUMN marketdata_bars.source IS '数据来源标识，例如 IMPORT / BACKFILL / FIXTURE_SYNC。';
COMMENT ON COLUMN marketdata_bars.ingested_at IS '该条历史 K 线被导入正式库的时间。';
