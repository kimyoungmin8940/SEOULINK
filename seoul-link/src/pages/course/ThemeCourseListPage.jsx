import { useMemo, useState } from 'react';
import {
    ArrowLeft,
    CalendarDays,
    MapPinned,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import CourseRecommendationCard from '../../components/course/CourseRecommendationCard';
import {
    getThemeCourseBookmarkStatus,
    saveThemeCourse,
} from '../../api/courseApi';
import {
    getThemeCourseDefinition,
    getThemeCourses,
} from '../../data/themeCourseData';
import { getCurrentMemberId } from '../../utils/courseHistory';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';
import nightImage from '../../assets/images/moods/mood-date-night.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import foodImage from '../../assets/images/moods/mood-local-food.png';

const fallbackImages = [
    nightImage,
    hanokImage,
    foodImage,
    sunsetImage,
    walkingImage,
];

function getThemeSlug() {
    const match = window.location.pathname.match(/^\/courses\/themes\/([^/]+)\/?$/);
    return match?.[1] || null;
}

function ThemeCourseListPage() {
    const themeSlug = useMemo(() => getThemeSlug(), []);
    const theme = useMemo(() => getThemeCourseDefinition(themeSlug), [themeSlug]);
    const courses = useMemo(() => getThemeCourses(themeSlug), [themeSlug]);
    const [activeDayByCourse, setActiveDayByCourse] = useState({});

    if (!theme || courses.length === 0) {
        return (
            <div className="page course-result-page theme-course-list-page">
                <Header variant="default" />
                <main className="course-result-shell">
                    <section className="course-result-state-card">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h1>아직 준비 중인 테마예요</h1>
                        <p>다른 서울 테마에서 추천 코스를 둘러보세요.</p>
                        <a href="/">홈으로 돌아가기</a>
                    </section>
                </main>
                <Footer />
            </div>
        );
    }

    return (
        <div className="page course-result-page theme-course-list-page">
            <Header variant="default" />

            <main className="course-result-shell">
                <section className="course-result-hero theme-course-list-hero">
                    <div className="course-result-hero-copy">
                        <a
                            className="course-result-back-btn"
                            href="/"
                            aria-label="홈으로 돌아가기"
                        >
                            <ArrowLeft size={20} strokeWidth={2.1} aria-hidden="true" />
                        </a>

                        <div>
                            <p className="course-result-eyebrow">
                                <Sparkles size={14} aria-hidden="true" />
                                SEOUL THEME COURSE
                            </p>
                            <h1>{theme.title}</h1>
                            <p>{theme.description}</p>

                            <div className="theme-course-hero-meta">
                                <span><CalendarDays size={15} aria-hidden="true" />당일치기 · 1박 2일</span>
                                <span><MapPinned size={15} aria-hidden="true" />서울 테마 코스 {courses.length}개</span>
                            </div>
                        </div>
                    </div>

                    <div className="course-result-hero-visual" aria-hidden="true">
                        <img src={theme.image} alt="" />
                    </div>
                </section>

                <div className="theme-course-list-heading">
                    <div>
                        <span>추천 코스</span>
                        <h2>{theme.title} 코스 {courses.length}개</h2>
                    </div>
                    <p>원하는 코스를 선택하면 날짜별 장소와 이동 동선을 자세히 볼 수 있어요.</p>
                </div>

                <section
                    className="course-result-list theme-course-result-list"
                    aria-label={`${theme.title} 추천 코스 목록`}
                >
                    {courses.map((course, index) => (
                        <CourseRecommendationCard
                            key={course.courseId}
                            option={course}
                            transportMode={course.transportMode}
                            fallbackImage={fallbackImages[index % fallbackImages.length]}
                            activeDayNo={activeDayByCourse[course.courseId] || course.days[0]?.dayNo || 1}
                            variant="theme"
                            detailPath={`/courses/themes/${themeSlug}/${course.courseId}`}
                            isEstimatedTravelTime={false}
                            onActiveDayChange={(dayNo) => {
                                setActiveDayByCourse((previous) => ({
                                    ...previous,
                                    [course.courseId]: dayNo,
                                }));
                            }}
                        />
                    ))}
                </section>
            </main>

            <Footer />
        </div>
    );
}

export default ThemeCourseListPage;

