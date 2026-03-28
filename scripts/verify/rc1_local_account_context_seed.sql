BEGIN;

DO $$
DECLARE
    v_owner_user_id BIGINT;
    v_target_exchange_account_id BIGINT;
    v_conflict_owner_user_id BIGINT;
BEGIN
    SELECT id
    INTO v_owner_user_id
    FROM users
    WHERE username = 'admin';

    IF v_owner_user_id IS NULL THEN
        RAISE EXCEPTION 'RC1 local account context seed requires admin user in users table';
    END IF;

    SELECT owner_user_id
    INTO v_conflict_owner_user_id
    FROM exchange_accounts
    WHERE exchange_account_id = 900001
      AND owner_user_id <> v_owner_user_id
    LIMIT 1;

    IF v_conflict_owner_user_id IS NOT NULL THEN
        RAISE EXCEPTION 'exchange_account_id=900001 already belongs to owner_user_id=%', v_conflict_owner_user_id;
    END IF;

    SELECT exchange_account_id
    INTO v_target_exchange_account_id
    FROM exchange_accounts
    WHERE owner_user_id = v_owner_user_id
      AND (
          exchange_account_id = 900001
          OR legacy_account_id = 900001
          OR (
              exchange_code = 'OKX'
              AND trade_env = 'SIM'
              AND account_alias = 'rc1-admin-default'
          )
      )
    ORDER BY CASE WHEN exchange_account_id = 900001 THEN 0 ELSE 1 END, exchange_account_id
    LIMIT 1;

    UPDATE exchange_accounts
    SET is_default = FALSE,
        updated_at = NOW()
    WHERE owner_user_id = v_owner_user_id
      AND is_default = TRUE
      AND (
          v_target_exchange_account_id IS NULL
          OR exchange_account_id <> v_target_exchange_account_id
      );

    IF v_target_exchange_account_id IS NULL THEN
        INSERT INTO exchange_accounts (
            exchange_account_id,
            owner_user_id,
            exchange_code,
            trade_env,
            account_alias,
            external_account_ref,
            legacy_account_id,
            is_default,
            status,
            created_at,
            updated_at
        )
        VALUES (
            900001,
            v_owner_user_id,
            'OKX',
            'SIM',
            'rc1-admin-default',
            NULL,
            900001,
            TRUE,
            'ACTIVE',
            NOW(),
            NOW()
        )
        RETURNING exchange_account_id INTO v_target_exchange_account_id;
    ELSE
        UPDATE exchange_accounts
        SET exchange_account_id = 900001,
            exchange_code = 'OKX',
            trade_env = 'SIM',
            account_alias = 'rc1-admin-default',
            status = 'ACTIVE',
            legacy_account_id = 900001,
            is_default = TRUE,
            updated_at = NOW()
        WHERE exchange_account_id = v_target_exchange_account_id;
    END IF;

    PERFORM setval(
        pg_get_serial_sequence('exchange_accounts', 'exchange_account_id'),
        GREATEST(
            900001,
            COALESCE((SELECT MAX(exchange_account_id) FROM exchange_accounts), 900001)
        ),
        TRUE
    );

    UPDATE exchange_accounts
    SET is_default = FALSE,
        updated_at = NOW()
    WHERE owner_user_id = v_owner_user_id
      AND is_default = TRUE
      AND exchange_account_id <> 900001;
END $$;

COMMIT;
