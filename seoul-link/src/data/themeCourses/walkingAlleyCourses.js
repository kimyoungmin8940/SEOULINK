import walkingAlleyImage from '../../assets/images/moods/mood-walking-alley.png';

/**
 * 혼자 걷기 좋은 골목 테마 코스 원본 데이터
 */
export const walkingAlleyCourseRecipes = [
    {
        courseId: 1601,
        themeSlug: 'walking-alley',
        title: '안국–삼청–익선동 골목',
        description:
            '안국의 공예 전시와 삼청동·안국의 카페를 즐긴 뒤 익선동 한옥거리를 걷는 코스예요',
        optionName: '안국 익선동 골목',
        badge: 'BEST',
        tone: 'preference',
        region: '안국 · 삼청동 · 익선동',
        tags: ['서울공예박물관', '삼청동', '익선동', '한옥거리'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '12:00',
                center: [37.577, 126.986],
                places: [
                    ['서울공예박물관', 'TOUR'],
                    ['블루보틀 삼청', 'CAFE'],
                    ['카페 어니언 안국', 'CAFE'],
                    ['익선동 한옥거리', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1602,
        themeSlug: 'walking-alley',
        title: '인사동–을지로 시간여행 골목',
        description:
            '전통 상권에서 오래된 도심 골목으로 이동하며 혼밥과 카페를 자연스럽게 즐기는 시간여행 코스예요',
        optionName: '인사동 을지로 골목',
        badge: 'RETRO',
        tone: 'balanced',
        region: '인사동 · 종로 · 을지로',
        tags: ['인사동', '을지로', '골목', '혼밥'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '12:00',
                center: [37.57, 126.99],
                places: [
                    ['쌈지길', 'TOUR'],
                    ['이문설농탕', 'RESTAURANT'],
                    ['커피한약방', 'CAFE'],
                    ['챔프커피 제3작업실', 'CAFE'],
                ],
            },
        ],
    },
    {
        courseId: 1603,
        themeSlug: 'walking-alley',
        title: '연남동–홍대–합정 골목',
        description:
            '연남동의 차분한 골목에서 홍대와 합정까지 걷고, 망원과 문화비축기지의 로컬 풍경을 이어서 만나는 1박 2일 코스예요',
        optionName: '연남 홍대 합정 1박 2일',
        badge: '1N2D',
        tone: 'distance',
        region: '연남동 · 홍대 · 합정 · 망원',
        tags: ['연남동', '홍대', '합정', '1박2일'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '12:00',
                center: [37.557, 126.923],
                places: [
                    ['경의선숲길 연남동 구간', 'TOUR'],
                    ['KT&G 상상마당 홍대', 'TOUR'],
                    ['홍대 걷고싶은거리', 'TOUR'],
                    ['앤트러사이트 합정점', 'CAFE'],
                    ['라이즈 오토그래프 컬렉션', 'HOTEL'],
                ],
            },
            {
                startTime: '10:00',
                center: [37.5605, 126.9076],
                places: [
                    ['오레노라멘 본점', 'RESTAURANT'],
                    ['망원시장', 'TOUR'],
                    ['문화비축기지', 'TOUR'],
                    ['프릳츠 도화점', 'CAFE'],
                ],
            },
        ],
    },
    {
        courseId: 1604,
        themeSlug: 'walking-alley',
        title: '문래 철공소 골목',
        description:
            '철공소와 예술 작업실이 섞인 문래창작촌을 천천히 순환하고 타임스퀘어에서 휴식하는 코스예요',
        optionName: '문래 철공소 골목',
        badge: 'ART',
        tone: 'balanced',
        region: '문래 · 영등포',
        tags: ['문래', '철공소', '예술', '산업골목'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '12:00',
                center: [37.516, 126.896],
                places: [
                    ['문래창작촌', 'TOUR'],
                    ['안양천 생태초화원', 'TOUR'],
                    ['맨홀커피', 'CAFE'],
                    ['타임스퀘어', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1605,
        themeSlug: 'walking-alley',
        title: '해방촌–한남 취향 골목',
        description:
            '오래된 시장 골목에서 음악과 커피 공간으로 이어지며 혼자 기록하고 걷기 좋은 취향 산책 코스예요',
        optionName: '해방촌 한남 골목',
        badge: 'MUSIC',
        tone: 'preference',
        region: '해방촌 · 이태원 · 한남',
        tags: ['해방촌', '한남동', '음악', '커피'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '12:00',
                center: [37.538, 126.995],
                places: [
                    ['해방촌 신흥시장', 'TOUR'],
                    ['현대카드 뮤직 라이브러리', 'TOUR'],
                    ['맥심플랜트', 'CAFE'],
                ],
            },
        ],
    },
];
