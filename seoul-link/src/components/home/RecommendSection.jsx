// RecommendSection은 메인페이지의 추천 코스 영역
// 로그인하지 않았거나, 로그인했어도 취향 검사 추천 기록이 없으면
// 테마별 코스 전체 목록에서 인기순 4개를 보여줌
import CourseCard from '../course/CourseCard';
import { Sparkles, ChevronRight } from 'lucide-react';
import { isLoggedIn as checkIsLoggedIn, handleProtectedLinkClick } from '../../utils/authGuard';

import { mockThemeCourseListResponse } from '../../mocks/homeMockData';

const themeCourses = mockThemeCourseListResponse.data;

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

function RecommendSection() {
    const myRecommendedCourses = getStoredRecommendedCourses();
    const hasMyRecommendedCourses = checkIsLoggedIn() && myRecommendedCourses.length > 0;

    // 추천받은 코스가 있으면 최신 4개, 없으면 테마별 전체 코스에서 인기순 4개
    const coursesToShow = hasMyRecommendedCourses
        ? myRecommendedCourses.slice(0, 4)
        : [...themeCourses]
            .sort((a, b) => b.likeCount - a.likeCount)
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
                {coursesToShow.map((course) => {
                    const recommendationId = course.recommendationId || course.courseId;
                    const detailPath = hasMyRecommendedCourses
                        ? `/courses/recommendations/${recommendationId}`
                        : `/courses/${course.courseId}`;

                    return (
                        <CourseCard
                            key={course.courseId}
                            course={course}
                            detailPath={detailPath}
                            requiresLogin={hasMyRecommendedCourses}
                        />
                    );
                })}
            </div>
        </section>
    );
}

export default RecommendSection;
