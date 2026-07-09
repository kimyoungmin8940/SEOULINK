// RecommendSection은 메인페이지의 추천 코스 영역
// 로그인하지 않았거나, 로그인했어도 취향 검사 추천 기록이 없으면
// 테마별 코스 전체 목록에서 인기순 4개를 보여줌
import CourseCard from '../course/CourseCard';
import { Sparkles, ChevronRight } from 'lucide-react';
import { isLoggedIn as checkIsLoggedIn, handleProtectedLinkClick } from '../../utils/authGuard';

import rainyCafe from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetSeoul from '../../assets/images/moods/mood-sunset-seoul.png';
import hanokPhoto from '../../assets/images/moods/mood-hanok-photo.png';
import walkingAlley from '../../assets/images/moods/mood-walking-alley.png';
import nightDate from '../../assets/images/moods/mood-date-night.png';
import localFood from '../../assets/images/moods/mood-local-food.png';

// 테마별 코스 전체 목록 임시 데이터
// 실제 백엔드가 연결되면 /api/courses/themes?sort=popular 같은 API 응답으로 대체하면 됨
// popularity 값이 높을수록 인기 있는 코스라고 가정
const themeCourses = [
    {
        courseId: 101,
        title: '한강 노을 산책 코스',
        description: '노을 명소와 한강 산책길을 따라 걷는 감성 코스',
        imageUrl: sunsetSeoul,
        duration: '약 4시간',
        area: '여의도 · 반포',
        tags: ['노을', '한강', '산책'],
        themeCode: 'sunset',
        popularity: 98,
        linkUrl: '/courses/101',
    },
    {
        courseId: 102,
        title: '성수 카페 & 감성 코스',
        description: '비 오는 날에도 좋은 성수 카페와 감성 공간 코스',
        imageUrl: rainyCafe,
        duration: '약 5시간',
        area: '성수동',
        tags: ['카페투어', '감성', '핫플'],
        themeCode: 'rainy-cafe',
        popularity: 95,
        linkUrl: '/courses/102',
    },
    {
        courseId: 103,
        title: '익선동 골목 데이트 코스',
        description: '한옥 골목과 야경 분위기를 함께 즐기는 데이트 코스',
        imageUrl: hanokPhoto,
        duration: '약 3시간',
        area: '익선동',
        tags: ['골목', '데이트', '카페'],
        themeCode: 'hanok-photo',
        popularity: 91,
        linkUrl: '/courses/103',
    },
    {
        courseId: 104,
        title: '북촌 감성 산책 코스',
        description: '한옥과 갤러리가 어우러진 북촌 산책 코스',
        imageUrl: walkingAlley,
        duration: '약 4시간',
        area: '북촌',
        tags: ['북촌', '문화', '사진명소'],
        themeCode: 'walking-alley',
        popularity: 88,
        linkUrl: '/courses/104',
    },
    {
        courseId: 105,
        title: '서울 야경 데이트 코스',
        description: '밤 산책과 야경 명소를 중심으로 즐기는 데이트 코스',
        imageUrl: nightDate,
        duration: '약 4시간',
        area: '남산 · 한강',
        tags: ['야경', '데이트', '밤산책'],
        themeCode: 'night-date',
        popularity: 84,
        linkUrl: '/courses/105',
    },
    {
        courseId: 106,
        title: '로컬 맛집 하루 코스',
        description: '서울의 동네 맛집과 시장 분위기를 즐기는 로컬 코스',
        imageUrl: localFood,
        duration: '약 5시간',
        area: '종로 · 을지로',
        tags: ['맛집', '로컬', '시장'],
        themeCode: 'local-food',
        popularity: 80,
        linkUrl: '/courses/106',
    },
];

function getStoredRecommendedCourses() {
    // 나중에 백엔드 연결 전까지 테스트하기 쉽게 localStorage도 확인
    // 예: localStorage.setItem('recommendedCourses', JSON.stringify([...]))
    const storageKeys = ['recommendedCourses', 'myRecommendedCourses', 'recommendationCourses'];

    for (const key of storageKeys) {
        const value = localStorage.getItem(key);

        if (!value) {
            continue;
        }

        try {
            const parsedValue = JSON.parse(value);

            if (Array.isArray(parsedValue)) {
                return parsedValue;
            }
        } catch {
            return [];
        }
    }

    return [];
}

function normalizeRecommendedCourse(course) {
    const courseId = course.recommendationId || course.courseId || course.id;

    return {
        ...course,
        courseId,
        linkUrl: `/courses/recommendations/${courseId}`,
        requiresLogin: true,
    };
}

function RecommendSection() {
    const myRecommendedCourses = getStoredRecommendedCourses().map(normalizeRecommendedCourse);
    const hasMyRecommendedCourses = checkIsLoggedIn() && myRecommendedCourses.length > 0;

    // 추천받은 코스가 있으면 최신 4개, 없으면 테마별 전체 코스에서 인기순 4개
    const coursesToShow = hasMyRecommendedCourses
        ? myRecommendedCourses.slice(0, 4)
        : [...themeCourses]
            .sort((a, b) => b.popularity - a.popularity)
            .slice(0, 4);

    const sectionTitle = hasMyRecommendedCourses ? '취향에 맞는 추천 코스' : '인기 테마 추천 코스';
    const sectionDescription = hasMyRecommendedCourses
        ? '취향 검사로 추천받은 최신 코스 4개를 확인해보세요.'
        : '아직 추천받은 코스가 없어 테마별 코스 중 인기순 4개를 보여드려요.';
    const moreLink = hasMyRecommendedCourses ? '/courses/recommendations' : '/courses/themes';

    return (
        <section className="section">
            {/* 섹션 상단 제목/설명/더보기 버튼 영역 */}
            <div className="course-heading">
                <div className="course-heading-left">
                    <div className="course-heading-title">
                        <Sparkles className="course-heading-icon" size={21} strokeWidth={2.2} />
                        <h2>{sectionTitle}</h2>
                    </div>

                    <p>{sectionDescription}</p>
                </div>

                <a
                    className="course-more-btn"
                    href={moreLink}
                    onClick={(event) => {
                        if (hasMyRecommendedCourses) {
                            handleProtectedLinkClick(event);
                        }
                    }}
                >
                    전체 보기
                    <ChevronRight size={16} strokeWidth={2.2} />
                </a>
            </div>

            {/* 상황에 따라 개인 추천 코스 또는 인기 테마 코스 4개를 보여줌 */}
            <div className="course-grid">
                {coursesToShow.map((course) => (
                    <CourseCard key={course.courseId} course={course} />
                ))}
            </div>
        </section>
    );
}

export default RecommendSection;
