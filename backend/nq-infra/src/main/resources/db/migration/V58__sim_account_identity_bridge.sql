-- SIM 账户可绑定确定性的兼容身份；既有 OKX/LIVE 身份和不可变约束保持原合同。
SET LOCAL lock_timeout = '5s';

CREATE OR REPLACE FUNCTION gate_y45_guard_canonical_legacy_bridge()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_account accounts%ROWTYPE;
    v_expected_code TEXT;
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.legacy_account_id IS NOT NULL)
        OR (TG_OP = 'UPDATE' AND OLD.legacy_account_id IS NULL
            AND NEW.legacy_account_id IS NOT NULL) THEN
        SELECT * INTO STRICT v_account FROM accounts
        WHERE account_id=NEW.legacy_account_id FOR KEY SHARE;
        IF NEW.trade_env = 'LIVE' AND NEW.exchange_code = 'OKX' THEN
            v_expected_code := gate_y45_canonical_legacy_account_code(NEW.exchange_account_id);
        ELSIF NEW.trade_env = 'SIM' AND NEW.exchange_code IN ('OKX', 'BINANCE') THEN
            v_expected_code := 'nq-' || lower(NEW.exchange_code) || '-sim-'
                || NEW.exchange_account_id::TEXT;
        ELSE
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is invalid';
        END IF;
        IF NEW.status <> 'ACTIVE' OR v_account.account_code <> v_expected_code
            OR v_account.venue <> NEW.exchange_code OR v_account.status <> 'ACTIVE' THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is invalid';
        END IF;
    ELSIF TG_OP = 'UPDATE' AND OLD.legacy_account_id IS NOT NULL
        AND NEW.legacy_account_id IS DISTINCT FROM OLD.legacy_account_id THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='canonical legacy account bridge is immutable';
    END IF;
    RETURN NEW;
END;
$$;
