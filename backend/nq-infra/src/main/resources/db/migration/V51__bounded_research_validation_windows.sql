SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='120s';

DO $$
DECLARE window_constraint text;
BEGIN
 SELECT conname INTO STRICT window_constraint FROM pg_constraint
 WHERE conrelid='experiment_validation_steps'::regclass AND contype='c'
   AND pg_get_constraintdef(oid)='CHECK (((end_bar > start_bar) AND (end_bar <= 720)))';
 EXECUTE format('ALTER TABLE experiment_validation_steps DROP CONSTRAINT %I',window_constraint);
END;
$$;
ALTER TABLE experiment_validation_steps ADD CONSTRAINT experiment_validation_steps_end_bar_check
 CHECK(end_bar>start_bar AND end_bar<=4320);

-- 长研究政策只接受两个固定预设；旧政策不能借总上限增加而改变已发布窗口含义。
CREATE FUNCTION nq_guard_research_validation_window() RETURNS trigger LANGUAGE plpgsql
 SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE
 policy jsonb;
 source_input jsonb;
 total integer;
 width integer;
 expected_start integer;
 expected_end integer;
 expected_role text;
BEGIN
 SELECT v.frozen_policy,e.frozen_input INTO policy,source_input
 FROM experiment_validations v JOIN strategy_experiments e ON e.experiment_id=v.source_experiment_id
 WHERE v.validation_id=NEW.validation_id AND v.tenant_id=nq_current_tenant_id();
 total := (policy->>'barCount')::integer;
 IF total IS NULL OR (
    (policy->>'version'='EXPANDING_WALK_FORWARD_V1' AND total=720)
    OR (policy->>'version'='EXPANDING_WALK_FORWARD_V2' AND total IN (2160,4320))) IS NOT TRUE
    OR total IS DISTINCT FROM (source_input->'datasetSnapshot'->>'barCount')::integer
    OR policy->>'datasetSha256' IS DISTINCT FROM source_input->'datasetSnapshot'->'frozenInput'->>'sha256'
    OR jsonb_typeof(policy->'candidates') IS DISTINCT FROM 'array'
    OR jsonb_array_length(policy->'candidates') NOT BETWEEN 1 AND (CASE WHEN total=720 THEN 16 ELSE 4 END) THEN
   RAISE EXCEPTION 'RESEARCH_VALIDATION_POLICY_MISMATCH' USING ERRCODE='23514';
 END IF;
 width := total/6;
 expected_end := width*(NEW.step_number/4+2);
 expected_start := CASE WHEN NEW.step_number%4=0 THEN 0 ELSE expected_end END;
 expected_end := CASE WHEN NEW.step_number%4=0 THEN expected_end ELSE expected_end+width END;
 expected_role := CASE NEW.step_number%4 WHEN 0 THEN 'IN_SAMPLE'
    WHEN 1 THEN CASE WHEN NEW.step_number/4=3 THEN 'FINAL_HOLDOUT' ELSE 'OUT_OF_SAMPLE' END
    WHEN 2 THEN 'FEE_STRESS' ELSE 'SLIPPAGE_STRESS' END;
 IF NEW.fold_number<>NEW.step_number/4+1 OR NEW.start_bar<>expected_start
    OR NEW.end_bar<>expected_end OR NEW.role<>expected_role THEN
   RAISE EXCEPTION 'RESEARCH_VALIDATION_WINDOW_MISMATCH' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_research_validation_window BEFORE INSERT OR UPDATE ON experiment_validation_steps
 FOR EACH ROW EXECUTE FUNCTION nq_guard_research_validation_window();
COMMENT ON FUNCTION nq_guard_research_validation_window() IS '按不可变父输入及政策版本核验预设窗口；不改变旧三十日合同';
