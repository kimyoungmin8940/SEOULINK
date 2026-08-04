-- Review list demo data: members, public courses, course stops, reviews, images, tags, and likes.
-- Run after all four place seed files. This script is safe to run repeatedly.

DECLARE
    -- PL/SQL에서는 변수 선언이 내부 함수·프로시저 선언보다 먼저 와야 한다.
    v_course_1 NUMBER;
    v_course_2 NUMBER;
    v_course_3 NUMBER;
    v_review_1 NUMBER;
    v_review_2 NUMBER;
    v_review_3 NUMBER;

    FUNCTION member_id(p_email VARCHAR2) RETURN NUMBER IS
        v_member_id NUMBER;
    BEGIN
        SELECT member_id INTO v_member_id FROM member WHERE email = p_email;
        RETURN v_member_id;
    END;

    FUNCTION place_id(p_category VARCHAR2, p_position NUMBER) RETURN NUMBER IS
        v_place_id NUMBER;
    BEGIN
        SELECT place_id INTO v_place_id
        FROM (
            SELECT place_id, ROW_NUMBER() OVER (ORDER BY place_id) AS row_no
            FROM places
            WHERE category = p_category
              AND is_active = 'Y'
        )
        WHERE row_no = p_position;
        RETURN v_place_id;
    END;

    PROCEDURE ensure_course(
        p_member_email VARCHAR2,
        p_title VARCHAR2,
        p_description VARCHAR2,
        p_course_id OUT NUMBER
    ) IS
        v_member_id NUMBER := member_id(p_member_email);
    BEGIN
        BEGIN
            SELECT course_id INTO p_course_id
            FROM travel_courses
            WHERE member_id = v_member_id AND title = p_title;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                INSERT INTO travel_courses (
                    member_id, title, description, course_type, region, is_public,
                    view_count, total_distance_km, total_travel_minutes,
                    total_visit_minutes, total_course_minutes
                ) VALUES (
                    v_member_id, p_title, p_description, 'CUSTOM', '서울', 'Y',
                    0, 4.8, 52, 240, 292
                )
                RETURNING course_id INTO p_course_id;
        END;
    END;

    PROCEDURE ensure_stop(
        p_course_id NUMBER,
        p_place_id NUMBER,
        p_order NUMBER,
        p_time VARCHAR2,
        p_memo VARCHAR2
    ) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM course_details
        WHERE course_id = p_course_id AND place_order = p_order;

        IF v_count = 0 THEN
            INSERT INTO course_details (
                course_id, place_id, day_no, place_order, memo, visit_time,
                stay_minutes, distance_from_prev_km, travel_minutes_from_prev
            ) VALUES (
                p_course_id, p_place_id, 1, p_order, p_memo, p_time,
                80, CASE WHEN p_order = 1 THEN 0 ELSE 1.6 END,
                CASE WHEN p_order = 1 THEN 0 ELSE 18 END
            );
        END IF;
    END;

    PROCEDURE ensure_review(
        p_member_email VARCHAR2,
        p_course_id NUMBER,
        p_category VARCHAR2,
        p_title VARCHAR2,
        p_content VARCHAR2,
        p_rating NUMBER,
        p_visit_date DATE,
        p_companion VARCHAR2,
        p_image_url VARCHAR2,
        p_review_id OUT NUMBER
    ) IS
        v_member_id NUMBER := member_id(p_member_email);
        v_place_id NUMBER := place_id(p_category, 1);
    BEGIN
        BEGIN
            SELECT review_id INTO p_review_id
            FROM review
            WHERE member_id = v_member_id AND review_title = p_title;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                INSERT INTO review (
                    member_id, place_id, course_id, visit_date, companion,
                    review_title, review_content, rating, image_url, view_count, is_deleted
                ) VALUES (
                    v_member_id, v_place_id, p_course_id, p_visit_date, p_companion,
                    p_title, p_content, p_rating, p_image_url, 0, 'N'
                )
                RETURNING review_id INTO p_review_id;
        END;
    END;

    PROCEDURE ensure_image(p_review_id NUMBER, p_image_url VARCHAR2, p_order NUMBER) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM review_image
        WHERE review_id = p_review_id AND display_order = p_order;
        IF v_count = 0 THEN
            INSERT INTO review_image (review_id, image_url, display_order)
            VALUES (p_review_id, p_image_url, p_order);
        END IF;
    END;

    PROCEDURE ensure_tag(p_review_id NUMBER, p_tag_name VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM review_tag
        WHERE review_id = p_review_id AND tag_name = p_tag_name;
        IF v_count = 0 THEN
            INSERT INTO review_tag (review_id, tag_name) VALUES (p_review_id, p_tag_name);
        END IF;
    END;

    PROCEDURE ensure_like(p_review_id NUMBER, p_member_email VARCHAR2) IS
        v_member_id NUMBER := member_id(p_member_email);
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM review_like
        WHERE review_id = p_review_id AND member_id = v_member_id;
        IF v_count = 0 THEN
            INSERT INTO review_like (review_id, member_id) VALUES (p_review_id, v_member_id);
        END IF;
    END;

BEGIN
    -- 후기 작성자와 좋아요용 회원
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'yongyong@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김용용', '용용', '010-2000-1001'
    FROM dual WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'yongyong@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'haneul@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이하늘', '하늘', '010-2000-1002'
    FROM dual WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'haneul@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'mina@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '박민아', '민아', '010-2000-1003'
    FROM dual WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'mina@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'jun@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '최준', '준', '010-2000-1004'
    FROM dual WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'jun@seoulink.demo');

    ensure_course('yongyong@seoulink.demo', '노을 따라 걷는 남산 카페 산책', '해 질 무렵 전망 좋은 카페와 산책길을 천천히 즐기는 코스', v_course_1);
    ensure_stop(v_course_1, place_id('TOUR', 1), 1, '15:00', '가벼운 산책으로 시작');
    ensure_stop(v_course_1, place_id('CAFE', 1), 2, '16:30', '창가 자리에 앉아 노을 보기');
    ensure_stop(v_course_1, place_id('TOUR', 2), 3, '18:20', '야경이 시작될 때까지 천천히 걷기');

    ensure_course('haneul@seoulink.demo', '자전거 타고 만난 한강의 오후', '바람 좋은 날 한강변을 달리고 피크닉을 즐기는 반나절 코스', v_course_2);
    ensure_stop(v_course_2, place_id('TOUR', 3), 1, '13:00', '자전거 대여 후 강변으로 이동');
    ensure_stop(v_course_2, place_id('TOUR', 4), 2, '14:20', '돗자리 피크닉과 휴식');
    ensure_stop(v_course_2, place_id('CAFE', 2), 3, '16:30', '시원한 음료로 마무리');

    ensure_course('mina@seoulink.demo', '골목의 온도를 따라간 북촌 하루', '고즈넉한 골목과 작은 식당, 카페를 차분하게 둘러보는 혼자 여행 코스', v_course_3);
    ensure_stop(v_course_3, place_id('TOUR', 5), 1, '11:00', '사람이 적은 시간에 골목 산책');
    ensure_stop(v_course_3, place_id('RESTAURANT', 1), 2, '12:30', '점심 식사');
    ensure_stop(v_course_3, place_id('CAFE', 3), 3, '14:00', '여행 메모 정리');

    ensure_review(
        'yongyong@seoulink.demo', v_course_1, 'CAFE',
        '해 질 무렵이 가장 아름다웠던 서울 하루',
        '오후에 천천히 출발해서 전망 좋은 카페에 앉았는데, 해가 넘어갈수록 서울의 색이 달라지는 모습이 정말 좋았어요. 창가 자리는 조금 기다렸지만 커피를 마시며 일정을 정리하기에 딱 좋았습니다. 카페를 나온 뒤에는 서두르지 않고 산책길을 걸었는데, 노을과 야경을 한 번에 볼 수 있어 데이트 코스로 특히 추천하고 싶어요. 편한 신발은 꼭 챙기세요.',
        5.0, DATE '2026-06-21', '친구', '/uploads/reviews/demo-namsan-sunset.png', v_review_1
    );
    ensure_image(v_review_1, '/uploads/reviews/demo-namsan-sunset.png', 1);
    ensure_tag(v_review_1, '야경'); ensure_tag(v_review_1, '카페 투어'); ensure_tag(v_review_1, '데이트'); ensure_tag(v_review_1, '서울 산책');

    ensure_review(
        'haneul@seoulink.demo', v_course_2, 'TOUR',
        '바람이 좋아서 자전거를 멈출 수 없었던 한강',
        '친구와 한강 자전거 코스를 따라 달렸는데 평일 오후라 사람이 많지 않아 훨씬 여유로웠어요. 중간중간 강 쪽으로 난 길에서 사진도 찍고, 돗자리를 펴고 쉬는 시간이 가장 기억에 남습니다. 햇빛이 강한 시간대에는 모자와 물이 꼭 필요했고, 해가 기울기 시작하면 바람이 조금 차가워져서 얇은 겉옷이 있으면 좋아요. 서울 안에서 이렇게 여행 기분을 낼 수 있다는 점이 이 코스의 매력이었습니다.',
        4.8, DATE '2026-06-15', '친구', '/uploads/reviews/demo-han-river-bike.png', v_review_2
    );
    ensure_image(v_review_2, '/uploads/reviews/demo-han-river-bike.png', 1);
    ensure_tag(v_review_2, '친구와 여행'); ensure_tag(v_review_2, '사진 명소'); ensure_tag(v_review_2, '서울 산책'); ensure_tag(v_review_2, '한강');

    ensure_review(
        'mina@seoulink.demo', v_course_3, 'TOUR',
        '혼자라서 더 천천히 보였던 북촌의 골목',
        '혼자 여행이라 일정을 빽빽하게 넣지 않고 골목마다 오래 머물렀어요. 오전에는 조용한 길을 먼저 걷고, 점심을 먹은 뒤 작은 카페에서 쉬면서 사진을 골랐습니다. 유명한 곳만 빠르게 지나가기보다 마음에 드는 풍경을 발견할 때마다 잠시 멈춘 것이 좋았어요. 경사가 있는 길이 있어 운동화가 편하고, 주말이라면 오전 일찍 시작하는 것을 추천합니다. 다음에는 비 오는 날에도 다시 와 보고 싶어요.',
        4.7, DATE '2026-05-31', '혼자', '/uploads/reviews/demo-namsan-sunset.png', v_review_3
    );
    ensure_image(v_review_3, '/uploads/reviews/demo-namsan-sunset.png', 1);
    ensure_tag(v_review_3, '혼자 여행'); ensure_tag(v_review_3, '사진 명소'); ensure_tag(v_review_3, '서울 산책'); ensure_tag(v_review_3, '골목 여행');

    ensure_like(v_review_1, 'haneul@seoulink.demo'); ensure_like(v_review_1, 'mina@seoulink.demo'); ensure_like(v_review_1, 'jun@seoulink.demo');
    ensure_like(v_review_2, 'yongyong@seoulink.demo'); ensure_like(v_review_2, 'mina@seoulink.demo');
    ensure_like(v_review_3, 'yongyong@seoulink.demo'); ensure_like(v_review_3, 'haneul@seoulink.demo');
    COMMIT;
