-- Review list demo data for the current SEOULLINK schema.
-- Run review-feature-migration.sql first, then run this file with SQL Developer F5.

DECLARE
    v_member_id MEMBER.MEMBER_ID%TYPE;
    v_bukchon_id PLACES.PLACE_ID%TYPE;
    v_namsan_id PLACES.PLACE_ID%TYPE;
    v_ikseon_id PLACES.PLACE_ID%TYPE;
    v_course_id TRAVEL_COURSES.COURSE_ID%TYPE;
    v_review_id REVIEW.REVIEW_ID%TYPE;

    PROCEDURE ensure_place(
        p_api_id VARCHAR2, p_name VARCHAR2, p_region VARCHAR2,
        p_address VARCHAR2, p_lat NUMBER, p_lng NUMBER, p_id OUT NUMBER
    ) IS
    BEGIN
        SELECT PLACE_ID INTO p_id
        FROM PLACES
        WHERE API_PROVIDER = 'SEOULLINK_DEMO' AND API_PLACE_ID = p_api_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PLACES (
                API_PROVIDER, API_PLACE_ID, NAME, CATEGORY, REGION, ADDRESS,
                LATITUDE, LONGITUDE, RATING, REVIEW_COUNT, IS_ACTIVE
            ) VALUES (
                'SEOULLINK_DEMO', p_api_id, p_name, 'TOUR', p_region, p_address,
                p_lat, p_lng, 0, 0, 'Y'
            ) RETURNING PLACE_ID INTO p_id;
    END;

    PROCEDURE add_review(
        p_title VARCHAR2, p_content VARCHAR2, p_rating NUMBER, p_place_id NUMBER,
        p_visit_date DATE, p_companion VARCHAR2, p_image VARCHAR2,
        p_tag1 VARCHAR2, p_tag2 VARCHAR2
    ) IS
    BEGIN
        INSERT INTO REVIEW (
            MEMBER_ID, PLACE_ID, COURSE_ID, REVIEW_TITLE, REVIEW_CONTENT, RATING,
            IMAGE_URL, VISIT_DATE, COMPANION, VIEW_COUNT, IS_DELETED
        ) VALUES (
            v_member_id, p_place_id, v_course_id, p_title, p_content, p_rating,
            p_image, p_visit_date, p_companion, 0, 'N'
        ) RETURNING REVIEW_ID INTO v_review_id;

        INSERT INTO REVIEW_IMAGE (REVIEW_ID, IMAGE_URL, DISPLAY_ORDER)
        VALUES (v_review_id, p_image, 0);
        INSERT INTO REVIEW_TAG (REVIEW_ID, TAG_NAME) VALUES (v_review_id, p_tag1);
        INSERT INTO REVIEW_TAG (REVIEW_ID, TAG_NAME) VALUES (v_review_id, p_tag2);
    END;
