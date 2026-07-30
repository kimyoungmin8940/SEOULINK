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

const themeCourses = mockThemeCourseListResponse.data;

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
            const courses = normalizeRecommendedCourseList(parsed);

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
    const shouldLoadServerRecommendations = isLoggedIn && Boolean(memberId);
    const [myRecommendedCourses, setMyRecommendedCourses] = useState(
        () => (
            isLoggedIn && !shouldLoadServerRecommendations
                ? readLocalRecommendedCourses()
                : []
        ),
    );
    const [isLoadingRecommendations, setIsLoadingRecommendations] = useState(
        shouldLoadServerRecommendations,
    );

    // 실제 회원은 서버 응답이 끝나기 전까지 임시·테마 카드를 렌더링하지 않습니다.
    // 콘솔 test-token처럼 memberId가 없는 임시 로그인에서만 브라우저 값을 사용합니다.
    useEffect(() => {
        if (!isLoggedIn) {
            setMyRecommendedCourses([]);
            setIsLoadingRecommendations(false);
            return undefined;
        }

        const localCourses = readLocalRecommendedCourses();

        if (!memberId) {
            setMyRecommendedCourses(localCourses);
            setIsLoadingRecommendations(false);
            return undefined;
        }

        const controller = new AbortController();
        setMyRecommendedCourses([]);
        setIsLoadingRecommendations(true);

        const loadRecommendedCourses = async () => {
            try {
                const response = await getRecommendedCourses(memberId, {
                    signal: controller.signal,
                });
                const serverCourses = normalizeRecommendedCourseList(response);

                // 실제 회원의 목록은 빈 배열까지 포함해 서버 값을 최종 기준으로 사용합니다.
                setMyRecommendedCourses(serverCourses);
            } catch (error) {
                if (error?.name === 'AbortError') return;

                // 서버 자체가 실패한 경우에만 현재 탭의 실제 추천 응답을 임시 복구합니다.
                setMyRecommendedCourses(localCourses);
            } finally {
                if (!controller.signal.aborted) {
                    setIsLoadingRecommendations(false);
                }
            }
        };

        loadRecommendedCourses();

        return () => controller.abort();
    }, [isLoggedIn, memberId]);

    const hasMyRecommendedCourses = isLoggedIn && myRecommendedCourses.length > 0;
    const showPersonalizedHeading =
        isLoadingRecommendations || hasMyRecommendedCourses;

    // 추천받은 코스가 있으면 최신 4개, 없으면 테마별 전체 코스에서 인기순 4개
    const coursesToShow = isLoadingRecommendations
        ? []
        : hasMyRecommendedCourses
            ? myRecommendedCourses.slice(0, 4)
            : [...themeCourses]
                .sort((a, b) => b.likeCount - a.likeCount)
                .slice(0, 4);

    const sectionTitle = showPersonalizedHeading
        ? '취향에 맞는 추천 코스'
        : '인기 테마 추천 코스';
    const sectionDescription = isLoadingRecommendations
        ? '추천받은 코스를 불러오고 있어요.'
        : hasMyRecommendedCourses
            ? '취향 검사로 추천받은 최신 코스 4개를 확인해보세요.'
            : '아직 추천받은 코스가 없어 테마별 코스 중 인기순 4개를 보여드려요.';
    // 추천 이력이 있으면 지금까지 추천받은 코스 전체 목록으로 이동합니다.
    const moreLink = showPersonalizedHeading
        ? '/courses/recommendations'
        : '/courses/themes';

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
                        if (showPersonalizedHeading) {
                            handleProtectedLinkClick(event);
                        }
                    }}
                >
                    전체 보기
                    <ChevronRight size={16} strokeWidth={2.2} />
                </a>
            </div>

            {/* 상황에 따라 개인 추천 코스 또는 인기 테마 코스 4개를 보여줌 */}
            <div
                className={`course-grid${isLoadingRecommendations ? ' is-loading' : ''}`}
                aria-busy={isLoadingRecommendations}
                aria-label={isLoadingRecommendations ? '추천 코스를 불러오는 중' : undefined}
            >
                {isLoadingRecommendations
                    ? [1, 2, 3, 4].map((item) => (
                        <div
                            className="course-card course-card-loading"
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
