-- Existing Oracle DB migration for social login support.
-- Run once in DBeaver or SQL Developer against the existing SEOULINK schema.
-- The migration preserves existing LOCAL members and stops if legacy social
-- members cannot be safely identified by their provider-issued user ID.

DECLARE
    v_count NUMBER;
    v_invalid_count NUMBER;

    PROCEDURE add_column_if_missing(p_column_name VARCHAR2, p_definition VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = 'MEMBER'
          AND COLUMN_NAME = p_column_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD (' || p_definition || ')';
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(p_constraint_name VARCHAR2, p_definition VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM USER_CONSTRAINTS
        WHERE TABLE_NAME = 'MEMBER'
          AND CONSTRAINT_NAME = p_constraint_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE MEMBER ADD CONSTRAINT '
                || p_constraint_name || ' ' || p_definition;
        END IF;
    END;
BEGIN
    add_column_if_missing('LOGIN_TYPE', 'LOGIN_TYPE VARCHAR2(20) DEFAULT ''LOCAL'' NOT NULL');
    add_column_if_missing('SOCIAL_PROVIDER', 'SOCIAL_PROVIDER VARCHAR2(30)');
    add_column_if_missing('SOCIAL_ID', 'SOCIAL_ID VARCHAR2(100)');

    UPDATE MEMBER
    SET LOGIN_TYPE = 'LOCAL'
    WHERE LOGIN_TYPE IS NULL;

    SELECT COUNT(*) INTO v_invalid_count
    FROM MEMBER
    WHERE (LOGIN_TYPE = 'LOCAL' AND (SOCIAL_PROVIDER IS NOT NULL OR SOCIAL_ID IS NOT NULL))
       OR (LOGIN_TYPE = 'SOCIAL' AND (SOCIAL_PROVIDER IS NULL OR SOCIAL_ID IS NULL))
       OR LOGIN_TYPE NOT IN ('LOCAL', 'SOCIAL');

    IF v_invalid_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20001,
            'MEMBER has legacy social-login rows without a valid provider/user ID. Correct those rows before rerunning migration 005.'
        );
    END IF;

    add_constraint_if_missing('UK_MEMBER_SOCIAL', 'UNIQUE (SOCIAL_PROVIDER, SOCIAL_ID)');
    add_constraint_if_missing('CK_MEMBER_LOGIN_TYPE', 'CHECK (LOGIN_TYPE IN (''LOCAL'', ''SOCIAL''))');
    add_constraint_if_missing('CK_MEMBER_SOCIAL_PROVIDER', 'CHECK (SOCIAL_PROVIDER IS NULL OR SOCIAL_PROVIDER IN (''GOOGLE'', ''NAVER'', ''KAKAO''))');
    add_constraint_if_missing(
        'CK_MEMBER_LOGIN_RULE',
        'CHECK ((LOGIN_TYPE = ''LOCAL'' AND SOCIAL_PROVIDER IS NULL AND SOCIAL_ID IS NULL) OR (LOGIN_TYPE = ''SOCIAL'' AND SOCIAL_PROVIDER IS NOT NULL AND SOCIAL_ID IS NOT NULL))'
    );
END;

COMMENT ON COLUMN MEMBER.SOCIAL_PROVIDER IS '소셜 로그인 제공자, GOOGLE/NAVER/KAKAO 중 하나';
COMMENT ON COLUMN MEMBER.SOCIAL_ID IS '소셜 로그인 제공자가 발급한 사용자 고유 ID';
COMMENT ON COLUMN MEMBER.LOGIN_TYPE IS '로그인 유형, 일반 로그인은 LOCAL, 소셜 로그인은 SOCIAL';
