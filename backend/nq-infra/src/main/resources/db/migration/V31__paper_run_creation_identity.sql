-- 沿用原创建回执的唯一键、主体和输入约束；不改写历史回执或Paper运行。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

ALTER TABLE research_creation_operations
 DROP CONSTRAINT research_creation_operations_kind_check;
ALTER TABLE research_creation_operations
 ADD CONSTRAINT research_creation_operations_kind_check
 CHECK (kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN','SIM_RUN','PAPER_RUN'));

COMMENT ON COLUMN research_creation_operations.kind IS
 '本地研究、隔离SIM或原Research Paper创建；PAPER_RUN仅保存原创建身份，不注资、不启动，不代表可信权益或LIVE授权';
