-- V6__schema_comments_backfill.sql
-- 目的：补齐整库剩余基础表的表注释与关键字段注释。
-- Why:
-- 1) GateE-0.2 已完成 5 张核心表注释，但 migration 基线中的其他表仍缺少注释；
-- 2) 注释回补只做 metadata 完整性，不新增字段、不改索引、不改 trigger；
-- 3) 本批按仓库 migration 实际表清单执行，当前整库实际为 16 张表。

COMMENT ON TABLE users IS '系统用户表。保存后台登录用户的最小身份与启用状态，不承载交易账户或策略身份。';
COMMENT ON COLUMN users.id IS '内部用户主键。';
COMMENT ON COLUMN users.username IS '登录用户名，系统内部唯一标识。';
COMMENT ON COLUMN users.password_hash IS '密码哈希值，不保存明文密码。';
COMMENT ON COLUMN users.enabled IS '用户启用开关，控制是否允许登录后台。';
COMMENT ON COLUMN users.created_at IS '用户创建时间。';
COMMENT ON COLUMN users.updated_at IS '用户最后更新时间。';

COMMENT ON TABLE roles IS '后台角色表。定义权限角色编码与描述，不区分交易所或交易环境。';
COMMENT ON COLUMN roles.id IS '内部角色主键。';
COMMENT ON COLUMN roles.role_code IS '角色唯一编码，例如 ADMIN / OPERATOR。';
COMMENT ON COLUMN roles.description IS '角色描述。';
COMMENT ON COLUMN roles.created_at IS '角色创建时间。';

COMMENT ON TABLE user_roles IS '用户与角色关联表。保存后台用户授权关系，不参与交易幂等或去重。';
COMMENT ON COLUMN user_roles.user_id IS '内部用户主键外键。';
COMMENT ON COLUMN user_roles.role_id IS '内部角色主键外键。';
COMMENT ON COLUMN user_roles.granted_at IS '角色授予时间。';

COMMENT ON TABLE accounts IS '交易账户表。保存账户主键、账户业务编码和账户绑定交易所，不区分策略定义级身份。';
COMMENT ON COLUMN accounts.account_id IS '内部账户主键。';
COMMENT ON COLUMN accounts.account_code IS '账户业务唯一编码。';
COMMENT ON COLUMN accounts.venue IS '账户绑定交易所标识。当前仍沿用历史 venue 命名，不是 GateE canonical exchange_code。';
COMMENT ON COLUMN accounts.status IS '账户状态，例如 ACTIVE / DISABLED。';
COMMENT ON COLUMN accounts.created_at IS '账户创建时间。';

COMMENT ON TABLE positions IS '持仓投影表。保存账户在某交易对上的当前持仓快照，由成交和账本链路驱动。';
COMMENT ON COLUMN positions.id IS '内部持仓记录主键。';
COMMENT ON COLUMN positions.account_id IS '账户维度外键。';
COMMENT ON COLUMN positions.symbol IS '内部交易对标识。';
COMMENT ON COLUMN positions.qty IS '当前总持仓数量。';
COMMENT ON COLUMN positions.available_qty IS '当前可用持仓数量。';
COMMENT ON COLUMN positions.frozen_qty IS '当前冻结持仓数量。';
COMMENT ON COLUMN positions.avg_price IS '当前持仓均价。';
COMMENT ON COLUMN positions.trace_id IS '最近一次更新该投影的链路追踪 ID。';
COMMENT ON COLUMN positions.updated_at IS '持仓投影最后更新时间。';

COMMENT ON TABLE account_snapshots IS '账户资金快照表。保存账户维度的余额、可用、冻结投影，不直接参与订单幂等。';
COMMENT ON COLUMN account_snapshots.snapshot_id IS '内部账户快照主键。';
COMMENT ON COLUMN account_snapshots.account_id IS '账户维度外键。';
COMMENT ON COLUMN account_snapshots.currency IS '币种标识。';
COMMENT ON COLUMN account_snapshots.balance IS '总余额。';
COMMENT ON COLUMN account_snapshots.available IS '可用余额。';
COMMENT ON COLUMN account_snapshots.frozen IS '冻结余额。';
COMMENT ON COLUMN account_snapshots.ts IS '快照事实时间。';
COMMENT ON COLUMN account_snapshots.trace_id IS '产出该快照的链路追踪 ID。';
COMMENT ON COLUMN account_snapshots.created_at IS '快照记录创建时间。';

