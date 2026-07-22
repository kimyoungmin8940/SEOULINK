-- 이미 생성된 COURSE_DETAILS에 ODsay 최적 대중교통 경로 종류를 추가한다.
-- 전체 seoulink.sql을 다시 실행하지 말고 이 파일만 한 번 실행한다.

ALTER TABLE COURSE_DETAILS
    ADD TRANSIT_PATH_TYPE VARCHAR2(20);

ALTER TABLE COURSE_DETAILS
    ADD CONSTRAINT CK_CDETAIL_TRANSIT_TYPE
        CHECK (
            TRANSIT_PATH_TYPE IS NULL
                OR TRANSIT_PATH_TYPE IN (
                    'SUBWAY',
                    'BUS',
                    'BUS_SUBWAY'
                   )
            );

COMMENT ON COLUMN COURSE_DETAILS.TRANSIT_PATH_TYPE
    IS 'ODsay 대중교통 최적 경로 종류, SUBWAY/BUS/BUS_SUBWAY';

COMMIT;
