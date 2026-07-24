// RecommendSection은 메인페이지의 추천 코스 영역
// 로그인하지 않았거나, 로그인했어도 취향 검사 추천 기록이 없으면
// 테마별 코스 전체 목록에서 인기순 4개를 보여줌
import { useEffect, useMemo, useState } from 'react';
import CourseCard from '../course/CourseCard';
import { Sparkles, ChevronRight } from 'lucide-react';
import { isLoggedIn as checkIsLoggedIn, handleProtectedLinkClick } from '../../utils/authGuard';
import { getRecommendedCourses } from '../../api/courseApi';
import {
    getCourseId,
    getCurrentMemberId,
    normalizeRecommendedCourseList,
} from '../../utils/courseHistory';

import { mockThemeCourseListResponse } from '../../mocks/homeMockData';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';

const themeCourses = mockThemeCourseListResponse.data;
const fallbackImages = [hanokImage, sunsetImage, rainyCafeImage, walkingImage];

function RecommendSection() {
    const isLoggedIn = checkIsLoggedIn();
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const [myRecommendedCourses, setMyRecommendedCourses] = useState([]);

    // 백엔드의 설문 기반 저장 코스 이력을 최신순으로 받아 메인에는 앞의 4개만 표시합니다.
    useEffect(() => {
        if (!isLoggedIn || !memberId) return undefined;

        const controller = new AbortController();

        getRecommendedCourses(memberId, { signal: controller.signal })
            .then((response) => {
                const courses = normalizeRecommendedCourseList(response, {
                    fallbackImages,
                });

                setMyRecommendedCourses(courses);
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;
                // 메인 화면 전체를 막지 않고 기본 인기 테마 카드를 유지합니다.
                setMyRecommendedCourses([]);
            });

        return () => controller.abort();
    }, [isLoggedIn, memberId]);

    const hasMyRecommendedCourses = isLoggedIn && myRecommendedCourses.length > 0;

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
    // 추천 이력이 있으면 지금까지 추천받은 코스 전체 목록으로 이동합니다.
    const moreLink = hasMyRecommendedCourses ? '/courses/recommendations' : '/courses/themes';

    return (
        <section className="section">
            {/* 섹션 상단 제목/설명/더보기 버튼 영역 */}
            <div className="course-heading">
                <div className="course-heading-left">
                    <div className="course-heading-title section-heading-title">
                        <Sparkles className="course-heading-icon section-heading-icon" size={21} strokeWidth={2.2} />
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
                    const courseId = getCourseId(course);

                    if (!courseId) return null;

                    const detailPath = hasMyRecommendedCourses
                        ? `/courses/recommendations/${courseId}`
                        : `/courses/${courseId}`;
                    const normalizedCourse = { ...course, courseId };

                    return (
                        <CourseCard
                            key={courseId}
                            course={normalizedCourse}
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