COMMENT ON TABLE ledger_entries IS '账本分录表。保存账户余额变更事实，idempotency_key 参与记账幂等。';
COMMENT ON COLUMN ledger_entries.entry_id IS '内部账本分录主键。';
COMMENT ON COLUMN ledger_entries.account_id IS '账户维度外键。';
COMMENT ON COLUMN ledger_entries.currency IS '账本币种。';
COMMENT ON COLUMN ledger_entries.delta IS '本次账本变动值。';
COMMENT ON COLUMN ledger_entries.balance_after IS '变动后余额。';
COMMENT ON COLUMN ledger_entries.direction IS '账本方向，固定为 DEBIT / CREDIT。';
COMMENT ON COLUMN ledger_entries.ref_type IS '账本关联对象类型，例如 TRADE / ORDER。';
COMMENT ON COLUMN ledger_entries.ref_id IS '账本关联对象内部标识。';
COMMENT ON COLUMN ledger_entries.idempotency_key IS '账本幂等键。用于避免重复记账。';
COMMENT ON COLUMN ledger_entries.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN ledger_entries.ts IS '账本事实时间。';
COMMENT ON COLUMN ledger_entries.created_at IS '账本分录创建时间。';

COMMENT ON TABLE ledger_events IS '账本事件表。保存账本分录衍生出的事件证据，用于审计与重放辅助。';
COMMENT ON COLUMN ledger_events.ledger_event_id IS '内部账本事件主键。';
COMMENT ON COLUMN ledger_events.entry_id IS '所属账本分录主键。';
COMMENT ON COLUMN ledger_events.event_type IS '账本事件类型。';
COMMENT ON COLUMN ledger_events.payload_json IS '账本事件载荷 JSON。';
COMMENT ON COLUMN ledger_events.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN ledger_events.created_at IS '账本事件创建时间。';

COMMENT ON TABLE risk_events IS '风控事件表。保存规则命中、拒绝和放行的审计事实，不直接代表订单状态。';
COMMENT ON COLUMN risk_events.risk_event_id IS '内部风控事件主键。';
COMMENT ON COLUMN risk_events.rule_id IS '风控规则标识。';
COMMENT ON COLUMN risk_events.scope IS '风控作用域，例如 ACCOUNT / ORDER。';
COMMENT ON COLUMN risk_events.scope_id IS '作用域对象标识。';
COMMENT ON COLUMN risk_events.decision IS '风控决策，例如 ALLOW / REJECT。';
COMMENT ON COLUMN risk_events.reason IS '风控原因摘要。';
COMMENT ON COLUMN risk_events.severity IS '风控严重级别。';
COMMENT ON COLUMN risk_events.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN risk_events.created_at IS '风控事件创建时间。';

COMMENT ON TABLE event_store IS '事件总表。保存跨域事件事实链，key_value 用于主题内幂等与顺序消费。';
COMMENT ON COLUMN event_store.event_id IS '内部事件主键。';
COMMENT ON COLUMN event_store.topic IS '事件主题。';
COMMENT ON COLUMN event_store.schema_version IS '事件 schema 版本号。';
COMMENT ON COLUMN event_store.event_type IS '事件类型。';
COMMENT ON COLUMN event_store.payload_json IS '事件载荷 JSON。';
COMMENT ON COLUMN event_store.key_value IS '事件业务键，用于主题内幂等与聚合定位。';
COMMENT ON COLUMN event_store.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN event_store.created_at IS '事件写入时间。';

COMMENT ON TABLE audit_logs IS '审计日志表。保存 domain / action / actor / detail 的最小审计证据，不直接承载业务主状态。';
COMMENT ON COLUMN audit_logs.id IS '内部审计日志主键。';
COMMENT ON COLUMN audit_logs.domain IS '审计域，例如 ORDER / RECONCILE / WS。';
COMMENT ON COLUMN audit_logs.action IS '审计动作，例如 CREATED / FAILED / COMPLETED。';
COMMENT ON COLUMN audit_logs.actor_id IS '审计主体标识。通常是内部对象 ID，不是交易所外部标识。';
COMMENT ON COLUMN audit_logs.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN audit_logs.detail_json IS '审计详情 JSON。';
COMMENT ON COLUMN audit_logs.created_at IS '审计日志创建时间。';
