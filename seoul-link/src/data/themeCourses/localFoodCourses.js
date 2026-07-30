import localFoodImage from '../../assets/images/moods/mood-local-food.png';

/**
 * 로컬처럼 먹는 하루 테마 코스 원본 데이터
 */
export const localFoodCourseRecipes = [
    {
        courseId: 1301,
        themeSlug: 'local-food',
        title: '종로–을지로 시장 미식 여행',
        description:
            '오래된 노포에서 시작해 시장과 골목 맛집을 걸으며 서울 중심부의 맛을 경험하는 코스예요',
        optionName: '시장 노포 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '종로 · 을지로',
        tags: ['노포', '광장시장', '을지로', '청계천'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:00',
                center: [37.57, 126.998],
                places: [
                    ['이문설농탕', 'RESTAURANT'],
                    ['광장시장', 'TOUR'],
                    ['부촌육회 본점', 'RESTAURANT'],
                    ['카페 어니언 광장시장', 'CAFE'],
                    ['방산시장', 'TOUR'],
                    ['우래옥', 'RESTAURANT'],
                    ['청계천', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1302,
        themeSlug: 'local-food',
        title: '마포 옛 골목 미식 여행',
        description:
            '설렁탕과 평양냉면, 돼지갈비 사이로 마포의 오래된 맛과 카페를 곁들이는 코스예요',
        optionName: '마포 미식 코스',
        badge: 'TASTE',
        tone: 'balanced',
        region: '마포 · 공덕',
        tags: ['마포', '설렁탕', '평양냉면', '돼지갈비'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:00',
                center: [37.54, 126.94],
                places: [
                    ['마포옥', 'RESTAURANT'],
                    ['문화비축기지', 'TOUR'],
                    ['프릳츠 도화점', 'CAFE'],
                    ['을밀대 본점', 'RESTAURANT'],
                    ['비로소커피', 'CAFE'],
                    ['조박집', 'RESTAURANT'],
                ],
            },
        ],
    },
    {
        courseId: 1303,
        themeSlug: 'local-food',
        title: '성수 로컬과 트렌드 사이',
        description:
            '성수의 새로운 맛과 오래된 골목을 지나 한강 산책까지 균형 있게 즐기는 코스예요',
        optionName: '성수 로컬 코스',
        badge: 'TREND',
        tone: 'distance',
        region: '성수 · 뚝섬',
        tags: ['성수', '서울숲', '카페', '골목맛집'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:00',
                center: [37.544, 127.045],
                places: [
                    ['서울숲', 'TOUR'],
                    ['난포', 'RESTAURANT'],
                    ['성수동 카페거리', 'TOUR'],
                    ['로우키 성수점', 'CAFE'],
                    ['성수족발', 'RESTAURANT'],
                    ['뚝섬한강공원', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1304,
        themeSlug: 'local-food',
        title: '성북동 오래된 맛과 풍경',
        description:
            '성북동의 오래된 식당과 찻집을 지나 전망 좋은 언덕까지 오르는 지역 미식 코스예요',
        optionName: '성북동 노포 코스',
        badge: 'LOCAL',
        tone: 'balanced',
        region: '성북동 · 한성대',
        tags: ['성북동', '전통찻집', '노포', '전망'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:00',
                center: [37.59, 127.0],
                places: [
                    ['북악팔각정', 'TOUR'],
                    ['금왕돈까스', 'RESTAURANT'],
                    ['수연산방', 'CAFE'],
                    ['성북동누룽지백숙', 'RESTAURANT'],
                    ['한양도성 백악구간', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1305,
        themeSlug: 'local-food',
        title: '서울 서남권 로컬 미식',
        description:
            '문래와 영등포, 노량진의 서로 다른 동네 맛을 경험하는 1박 2일 미식 코스예요',
        optionName: '서남권 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '양천 · 문래 · 노량진',
        tags: ['문래', '영등포', '노량진', '로컬미식'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:00',
                center: [37.52, 126.88],
                places: [
                    ['양천뼈다귀본점', 'RESTAURANT'],
                    ['문래창작촌', 'TOUR'],
                    ['맨홀커피', 'CAFE'],
                    ['부일숯불갈비', 'RESTAURANT'],
                    ['코트야드 바이 메리어트 서울 타임스퀘어', 'HOTEL'],
                ],
            },
            {
                startTime: '10:00',
                center: [37.51, 126.95],
                places: [
                    ['안양천 생태초화원', 'TOUR'],
                    ['정인면옥', 'RESTAURANT'],
                    ['카페 진정성 한강편 커피하우스', 'CAFE'],
                    ['노량진수산시장', 'RESTAURANT'],
                    ['용양봉저정공원', 'TOUR'],
                ],
            },
        ],
    },
];
