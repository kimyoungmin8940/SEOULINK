-- Safe to rerun: normalize legacy companion labels before enforcing the four public values.
DECLARE
    v_constraint_count NUMBER;
BEGIN
    UPDATE REVIEW
    SET COMPANION = CASE COMPANION
        WHEN '친구와 함께' THEN '친구'
        WHEN '연인과 함께' THEN '연인'
        WHEN '부모님과 함께' THEN '가족'
        WHEN '아이와 함께' THEN '가족'
        WHEN '가족과 함께' THEN '가족'
        ELSE COMPANION
    END;

    UPDATE REVIEW
    SET COMPANION = '혼자'
    WHERE COMPANION IS NULL;

    EXECUTE IMMEDIATE 'ALTER TABLE REVIEW MODIFY (COMPANION DEFAULT ''혼자'' NOT NULL)';

    SELECT COUNT(*) INTO v_constraint_count
    FROM USER_CONSTRAINTS
    WHERE TABLE_NAME = 'REVIEW'
      AND CONSTRAINT_NAME = 'CK_REVIEW_COMPANION';

    IF v_constraint_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE REVIEW ADD CONSTRAINT CK_REVIEW_COMPANION '
            || 'CHECK (COMPANION IN (''혼자'', ''연인'', ''친구'', ''가족''))';
    END IF;
END;

COMMIT;
