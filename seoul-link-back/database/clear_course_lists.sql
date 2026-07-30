/*
 * 개발 DB에서 지정한 회원의 내 코스·추천받은 코스 전체보기 데이터를
 * 모두 비우는 1회용 스크립트입니다.
 *
 * 삭제 범위:
 * - 대상 회원의 COURSE_DETAILS
 * - 대상 회원의 TRAVEL_COURSES
 *
 * 장소·회원·설문·설문 결과·후기·챗봇 대화 자체는 삭제하지 않습니다.
 * 후기와 챗봇 이력이 삭제 대상 코스를 참조하면 COURSE_ID 연결만 해제합니다.
 *
 * 실행 전 아래 조회로 MEMBER_ID를 확인한 다음, DBeaver가
 * TARGET_MEMBER_ID 값을 물으면 해당 번호를 입력합니다.
 */

SELECT MEMBER_ID, EMAIL, NAME, NICKNAME
  FROM MEMBER
 ORDER BY MEMBER_ID;

UPDATE CHATBOT_HISTORY
   SET COURSE_ID = NULL
 WHERE COURSE_ID IN (
           SELECT COURSE_ID
             FROM TRAVEL_COURSES
            WHERE MEMBER_ID = :TARGET_MEMBER_ID
       );

UPDATE REVIEW
   SET COURSE_ID = NULL
 WHERE COURSE_ID IN (
           SELECT COURSE_ID
             FROM TRAVEL_COURSES
            WHERE MEMBER_ID = :TARGET_MEMBER_ID
       );

DELETE FROM COURSE_DETAILS
 WHERE COURSE_ID IN (
           SELECT COURSE_ID
             FROM TRAVEL_COURSES
            WHERE MEMBER_ID = :TARGET_MEMBER_ID
       );

DELETE FROM TRAVEL_COURSES
 WHERE MEMBER_ID = :TARGET_MEMBER_ID;

COMMIT;

SELECT COUNT(*) AS REMAINING_TARGET_COURSES
  FROM TRAVEL_COURSES
 WHERE MEMBER_ID = :TARGET_MEMBER_ID;
