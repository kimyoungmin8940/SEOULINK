import rainyCafe from '../assets/images/moods/mood-rainy-cafe.png';
import sunsetSeoul from '../assets/images/moods/mood-sunset-seoul.png';
import hanokPhoto from '../assets/images/moods/mood-hanok-photo.png';
import walkingAlley from '../assets/images/moods/mood-walking-alley.png';
import nightDate from '../assets/images/moods/mood-date-night.png';
import localFood from '../assets/images/moods/mood-local-food.png';
import ctaNight from '../assets/images/cta-seoul-night.jpg';

/**
 * 코스 목록 API의 임시 응답 데이터이다.
 *
 * 실제 백엔드 연결 후에는 아래 객체 대신
 * GET /api/courses 응답의 data 배열을 사용하면 된다.
 *
 * 화면 이동 경로(linkUrl), 로그인 필요 여부(requiresLogin)처럼
 * 프론트 화면에서만 사용하는 값은 API 데이터에 포함하지 않는다.
 */
export const mockThemeCourseListResponse = {
    success: true,
    message: '인기 테마 코스 목록 조회 성공',
    data: [
        {
            courseId: 101,
            title: '한강 노을 산책 코스',
            description: '노을 명소와 한강 산책길을 따라 걷는 감성 코스',
            imageUrl: sunsetSeoul,
            duration: '약 4시간',
            area: '여의도 · 반포',
            tags: ['노을', '한강', '산책'],
            themeCode: 'SUNSET',
            likeCount: 98,
            liked: false,
        },
        {
            courseId: 102,
            title: '성수 카페 & 감성 코스',
            description: '비 오는 날에도 좋은 성수 카페와 감성 공간 코스',
            imageUrl: rainyCafe,
            duration: '약 5시간',
            area: '성수동',
            tags: ['카페투어', '감성', '핫플'],
            themeCode: 'RAINY_CAFE',
            likeCount: 95,
            liked: false,
        },
        {
            courseId: 103,
            title: '익선동 골목 데이트 코스',
            description: '한옥 골목과 야경 분위기를 함께 즐기는 데이트 코스',
            imageUrl: hanokPhoto,
            duration: '약 3시간',
            area: '익선동',
            tags: ['골목', '데이트', '카페'],
            themeCode: 'HANOK_PHOTO',
            likeCount: 91,
            liked: false,
        },
        {
            courseId: 104,
            title: '북촌 감성 산책 코스',
            description: '한옥과 갤러리가 어우러진 북촌 산책 코스',
            imageUrl: walkingAlley,
            duration: '약 4시간',
            area: '북촌',
            tags: ['북촌', '문화', '사진명소'],
            themeCode: 'WALKING_ALLEY',
            likeCount: 88,
            liked: false,
        },
        {
            courseId: 105,
            title: '서울 야경 데이트 코스',
            description: '밤 산책과 야경 명소를 중심으로 즐기는 데이트 코스',
            imageUrl: nightDate,
            duration: '약 4시간',
            area: '남산 · 한강',
            tags: ['야경', '데이트', '밤산책'],
            themeCode: 'NIGHT_DATE',
            likeCount: 84,
            liked: false,
        },
        {
            courseId: 106,
            title: '로컬 맛집 하루 코스',
            description: '서울의 동네 맛집과 시장 분위기를 즐기는 로컬 코스',
            imageUrl: localFood,
            duration: '약 5시간',
            area: '종로 · 을지로',
            tags: ['맛집', '로컬', '시장'],
            themeCode: 'LOCAL_FOOD',
            likeCount: 80,
            liked: false,
        },
    ],
};

/**
 * 후기 목록 API의 임시 응답 데이터이다.
 *
 * 실제 백엔드 연결 후에는 아래 객체 대신
 * GET /api/reviews 응답의 data 배열을 사용하면 된다.
 */
export const mockReviewListResponse = {
    success: true,
    message: '후기 목록 조회 성공',
    data: [
        {
            reviewId: 1,
            courseId: 102,
            courseTitle: '성수 카페 & 감성 코스',
            memberId: 11,
            nickname: 'soyeon_79',
            profileImageUrl: 'https://i.pravatar.cc/80?img=47',
            rating: 5,
            content: '성수 코스 그대로 따라갔는데 하루 동선이 편하고 알찼어요!',
            imageUrls: [rainyCafe, hanokPhoto],
            likeCount: 24,
            liked: false,
            createdAt: '2026-07-08T14:20:00',
        },
        {
            reviewId: 2,
            courseId: 104,
            courseTitle: '북촌 감성 산책 코스',
            memberId: 12,
            nickname: 'travel_jaemin',
            profileImageUrl: 'https://i.pravatar.cc/80?img=12',
            rating: 5,
            content: '혼자 여행이었는데 장소 분위기가 정말 잘 맞았어요.',
            imageUrls: [walkingAlley, ctaNight],
            likeCount: 18,
            liked: false,
            createdAt: '2026-07-07T11:10:00',
        },
        {
            reviewId: 3,
            courseId: 101,
            courseTitle: '한강 노을 산책 코스',
            memberId: 13,
            nickname: 'minzi_trip',
            profileImageUrl: 'https://i.pravatar.cc/80?img=32',
            rating: 5,
            content: '한강 노을 코스는 저장해두고 다시 가고 싶었어요!',
            imageUrls: [sunsetSeoul, localFood],
            likeCount: 31,
            liked: false,
            createdAt: '2026-07-06T19:40:00',
        },
    ],
};
