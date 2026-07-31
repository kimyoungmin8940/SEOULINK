// 메인페이지 추천 코스 영역
// 비로그인 또는 추천 이력이 없는 회원에게는 인기 테마 코스를,
// 로그인 회원에게 실제 추천 이력이 있을 때만 취향 맞춤 코스를 보여준다.
import { useEffect, useMemo, useState } from 'react';
import { ChevronRight, Sparkles } from 'lucide-react';

import CourseCard from '../course/CourseCard';
import { getRecommendedCourses } from '../../api/courseApi';
import { getThemeCoursePopularity } from '../../api/themeCourseApi';
import useThemeCourseCatalog from '../../hooks/useThemeCourseCatalog';
import {
    isLoggedIn as checkIsLoggedIn,
    handleProtectedLinkClick,
} from '../../utils/authGuard';
import {
    getCourseId,
    getCurrentMemberId,
    normalizeRecommendedCourseList,
} from '../../utils/courseHistory';

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
        // useThemeCourseCatalog가 DB 장소 사진으로 교체한 대표사진을 그대로 사용한다.
        imageUrl: course.coverImageUrl,
        duration: formatCourseDuration(course.totalCourseTimeMinutes),
        area: course.region,
    };
}

function RecommendSection() {
    const isLoggedIn = checkIsLoggedIn();
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const {
        courses: catalogCourses,
        status: catalogStatus,
    } = useThemeCourseCatalog('all');
    const [myRecommendedCourses, setMyRecommendedCourses] = useState([]);
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

    // localStorage의 과거 추천 더미는 사용하지 않는다.
    // 로그인 + 실제 서버 추천 이력이 모두 있을 때만 개인 추천으로 바꾼다.
    useEffect(() => {
        if (!isLoggedIn || !memberId) {
            setMyRecommendedCourses([]);
            return undefined;
        }

        const controller = new AbortController();
        setMyRecommendedCourses([]);

        getRecommendedCourses(memberId, { signal: controller.signal })
            .then((response) => {
                if (controller.signal.aborted) return;
                setMyRecommendedCourses(normalizeRecommendedCourseList(response));
            })
            .catch((error) => {
                if (error?.name !== 'AbortError') {
                    // 조회 실패 시에도 더미 추천으로 대체하지 않고 인기 테마를 유지한다.
                    setMyRecommendedCourses([]);
                }
            });

        return () => controller.abort();
    }, [isLoggedIn, memberId]);

    const hasMyRecommendedCourses =
        isLoggedIn && Boolean(memberId) && myRecommendedCourses.length > 0;

    const popularThemeCourses = useMemo(
        () => catalogCourses
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

                return Number(left.courseId || 0) - Number(right.courseId || 0);
            })
            .slice(0, 4),
        [catalogCourses, themeSaveCounts],
    );

    const coursesToShow = hasMyRecommendedCourses
        ? myRecommendedCourses.slice(0, 4)
        : popularThemeCourses;
    const isThemeCatalogLoading =
        !hasMyRecommendedCourses && catalogStatus === 'loading';

    const sectionTitle = hasMyRecommendedCourses
        ? '취향에 맞는 추천 코스'
        : '인기 테마 추천 코스';
    const sectionDescription = hasMyRecommendedCourses
        ? '취향 검사로 추천받은 최신 코스 4개를 확인해보세요.'
        : '지금 가장 인기 있는 테마 코스를 미리 만나보세요.';
    const moreLink = hasMyRecommendedCourses
        ? '/courses/recommendations'
        : '/courses/themes/popular';

    return (
        <section className="section recommend-section">
            <div className="course-heading">
                <div className="course-heading-left">
                    <div className="course-heading-title section-heading-title">
                        <Sparkles
                            className="course-heading-icon section-heading-icon"
                            size={21}
                            strokeWidth={2.2}
                        />
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

            <div
                className={`course-grid course-grid--home${isThemeCatalogLoading ? ' is-loading' : ''}`}
                aria-busy={isThemeCatalogLoading}
                aria-label={isThemeCatalogLoading ? '테마 코스를 불러오는 중' : undefined}
            >
                {isThemeCatalogLoading
                    ? [1, 2, 3, 4].map((item) => (
                        <div
                            className="course-card course-card--home course-card-loading"
                            key={item}
                            aria-hidden="true"
                        >
                            <span className="course-card-loading__image" />
                            <span className="course-card-loading__body">
                                <i />
                                <i />
                                <i />
                            </span>
                        </div>
                    ))
                    : coursesToShow.map((course) => {
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
                                variant="home"
                                showHeart={!hasMyRecommendedCourses}
                                maxTags={3}
                            />
                        );
                    })}
            </div>
        </section>
    );
}

export default RecommendSection;
