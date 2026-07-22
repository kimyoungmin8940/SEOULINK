-- Align the legacy COURSE_DETAILS primary-key column with CourseDetail.java.
-- Safe to run once; existing identity values, primary key, and data are preserved.

DECLARE
    v_legacy_column_count NUMBER;
    v_target_column_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_legacy_column_count
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'COURSE_DETAILS'
      AND COLUMN_NAME = 'DETAIL_ID';

    SELECT COUNT(*) INTO v_target_column_count
    FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'COURSE_DETAILS'
      AND COLUMN_NAME = 'COURSE_DETAIL_ID';

    IF v_legacy_column_count = 1 AND v_target_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COURSE_DETAILS RENAME COLUMN DETAIL_ID TO COURSE_DETAIL_ID';
    END IF;
END;
/
