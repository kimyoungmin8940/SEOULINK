import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';

/**
 * 노을이 예쁜 서울 테마 코스 원본 데이터
 */
export const sunsetCourseRecipes = [
    {
        courseId: 1401,
        themeSlug: 'sunset',
        title: '서울숲 끝에서 만나는 응봉산 노을',
        description:
            '성수 전시와 카페를 즐긴 뒤 응봉산에서 한강 노을을 감상하는 코스예요. 비교적 짧게 산책하면서 확실한 전망을 즐길 수 있어요',
        optionName: '서울숲 노을 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '성수 · 서울숲 · 응봉산',
        tags: ['노을', '서울숲', '전망', '산책'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:00',
                center: [37.548, 127.043],
                places: [
                    ['서울숲', 'TOUR'],
                    ['난포', 'RESTAURANT'],
                    ['D뮤지엄', 'TOUR'],
                    ['센터커피 서울숲점', 'CAFE'],
                    ['응봉산 팔각정', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1402,
        themeSlug: 'sunset',
        title: '노들섬으로 이어지는 한강 노을길',
        description:
            '국립중앙박물관을 관람하고 용산에서 식사와 카페, 쇼핑을 즐긴 뒤 노들섬 노을로 마무리하는 반나절 코스예요',
        optionName: '노들섬 노을 코스',
        badge: 'VIEW',
        tone: 'balanced',
        region: '이촌 · 용산 · 노들섬',
        tags: ['국립중앙박물관', '용산', '노들섬', '노을'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:00',
                center: [37.514, 126.976],
                places: [
                    ['국립중앙박물관', 'TOUR'],
                    ['봉산집', 'RESTAURANT'],
                    ['테디 뵈르 하우스 용산점', 'CAFE'],
                    ['아이파크몰 용산', 'TOUR'],
                    ['노들섬', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1403,
        themeSlug: 'sunset',
        title: '여의도를 물들이는 황금빛 한강',
        description:
            '영등포에서 쇼핑과 식사를 즐기고 여의도의 카페와 더현대 서울을 거쳐 한강공원 노을을 감상하는 코스예요',
        optionName: '영등포 여의도 노을',
        badge: 'SUNSET',
        tone: 'distance',
        region: '영등포 · 여의도 · 한강',
        tags: ['타임스퀘어', '더현대서울', '여의도한강공원', '노을'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:00',
                center: [37.524, 126.93],
                places: [
                    ['타임스퀘어', 'TOUR'],
                    ['대한옥', 'RESTAURANT'],
                    ['카페꼼마 여의도신영증권점', 'CAFE'],
                    ['더현대 서울', 'TOUR'],
                    ['여의도한강공원', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1404,
        themeSlug: 'sunset',
        title: '세빛섬과 함께 빛나는 반포의 밤',
        description:
            '서초에서 식사와 예술 전시, 카페를 즐기고 고속터미널 쇼핑을 거쳐 세빛섬의 저녁 풍경으로 마무리하는 코스예요',
        optionName: '서초 세빛섬 저녁',
        badge: 'NIGHT',
        tone: 'balanced',
        region: '서초 · 고속터미널 · 반포',
        tags: ['예술의전당', '고투몰', '세빛섬', '저녁풍경'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:00',
                center: [37.506, 126.995],
                places: [
                    ['봉산옥', 'RESTAURANT'],
                    ['예술의전당', 'TOUR'],
                    ['테라로사 예술의전당점', 'CAFE'],
                    ['고투몰', 'TOUR'],
                    ['세빛섬', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1405,
        themeSlug: 'sunset',
        title: '시장 골목 끝에서 만나는 낙산 노을',
        description:
            '광화문과 시장 골목을 지나 낙산의 도심 노을을 감상하고, 다음 날 대학로와 익선동·안국을 여유롭게 걷는 1박 2일 코스예요',
        optionName: '광화문 낙산 노을 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '광화문 · 종로 · 낙산 · 안국',
        tags: ['광화문광장', '낙산공원', '도심노을', '1박2일'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:00',
                center: [37.592, 126.965],
                places: [
                    ['광화문미진', 'RESTAURANT'],
                    ['광화문광장', 'TOUR'],
                    ['광장시장', 'TOUR'],
                    ['카페 어니언 광장시장', 'CAFE'],
                    ['낙산공원', 'TOUR'],
                    ['토요코인 서울동대문2', 'HOTEL'],
                ],
            },
            {
                startTime: '10:00',
                center: [37.5782, 126.9886],
                places: [
                    ['대학로 공연거리', 'TOUR'],
                    ['익선동 한옥거리', 'TOUR'],
                    ['쌈지길', 'TOUR'],
                    ['카페 어니언 안국', 'CAFE'],
                    ['경복궁', 'TOUR'],
                ],
            },
        ],
    },
];
