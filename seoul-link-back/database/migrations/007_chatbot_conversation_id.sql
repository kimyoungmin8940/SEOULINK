-- Safe to rerun: bring legacy chatbot history up to the current session-based model.
DECLARE
    v_column_count NUMBER;
    v_nullable VARCHAR2(1);
    v_index_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_column_count
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'CHATBOT_HISTORY'
      AND COLUMN_NAME = 'CONVERSATION_ID';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CHATBOT_HISTORY ADD (CONVERSATION_ID VARCHAR2(36))';
    END IF;

    -- Backfill a stable session key before enforcing the NOT NULL constraint.
    UPDATE CHATBOT_HISTORY
    SET CONVERSATION_ID = 'legacy-' || CHAT_ID
    WHERE CONVERSATION_ID IS NULL;

    SELECT NULLABLE INTO v_nullable
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'CHATBOT_HISTORY'
      AND COLUMN_NAME = 'CONVERSATION_ID';

    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CHATBOT_HISTORY MODIFY (CONVERSATION_ID NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO v_index_count
    FROM USER_INDEXES
    WHERE INDEX_NAME = 'IDX_CHATBOT_HISTORY_MEMBER_CONVERSATION';

    IF v_index_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_CHATBOT_HISTORY_MEMBER_CONVERSATION '
            || 'ON CHATBOT_HISTORY (MEMBER_ID, CONVERSATION_ID, CREATED_AT DESC)';
    END IF;
END;

COMMENT ON COLUMN CHATBOT_HISTORY.CONVERSATION_ID IS 'Client-generated UUID identifying one continuous chatbot conversation';

COMMIT;
