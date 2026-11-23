SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE research_dataset_versions (
    dataset_version_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL DEFAULT nq_current_tenant_id() REFERENCES tenants(tenant_id),
    owner_user_id bigint NOT NULL REFERENCES users(id),
    dataset_id uuid NOT NULL REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT,
    bar_count integer NOT NULL CHECK(bar_count BETWEEN 721 AND 4320),
    chunk_count integer NOT NULL CHECK(chunk_count BETWEEN 2 AND 9),
    total_bytes integer NOT NULL CHECK(total_bytes BETWEEN 1 AND 8388608),
    content_sha256 varchar(64) NOT NULL CHECK(content_sha256 ~ '^[0-9a-f]{64}$'),
    source_sha256 varchar(64) NOT NULL CHECK(source_sha256 ~ '^[0-9a-f]{64}$'),
    manifest_sha256 varchar(64) NOT NULL CHECK(manifest_sha256 ~ '^[0-9a-f]{64}$'),
    frozen_at timestamptz NOT NULL CHECK(isfinite(frozen_at)),
    manifest_json text NOT NULL CHECK(octet_length(manifest_json)<=65536 AND jsonb_typeof(manifest_json::jsonb)='object'),
    reference_json text NOT NULL CHECK(octet_length(reference_json)<=8192 AND jsonb_typeof(reference_json::jsonb)='object'),
    UNIQUE(tenant_id,owner_user_id,dataset_id)
);
COMMENT ON TABLE research_dataset_versions IS '仅用于长历史研究的首次不可变冻结版本；后续配置和Run复用引用，不改变SIM输入准入';
COMMENT ON COLUMN research_dataset_versions.owner_user_id IS '从认证主体取得的研究所有者；后台仅在原正式Run授权链中恢复读取';
COMMENT ON COLUMN research_dataset_versions.dataset_id IS '原Dataset父身份；同租户同所有者的首次冻结固定，刷新共享行情不覆盖此版本';
COMMENT ON COLUMN research_dataset_versions.manifest_json IS '规范UTF-8 JSON原文，保留字节摘要；含父范围、原观察与回放假设、内容摘要及有界分块描述';
COMMENT ON COLUMN research_dataset_versions.reference_json IS '配置与Run保存的紧凑V2引用，不嵌入完整行情';
COMMENT ON COLUMN research_dataset_versions.content_sha256 IS '按原bar规范字段计算的回放消费摘要；原观察摘要另存source_sha256';
COMMENT ON COLUMN research_dataset_versions.frozen_at IS '实际冻结时间，不冒充历史供应方发布时间';

CREATE TABLE research_dataset_chunks (
    dataset_version_id uuid NOT NULL REFERENCES research_dataset_versions(dataset_version_id) ON DELETE RESTRICT,
    ordinal integer NOT NULL CHECK(ordinal BETWEEN 0 AND 8),
    start_bar integer NOT NULL CHECK(start_bar BETWEEN 0 AND 4319),
    bar_count integer NOT NULL CHECK(bar_count BETWEEN 1 AND 500),
    byte_count integer NOT NULL CHECK(byte_count BETWEEN 1 AND 524288),
    content_sha256 varchar(64) NOT NULL CHECK(content_sha256 ~ '^[0-9a-f]{64}$'),
    canonical_json text NOT NULL CHECK(octet_length(canonical_json)=byte_count AND jsonb_typeof(canonical_json::jsonb)='object'),
    PRIMARY KEY(dataset_version_id,ordinal),
    UNIQUE(dataset_version_id,start_bar)
);
COMMENT ON TABLE research_dataset_chunks IS '每块最多500根的研究冻结原文；序号、范围、数量、字节及摘要在读取时共同复核';
COMMENT ON COLUMN research_dataset_chunks.canonical_json IS '规范JSON原文，保留回放bar与对应原观察时间数组；不使用JSONB重排后字节作为原摘要';
COMMENT ON COLUMN research_dataset_chunks.content_sha256 IS '该块规范UTF-8字节的SHA256，由不可变manifest绑定';

