-- ----------------------------
-- 질문 10개와 선택지 20개
DECLARE
    PROCEDURE ADD_QUESTION (
        P_QUESTION_TEXT   IN VARCHAR2,
        P_CATEGORY        IN VARCHAR2,
        P_DISPLAY_ORDER   IN NUMBER,

        P_OPTION1_TEXT    IN VARCHAR2,
        P_OPTION1_CODE    IN VARCHAR2,
        P_OPTION1_SCORE   IN NUMBER,
        P_OPTION1_IMAGE   IN VARCHAR2,

        P_OPTION2_TEXT    IN VARCHAR2,
        P_OPTION2_CODE    IN VARCHAR2,
        P_OPTION2_SCORE   IN NUMBER,
        P_OPTION2_IMAGE   IN VARCHAR2
    )
    IS
        V_QUESTION_ID SURVEY_QUESTION.QUESTION_ID%TYPE;
    BEGIN
        INSERT INTO SURVEY_QUESTION (
            QUESTION_TEXT,
            CATEGORY,
            DISPLAY_ORDER
        )
        VALUES (
            P_QUESTION_TEXT,
            P_CATEGORY,
            P_DISPLAY_ORDER
        )
        RETURNING QUESTION_ID INTO V_QUESTION_ID;


        INSERT INTO SURVEY_OPTION (
            QUESTION_ID,
            OPTION_TEXT,
            SCORE_CODE,
            SCORE_VALUE,
            IMAGE_URL
        )
        VALUES (
            V_QUESTION_ID,
            P_OPTION1_TEXT,
            P_OPTION1_CODE,
            P_OPTION1_SCORE,
            P_OPTION1_IMAGE
        );


        INSERT INTO SURVEY_OPTION (
            QUESTION_ID,
            OPTION_TEXT,
            SCORE_CODE,
            SCORE_VALUE,
            IMAGE_URL
        )
        VALUES (
            V_QUESTION_ID,
            P_OPTION2_TEXT,
            P_OPTION2_CODE,
            P_OPTION2_SCORE,
            P_OPTION2_IMAGE
        );
    END ADD_QUESTION;

