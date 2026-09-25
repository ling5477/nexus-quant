-- 规则原始响应与解析后的规则身份各自冻结；不改写已执行的捕获迁移。
SET lock_timeout = '5s';

ALTER TABLE public_market_captures
    ADD COLUMN rule_request_path TEXT,
    ADD COLUMN rule_raw_response TEXT,
    ADD COLUMN rule_raw_sha256 CHAR(64);

-- 既存记录保持原义；新捕获必须同时写入三项原始身份。
ALTER TABLE public_market_captures
    ADD CONSTRAINT chk_public_market_rule_raw_complete CHECK (
        (rule_request_path IS NULL AND rule_raw_response IS NULL AND rule_raw_sha256 IS NULL)
        OR (rule_request_path IS NOT NULL AND rule_raw_response IS NOT NULL AND rule_raw_sha256 IS NOT NULL)
    );

CREATE OR REPLACE FUNCTION require_public_market_rule_raw_identity() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.rule_request_path IS NULL OR NEW.rule_raw_response IS NULL
            OR NEW.rule_raw_sha256 IS NULL THEN
        RAISE EXCEPTION 'public rule raw identity required for new capture';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_public_market_rule_raw_required
BEFORE INSERT ON public_market_captures
FOR EACH ROW EXECUTE FUNCTION require_public_market_rule_raw_identity();

COMMENT ON COLUMN public_market_captures.rule_raw_sha256 IS
    '公开 instrument 原始 HTTP 响应 UTF-8 字节的 SHA-256；与 rule_sha256 规范化选择字段身份分开。';
