-- Travel-type seed data imported from seoulink (1).sql (32 combinations).

INSERT INTO TRAVEL_TYPE_MASTER (
    TRAVEL_CODE,
    TYPE_TITLE,
    TYPE_DESCRIPTION,
    IMAGE_URL
)
WITH
    ACTIVITY_AXIS AS (
        SELECT
            'A' AS CODE,
            '아침부터 여러 장소를 둘러보며 여행을 알차게 즐기는 편입니다' AS DESCRIPTION
        FROM DUAL

        UNION ALL

        SELECT
            'H',
            '서두르지 않고 마음에 드는 장소에서 여유롭게 머무는 편입니다'
        FROM DUAL
    ),
    CULTURE_AXIS AS (
        SELECT
            'T' AS CODE,
            '오래된 골목과 역사, 현지의 생활문화를 경험하는 것을 좋아합니다' AS DESCRIPTION
        FROM DUAL

        UNION ALL

        SELECT
            'M',
            '최신 전시와 세련된 공간, 도시의 새로운 문화를 즐기는 것을 좋아합니다'
        FROM DUAL
    ),
    SPEND_AXIS AS (
        SELECT
            'L' AS CODE,
            '숙소와 식사에 투자하며 좋은 서비스와 특별한 경험을 중요하게 생각합니다' AS DESCRIPTION
        FROM DUAL

        UNION ALL

        SELECT
            'B',
            '합리적인 비용으로 현지 분위기를 가까이 경험하는 것을 중요하게 생각합니다'
        FROM DUAL
    ),
    STIMULUS_AXIS AS (
        SELECT
            'S' AS CODE,
            '검증된 유명 명소와 익숙하고 편안한 여행 경험을 선호합니다' AS DESCRIPTION
        FROM DUAL

        UNION ALL

        SELECT
            'D',
            '잘 알려지지 않은 장소와 매번 새로운 여행 경험을 찾아 나서는 편입니다'
        FROM DUAL
    ),
    SCHEDULE_AXIS AS (
        SELECT
            'P' AS CODE,
            '일정과 준비물을 미리 정리하여 안정적으로 여행하는 것을 좋아합니다' AS DESCRIPTION
        FROM DUAL

        UNION ALL

        SELECT
            'R',
            '상황과 기분에 따라 자유롭게 움직이는 여행을 좋아합니다'
        FROM DUAL
    ),
    TRAVEL_TYPE_COMBINATIONS AS (
        SELECT
            A.CODE || C.CODE || E.CODE || S.CODE || P.CODE
                AS TRAVEL_CODE,

            A.DESCRIPTION || ' ' ||
            C.DESCRIPTION || ' ' ||
            E.DESCRIPTION || ' ' ||
            S.DESCRIPTION || ' ' ||
            P.DESCRIPTION
                AS TYPE_DESCRIPTION
        FROM ACTIVITY_AXIS A
                 CROSS JOIN CULTURE_AXIS C
                 CROSS JOIN SPEND_AXIS E
                 CROSS JOIN STIMULUS_AXIS S
                 CROSS JOIN SCHEDULE_AXIS P
    )
SELECT
    TRAVEL_CODE,

    CASE TRAVEL_CODE
        -- 활동형 + 전통형 + 럭셔리형
        WHEN 'ATLSP' THEN '품격 있는 역사 정복자'
        WHEN 'ATLSR' THEN '우아한 전통 탐험가'
        WHEN 'ATLDP' THEN '대담한 문화 전략가'
        WHEN 'ATLDR' THEN '자유로운 문화 개척자'

        -- 활동형 + 전통형 + 실속형
        WHEN 'ATBSP' THEN '알찬 역사 길잡이'
        WHEN 'ATBSR' THEN '활기찬 골목 여행가'
        WHEN 'ATBDP' THEN '실속 있는 보물 사냥꾼'
        WHEN 'ATBDR' THEN '즉흥적인 로컬 모험가'

        -- 활동형 + 현대형 + 럭셔리형
        WHEN 'AMLSP' THEN '세련된 도시 전략가'
        WHEN 'AMLSR' THEN '감각적인 시티 유목민'
        WHEN 'AMLDP' THEN '화려한 트렌드 개척자'
        WHEN 'AMLDR' THEN '자유로운 핫플 탐험가'

        -- 활동형 + 현대형 + 실속형
        WHEN 'AMBSP' THEN '똑똑한 도시 정복자'
        WHEN 'AMBSR' THEN '경쾌한 도심 산책가'
        WHEN 'AMBDP' THEN '실속파 트렌드 헌터'
        WHEN 'AMBDR' THEN '즉흥적인 도시 모험가'

        -- 휴식형 + 전통형 + 럭셔리형
        WHEN 'HTLSP' THEN '고요한 전통 설계자'
        WHEN 'HTLSR' THEN '품격 있는 시간 여행자'
        WHEN 'HTLDP' THEN '사색하는 문화 개척자'
        WHEN 'HTLDR' THEN '낭만적인 골목 유랑자'

        -- 휴식형 + 전통형 + 실속형
        WHEN 'HTBSP' THEN '차분한 역사 탐구가'
        WHEN 'HTBSR' THEN '소박한 로컬 생활가'
        WHEN 'HTBDP' THEN '숨은 유산 수집가'
        WHEN 'HTBDR' THEN '느긋한 골목 발견가'

        -- 휴식형 + 현대형 + 럭셔리형
        WHEN 'HMLSP' THEN '우아한 도시 설계자'
        WHEN 'HMLSR' THEN '여유로운 감성 여행가'
        WHEN 'HMLDP' THEN '섬세한 트렌드 큐레이터'
        WHEN 'HMLDR' THEN '낭만적인 도시 유목민'

        -- 휴식형 + 현대형 + 실속형
        WHEN 'HMBSP' THEN '안정적인 도심 관찰자'
        WHEN 'HMBSR' THEN '느긋한 카페 산책가'
        WHEN 'HMBDP' THEN '조용한 핫플 발굴가'
        WHEN 'HMBDR' THEN '여유로운 도시 탐험가'

        ELSE '이름 없는 여행자'
        END AS TYPE_TITLE,

    TYPE_DESCRIPTION,

    NULL AS IMAGE_URL
FROM TRAVEL_TYPE_COMBINATIONS;