BEGIN
    SELECT MEMBER_ID INTO v_member_id
    FROM (
        SELECT MEMBER_ID FROM MEMBER
        WHERE STATUS = 'ACTIVE'
        ORDER BY MEMBER_ID
    )
    WHERE ROWNUM = 1;

    ensure_place('BUKCHON_V2', '북촌한옥마을', '서울 종로구', '서울 종로구 계동길', 37.5826, 126.9830, v_bukchon_id);
    ensure_place('NAMSAN_V2', 'N서울타워', '서울 용산구', '서울 용산구 남산공원길 105', 37.5512, 126.9882, v_namsan_id);
    ensure_place('IKSEONDONG_V2', '익선동 한옥거리', '서울 종로구', '서울 종로구 익선동', 37.5730, 126.9897, v_ikseon_id);

    BEGIN
        SELECT COURSE_ID INTO v_course_id
        FROM TRAVEL_COURSES
        WHERE MEMBER_ID = v_member_id AND TITLE = '[DEMO] 서울 감성 산책 코스';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO TRAVEL_COURSES (
                MEMBER_ID, TITLE, DESCRIPTION, COURSE_TYPE, REGION, IS_PUBLIC, VIEW_COUNT
            ) VALUES (
                v_member_id, '[DEMO] 서울 감성 산책 코스',
                '북촌, 익선동, 남산을 잇는 서울 하루 산책', 'CUSTOM', '서울', 'Y', 0
            ) RETURNING COURSE_ID INTO v_course_id;
    END;

    DELETE FROM COURSE_DETAILS WHERE COURSE_ID = v_course_id;
    INSERT INTO COURSE_DETAILS (COURSE_ID, PLACE_ID, DAY_NO, PLACE_ORDER, VISIT_TIME, MEMO)
    VALUES (v_course_id, v_bukchon_id, 1, 1, '09:30', '고즈넉한 한옥 골목 산책');
    INSERT INTO COURSE_DETAILS (COURSE_ID, PLACE_ID, DAY_NO, PLACE_ORDER, VISIT_TIME, MEMO)
    VALUES (v_course_id, v_ikseon_id, 1, 2, '14:00', '한옥 카페에서 여유로운 휴식');
    INSERT INTO COURSE_DETAILS (COURSE_ID, PLACE_ID, DAY_NO, PLACE_ORDER, VISIT_TIME, MEMO)
    VALUES (v_course_id, v_namsan_id, 1, 3, '19:00', '서울 야경 감상');

    -- REVIEW_IMAGE and REVIEW_TAG use ON DELETE CASCADE.
    DELETE FROM REVIEW WHERE REVIEW_TITLE LIKE '[DEMO V2] %';

    add_review('[DEMO V2] 북촌에서 시작한 느린 아침',
        '햇살이 기와지붕을 비추는 시간에 북촌 골목을 걸었습니다. 조용한 골목 끝에서 서울 도심이 보이는 순간이 특히 인상적이었어요.',
        4.9, v_bukchon_id, DATE '2026-07-12', '혼자', '/review-seed/bukchon-sunrise-v2.png', '혼자 여행', '사진 명소');
    add_review('[DEMO V2] 한옥과 도시가 만나는 풍경',
        '오전에 방문하니 한옥의 디테일을 천천히 볼 수 있었습니다. 북촌을 처음 방문한다면 조금 이른 시간대를 추천하고 싶어요.',
        4.8, v_bukchon_id, DATE '2026-07-10', '친구와 함께', '/review-seed/bukchon-sunrise-v2.png', '데이트', '서울 산책');
    add_review('[DEMO V2] 익선동 카페에서 보낸 오후',
        '골목을 따라 걷다가 발견한 한옥 카페가 정말 아늑했습니다. 따뜻한 조명과 식물이 어우러져 쉬어가기 좋았어요.',
        4.7, v_ikseon_id, DATE '2026-07-08', '친구와 함께', '/review-seed/ikseondong-cafe-v2.png', '카페 투어', '맛집');
    add_review('[DEMO V2] 남산에서 본 서울의 밤',
        '해가 진 뒤 전망대에서 본 서울은 정말 반짝였습니다. 선선한 날에 방문하면 야경을 오래 즐기기 좋아요.',
        4.9, v_namsan_id, DATE '2026-07-06', '연인과 함께', '/review-seed/namsan-night-v2.png', '야경', '데이트');
    add_review('[DEMO V2] 가족과 함께한 남산 야경',
        '케이블카를 타고 올라가는 과정부터 즐거웠습니다. 사진을 남기기 좋은 포인트가 많고 아이들과 함께하기에도 좋았어요.',
        4.8, v_namsan_id, DATE '2026-07-03', '가족과 함께', '/review-seed/namsan-night-v2.png', '가족 여행', '야경');
    add_review('[DEMO V2] 다시 걷고 싶은 서울 골목',
        '한옥 골목과 카페, 야경까지 하루에 담을 수 있는 코스였습니다. 다음에는 계절이 바뀐 뒤에 다시 방문하고 싶습니다.',
        4.8, v_ikseon_id, DATE '2026-07-01', '혼자', '/review-seed/ikseondong-cafe-v2.png', '혼자 여행', '카페 투어');

    INSERT INTO REVIEW_COMMENT (REVIEW_ID, MEMBER_ID, CONTENT, IS_DELETED)
    VALUES (v_review_id, v_member_id, '사진과 여행 분위기가 정말 좋네요. 다음 서울 여행 때 참고할게요!', 'N');

    UPDATE PLACES p
    SET RATING = (
            SELECT AVG(r.RATING) FROM REVIEW r
            WHERE r.PLACE_ID = p.PLACE_ID AND r.IS_DELETED = 'N'
        ),
        REVIEW_COUNT = (
            SELECT COUNT(*) FROM REVIEW r
            WHERE r.PLACE_ID = p.PLACE_ID AND r.IS_DELETED = 'N'
        )
    WHERE p.PLACE_ID IN (v_bukchon_id, v_namsan_id, v_ikseon_id);

    COMMIT;
END;
/
