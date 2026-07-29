-- Demo data for the review list. Run after review-feature-migration.sql.
-- The two generated image assets are served by the React app from /review-seed/.

DECLARE
    v_member_id MEMBER.MEMBER_ID%TYPE;
    v_bukchon_id PLACES.PLACE_ID%TYPE;
    v_namsan_id PLACES.PLACE_ID%TYPE;
    v_course_id TRAVEL_COURSES.COURSE_ID%TYPE;
    v_review_id REVIEW.REVIEW_ID%TYPE;

    PROCEDURE ensure_place(
        p_api_id VARCHAR2, p_name VARCHAR2, p_category VARCHAR2,
        p_region VARCHAR2, p_address VARCHAR2, p_lat NUMBER, p_lng NUMBER,
        p_id OUT NUMBER
    ) IS
    BEGIN
        SELECT PLACE_ID INTO p_id FROM PLACES
        WHERE API_PROVIDER = 'SEOULLINK_DEMO' AND API_PLACE_ID = p_api_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO PLACES (
                API_PROVIDER, API_PLACE_ID, NAME, CATEGORY, REGION, ADDRESS,
                LATITUDE, LONGITUDE, RATING, REVIEW_COUNT, IS_ACTIVE
            ) VALUES (
                'SEOULLINK_DEMO', p_api_id, p_name, p_category, p_region, p_address,
                p_lat, p_lng, 0, 0, 'Y'
            ) RETURNING PLACE_ID INTO p_id;
    END;

    PROCEDURE add_review(
        p_title VARCHAR2, p_body VARCHAR2, p_rating NUMBER, p_place_id NUMBER,
        p_visit_date DATE, p_companion VARCHAR2, p_image VARCHAR2,
        p_tag1 VARCHAR2, p_tag2 VARCHAR2
    ) IS
    BEGIN
        INSERT INTO REVIEW (
            MEMBER_ID, PLACE_ID, COURSE_ID, REVIEW_TITLE, REVIEW_CONTENT, RATING,
            IMAGE_URL, VISIT_DATE, COMPANION, VIEW_COUNT, IS_DELETED
        ) VALUES (
            v_member_id, p_place_id, v_course_id, p_title, p_body, p_rating,
            p_image, p_visit_date, p_companion, 0, 'N'
        ) RETURNING REVIEW_ID INTO v_review_id;

        INSERT INTO REVIEW_IMAGE (REVIEW_ID, IMAGE_URL, DISPLAY_ORDER)
        VALUES (v_review_id, p_image, 0);
        INSERT INTO REVIEW_TAG (REVIEW_ID, TAG_NAME) VALUES (v_review_id, p_tag1);
        INSERT INTO REVIEW_TAG (REVIEW_ID, TAG_NAME) VALUES (v_review_id, p_tag2);
    END;
BEGIN
    SELECT MEMBER_ID INTO v_member_id
    FROM (SELECT MEMBER_ID FROM MEMBER WHERE STATUS = 'ACTIVE' ORDER BY MEMBER_ID)
    WHERE ROWNUM = 1;

    ensure_place('BUKCHON', '북촌한옥마을', 'TOUR', '서울 종로구', '서울 종로구 계동길', 37.5826, 126.9830, v_bukchon_id);
    ensure_place('NAMSAN', 'N서울타워', 'TOUR', '서울 용산구', '서울 용산구 남산공원길 105', 37.5512, 126.9882, v_namsan_id);

    BEGIN
        SELECT COURSE_ID INTO v_course_id
        FROM TRAVEL_COURSES
        WHERE MEMBER_ID = v_member_id AND TITLE = '[DEMO] 서울 하루 산책 코스';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            INSERT INTO TRAVEL_COURSES (MEMBER_ID, TITLE, DESCRIPTION, COURSE_TYPE, REGION, IS_PUBLIC, VIEW_COUNT)
            VALUES (v_member_id, '[DEMO] 서울 하루 산책 코스', '북촌과 남산을 함께 즐기는 서울 하루 여행', 'CUSTOM', '서울', 'Y', 0)
            RETURNING COURSE_ID INTO v_course_id;
    END;
    DELETE FROM COURSE_DETAILS WHERE COURSE_ID = v_course_id;
    INSERT INTO COURSE_DETAILS (COURSE_ID, PLACE_ID, DAY_NO, PLACE_ORDER, VISIT_TIME, MEMO)
    VALUES (v_course_id, v_bukchon_id, 1, 1, '10:00', '한옥 골목 산책');
    INSERT INTO COURSE_DETAILS (COURSE_ID, PLACE_ID, DAY_NO, PLACE_ORDER, VISIT_TIME, MEMO)
    VALUES (v_course_id, v_namsan_id, 1, 2, '18:30', '서울 야경 감상');

    DELETE FROM REVIEW WHERE REVIEW_TITLE LIKE '[DEMO] %';

    add_review('[DEMO] 북촌에서 만난 느린 아침',
        '아침 햇살이 기와지붕을 비추는 시간에 북촌 골목을 걸었어요. 조용한 골목과 서울 도심의 풍경이 함께 보여 오래 기억에 남을 것 같습니다.',
        4.9, v_bukchon_id, DATE '2026-07-12', '혼자', '/review-seed/bukchon-sunrise.png', '혼자 여행', '사진 명소');
    add_review('[DEMO] 한옥 골목 끝에서 만난 서울',
        '북촌을 천천히 산책하고 작은 카페에서 쉬었습니다. 붐비기 전 오전에 방문하면 한층 여유롭게 즐길 수 있어요.',
        4.8, v_bukchon_id, DATE '2026-07-10', '친구와 함께', '/review-seed/bukchon-sunrise.png', '데이트', '카페 투어');
    add_review('[DEMO] 남산 야경이 주는 설렘',
        '해가 진 뒤 전망대에서 본 서울은 정말 반짝였어요. 밤공기가 선선한 날에 가면 더 좋은 추억이 될 것 같습니다.',
        4.9, v_namsan_id, DATE '2026-07-09', '연인과 함께', '/review-seed/namsan-night.png', '야경', '데이트');
    add_review('[DEMO] 가족과 함께한 서울의 밤',
        '케이블카를 타고 올라가니 아이들도 무척 좋아했어요. 사진을 남기기 좋은 포인트가 많고, 야경도 멋집니다.',
        4.7, v_namsan_id, DATE '2026-07-06', '가족과 함께', '/review-seed/namsan-night.png', '가족 여행', '야경');
    add_review('[DEMO] 다시 걷고 싶은 북촌 산책',
        '익숙한 서울에서도 여행자처럼 걸을 수 있었던 하루였습니다. 한옥의 디테일을 찾으며 걷는 재미가 있어요.',
        4.8, v_bukchon_id, DATE '2026-07-03', '친구와 함께', '/review-seed/bukchon-sunrise.png', '맛집', '사진 명소');

    INSERT INTO REVIEW_COMMENT (REVIEW_ID, MEMBER_ID, CONTENT, IS_DELETED)
    VALUES (v_review_id, v_member_id, '사진과 여행 분위기가 정말 좋네요. 다음 서울 여행 때 참고할게요!', 'N');

    UPDATE PLACES p
    SET RATING = (SELECT AVG(r.RATING) FROM REVIEW r WHERE r.PLACE_ID = p.PLACE_ID AND r.IS_DELETED = 'N'),
        REVIEW_COUNT = (SELECT COUNT(*) FROM REVIEW r WHERE r.PLACE_ID = p.PLACE_ID AND r.IS_DELETED = 'N')
    WHERE p.PLACE_ID IN (v_bukchon_id, v_namsan_id);

    COMMIT;
END;
/
