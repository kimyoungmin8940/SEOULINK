import hanokPhotoImage from '../../assets/images/moods/mood-hanok-photo.png';

/**
 * 사진 찍기 좋은 한옥길 테마 코스 원본 데이터
 */
export const hanokCourseRecipes = [
    {
        courseId: 1201,
        themeSlug: 'hanok-photo',
        title: '북촌 건축과 한옥 셔터 투어',
        description:
            '공예와 건축을 보고 북촌에서 익선동까지 한옥의 표정을 사진에 담는 코스예요',
        optionName: '북촌 사진 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '안국 · 북촌 · 익선동',
        tags: ['북촌', '한옥', '공예', '사진명소'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.579, 126.985],
                places: [
                    ['서울공예박물관', 'TOUR'],
                    ['아라리오뮤지엄 인 스페이스', 'TOUR'],
                    ['프릳츠 원서점', 'CAFE'],
                    ['북촌한옥마을', 'TOUR'],
                    ['깡통만두', 'RESTAURANT'],
                    ['익선동 한옥거리', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1202,
        themeSlug: 'hanok-photo',
        title: '서촌 궁궐과 인왕산 프레임',
        description:
            '경복궁의 선과 서촌의 식당·카페를 지나 윤동주 시인의 언덕에서 도심 풍경을 담는 사진 산책 코스예요',
        optionName: '서촌 풍경 코스',
        badge: 'FRAME',
        tone: 'balanced',
        region: '경복궁 · 서촌',
        tags: ['경복궁', '서촌', '윤동주시인의언덕', '사진산책'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.577, 126.968],
                places: [
                    ['경복궁', 'TOUR'],
                    ['토속촌삼계탕', 'RESTAURANT'],
                    ['스태픽스', 'CAFE'],
                    ['아키비스트 서촌', 'CAFE'],
                    ['윤동주 시인의 언덕', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1203,
        themeSlug: 'hanok-photo',
        title: '고요한 한옥과 북한산 뷰',
        description:
            '한옥의 단정한 선과 북한산 능선을 한 장면에 담으며 천천히 머무는 코스예요',
        optionName: '은평 한옥 코스',
        badge: 'VIEW',
        tone: 'distance',
        region: '은평 · 북한산',
        tags: ['은평한옥마을', '북한산', '전망', '한옥'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.64, 126.94],
                places: [
                    ['은평역사한옥박물관', 'TOUR'],
                    ['은평한옥마을', 'TOUR'],
                    ['1인1잔', 'CAFE'],
                    ['북한산국립공원', 'TOUR'],
                    ['북한산큰숲 제빵소', 'CAFE'],
                ],
            },
        ],
    },
    {
        courseId: 1204,
        themeSlug: 'hanok-photo',
        title: '남산골 한옥 정원과 오래된 서울',
        description:
            '남산골 한옥과 전통문화 공간, 남산공원을 둘러본 뒤 문화역서울284의 근대 건축까지 담는 코스예요',
        optionName: '남산골 한옥 코스',
        badge: 'GREEN',
        tone: 'balanced',
        region: '충무로 · 남산 · 서울역',
        tags: ['남산골한옥마을', '남산공원', '문화역서울284', '건축사진'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.559, 126.994],
                places: [
                    ['남산골한옥마을', 'TOUR'],
                    ['목멱산방', 'RESTAURANT'],
                    ['피크닉', 'TOUR'],
                    ['남산공원', 'TOUR'],
                    ['문화역서울284', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1205,
        themeSlug: 'hanok-photo',
        title: '한옥에서 머무는 북촌 여행',
        description:
            '궁궐과 골목을 충분히 걷고 북촌의 한옥 숙소에서 밤을 보내는 1박 2일 코스예요',
        optionName: '북촌 한옥 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '서촌 · 북촌 · 익선동',
        tags: ['한옥숙소', '궁궐', '인사동', '익선동'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.58, 126.98],
                places: [
                    ['경복궁', 'TOUR'],
                    ['토속촌삼계탕', 'RESTAURANT'],
                    ['스태픽스', 'CAFE'],
                    ['북촌한옥마을', 'TOUR'],
                    ['락고재 서울 북촌', 'HOTEL'],
                ],
            },
            {
                startTime: '10:00',
                center: [37.574, 126.987],
                places: [
                    ['카페 레이어드 안국', 'CAFE'],
                    ['서울공예박물관', 'TOUR'],
                    ['꽃,밥에피다 인사동점', 'RESTAURANT'],
                    ['쌈지길', 'TOUR'],
                    ['익선동 한옥거리', 'TOUR'],
                    ['아라리오뮤지엄 인 스페이스', 'TOUR'],
                ],
            },
        ],
    },
];
