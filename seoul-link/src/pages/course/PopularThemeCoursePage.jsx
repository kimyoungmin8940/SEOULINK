import { useEffect, useMemo, useState } from 'react';
import {
    ArrowLeft,
    CalendarDays,
    MapPinned,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import CourseRecommendationCard from '../../components/course/CourseRecommendationCard';
import { getThemeCoursePopularity } from '../../api/themeCourseApi';
import useThemeCourseCatalog from '../../hooks/useThemeCourseCatalog';
import useThemeCourseBookmarks from '../../hooks/useThemeCourseBookmarks';
import { requireLogin } from '../../utils/authGuard';
import { getCurrentMemberId } from '../../utils/courseHistory';
import { themeCourseDefinitions } from '../../data/themeCourseData';
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

const defaultCoursePriority = [
    'SUNSET_1401',
    'RAINY_CAFE_1501',
    'HANOK_PHOTO_1201',
    'WALKING_ALLEY_1601',
];

function getDefaultRank(sourceCourseKey) {
    const rank = defaultCoursePriority.indexOf(sourceCourseKey);
    return rank < 0 ? defaultCoursePriority.length : rank;
}

function PopularThemeCoursePage() {
    const {
        courses,
        status: catalogStatus,
        error: catalogError,
        missingPlaceNames,
    } = useThemeCourseCatalog('all');
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const [activeTheme, setActiveTheme] = useState('all');
    const [activeDayByCourse, setActiveDayByCourse] = useState({});
    const [bookmarkNotice, setBookmarkNotice] = useState('');
    const [saveCounts, setSaveCounts] = useState({});
    const bookmarks = useThemeCourseBookmarks(memberId, { courses });

    useEffect(() => {
        const controller = new AbortController();

        getThemeCoursePopularity({ signal: controller.signal })
            .then((response) => {
                setSaveCounts(Object.fromEntries(
                    (Array.isArray(response) ? response : [])
                        .filter((item) => item?.sourceCourseKey)
                        .map((item) => [
                            item.sourceCourseKey,
                            Number(item.saveCount) || 0,
                        ]),
                ));
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') setSaveCounts({});
            });

        return () => controller.abort();
    }, []);

    const sortedCourses = useMemo(
        () => [...courses]
            .sort((left, right) => {
                const countDifference = (saveCounts[right.sourceCourseKey] || 0)
                    - (saveCounts[left.sourceCourseKey] || 0);

                if (countDifference !== 0) return countDifference;

                const rankDifference = getDefaultRank(left.sourceCourseKey)
                    - getDefaultRank(right.sourceCourseKey);

                if (rankDifference !== 0) return rankDifference;
                return left.courseId - right.courseId;
            }),
        [courses, saveCounts],
    );

    const bestCourses = useMemo(
        () => sortedCourses.filter((course) => course.badge === 'BEST'),
        [sortedCourses],
    );

    const visibleCourses = useMemo(
        () => (activeTheme === 'all'
            ? bestCourses
            : bestCourses.filter((course) => course.themeSlug === activeTheme)),
        [activeTheme, bestCourses],
    );

    const themes = useMemo(
        () => Object.values(themeCourseDefinitions),
        [],
    );

    const toggleBookmark = async (course) => {
        if (!requireLogin('코스를 저장하려면 로그인이 필요해요')) return;

        if (!memberId) {
            setBookmarkNotice('회원 정보를 확인할 수 없어요 다시 로그인해 주세요');
            return;
        }

        try {
            const saved = await bookmarks.toggle(course);
            setBookmarkNotice(
                saved
                    ? '저장한 추천 코스에 추가했어요'
                    : '저장한 추천 코스에서 삭제했어요',
            );
        } catch (error) {
            setBookmarkNotice(
                error?.message || '코스 저장 상태를 변경하지 못했어요',
            );
        }
    };

    if (catalogStatus === 'loading') {
        return (
            <div className="page course-result-page theme-course-list-page">
                <Header variant="default" />
                <main className="course-result-shell">
                    <section className="course-result-state-card" aria-live="polite">
                        <span><Sparkles size={24} aria-hidden="true" /></span>
                        <h1>인기 테마 코스를 불러오고 있어요</h1>
                        <p>장소 정보와 저장 순위를 준비하는 중입니다</p>
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
                        <h1>인기 테마 코스를 불러오지 못했어요</h1>
                        <p>{catalogError?.message || '백엔드와 DB 연결 상태를 확인해 주세요'}</p>
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
        <div className="page course-result-page theme-course-list-page popular-theme-page">
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
                                POPULAR THEME COURSE
                            </p>
                            <h1>인기 테마 추천 코스 전체보기</h1>
                            <p>여행자들이 많이 저장한 서울 테마 코스를 인기순으로 둘러보세요</p>

                            <div className="theme-course-hero-meta">
                                <span><CalendarDays size={15} aria-hidden="true" />당일치기 · 1박 2일</span>
                                <span><MapPinned size={15} aria-hidden="true" />서울 테마 코스 {courses.length}개</span>
                            </div>
                        </div>
                    </div>

                    <div className="course-result-hero-visual" aria-hidden="true">
                        <img src={sunsetImage} alt="" />
                    </div>
                </section>

                <section className="theme-all-toolbar" aria-label="테마 필터">
                    <div className="theme-all-filters" role="group" aria-label="테마 선택">
                        <button
                            className={activeTheme === 'all' ? 'is-active' : ''}
                            type="button"
                            aria-pressed={activeTheme === 'all'}
                            onClick={() => setActiveTheme('all')}
                        >
                            전체
                        </button>
                        {themes.map((theme) => (
                            <button
                                className={activeTheme === theme.slug ? 'is-active' : ''}
                                type="button"
                                key={theme.slug}
                                aria-pressed={activeTheme === theme.slug}
                                onClick={() => setActiveTheme(theme.slug)}
                            >
                                {theme.title}
                            </button>
                        ))}
                    </div>
                </section>

                <div className="theme-course-list-heading">
                    <div>
                        <span>인기순</span>
                        <h2>서울 인기 테마 코스 {visibleCourses.length}개</h2>
                    </div>
                    <p>각 테마를 대표하는 BEST 코스를 한곳에 모았어요</p>
                </div>

                {bookmarkNotice && (
                    <p className="theme-course-bookmark-notice" role="status">
                        {bookmarkNotice}
                    </p>
                )}

                {missingPlaceNames.length > 0 && (
                    <p className="theme-course-bookmark-notice" role="status">
                        DB에서 찾지 못한 장소 {missingPlaceNames.length}개는 임시 정보로 표시 중이에요
                    </p>
                )}

                <section
                    className="course-result-list theme-course-result-list"
                    aria-label="인기 테마 추천 코스 목록"
                >
                    {visibleCourses.map((course, index) => (
                        <CourseRecommendationCard
                            key={course.courseId}
                            option={course}
                            transportMode={course.transportMode}
                            fallbackImage={fallbackImages[index % fallbackImages.length]}
                            activeDayNo={activeDayByCourse[course.courseId] || course.days[0]?.dayNo || 1}
                            variant="theme"
                            detailPath={`/courses/themes/${course.themeSlug}/${course.courseId}`}
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

export default PopularThemeCoursePage;