END;



-- Additional review-list demo data: run after 05_demo_reviews.sql.
-- Creates 10 more reviews with their own generated Seoul travel photos.

DECLARE
    -- 변수 선언은 내부 FUNCTION·PROCEDURE 선언보다 앞에 와야 한다.
    v_review_01 NUMBER; v_review_02 NUMBER; v_review_03 NUMBER; v_review_04 NUMBER; v_review_05 NUMBER;
    v_review_06 NUMBER; v_review_07 NUMBER; v_review_08 NUMBER; v_review_09 NUMBER; v_review_10 NUMBER;

    FUNCTION member_id(p_email VARCHAR2) RETURN NUMBER IS
        v_member_id NUMBER;
    BEGIN
        SELECT member_id INTO v_member_id FROM member WHERE email = p_email;
        RETURN v_member_id;
    END;

    FUNCTION place_id(p_category VARCHAR2, p_position NUMBER) RETURN NUMBER IS
        v_place_id NUMBER;
    BEGIN
        SELECT place_id INTO v_place_id
        FROM (
            SELECT place_id, ROW_NUMBER() OVER (ORDER BY place_id) AS row_no
            FROM places
            WHERE category = p_category AND is_active = 'Y'
        )
        WHERE row_no = p_position;
        RETURN v_place_id;
    END;

    FUNCTION course_id(p_title VARCHAR2) RETURN NUMBER IS
        v_course_id NUMBER;
    BEGIN
        SELECT course_id INTO v_course_id FROM travel_courses WHERE title = p_title;
        RETURN v_course_id;
    END;

    PROCEDURE ensure_review(
        p_email VARCHAR2, p_category VARCHAR2, p_position NUMBER, p_course_title VARCHAR2,
        p_title VARCHAR2, p_content VARCHAR2, p_rating NUMBER, p_visit_date DATE,
        p_companion VARCHAR2, p_image_url VARCHAR2, p_review_id OUT NUMBER
    ) IS
        v_member_id NUMBER := member_id(p_email);
        v_place_id NUMBER;
        v_course_id NUMBER;
    BEGIN
        -- 로컬 함수 호출 결과를 먼저 PL/SQL 변수로 계산한다.
        -- Oracle은 익명 블록의 로컬 함수를 INSERT SQL 안에서 직접 호출할 수 없다.
        v_place_id := place_id(p_category, p_position);
        v_course_id := course_id(p_course_title);

        BEGIN
            SELECT review_id INTO p_review_id
            FROM review
            WHERE member_id = v_member_id AND review_title = p_title;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                INSERT INTO review (
                    member_id, place_id, course_id, visit_date, companion,
                    review_title, review_content, rating, image_url, view_count, is_deleted
                ) VALUES (
                    v_member_id, v_place_id, v_course_id,
                    p_visit_date, p_companion, p_title, p_content, p_rating,
                    p_image_url, 0, 'N'
                ) RETURNING review_id INTO p_review_id;
        END;
    END;

    PROCEDURE ensure_image(p_review_id NUMBER, p_image_url VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM review_image
        WHERE review_id = p_review_id AND display_order = 1;
        IF v_count = 0 THEN
            INSERT INTO review_image (review_id, image_url, display_order)
            VALUES (p_review_id, p_image_url, 1);
        END IF;
    END;

    PROCEDURE ensure_tag(p_review_id NUMBER, p_tag_name VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM review_tag
        WHERE review_id = p_review_id AND tag_name = p_tag_name;
        IF v_count = 0 THEN
            INSERT INTO review_tag (review_id, tag_name) VALUES (p_review_id, p_tag_name);
        END IF;
    END;

    PROCEDURE ensure_like(p_review_id NUMBER, p_email VARCHAR2) IS
        v_count NUMBER;
        v_member_id NUMBER;
    BEGIN
        v_member_id := member_id(p_email);
        SELECT COUNT(*) INTO v_count FROM review_like
        WHERE review_id = p_review_id AND member_id = v_member_id;
        IF v_count = 0 THEN
            INSERT INTO review_like (review_id, member_id) VALUES (p_review_id, v_member_id);
        END IF;
    END;

BEGIN
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'jiwoo@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김지우', '지우', '010-2000-1005' FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'jiwoo@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'doyun@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이도윤', '도윤', '010-2000-1006' FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'doyun@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'sora@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '최소라', '소라', '010-2000-1007' FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'sora@seoulink.demo');
    INSERT INTO member (email, password, name, nickname, phone)
    SELECT 'minho@seoulink.demo', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '박민호', '민호', '010-2000-1008' FROM dual
    WHERE NOT EXISTS (SELECT 1 FROM member WHERE email = 'minho@seoulink.demo');

    ensure_review('jiwoo@seoulink.demo', 'TOUR', 1, '골목의 온도를 따라간 북촌 하루', '아침 공기가 좋았던 경복궁 산책', '개장 시간에 맞춰 도착하니 궁궐의 넓은 마당을 여유롭게 걸을 수 있었어요. 햇살이 지붕 위로 번질 때 사진이 특히 잘 나왔고, 북촌까지 이어서 걷기 좋은 동선이었습니다.', 4.9, DATE '2026-06-15', '혼자', '/uploads/reviews/demo-gyeongbokgung-morning.png', v_review_01);
    ensure_review('doyun@seoulink.demo', 'CAFE', 1, '골목의 온도를 따라간 북촌 하루', '비 오는 익선동, 더 오래 기억나는 오후', '비가 와서 걱정했는데 한옥 골목의 분위기가 더 좋았어요. 우산 쓰고 천천히 걷다가 따뜻한 커피 한 잔 마시니 여행 속도가 자연스럽게 느려졌습니다.', 4.8, DATE '2026-06-22', '연인', '/uploads/reviews/demo-ikseondong-rain.png', v_review_02);
    ensure_review('sora@seoulink.demo', 'CAFE', 2, '노을 따라 걷는 남산 카페 산책', '성수에서 보낸 느긋한 카페 데이', '성수 골목은 가게마다 분위기가 달라서 목적지 없이 걸어도 재미있었어요. 카페에서 쉬고 작은 편집숍까지 들르니 반나절이 금방 지나갔습니다.', 4.7, DATE '2026-06-08', '친구', '/uploads/reviews/demo-seongsu-cafe.png', v_review_03);
    ensure_review('minho@seoulink.demo', 'TOUR', 2, '자전거 타고 만난 한강의 오후', '아이와 걷기 좋았던 석촌호수 한 바퀴', '유모차를 끌어도 길이 편했고 중간중간 쉬어 갈 벤치가 많았습니다. 호수 풍경을 보며 천천히 걷고 근처에서 간단히 식사하기 좋은 가족 코스예요.', 4.9, DATE '2026-05-17', '가족', '/uploads/reviews/demo-seokchon-picnic.png', v_review_04);
    ensure_review('yongyong@seoulink.demo', 'RESTAURANT', 1, '골목의 온도를 따라간 북촌 하루', '망원시장 먹거리로 완성한 점심', '시장 안에서 조금씩 나눠 먹으니 여러 메뉴를 맛볼 수 있어 좋았습니다. 붐비는 시간보다 점심 직전이 덜 복잡했고, 산책 전후로 들르기 딱 좋았어요.', 4.6, DATE '2026-06-29', '친구', '/uploads/reviews/demo-mangwon-market.png', v_review_05);
    ensure_review('haneul@seoulink.demo', 'TOUR', 3, '자전거 타고 만난 한강의 오후', '은행잎이 물든 서울숲의 오후', '나무 사이 길을 따라 걷는 것만으로도 기분이 좋아지는 곳이었어요. 자전거를 타는 사람도 많았지만 산책 구간은 비교적 조용해서 혼자 쉬기 좋았습니다.', 4.8, DATE '2026-05-11', '혼자', '/uploads/reviews/demo-seoul-forest-autumn.png', v_review_06);
    ensure_review('mina@seoulink.demo', 'TOUR', 4, '자전거 타고 만난 한강의 오후', '돗자리 하나로 충분했던 여의도 한강', '간단한 간식과 돗자리만 챙겼는데도 훌륭한 오후가 됐어요. 해 질 무렵 강 건너 풍경이 예뻐서 오래 앉아 있게 되었습니다.', 4.9, DATE '2026-06-14', '친구', '/uploads/reviews/demo-yeouido-picnic.png', v_review_07);
    ensure_review('jun@seoulink.demo', 'CAFE', 3, '노을 따라 걷는 남산 카페 산책', '비 온 뒤 홍대 골목의 밤', '비가 그친 뒤라 골목 바닥에 불빛이 반사되어 분위기가 좋았어요. 너무 시끄러운 길은 피하고 작은 골목을 따라 걸으니 생각보다 차분하게 즐길 수 있었습니다.', 4.5, DATE '2026-06-07', '연인', '/uploads/reviews/demo-hongdae-night.png', v_review_08);
    ensure_review('jiwoo@seoulink.demo', 'TOUR', 5, '골목의 온도를 따라간 북촌 하루', '도시가 한눈에 보였던 인왕산', '경사가 조금 있지만 천천히 오르면 충분히 갈 수 있는 코스예요. 정상 부근에서 본 서울 풍경이 시원했고, 물과 편한 운동화는 꼭 챙기는 걸 추천합니다.', 4.8, DATE '2026-05-24', '가족', '/uploads/reviews/demo-inwangsan-hike.png', v_review_09);
    ensure_review('doyun@seoulink.demo', 'TOUR', 1, '노을 따라 걷는 남산 카페 산책', '청계천 밤산책, 생각보다 좋았던 코스', '저녁 식사 후 가볍게 걷기 시작했는데 물소리와 조명이 어우러져 기분 전환이 됐어요. 오래 걷지 않아도 서울의 다른 표정을 볼 수 있는 코스였습니다.', 4.7, DATE '2026-06-01', '연인', '/uploads/reviews/demo-cheonggyecheon-night.png', v_review_10);

    ensure_image(v_review_01, '/uploads/reviews/demo-gyeongbokgung-morning.png'); ensure_tag(v_review_01, '고궁'); ensure_tag(v_review_01, '혼자 여행'); ensure_tag(v_review_01, '사진 명소');
    ensure_image(v_review_02, '/uploads/reviews/demo-ikseondong-rain.png'); ensure_tag(v_review_02, '익선동'); ensure_tag(v_review_02, '비 오는 날'); ensure_tag(v_review_02, '데이트');
    ensure_image(v_review_03, '/uploads/reviews/demo-seongsu-cafe.png'); ensure_tag(v_review_03, '카페 투어'); ensure_tag(v_review_03, '성수'); ensure_tag(v_review_03, '친구와 여행');
    ensure_image(v_review_04, '/uploads/reviews/demo-seokchon-picnic.png'); ensure_tag(v_review_04, '가족 여행'); ensure_tag(v_review_04, '호수 산책'); ensure_tag(v_review_04, '아이와 여행');
    ensure_image(v_review_05, '/uploads/reviews/demo-mangwon-market.png'); ensure_tag(v_review_05, '맛집'); ensure_tag(v_review_05, '시장 투어'); ensure_tag(v_review_05, '친구와 여행');
    ensure_image(v_review_06, '/uploads/reviews/demo-seoul-forest-autumn.png'); ensure_tag(v_review_06, '서울숲'); ensure_tag(v_review_06, '서울 산책'); ensure_tag(v_review_06, '혼자 여행');
    ensure_image(v_review_07, '/uploads/reviews/demo-yeouido-picnic.png'); ensure_tag(v_review_07, '한강'); ensure_tag(v_review_07, '피크닉'); ensure_tag(v_review_07, '친구와 여행');
    ensure_image(v_review_08, '/uploads/reviews/demo-hongdae-night.png'); ensure_tag(v_review_08, '야경'); ensure_tag(v_review_08, '데이트'); ensure_tag(v_review_08, '홍대');
    ensure_image(v_review_09, '/uploads/reviews/demo-inwangsan-hike.png'); ensure_tag(v_review_09, '등산'); ensure_tag(v_review_09, '서울 전망'); ensure_tag(v_review_09, '부모님과 여행');
    ensure_image(v_review_10, '/uploads/reviews/demo-cheonggyecheon-night.png'); ensure_tag(v_review_10, '서울 산책'); ensure_tag(v_review_10, '야경'); ensure_tag(v_review_10, '데이트');

    ensure_like(v_review_01, 'haneul@seoulink.demo'); ensure_like(v_review_01, 'mina@seoulink.demo');
    ensure_like(v_review_02, 'jiwoo@seoulink.demo'); ensure_like(v_review_03, 'doyun@seoulink.demo'); ensure_like(v_review_03, 'minho@seoulink.demo');
    ensure_like(v_review_04, 'sora@seoulink.demo'); ensure_like(v_review_05, 'jun@seoulink.demo'); ensure_like(v_review_06, 'yongyong@seoulink.demo');
    ensure_like(v_review_07, 'haneul@seoulink.demo'); ensure_like(v_review_08, 'mina@seoulink.demo'); ensure_like(v_review_09, 'doyun@seoulink.demo'); ensure_like(v_review_10, 'jiwoo@seoulink.demo');
    COMMIT;
END;

SELECT * FROM review;
