-- Existing Oracle DB migration: protect PG approval keys and keep KRW amounts integral.
-- The migration stops rather than silently changing payment data that needs reconciliation.
DECLARE
    v_constraint_count NUMBER;
    v_fractional_amount_count NUMBER;
    v_duplicate_key_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_fractional_amount_count
    FROM PAYMENT
    WHERE AMOUNT <> TRUNC(AMOUNT);

    IF v_fractional_amount_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20009,
            'PAYMENT contains fractional amounts. Reconcile them before applying migration 009.'
        );
    END IF;

    SELECT COUNT(*) INTO v_duplicate_key_count
    FROM (
        SELECT PAYMENT_KEY
        FROM PAYMENT
        WHERE PAYMENT_KEY IS NOT NULL
        GROUP BY PAYMENT_KEY
        HAVING COUNT(*) > 1
    );

    IF v_duplicate_key_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20010,
            'PAYMENT contains duplicate PG approval keys. Reconcile them before applying migration 009.'
        );
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE PAYMENT MODIFY (AMOUNT NUMBER(10, 0))';

    SELECT COUNT(*) INTO v_constraint_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'PAYMENT'
      AND CONSTRAINT_NAME = 'UK_PAYMENT_KEY';

    IF v_constraint_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE PAYMENT ADD CONSTRAINT UK_PAYMENT_KEY UNIQUE (PAYMENT_KEY)';
    END IF;
END;

COMMENT ON COLUMN PAYMENT.PAYMENT_KEY IS 'PG approval key; unique when present to prevent duplicate approval records';
COMMENT ON COLUMN PAYMENT.AMOUNT IS 'Payment amount in Korean won, stored as an integer';

COMMIT;
