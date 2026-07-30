import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';

/**
 * 비 오는 날의 카페 테마 코스 원본 데이터
 */
export const rainyCafeCourseRecipes = [
    {
        courseId: 1501,
        themeSlug: 'rainy-cafe',
        title: '비 내리는 안국에서 만나는 한옥 카페',
        description:
            '한옥 처마와 비에 젖은 돌길의 분위기를 즐기며 공예 전시와 카페를 함께 둘러보는 실내 중심 코스예요',
        optionName: '안국 한옥 카페',
        badge: 'BEST',
        tone: 'preference',
        region: '안국 · 삼청동 · 인사동',
        tags: ['한옥', '카페', '전시', '빗길'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '14:00',
                center: [37.577, 126.985],
                places: [
                    ['꽃,밥에피다 인사동점', 'RESTAURANT'],
                    ['쌈지길', 'TOUR'],
                    ['카페 레이어드 안국', 'CAFE'],
                    ['서울공예박물관', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1502,
        themeSlug: 'rainy-cafe',
        title: '경춘선숲길 끝에서 만나는 기차 카페',
        description:
            '경춘선숲길 갤러리와 기차가 있는 카페를 거쳐 북서울미술관 전시까지 즐기는 노원 실내·문화 코스예요',
        optionName: '경춘선 기차 카페',
        badge: 'RETRO',
        tone: 'balanced',
        region: '공릉 · 노원',
        tags: ['경춘선숲길', '기차카페', '북서울미술관', '전시'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '14:00',
                center: [37.568, 126.993],
                places: [
                    ['경춘선숲길 갤러리', 'TOUR'],
                    ['카페 기차가 있는 풍경', 'CAFE'],
                    ['서울시립 북서울미술관', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1503,
        themeSlug: 'rainy-cafe',
        title: '박물관 사이에서 쉬어가는 강서 카페',
        description:
            '겸재정선미술관과 허준박물관을 관람한 뒤 강서의 카페에서 쉬어가는 비 오는 날 문화 코스예요',
        optionName: '강서 박물관 카페',
        badge: 'TREND',
        tone: 'distance',
        region: '가양 · 마곡',
        tags: ['겸재정선미술관', '허준박물관', '강서', '카페'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '14:00',
                center: [37.544, 127.044],
                places: [
                    ['겸재정선미술관', 'TOUR'],
                    ['허준박물관', 'TOUR'],
                    ['어나더사이드', 'CAFE'],
                ],
            },
        ],
    },
    {
        courseId: 1504,
        themeSlug: 'rainy-cafe',
        title: '비 걱정 없이 즐기는 코엑스 실내 하루',
        description:
            '외부 이동을 최소화하고 카페, 쇼핑몰, 아쿠아리움과 백화점을 연결해 폭우에도 즐길 수 있는 코스예요',
        optionName: '코엑스 실내 코스',
        badge: 'INDOOR',
        tone: 'balanced',
        region: '삼성 · 코엑스',
        tags: ['실내', '코엑스', '아쿠아리움', '쇼핑'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '14:00',
                center: [37.511, 127.059],
                places: [
                    ['테라로사 포스코센터점', 'CAFE'],
                    ['스타필드 코엑스몰', 'TOUR'],
                    ['씨라이프 코엑스 아쿠아리움', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1505,
        themeSlug: 'rainy-cafe',
        title: '북한산을 바라보는 은평 한옥 카페',
        description:
            '통창 너머 한옥과 북한산의 비 풍경을 감상하며 박물관과 카페에서 조용히 머무는 코스예요',
        optionName: '은평 한옥 빗길',
        badge: 'HEALING',
        tone: 'preference',
        region: '은평 · 진관동',
        tags: ['은평', '한옥', '북한산', '카페'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '14:00',
                center: [37.641, 126.938],
                places: [
                    ['은평한옥마을', 'TOUR'],
                    ['은평역사한옥박물관', 'TOUR'],
                    ['롱브레드 은평한옥마을점', 'CAFE'],
                ],
            },
        ],
    },
];
