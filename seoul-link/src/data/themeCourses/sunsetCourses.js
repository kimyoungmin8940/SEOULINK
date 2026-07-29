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
            '성수 전시와 카페를 즐긴 뒤 응봉산에서 한강 노을을 감상하는 코스예요. 비교적 짧게 산책하면서 확실한 전망을 즐길 수 있어요.',
        optionName: '서울숲 노을 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '성수 · 서울숲 · 응봉산',
        tags: ['노을', '서울숲', '전망', '산책'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '14:30',
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
            '국립중앙박물관을 관람한 뒤 한강을 따라 노들섬과 동작대교 전망 명소를 둘러보는 반나절 코스예요.',
        optionName: '노들섬 노을 코스',
        badge: 'VIEW',
        tone: 'balanced',
        region: '용산 · 노들섬 · 동작',
        tags: ['한강', '노들섬', '박물관', '노을'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '13:30',
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
            '쇼핑과 식사를 즐긴 뒤 여의도 한강변에서 노을과 야경을 감상하는 여행다운 서울 코스예요.',
        optionName: '여의도 한강 노을',
        badge: 'SUNSET',
        tone: 'distance',
        region: '여의도 · 한강',
        tags: ['여의도', '한강', '크루즈', '야경'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '15:30',
                center: [37.524, 126.93],
                places: [
                    ['타임스퀘어', 'TOUR'],
                    ['대한옥', 'RESTAURANT'],
                    ['카페꼼마 여의도신영증권점', 'CAFE'],
                    ['더현대 서울', 'TOUR'],
                    ['여의도 한강 공원', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1404,
        themeSlug: 'sunset',
        title: '세빛섬과 함께 빛나는 반포의 밤',
        description:
            '실내 쇼핑으로 시작해 세빛섬과 잠수교를 거쳐 한강 노을과 반포대교 야경으로 마무리하는 코스예요.',
        optionName: '반포 수변 노을',
        badge: 'NIGHT',
        tone: 'balanced',
        region: '고속터미널 · 반포',
        tags: ['반포', '세빛섬', '잠수교', '야경'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '15:30',
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
            '부암동의 조용한 식당과 카페를 즐기고 인왕산에서 서울 도심의 노을을 바라보는 서정적인 코스예요.',
        optionName: '부암동 인왕산 노을',
        badge: 'HEALING',
        tone: 'preference',
        region: '부암동 · 인왕산',
        tags: ['부암동', '인왕산', '전망', '감성'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '14:00',
                center: [37.592, 126.965],
                places: [
                    ['광화문미진', 'RESTAURANT'],
                    ['광화문광장', 'TOUR'],
                    ['광장시장', 'TOUR'],
                    ['카페 어니언 광장시장', 'CAFE'],
                    ['낙산공원', 'TOUR'],
                ],
            },
        ],
    },
];