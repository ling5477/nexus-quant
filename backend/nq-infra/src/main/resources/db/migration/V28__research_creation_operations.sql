CREATE TABLE research_creation_operations (
    operation_id UUID PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN')),
    parent_key VARCHAR(128) NOT NULL DEFAULT '',
    request_json JSONB NOT NULL CHECK (jsonb_typeof(request_json) = 'object' AND octet_length(request_json::text) <= 262144),
    object_id VARCHAR(64),
    lookup_key VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((object_id IS NULL AND lookup_key IS NULL) OR (length(object_id) > 0 AND length(lookup_key) > 0))
);
COMMENT ON TABLE research_creation_operations IS '研究链创建命令的原请求与成功对象身份；与正式创建共用事务，不复制业务事实';
COMMENT ON COLUMN research_creation_operations.operation_id IS '客户端在首次发送前持久保存的稳定UUID；未知结果重试不得更换';
COMMENT ON COLUMN research_creation_operations.owner_user_id IS '认证主体所有者，结果读取严格按此隔离';
COMMENT ON COLUMN research_creation_operations.kind IS '限定五种创建操作；不包含执行、发布或交易写入';
COMMENT ON COLUMN research_creation_operations.parent_key IS '版本创建的正式父策略编码，其余操作为空';
COMMENT ON COLUMN research_creation_operations.request_json IS '冻结的原始输入，重复提交必须语义一致';
COMMENT ON COLUMN research_creation_operations.object_id IS '原正式服务创建的唯一对象身份';
COMMENT ON COLUMN research_creation_operations.lookup_key IS '读取正式对象所需的原编码或ID，不存储可变业务状态';
COMMENT ON COLUMN research_creation_operations.created_at IS '命令与创建事实的同事务登记时间';