ALTER TABLE research_dataset_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_dataset_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY research_version_read ON research_dataset_versions FOR SELECT USING(
    nq_tenant_is_current(tenant_id) AND (
        NULLIF(current_setting('nq.user_id',true),'') IS NULL
        OR owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint));
CREATE POLICY research_version_insert ON research_dataset_versions FOR INSERT WITH CHECK(
    nq_tenant_is_current(tenant_id)
    AND owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
    AND EXISTS(SELECT 1 FROM tenant_memberships m JOIN tenants t USING(tenant_id)
        WHERE m.tenant_id=research_dataset_versions.tenant_id AND m.user_id=owner_user_id
            AND m.status='ACTIVE' AND t.tenant_kind='LOCAL' AND t.status='ACTIVE')
    AND EXISTS(SELECT 1 FROM marketdata_datasets d WHERE d.dataset_id=research_dataset_versions.dataset_id
        AND d.tenant_id=research_dataset_versions.tenant_id AND d.visibility='TENANT_PRIVATE'));
ALTER TABLE research_dataset_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_dataset_chunks FORCE ROW LEVEL SECURITY;
CREATE POLICY research_chunk_read ON research_dataset_chunks FOR SELECT USING(EXISTS(
    SELECT 1 FROM research_dataset_versions v WHERE v.dataset_version_id=research_dataset_chunks.dataset_version_id));
CREATE POLICY research_chunk_insert ON research_dataset_chunks FOR INSERT WITH CHECK(EXISTS(
    SELECT 1 FROM research_dataset_versions v WHERE v.dataset_version_id=research_dataset_chunks.dataset_version_id
        AND v.owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint));

CREATE FUNCTION nq_guard_research_dataset_history() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'RESEARCH_DATASET_HISTORY_IMMUTABLE' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER trg_research_dataset_version_immutable BEFORE UPDATE OR DELETE ON research_dataset_versions
FOR EACH ROW EXECUTE FUNCTION nq_guard_research_dataset_history();
CREATE TRIGGER trg_research_dataset_chunk_immutable BEFORE UPDATE OR DELETE ON research_dataset_chunks
FOR EACH ROW EXECUTE FUNCTION nq_guard_research_dataset_history();

CREATE FUNCTION nq_check_research_dataset_complete() RETURNS trigger
LANGUAGE plpgsql SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE version_row research_dataset_versions%ROWTYPE; counts record;
BEGIN
    SELECT * INTO STRICT version_row FROM research_dataset_versions WHERE dataset_version_id=NEW.dataset_version_id;
    SELECT count(*) AS chunks,coalesce(sum(bar_count),0) AS bars,coalesce(sum(byte_count),0) AS bytes,
        min(ordinal) AS first_ordinal,max(ordinal) AS last_ordinal,
        coalesce(bool_and(start_bar=ordinal*500 AND bar_count=least(500,version_row.bar_count-start_bar)),false) AS ordered
    INTO counts FROM research_dataset_chunks WHERE dataset_version_id=NEW.dataset_version_id;
    IF counts.chunks<>version_row.chunk_count OR counts.bars<>version_row.bar_count
        OR counts.bytes<>version_row.total_bytes OR counts.first_ordinal<>0
        OR counts.last_ordinal<>version_row.chunk_count-1 OR NOT counts.ordered THEN
        RAISE EXCEPTION 'RESEARCH_DATASET_CHUNKS_INCOMPLETE' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_research_dataset_version_complete AFTER INSERT ON research_dataset_versions
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_check_research_dataset_complete();
CREATE CONSTRAINT TRIGGER trg_research_dataset_chunk_complete AFTER INSERT ON research_dataset_chunks
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_check_research_dataset_complete();
COMMENT ON FUNCTION nq_check_research_dataset_complete() IS '提交时拒绝不完整、乱序或超预算的研究分块，避免部分冻结对其他事务可见';
