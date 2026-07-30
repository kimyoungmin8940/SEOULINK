// RecommendSection은 메인페이지의 추천 코스 영역
// 로그인하지 않았거나, 로그인했어도 취향 검사 추천 기록이 없으면
// 테마별 코스 전체 목록에서 인기순 4개를 보여줌
import { useEffect, useMemo, useState } from 'react';
import CourseCard from '../course/CourseCard';
import { Sparkles, ChevronRight } from 'lucide-react';
import { isLoggedIn as checkIsLoggedIn, handleProtectedLinkClick } from '../../utils/authGuard';
import { getRecommendedCourses } from '../../api/courseApi';
import { getThemeCoursePopularity } from '../../api/themeCourseApi';
import { themeCourses } from '../../data/themeCourseData';
import {
    getCourseId,
    getCurrentMemberId,
    normalizeRecommendedCourseList,
} from '../../utils/courseHistory';

import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';

const fallbackImages = [hanokImage, sunsetImage, rainyCafeImage, walkingImage];
const DEFAULT_COURSE_PRIORITY = [
    'SUNSET_1401',
    'RAINY_CAFE_1501',
    'HANOK_PHOTO_1201',
    'WALKING_ALLEY_1601',
];

function formatCourseDuration(totalMinutes) {
    const minutes = Number(totalMinutes);

    if (!Number.isFinite(minutes) || minutes <= 0) return '';

    const hours = Math.floor(minutes / 60);
    const remainder = Math.round(minutes % 60);

    if (hours === 0) return `약 ${remainder}분`;
    if (remainder === 0) return `약 ${hours}시간`;
    return `약 ${hours}시간 ${remainder}분`;
}

function normalizeThemeCourseCard(course) {
    return {
        ...course,
        imageUrl: course.coverImageUrl,
        duration: formatCourseDuration(course.totalCourseTimeMinutes),
        area: course.region,
    };
}

// 로그인 기능이 완성되기 전 콘솔 테스트와 기존 프론트 저장값을 함께 지원합니다.
const LOCAL_RECOMMENDED_COURSE_KEYS = [
    'recommendedCourses',
    'recentRecommendedCourses',
    'seoulinkRecommendedCourses',
    'latestRecommendedCourses',
    'myRecommendedCourses',
];

function safelyParse(value) {
    if (!value || typeof value !== 'string') return null;

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

/**
 * 브라우저에 저장된 임시/구버전 추천 코스를 읽습니다.
 * localStorage를 우선하고, 같은 값이 sessionStorage에만 있어도 찾습니다.
 */
function readLocalRecommendedCourses() {
    for (const storage of [localStorage, sessionStorage]) {
        for (const key of LOCAL_RECOMMENDED_COURSE_KEYS) {
            const parsed = safelyParse(storage.getItem(key));
            const courses = normalizeRecommendedCourseList(parsed, {
                fallbackImages,
            }).filter(
                (course) => String(course.courseType || '').toUpperCase() === 'SURVEY',
            );

            if (courses.length > 0) {
                return courses;
            }
        }
    }

    return [];
}

function RecommendSection() {
    const isLoggedIn = checkIsLoggedIn();
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const [myRecommendedCourses, setMyRecommendedCourses] = useState(
        () => (isLoggedIn ? readLocalRecommendedCourses() : []),
    );
    const [themeSaveCounts, setThemeSaveCounts] = useState({});

    useEffect(() => {
        const controller = new AbortController();

        getThemeCoursePopularity({ signal: controller.signal })
            .then((response) => {
                const saveCounts = Object.fromEntries(
                    (Array.isArray(response) ? response : [])
                        .filter((item) => item?.sourceCourseKey)
                        .map((item) => [
                            item.sourceCourseKey,
                            Number(item.saveCount) || 0,
                        ]),
                );

                setThemeSaveCounts(saveCounts);
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') {
                    setThemeSaveCounts({});
                }
            });

        return () => controller.abort();
    }, []);

    // 브라우저 임시 추천 코스를 먼저 표시하고,
    // 백엔드에 실제 저장 코스가 있으면 서버 결과로 교체합니다.
    useEffect(() => {
        if (!isLoggedIn) {
            setMyRecommendedCourses([]);
            return undefined;
        }

        const localCourses = readLocalRecommendedCourses();

        if (localCourses.length > 0) {
            setMyRecommendedCourses(localCourses);
        }

        // 로그인 통합 전 memberId가 없거나 가짜 로그인만 한 경우에는
        // 브라우저 저장 추천 코스를 그대로 유지합니다.
        if (!memberId) return undefined;

        const controller = new AbortController();

        getRecommendedCourses(memberId, { signal: controller.signal })
            .then((response) => {
                const serverCourses = normalizeRecommendedCourseList(response, {
                    fallbackImages,
                });

                // 실제 서버 데이터가 있을 때만 브라우저 임시 데이터를 교체합니다.
                // 서버가 빈 배열을 반환하면 기존 로컬 추천 코스를 유지합니다.
                setMyRecommendedCourses(serverCourses);
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;

                // test-token처럼 실제 인증이 되지 않아 API가 실패해도
                // 콘솔에 저장한 추천 코스를 지우지 않습니다.
                if (localCourses.length === 0) {
                    setMyRecommendedCourses([]);
                }
            });

        return () => controller.abort();
    }, [isLoggedIn, memberId]);

    const hasMyRecommendedCourses = isLoggedIn && myRecommendedCourses.length > 0;
    const popularThemeCourses = useMemo(
        () => themeCourses
            .map((course) => ({
                ...normalizeThemeCourseCard(course),
                saveCount: themeSaveCounts[course.sourceCourseKey] ?? 0,
            }))
            .sort((left, right) => {
                const saveCountDifference = right.saveCount - left.saveCount;

                if (saveCountDifference !== 0) return saveCountDifference;

                const leftDefaultRank = DEFAULT_COURSE_PRIORITY.indexOf(
                    left.sourceCourseKey,
                );
                const rightDefaultRank = DEFAULT_COURSE_PRIORITY.indexOf(
                    right.sourceCourseKey,
                );
                const normalizedLeftRank = leftDefaultRank < 0
                    ? DEFAULT_COURSE_PRIORITY.length
                    : leftDefaultRank;
                const normalizedRightRank = rightDefaultRank < 0
                    ? DEFAULT_COURSE_PRIORITY.length
                    : rightDefaultRank;

                if (normalizedLeftRank !== normalizedRightRank) {
                    return normalizedLeftRank - normalizedRightRank;
                }

                return left.courseId - right.courseId;
            })
            .slice(0, 4),
        [themeSaveCounts],
    );

    // 추천받은 코스가 있으면 최신 4개, 없으면 테마별 전체 코스에서 인기순 4개
    const coursesToShow = hasMyRecommendedCourses
        ? myRecommendedCourses.slice(0, 4)
        : popularThemeCourses;

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
                        : `/courses/themes/${course.themeSlug}/${courseId}`;
                    const normalizedCourse = { ...course, courseId };

                    return (
                        <CourseCard
                            key={courseId}
                            course={normalizedCourse}
                            detailPath={detailPath}
                            requiresLogin={hasMyRecommendedCourses}
                            showBookmark={hasMyRecommendedCourses}
                        />
                    );
                })}
            </div>
        </section>
    );
}

export default RecommendSection;
