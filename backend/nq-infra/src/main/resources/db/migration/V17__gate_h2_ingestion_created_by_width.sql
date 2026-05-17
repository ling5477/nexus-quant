-- V17__gate_h2_ingestion_created_by_width.sql
-- GateH-2: allow Spring Security principal names to be stored as audit labels.

ALTER TABLE marketdata_ingestion_jobs
    ALTER COLUMN created_by TYPE VARCHAR(512);

COMMENT ON COLUMN marketdata_ingestion_jobs.created_by IS '创建任务的用户标识，用于审计；允许最长 512 字符以兼容 Spring Security principal name，本字段不保存 token、密钥或 cookie';
