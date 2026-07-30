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
import useThemeCourseCatalog from '../../hooks/useThemeCourseCatalog';
import useThemeCourseBookmarks from '../../hooks/useThemeCourseBookmarks';
import { requireLogin } from '../../utils/authGuard';
import { getCurrentMemberId } from '../../utils/courseHistory';
import {
    getThemeCourseDefinition,
} from '../../data/themeCourseData';
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
    const {
        courses,
        status: catalogStatus,
        error: catalogError,
        missingPlaceNames,
    } = useThemeCourseCatalog(themeSlug);
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const [activeDayByCourse, setActiveDayByCourse] = useState({});
    const [bookmarkNotice, setBookmarkNotice] = useState('');
    const bookmarks = useThemeCourseBookmarks(memberId, { courses });

    const toggleBookmark = async (course) => {
        if (!requireLogin('코스를 저장하려면 로그인이 필요해요.')) return;

        if (!memberId) {
            setBookmarkNotice('회원 정보를 확인할 수 없어요. 다시 로그인해 주세요.');
            return;
        }

        try {
            const saved = await bookmarks.toggle(course);
            setBookmarkNotice(
                saved
                    ? '저장한 추천 코스에 추가했어요.'
                    : '저장한 추천 코스에서 삭제했어요.',
            );
        } catch (error) {
            setBookmarkNotice(
                error?.message || '코스 저장 상태를 변경하지 못했어요.',
            );
        }
    };

    if (!theme || catalogStatus === 'not-found') {
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

    if (catalogStatus === 'loading') {
        return (
            <div className="page course-result-page theme-course-list-page">
                <Header variant="default" />
                <main className="course-result-shell">
                    <section className="course-result-state-card" aria-live="polite">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h1>DB에서 코스 장소를 불러오고 있어요</h1>
                        <p>장소 정보와 사진을 준비하는 중입니다.</p>
                    </section>
                </main>
                <Footer />
            </div>
        );
    }

    if (catalogStatus === 'error') {
        return (
            <div className="page course-result-page theme-course-list-page">
                <Header variant="default" />
                <main className="course-result-shell">
                    <section className="course-result-state-card" role="alert">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h1>장소 데이터를 불러오지 못했어요</h1>
                        <p>{catalogError?.message || '백엔드와 DB 연결 상태를 확인해 주세요.'}</p>
                        <button type="button" onClick={() => window.location.reload()}>
                            다시 불러오기
                        </button>
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
                                <span><MapPinned size={15} aria-hidden="true" />서울 테마 코스 5개</span>
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
                        <h2>{theme.title} 코스 5개</h2>
                    </div>
                    <p>원하는 코스를 선택하면 날짜별 장소와 이동 동선을 자세히 볼 수 있어요.</p>
                </div>

                {bookmarkNotice && (
                    <p className="theme-course-bookmark-notice" role="status">
                        {bookmarkNotice}
                    </p>
                )}

                {missingPlaceNames.length > 0 && (
                    <p className="theme-course-bookmark-notice" role="status">
                        DB에서 찾지 못한 장소 {missingPlaceNames.length}개는 임시 정보로 표시 중이에요.
                    </p>
                )}

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
                            isBookmarked={bookmarks.isSaved(course.sourceCourseKey)}
                            isBookmarking={bookmarks.isBusy(course.sourceCourseKey)}
                            onToggleBookmark={toggleBookmark}
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
