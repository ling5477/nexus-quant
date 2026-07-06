CREATE TABLE shadow_runs (
    id UUID PRIMARY KEY,
    strategy_version_id VARCHAR(128) NOT NULL,
    dataset_id UUID NOT NULL,
    evaluation_id VARCHAR(128),
    publish_id VARCHAR(128),
    paper_run_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    window_start TIMESTAMPTZ,
    window_end TIMESTAMPTZ,
    side_effect_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    no_order_submission BOOLEAN NOT NULL DEFAULT TRUE,
    no_credential_access BOOLEAN NOT NULL DEFAULT TRUE,
    no_private_endpoint BOOLEAN NOT NULL DEFAULT TRUE,
    no_ledger_mutation BOOLEAN NOT NULL DEFAULT TRUE,
    no_account_mutation BOOLEAN NOT NULL DEFAULT TRUE,
    no_external_private_io BOOLEAN NOT NULL DEFAULT TRUE,
    authorization_boundary VARCHAR(64) NOT NULL DEFAULT 'DIAGNOSTIC_ONLY',
    request_id VARCHAR(128),
    idempotency_key VARCHAR(160) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    blockers JSONB NOT NULL DEFAULT '[]'::jsonb,
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    next_steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_shadow_runs_status
        CHECK (status IN ('CREATED', 'PRECHECKING', 'READY', 'RUNNING', 'STOP_REQUESTED',
                          'STOPPED', 'COMPLETED', 'BLOCKED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_shadow_runs_authorization_boundary
        CHECK (authorization_boundary IN ('DIAGNOSTIC_ONLY', 'REVIEW_ONLY', 'REPLAY_ONLY')),
    CONSTRAINT chk_shadow_runs_window
        CHECK (window_start IS NULL OR window_end IS NULL OR window_end >= window_start),
    CONSTRAINT chk_shadow_runs_side_effect_policy_json
        CHECK (jsonb_typeof(side_effect_policy) = 'object'),
    CONSTRAINT chk_shadow_runs_json_arrays
        CHECK (jsonb_typeof(blockers) = 'array'
            AND jsonb_typeof(warnings) = 'array'
            AND jsonb_typeof(next_steps) = 'array'),
    CONSTRAINT chk_shadow_runs_no_side_effects
        CHECK (no_order_submission IS TRUE
            AND no_credential_access IS TRUE
            AND no_private_endpoint IS TRUE
            AND no_ledger_mutation IS TRUE
            AND no_account_mutation IS TRUE
            AND no_external_private_io IS TRUE),
    CONSTRAINT chk_shadow_runs_version
        CHECK (version >= 0),
    CONSTRAINT fk_shadow_runs_strategy_version
        FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions (strategy_version_id),
    CONSTRAINT fk_shadow_runs_dataset
        FOREIGN KEY (dataset_id) REFERENCES marketdata_datasets (dataset_id),
    CONSTRAINT fk_shadow_runs_evaluation
        FOREIGN KEY (evaluation_id) REFERENCES backtest_eval_reports (eval_report_id),
    CONSTRAINT fk_shadow_runs_publish
        FOREIGN KEY (publish_id) REFERENCES backtest_publish_records (publish_record_id),
    CONSTRAINT fk_shadow_runs_paper_run
        FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs (paper_run_id)
);

CREATE UNIQUE INDEX idx_shadow_runs_idempotency_key ON shadow_runs (idempotency_key);
CREATE INDEX idx_shadow_runs_status_created_at ON shadow_runs (status, created_at DESC);
CREATE INDEX idx_shadow_runs_strategy_dataset ON shadow_runs (strategy_version_id, dataset_id);
CREATE INDEX idx_shadow_runs_paper_run_id ON shadow_runs (paper_run_id);

CREATE TABLE shadow_run_events (
    id UUID PRIMARY KEY,
    shadow_run_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    reason_code VARCHAR(128),
    message TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id VARCHAR(128),
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_shadow_run_events_event_type
        CHECK (event_type IN ('CREATED', 'PRECHECK_STARTED', 'PRECHECK_PASSED',
                              'PRECHECK_BLOCKED', 'RUN_STARTED', 'STOP_REQUESTED',
                              'STOPPED', 'COMPLETED', 'FAILED', 'CANCELLED',
                              'ILLEGAL_STATE_TRANSITION_ATTEMPT',
                              'SNAPSHOT_CAPTURED', 'CONSISTENCY_REPORT_GENERATED')),
    CONSTRAINT chk_shadow_run_events_from_status
        CHECK (from_status IS NULL OR from_status IN ('CREATED', 'PRECHECKING', 'READY', 'RUNNING',
                                                      'STOP_REQUESTED', 'STOPPED', 'COMPLETED',
                                                      'BLOCKED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_shadow_run_events_to_status
        CHECK (to_status IS NULL OR to_status IN ('CREATED', 'PRECHECKING', 'READY', 'RUNNING',
                                                  'STOP_REQUESTED', 'STOPPED', 'COMPLETED',
                                                  'BLOCKED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_shadow_run_events_metadata_json
        CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT fk_shadow_run_events_run
        FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs (id)
);

CREATE INDEX idx_shadow_run_events_run_created_at
    ON shadow_run_events (shadow_run_id, created_at ASC);

CREATE TABLE shadow_run_snapshots (
    id UUID PRIMARY KEY,
    shadow_run_id UUID NOT NULL,
    snapshot_type VARCHAR(48) NOT NULL,
    sequence_no INTEGER NOT NULL,
    source VARCHAR(128) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_shadow_run_snapshots_type
        CHECK (snapshot_type IN ('INPUT_MARKETDATA', 'STRATEGY_DECISION',
                                 'RISK_PREFLIGHT', 'ORDER_INTENT_PREVIEW')),
    CONSTRAINT chk_shadow_run_snapshots_sequence
        CHECK (sequence_no >= 0),
    CONSTRAINT chk_shadow_run_snapshots_checksum
        CHECK (length(trim(checksum)) > 0),
    CONSTRAINT chk_shadow_run_snapshots_payload_json
        CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT uq_shadow_run_snapshots_run_type_seq
        UNIQUE (shadow_run_id, snapshot_type, sequence_no),
    CONSTRAINT fk_shadow_run_snapshots_run
        FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs (id)
);

CREATE INDEX idx_shadow_run_snapshots_run_type_sequence
    ON shadow_run_snapshots (shadow_run_id, snapshot_type, sequence_no);

CREATE TABLE shadow_consistency_reports (
    id UUID PRIMARY KEY,
    shadow_run_id UUID NOT NULL,
    paper_run_id VARCHAR(64),
    comparison_status VARCHAR(48) NOT NULL,
    metric_delta JSONB NOT NULL DEFAULT '{}'::jsonb,
    divergence_reasons JSONB NOT NULL DEFAULT '[]'::jsonb,
    limitations JSONB NOT NULL DEFAULT '[]'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    trace_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_shadow_consistency_reports_status
        CHECK (comparison_status IN ('CONSISTENT', 'DIVERGED', 'NOT_COMPARABLE',
                                     'PARTIAL', 'FAILED')),
    CONSTRAINT chk_shadow_consistency_reports_json
        CHECK (jsonb_typeof(metric_delta) = 'object'
            AND jsonb_typeof(divergence_reasons) = 'array'
            AND jsonb_typeof(limitations) = 'array'),
    CONSTRAINT fk_shadow_consistency_reports_run
        FOREIGN KEY (shadow_run_id) REFERENCES shadow_runs (id),
    CONSTRAINT fk_shadow_consistency_reports_paper_run
        FOREIGN KEY (paper_run_id) REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_shadow_consistency_reports_run_generated
    ON shadow_consistency_reports (shadow_run_id, generated_at DESC);
CREATE INDEX idx_shadow_consistency_reports_paper_generated
    ON shadow_consistency_reports (paper_run_id, generated_at DESC);

COMMENT ON TABLE shadow_runs IS 'GateR-2 Shadow Run 本地事实主表：记录一次无真实交易副作用的影子运行状态和追溯链；不代表交易授权，不保存 credential material，不代表 LIVE ready，不产生真实交易副作用';
COMMENT ON COLUMN shadow_runs.id IS 'Shadow Run 本地事实主键，UUID；只用于本地审计和复盘，不是交易所订单 ID';
COMMENT ON COLUMN shadow_runs.strategy_version_id IS '引用 strategy_versions.strategy_version_id；作为只读输入事实，不改写策略版本';
COMMENT ON COLUMN shadow_runs.dataset_id IS '引用 marketdata_datasets.dataset_id；作为只读行情数据集输入事实';
COMMENT ON COLUMN shadow_runs.evaluation_id IS '可空引用 backtest_eval_reports.eval_report_id；用于追溯评估事实，不改写评估状态';
COMMENT ON COLUMN shadow_runs.publish_id IS '可空引用 backtest_publish_records.publish_record_id；用于追溯发布事实，不表示 LIVE 发布';
COMMENT ON COLUMN shadow_runs.paper_run_id IS '可空引用 paper_trading_runs.paper_run_id；仅用于 Paper vs Shadow 复盘对照，不写 Paper 事实';
COMMENT ON COLUMN shadow_runs.status IS 'Shadow Run 状态：CREATED、PRECHECKING、READY、RUNNING、STOP_REQUESTED、STOPPED、COMPLETED、BLOCKED、FAILED、CANCELLED；终态不可回写运行态';
COMMENT ON COLUMN shadow_runs.window_start IS 'Shadow Run 输入窗口开始时间；为空表示本轮尚未绑定窗口';
COMMENT ON COLUMN shadow_runs.window_end IS 'Shadow Run 输入窗口结束时间；存在时不得早于 window_start';
COMMENT ON COLUMN shadow_runs.side_effect_policy IS '无副作用策略 JSONB，仅保存本地诊断策略，不保存密钥、token、cookie、private endpoint payload 或真实请求响应';
COMMENT ON COLUMN shadow_runs.no_order_submission IS '固定为 true，表示 Shadow Run 禁止提交真实订单';
COMMENT ON COLUMN shadow_runs.no_credential_access IS '固定为 true，表示 Shadow Run 禁止读取 credential material';
COMMENT ON COLUMN shadow_runs.no_private_endpoint IS '固定为 true，表示 Shadow Run 禁止调用 private endpoint';
COMMENT ON COLUMN shadow_runs.no_ledger_mutation IS '固定为 true，表示 Shadow Run 禁止修改 ledger 或账务事实';
COMMENT ON COLUMN shadow_runs.no_account_mutation IS '固定为 true，表示 Shadow Run 禁止修改真实账户事实';
COMMENT ON COLUMN shadow_runs.no_external_private_io IS '固定为 true，表示 Shadow Run 禁止外部私有 IO';
COMMENT ON COLUMN shadow_runs.authorization_boundary IS '授权边界枚举：DIAGNOSTIC_ONLY、REVIEW_ONLY、REPLAY_ONLY；只表达诊断/复盘边界，不表达交易授权';
COMMENT ON COLUMN shadow_runs.request_id IS '创建请求 ID，可空；用于本地审计和幂等追踪，不保存原始请求体';
COMMENT ON COLUMN shadow_runs.idempotency_key IS '创建幂等键，唯一；重复创建必须返回同一 Shadow Run';
COMMENT ON COLUMN shadow_runs.trace_id IS '追踪 ID，串联 run、event、snapshot 和 report，不保存 credential 或 token';
COMMENT ON COLUMN shadow_runs.blockers IS '阻断原因 JSONB 数组，仅保存脱敏复盘原因，不保存凭证、私有请求、真实账户余额或真实订单状态';
COMMENT ON COLUMN shadow_runs.warnings IS '警告 JSONB 数组，仅保存脱敏诊断信息';
COMMENT ON COLUMN shadow_runs.next_steps IS '后续动作 JSONB 数组，仅保存人工复核建议，不表示交易批准';
COMMENT ON COLUMN shadow_runs.version IS '乐观锁版本号；状态更新必须带 expected version，防止并发覆盖和终态回写';
COMMENT ON COLUMN shadow_runs.created_at IS 'Shadow Run 本地事实创建时间';
COMMENT ON COLUMN shadow_runs.updated_at IS 'Shadow Run 本地事实最近更新时间';
COMMENT ON COLUMN shadow_runs.started_at IS 'Shadow Run 本地无副作用运行开始时间；不表示真实交易开始';
COMMENT ON COLUMN shadow_runs.stopped_at IS 'Shadow Run 本地无副作用停止时间；不表示撤单或交易停止';
COMMENT ON COLUMN shadow_runs.completed_at IS 'Shadow Run 本地事实完成时间；不表示交易批准或 LIVE ready';

COMMENT ON TABLE shadow_run_events IS 'GateR-2 Shadow Run append-only 事件表：记录状态流转、阻断、失败、非法流转尝试和审计事件；不保存 credential material，不调用真实交易';
COMMENT ON COLUMN shadow_run_events.id IS 'Shadow Run 事件主键，UUID';
COMMENT ON COLUMN shadow_run_events.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';
COMMENT ON COLUMN shadow_run_events.event_type IS '事件类型枚举：CREATED、PRECHECK_STARTED、PRECHECK_PASSED、PRECHECK_BLOCKED、RUN_STARTED、STOP_REQUESTED、STOPPED、COMPLETED、FAILED、CANCELLED、ILLEGAL_STATE_TRANSITION_ATTEMPT、SNAPSHOT_CAPTURED、CONSISTENCY_REPORT_GENERATED';
COMMENT ON COLUMN shadow_run_events.from_status IS '事件发生前状态；仅用于状态迁移审计';
COMMENT ON COLUMN shadow_run_events.to_status IS '事件目标状态；非法流转事件不更新主表状态';
COMMENT ON COLUMN shadow_run_events.reason_code IS '结构化原因码；不得包含密钥、token、cookie 或真实交易所响应';
COMMENT ON COLUMN shadow_run_events.message IS '脱敏摘要，不保存原始 private request、raw response、签名串或 credential material';
COMMENT ON COLUMN shadow_run_events.metadata IS '脱敏事件元数据 JSONB，只保存本地上下文和计数，不保存 credential、raw request、raw response 或 private endpoint payload';
COMMENT ON COLUMN shadow_run_events.request_id IS '事件来源请求 ID，可空；不保存原始请求体';
COMMENT ON COLUMN shadow_run_events.trace_id IS '事件追踪 ID，串联本地审计链';
COMMENT ON COLUMN shadow_run_events.created_at IS '事件写入时间；事件表按 append-only 使用';

COMMENT ON TABLE shadow_run_snapshots IS 'GateR-2 Shadow Run 快照表：保存输入行情、策略决策、风险预检和订单意图预览的本地脱敏快照；不保存 credential material，不保存真实订单状态，不代表交易授权';
COMMENT ON COLUMN shadow_run_snapshots.id IS 'Shadow Run 快照主键，UUID';
COMMENT ON COLUMN shadow_run_snapshots.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';
COMMENT ON COLUMN shadow_run_snapshots.snapshot_type IS '快照类型：INPUT_MARKETDATA、STRATEGY_DECISION、RISK_PREFLIGHT、ORDER_INTENT_PREVIEW';
COMMENT ON COLUMN shadow_run_snapshots.sequence_no IS '同一 Shadow Run 与快照类型内的顺序号，用于 replay / review';
COMMENT ON COLUMN shadow_run_snapshots.source IS '快照来源，例如 dataset、strategy、risk-preflight、order-intent-preview；不保存 private endpoint payload';
COMMENT ON COLUMN shadow_run_snapshots.schema_version IS '快照结构版本，用于后续兼容读取';
COMMENT ON COLUMN shadow_run_snapshots.checksum IS '快照内容校验摘要，不得为空；用于证明本地脱敏快照一致性';
COMMENT ON COLUMN shadow_run_snapshots.payload IS '脱敏快照 JSONB；禁止保存 credential、private request/response、真实账户余额、真实订单状态或交易所私有 payload';
COMMENT ON COLUMN shadow_run_snapshots.captured_at IS '快照捕获时间，表示本地事实时间';
COMMENT ON COLUMN shadow_run_snapshots.trace_id IS '快照追踪 ID，串联本地审计链';
COMMENT ON COLUMN shadow_run_snapshots.created_at IS '快照写入时间';

COMMENT ON TABLE shadow_consistency_reports IS 'GateR-2 Paper vs Shadow 一致性报告表：只表达复盘和差异分析，不表达交易授权，不表示 LIVE ready，不保存 credential material';
COMMENT ON COLUMN shadow_consistency_reports.id IS '一致性报告主键，UUID';
COMMENT ON COLUMN shadow_consistency_reports.shadow_run_id IS '所属 Shadow Run，本地外键 shadow_runs.id';
COMMENT ON COLUMN shadow_consistency_reports.paper_run_id IS '可空 Paper run 引用，仅用于复盘对照，不写 Paper 事实';
COMMENT ON COLUMN shadow_consistency_reports.comparison_status IS '一致性状态：CONSISTENT、DIVERGED、NOT_COMPARABLE、PARTIAL、FAILED；不包含授权或批准语义';
COMMENT ON COLUMN shadow_consistency_reports.metric_delta IS '脱敏指标差异 JSONB，只保存复盘指标，不保存真实账户余额、真实持仓或真实订单 ID';
COMMENT ON COLUMN shadow_consistency_reports.divergence_reasons IS '偏离原因 JSONB 数组，只保存脱敏复盘原因';
COMMENT ON COLUMN shadow_consistency_reports.limitations IS '限制与不可比说明 JSONB 数组；必须显式表达缺失或不可比，不得补造成功态';
COMMENT ON COLUMN shadow_consistency_reports.generated_at IS '报告生成时间';
COMMENT ON COLUMN shadow_consistency_reports.trace_id IS '报告追踪 ID，串联本地审计链';
COMMENT ON COLUMN shadow_consistency_reports.created_at IS '报告写入时间';
