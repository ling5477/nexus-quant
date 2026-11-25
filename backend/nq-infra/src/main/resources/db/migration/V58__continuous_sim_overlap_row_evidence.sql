SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';

ALTER TABLE continuous_sim_overlap_checks
    ADD COLUMN response_row_failures jsonb,
    ADD CONSTRAINT ck_continuous_overlap_row_failures CHECK (
        response_row_failures IS NULL OR (response_captured_at IS NOT NULL
            AND CASE WHEN jsonb_typeof(response_row_failures)='array'
                THEN jsonb_array_length(response_row_failures)<=24
                    AND octet_length(response_row_failures::text)<=4096
                    AND (captured_result<>'VERIFIED' OR response_row_failures='[]'::jsonb)
                ELSE false END));

CREATE FUNCTION nq_guard_continuous_overlap_row_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.response_captured_at IS NOT NULL
        AND OLD.response_row_failures IS DISTINCT FROM NEW.response_row_failures THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_OVERLAP_ROW_EVIDENCE_IMMUTABLE' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_continuous_overlap_row_evidence_immutable BEFORE UPDATE ON continuous_sim_overlap_checks
    FOR EACH ROW EXECUTE FUNCTION nq_guard_continuous_overlap_row_evidence();

COMMENT ON COLUMN continuous_sim_overlap_checks.response_row_failures IS
    '响应 data 中无效元素的零基索引及原因。旧解析器未证明其遍历范围，保留空值且不可据此补读。';
