import nightDateImage from '../../assets/images/moods/mood-date-night.png';

/**
 * 데이트하기 좋은 밤 테마 코스 원본 데이터
 */
export const dateNightCourseRecipes = [
    {
        courseId: 1101,
        themeSlug: 'night-date',
        title: '정동 문화 데이트와 서울로 야경',
        description:
            '정동의 미술관과 고궁을 둘러보고 서울로의 불빛으로 마무리하는 도심 야간 데이트 코스예요.',
        optionName: '정동 야경 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '정동 · 서울역',
        tags: ['미술관', '고궁', '야경', '데이트'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '13:00',
                center: [37.5655, 126.975],
                places: [
                    ['서울시립미술관 서소문본관', 'TOUR'],
                    ['정동길', 'TOUR'],
                    ['르풀', 'CAFE'],
                    ['덕수궁', 'TOUR'],
                    ['서울로7017', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1102,
        themeSlug: 'night-date',
        title: '성수 감성에서 응봉산 야경까지',
        description:
            '전시와 서울숲, 성수의 맛집과 카페를 즐긴 뒤 응봉산에서 한강 야경을 감상하는 코스예요.',
        optionName: '성수 감성 야경 코스',
        badge: 'PICK',
        tone: 'balanced',
        region: '성수 · 서울숲 · 응봉산',
        tags: ['전시', '서울숲', '카페', '야경'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '12:30',
                center: [37.5445, 127.044],
                places: [
                    ['D뮤지엄', 'TOUR'],
                    ['난포', 'RESTAURANT'],
                    ['서울숲', 'TOUR'],
                    ['센터커피 서울숲점', 'CAFE'],
                    ['응봉산 팔각정', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1103,
        themeSlug: 'night-date',
        title: '잠실 뮤지컬 기념일 데이트',
        description:
            '책과 호수 산책, 카페와 공연을 즐긴 뒤 서울의 높은 야경을 감상하는 기념일 코스예요.',
        optionName: '잠실 기념일 코스',
        badge: 'SPECIAL',
        tone: 'distance',
        region: '잠실 · 송파',
        tags: ['석촌호수', '뮤지컬', '서울스카이', '기념일'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '11:30',
                center: [37.512, 127.102],
                places: [
                    ['서울책보고', 'TOUR'],
                    ['석촌호수', 'TOUR'],
                    ['카페 캠프통', 'CAFE'],
                    ['샤롯데씨어터', 'TOUR'],
                    ['빌즈 잠실', 'RESTAURANT'],
                    ['서울스카이', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1104,
        themeSlug: 'night-date',
        title: '망원에서 선유도까지 노을 데이트',
        description:
            '망원동의 시장과 카페를 즐기고 선유도의 노을과 야간 산책으로 이어지는 코스예요.',
        optionName: '한강 노을 데이트',
        badge: 'SUNSET',
        tone: 'balanced',
        region: '망원 · 선유도',
        tags: ['시장', '카페', '한강', '노을'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '14:00',
                center: [37.552, 126.91],
                places: [
                    ['망원시장', 'TOUR'],
                    ['망원동 맛집거리', 'RESTAURANT'],
                    ['카페 꼼마 합정점', 'CAFE'],
                    ['선유도공원', 'TOUR'],
                    ['선유교', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1105,
        themeSlug: 'night-date',
        title: '반포 빛나는 기념일',
        description:
            '예술과 식사, 한강 야경을 여유롭게 연결한 특별한 날을 위한 1박 2일 데이트 코스예요.',
        optionName: '반포 기념일 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '서초 · 반포',
        tags: ['예술', '한강', '야경', '호캉스'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '12:00',
                center: [37.505, 127.005],
                places: [
                    ['예술의전당', 'TOUR'],
                    ['테라로사 예술의전당점', 'CAFE'],
                    ['고속터미널 맛집거리', 'RESTAURANT'],
                    ['반포한강공원', 'TOUR'],
                    ['세빛섬', 'TOUR'],
                    ['JW 메리어트 호텔 서울', 'HOTEL'],
                ],
            },
            {
                startTime: '10:30',
                center: [37.5065, 127.004],
                places: [
                    ['고투몰', 'TOUR'],
                    ['카페 앤트러사이트 서래', 'CAFE'],
                    ['서래마을', 'TOUR'],
                    ['서리풀공원', 'TOUR'],
                ],
            },
        ],
    },
];