BEGIN
    -- 질문 1: 활동 핵심 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행 사진첩에 더 많이 남을 장면은?',
        P_CATEGORY      => 'ACTIVITY',
        P_DISPLAY_ORDER => 1,

        P_OPTION1_TEXT  => '새로운 활동에 도전하며 신나게 즐기는 모습',
        P_OPTION1_CODE  => 'A',
        P_OPTION1_SCORE => 2,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q1-1.png',

        P_OPTION2_TEXT  => '예쁜 공간에서 차 한잔하며 쉬고 있는 모습',
        P_OPTION2_CODE  => 'H',
        P_OPTION2_SCORE => 2,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q1-2.png'
    );


    -- 질문 2: 활동 보조 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행에서 더 아쉬운 순간은?',
        P_CATEGORY      => 'ACTIVITY',
        P_DISPLAY_ORDER => 2,

        P_OPTION1_TEXT  => '해보고 싶던 활동을 놓쳤을 때',
        P_OPTION1_CODE  => 'A',
        P_OPTION1_SCORE => 1,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q2-1.png',

        P_OPTION2_TEXT  => '제대로 쉬고 풍경을 즐길 시간이 부족했을 때',
        P_OPTION2_CODE  => 'H',
        P_OPTION2_SCORE => 1,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q2-2.png'
    );


    -- 질문 3: 문화 핵심 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '어떤 장소를 더 방문하고 싶나요?',
        P_CATEGORY      => 'CULTURE',
        P_DISPLAY_ORDER => 3,

        P_OPTION1_TEXT  => '오래된 골목과 역사 속 이야기를 찾아서',
        P_OPTION1_CODE  => 'T',
        P_OPTION1_SCORE => 2,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q3-1.png',

        P_OPTION2_TEXT  => '최신 전시와 세련된 핫플레이스를 찾아서',
        P_OPTION2_CODE  => 'M',
        P_OPTION2_SCORE => 2,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q3-2.png'
    );


    -- 질문 4: 문화 보조 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행지에서 쇼핑한다면?',
        P_CATEGORY      => 'CULTURE',
        P_DISPLAY_ORDER => 4,

        P_OPTION1_TEXT  => '현지인 사이에서 로컬 시장 구경하기',
        P_OPTION1_CODE  => 'T',
        P_OPTION1_SCORE => 1,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q4-1.png',

        P_OPTION2_TEXT  => '시원하고 세련된 쇼핑몰에서 신상품 구경하기',
        P_OPTION2_CODE  => 'M',
        P_OPTION2_SCORE => 1,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q4-2.png'
    );


    -- 질문 5: 소비 핵심 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행지에서 머물 숙소를 선택한다면?',
        P_CATEGORY      => 'SPEND',
        P_DISPLAY_ORDER => 5,

        P_OPTION1_TEXT  => '오늘만큼은 주인공! 서비스 좋은 고급 호텔',
        P_OPTION1_CODE  => 'L',
        P_OPTION1_SCORE => 2,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q5-1.png',

        P_OPTION2_TEXT  => '잠만 편하면 충분해! 정겨운 현지 게스트하우스',
        P_OPTION2_CODE  => 'B',
        P_OPTION2_SCORE => 2,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q5-2.png'
    );


    -- 질문 6: 소비 보조 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행지에서 식사한다면?',
        P_CATEGORY      => 'SPEND',
        P_DISPLAY_ORDER => 6,

        P_OPTION1_TEXT  => '여행 온 날만큼은 근사한 레스토랑 풀코스',
        P_OPTION1_CODE  => 'L',
        P_OPTION1_SCORE => 1,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q6-1.png',

        P_OPTION2_TEXT  => '줄 서서 먹는 현지 길거리 음식이 진짜 여행',
        P_OPTION2_CODE  => 'B',
        P_OPTION2_SCORE => 1,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q6-2.png'
    );


    -- 질문 7: 자극 핵심 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '어떤 여행지를 방문하는 것이 더 좋은가요?',
        P_CATEGORY      => 'STIMULUS',
        P_DISPLAY_ORDER => 7,

        P_OPTION1_TEXT  => '처음 왔다면 대표 명소 인증샷은 필수',
        P_OPTION1_CODE  => 'S',
        P_OPTION1_SCORE => 2,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q7-1.png',

        P_OPTION2_TEXT  => '지도에도 잘 안 나오는 나만의 장소를 찾아서',
        P_OPTION2_CODE  => 'D',
        P_OPTION2_SCORE => 2,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q7-2.png'
    );


    -- 질문 8: 자극 보조 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '앞으로 여행지를 계속 선택할 수 있다면?',
        P_CATEGORY      => 'STIMULUS',
        P_DISPLAY_ORDER => 8,

        P_OPTION1_TEXT  => '검증된 최애 여행지에 다시 가서 익숙한 행복 즐기기',
        P_OPTION1_CODE  => 'S',
        P_OPTION1_SCORE => 1,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q8-1.png',

        P_OPTION2_TEXT  => '같은 곳은 아쉬워! 매번 새로운 여행지 도전하기',
        P_OPTION2_CODE  => 'D',
        P_OPTION2_SCORE => 1,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q8-2.png'
    );


    -- 질문 9: 일정 핵심 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '나에게 더 잘 맞는 하루 여행 방식은?',
        P_CATEGORY      => 'SCHEDULE',
        P_DISPLAY_ORDER => 9,

        P_OPTION1_TEXT  => '볼 수 있을 때 최대한 많이! 여러 장소를 알차게 둘러보기',
        P_OPTION1_CODE  => 'P',
        P_OPTION1_SCORE => 2,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q9-1.png',

        P_OPTION2_TEXT  => '적게 봐도 제대로! 마음에 드는 몇 곳을 천천히 즐기기',
        P_OPTION2_CODE  => 'R',
        P_OPTION2_SCORE => 2,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q9-2.png'
    );


    -- 질문 10: 일정 보조 질문
    ADD_QUESTION (
        P_QUESTION_TEXT => '여행 일정을 확인했는데 시간이 남는다면?',
        P_CATEGORY      => 'SCHEDULE',
        P_DISPLAY_ORDER => 10,

        P_OPTION1_TEXT  => '한 곳 더 갈 수 있다니 좋아! 새로운 장소를 추가하기',
        P_OPTION1_CODE  => 'P',
        P_OPTION1_SCORE => 1,
        P_OPTION1_IMAGE => '/src/assets/images/survey/q10-1.png',

        P_OPTION2_TEXT  => '여유가 생겼다니 좋아! 지금 있는 곳을 더 즐기기',
        P_OPTION2_CODE  => 'R',
        P_OPTION2_SCORE => 1,
        P_OPTION2_IMAGE => '/src/assets/images/survey/q10-2.png'
    );
END;
