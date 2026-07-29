import { useEffect, useMemo, useState } from 'react';
import {
    ArrowLeft,
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Clock3,
    Info,
    ListFilter,
    MapPin,
    RefreshCw,
    Route,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import PagePlaceholder from '../../components/common/PagePlaceholder';
import { getRecommendedCourses } from '../../api/courseApi';
import {
    getCurrentMemberId,
    normalizeRecommendedCourseList,
} from '../../utils/courseHistory';
import { requireLogin } from '../../utils/authGuard';
import {
    isTemporaryLogin,
    recommendedCourseHistoryPreview,
} from '../../mocks/recommendedCourseHistory';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';

const fallbackImages = [hanokImage, sunsetImage, rainyCafeImage, walkingImage];
const COURSES_PER_PAGE = 8;
const COURSE_DETAIL_ENTRY_KEY = 'seoulinkCourseDetailEntry';
const SORT_OPTIONS = [
    { value: 'latest', label: '최신순' },
    { value: 'oldest', label: '오래된순' },
    { value: 'duration-asc', label: '짧은 일정순' },
    { value: 'distance-asc', label: '짧은 동선순' },
    { value: 'places-desc', label: '장소 많은순' },
];

function getInitialPage() {
    const page = Number(new URLSearchParams(window.location.search).get('page'));
    return Number.isInteger(page) && page > 0 ? page : 1;
}

const themeInfo = {
    sunset: {
        title: '노을이 예쁜 서울 테마 코스',
        description:
            '노을 명소와 산책하기 좋은 장소를 중심으로 미리 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'rainy-cafe': {
        title: '비 오는 날의 카페 테마 코스',
        description:
            '비 오는 날 가기 좋은 실내 공간, 감성 카페, 조용한 동선을 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'walking-alley': {
        title: '혼자 걷기 좋은 골목 테마 코스',
        description:
            '혼자 천천히 걷기 좋은 골목, 산책길, 감성 장소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'night-date': {
        title: '데이트하기 좋은 밤 테마 코스',
        description:
            '야경, 분위기 좋은 식당, 밤 산책 장소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'hanok-photo': {
        title: '사진 찍기 좋은 한옥길 테마 코스',
        description:
            '한옥 거리, 전통 분위기, 사진 명소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'local-food': {
        title: '로컬처럼 먹는 하루 테마 코스',
        description:
            '서울의 로컬 맛집, 시장, 동네 식당을 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
};

function getInitialHistoryState() {
    const memberId = getCurrentMemberId();

    return {
        memberId,
        courses: [],
        status: memberId || isTemporaryLogin() ? 'loading' : 'member-error',
    };
}

function safelyParse(value) {
    if (!value || typeof value !== 'string') return null;

    try {
        return JSON.parse(value);
    } catch {
        return null;
    }
}

function readStoredRecommendedCourses() {
    const keys = [
        'recommendedCourses',
        'recentRecommendedCourses',
        'seoulinkRecommendedCourses',
        'latestRecommendedCourses',
        'myRecommendedCourses',
    ];

    for (const key of keys) {
        const parsed = safelyParse(localStorage.getItem(key)) || safelyParse(sessionStorage.getItem(key));
        if (!Array.isArray(parsed) || parsed.length === 0) continue;

        const normalized = normalizeRecommendedCourseList(parsed, { fallbackImages }).map((course) => ({
            ...course,
            previewOnly: true,
        }));

        if (normalized.length > 0) return normalized;
    }

    return [];
}

function formatDateText(value) {
    if (!value) return '날짜 미정';

    const date = new Date(`${value}T00:00:00`);
    if (Number.isNaN(date.getTime())) return '날짜 미정';

    return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).format(date);
}

function normalizeTransportLabel(value) {
    const normalized = String(value || '').trim().toUpperCase();

    if (normalized === 'WALKING') return '도보 이동';
    if (normalized === 'CAR') return '자동차 이동';
    if (normalized === 'PUBLIC_TRANSIT') return '대중교통 이동';
    return '이동수단 미정';
}

function sortCourses(list, sortOption) {
    const copied = [...list];
    const getDateValue = (course) => {
        const raw = course?.createdAt
            || course?.updatedAt
            || course?.startDate
            || course?.travelStartDate
            || course?.endDate
            || null;
        if (!raw) return 0;
        const value = new Date(raw).getTime();
        return Number.isFinite(value) ? value : 0;
    };

    if (sortOption === 'oldest') {
        copied.sort((a, b) => getDateValue(a) - getDateValue(b));
        return copied;
    }

    if (sortOption === 'duration-asc') {
        copied.sort(
            (a, b) => (Number(a?.totalCourseTimeMinutes) || Number.MAX_SAFE_INTEGER)
                - (Number(b?.totalCourseTimeMinutes) || Number.MAX_SAFE_INTEGER),
        );
        return copied;
    }

    if (sortOption === 'distance-asc') {
        copied.sort(
            (a, b) => (Number(a?.totalDistanceKm) || Number.MAX_SAFE_INTEGER)
                - (Number(b?.totalDistanceKm) || Number.MAX_SAFE_INTEGER),
        );
        return copied;
    }

    if (sortOption === 'places-desc') {
        copied.sort(
            (a, b) => (Number(b?.placeCount) || 0) - (Number(a?.placeCount) || 0),
        );
        return copied;
    }

    copied.sort((a, b) => getDateValue(b) - getDateValue(a));
    return copied;
}

function getCourseId(course) {
    const courseId = Number(
        course?.courseId
        ?? course?.savedCourseId
        ?? course?.recommendationId
        ?? course?.id,
    );

    return Number.isInteger(courseId) && courseId > 0 ? courseId : null;
}

function RecommendedHistoryListItem({ course }) {
    const moveToRecommendedCourseDetail = () => {
        if (!requireLogin()) return;

        const courseId = getCourseId(course);
        if (!courseId) {
            window.alert('코스 정보를 확인할 수 없습니다. 목록을 새로고침해 주세요.');
            return;
        }

        sessionStorage.setItem(COURSE_DETAIL_ENTRY_KEY, JSON.stringify({
            detailId: courseId,
            returnPath: `${window.location.pathname}${window.location.search}`,
            summary: {
                ...course,
                coverImageUrl: course.coverImageUrl || course.imageUrl || null,
            },
        }));

        window.location.href = `/courses/recommendations/${courseId}`;
    };

    const handleKeyDown = (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            moveToRecommendedCourseDetail();
        }
    };

    return (
        <article
            className="recommended-history-item"
            role="link"
            tabIndex={0}
            onClick={moveToRecommendedCourseDetail}
            onKeyDown={handleKeyDown}
            aria-label={`${course.title} 추천 코스 상세보기`}
        >
            <div className="recommended-history-item__image-wrap">
                {course.imageUrl && (
                    <img
                        className="recommended-history-item__image"
                        src={course.imageUrl}
                        alt={course.title}
                    />
                )}
            </div>

            <div className="recommended-history-item__body">
                <span className="recommended-history-item__badge">
                    {course.optionName || '추천 코스'}
                </span>

                <div className="recommended-history-item__title-group">
                    <h3>{course.title}</h3>
                    <p className="recommended-history-item__description">{course.description}</p>
                </div>

                <div className="recommended-history-item__meta">
                    <span>
                        <CalendarDays size={16} aria-hidden="true" />
                        {formatDateText(course.startDate || course.endDate)}
                    </span>
                    <span>
                        <Clock3 size={16} aria-hidden="true" />
                        {course.duration || '일정 정보 없음'}
                    </span>
                    <span>
                        <MapPin size={16} aria-hidden="true" />
                        {course.area || '서울'}
                    </span>
                </div>
            </div>

            <aside className="recommended-history-item__summary-panel">
                <div className="recommended-history-item__summary-stat">
                    <CalendarDays size={22} aria-hidden="true" />
                    <div>
                        <small>일정</small>
                        <strong>{course.dayCount || 1}일</strong>
                    </div>
                </div>

                <div className="recommended-history-item__summary-stat">
                    <MapPin size={22} aria-hidden="true" />
                    <div>
                        <small>장소</small>
                        <strong>{course.placeCount || 0}곳</strong>
                    </div>
                </div>

                <div className="recommended-history-item__summary-stat">
                    <Route size={22} aria-hidden="true" />
                    <div>
                        <small>이동수단</small>
                        <strong>{normalizeTransportLabel(course.transportMode).replace(' 이동', '')}</strong>
                    </div>
                </div>

                <span className="recommended-history-item__arrow" aria-hidden="true">
                    <ChevronRight size={20} strokeWidth={2.2} />
                </span>
            </aside>
        </article>
    );
}

function RecommendedCourseHistoryPage() {
    const [initialState] = useState(getInitialHistoryState);
    const [courses, setCourses] = useState(initialState.courses);
    const [status, setStatus] = useState(initialState.status);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [requestedPage, setRequestedPage] = useState(getInitialPage);
    const [sortOption, setSortOption] = useState('latest');

    useEffect(() => {
        const localPreviewCourses = readStoredRecommendedCourses();
        const fallbackPreviewCourses = localPreviewCourses.length > 0
            ? localPreviewCourses
            : recommendedCourseHistoryPreview;

        if (!initialState.memberId) {
            if (isTemporaryLogin()) {
                setCourses(fallbackPreviewCourses);
                setStatus(fallbackPreviewCourses.length > 0 ? 'success' : 'empty');
            }
            return undefined;
        }

        const controller = new AbortController();

        getRecommendedCourses(initialState.memberId, { signal: controller.signal })
            .then((response) => {
                const normalizedCourses = normalizeRecommendedCourseList(response, { fallbackImages });

                if (normalizedCourses.length > 0) {
                    setCourses(normalizedCourses);
                    setStatus('success');
                    setErrorMessage('');
                    return;
                }

                if (fallbackPreviewCourses.length > 0 && isTemporaryLogin()) {
                    setCourses(fallbackPreviewCourses);
                    setStatus('success');
                    setErrorMessage('');
                    return;
                }

                setCourses([]);
                setStatus('empty');
                setErrorMessage('');
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;

                if (fallbackPreviewCourses.length > 0 && isTemporaryLogin()) {
                    setCourses(fallbackPreviewCourses);
                    setStatus('success');
                    setErrorMessage('');
                    return;
                }

                setErrorMessage(error?.message || '추천받은 코스 목록을 불러오지 못했습니다.');
                setStatus('error');
            });

        return () => controller.abort();
    }, [initialState, reloadKey]);

    const sortedCourses = useMemo(() => sortCourses(courses, sortOption), [courses, sortOption]);

    const totalPages = Math.max(1, Math.ceil(sortedCourses.length / COURSES_PER_PAGE));
    const currentPage = Math.min(requestedPage, totalPages);
    const pageStartIndex = (currentPage - 1) * COURSES_PER_PAGE;
    const visibleCourses = sortedCourses.slice(pageStartIndex, pageStartIndex + COURSES_PER_PAGE);

    useEffect(() => {
        if (requestedPage > totalPages) {
            setRequestedPage(totalPages);
        }
    }, [requestedPage, totalPages]);

    const retry = () => {
        setStatus('loading');
        setErrorMessage('');
        setReloadKey((value) => value + 1);
    };

    const moveToPage = (page) => {
        const nextPage = Math.min(Math.max(page, 1), totalPages);
        setRequestedPage(nextPage);

        const nextUrl = new URL(window.location.href);
        if (nextPage === 1) {
            nextUrl.searchParams.delete('page');
        } else {
            nextUrl.searchParams.set('page', String(nextPage));
        }
        window.history.replaceState(null, '', nextUrl);
        document.querySelector('.recommended-history-heading')?.scrollIntoView({
            behavior: 'smooth',
            block: 'start',
        });
    };

    return (
        <div className="page recommended-history-page">
            <Header variant="default" />

            <main className="recommended-history-shell">
                <a className="recommended-history-back" href="/">
                    <ArrowLeft size={18} aria-hidden="true" /> 돌아가기
                </a>

                <section className="recommended-history-heading">
                    <div className="recommended-history-heading__copy">
                        <h1>추천받은 코스 전체보기</h1>
                        <span>지금까지 추천받은 모든 코스를 한눈에 확인하세요.</span>
                    </div>

                    {status === 'success' && (
                        <div className="recommended-history-heading__actions">
                            <strong>총 {sortedCourses.length}개</strong>
                            <div className="recommended-history-heading__sort">
                                <label htmlFor="recommended-history-sort">
                                    <ListFilter size={16} aria-hidden="true" /> 정렬
                                </label>
                                <select
                                    id="recommended-history-sort"
                                    value={sortOption}
                                    onChange={(event) => {
                                        setSortOption(event.target.value);
                                        moveToPage(1);
                                    }}
                                >
                                    {SORT_OPTIONS.map((option) => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>
                    )}
                </section>

                {status === 'loading' && (
                    <div className="recommended-history-loading" aria-label="추천 코스 목록을 불러오는 중">
                        {[1, 2, 3, 4].map((item) => <span key={item} />)}
                    </div>
                )}

                {status === 'member-error' && (
                    <section className="recommended-history-state" role="alert">
                        <Info size={28} aria-hidden="true" />
                        <h2>회원 정보를 확인할 수 없어요</h2>
                        <p>다시 로그인한 뒤 추천받은 코스를 확인해주세요.</p>
                        <a href="/login">로그인하기</a>
                    </section>
                )}

                {status === 'error' && (
                    <section className="recommended-history-state" role="alert">
                        <Info size={28} aria-hidden="true" />
                        <h2>추천 코스를 불러오지 못했어요</h2>
                        <p>{errorMessage}</p>
                        <button type="button" onClick={retry}>
                            <RefreshCw size={16} aria-hidden="true" /> 다시 불러오기
                        </button>
                    </section>
                )}

                {status === 'empty' && (
                    <section className="recommended-history-state">
                        <Sparkles size={30} aria-hidden="true" />
                        <h2>아직 추천받은 코스가 없어요</h2>
                        <p>취향 검사 후 마음에 드는 코스를 선택하면 이곳에 차곡차곡 모입니다.</p>
                        <a href="/travel-info"><Route size={16} aria-hidden="true" /> 첫 코스 추천받기</a>
                    </section>
                )}

                {status === 'success' && (
                    <>
                        <section className="recommended-history-list" aria-label="추천받은 코스 목록">
                            {visibleCourses.map((course) => (
                                <RecommendedHistoryListItem key={course.courseId} course={course} />
                            ))}
                        </section>

                        {totalPages > 1 && (
                            <nav className="recommended-history-pagination" aria-label="추천 코스 페이지 이동">
                                <button
                                    type="button"
                                    aria-label="이전 페이지"
                                    disabled={currentPage === 1}
                                    onClick={() => moveToPage(currentPage - 1)}
                                >
                                    <ChevronLeft size={18} aria-hidden="true" />
                                </button>

                                <div>
                                    {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
                                        <button
                                            className={page === currentPage ? 'is-current' : ''}
                                            type="button"
                                            key={page}
                                            aria-label={`${page}페이지`}
                                            aria-current={page === currentPage ? 'page' : undefined}
                                            onClick={() => moveToPage(page)}
                                        >
                                            {page}
                                        </button>
                                    ))}
                                </div>

                                <button
                                    type="button"
                                    aria-label="다음 페이지"
                                    disabled={currentPage === totalPages}
                                    onClick={() => moveToPage(currentPage + 1)}
                                >
                                    <ChevronRight size={18} aria-hidden="true" />
                                </button>
                            </nav>
                        )}
                    </>
                )}
            </main>

            <Footer />
        </div>
    );
}

function CourseListPage() {
    const pathname = window.location.pathname;

    if (pathname === '/courses/recommendations') return <RecommendedCourseHistoryPage />;

    if (pathname === '/courses/themes') {
        return (
            <PagePlaceholder
                title="테마별 추천 코스 전체보기"
                description="노을, 비 오는 날의 카페, 골목 산책, 야간 데이트, 한옥길, 로컬 맛집처럼 미리 만들어진 모든 테마 코스를 모아 보여줄 자리입니다."
                links={[{ href: '/', label: '메인으로 돌아가기' }]}
            />
        );
    }

    const themeCode = pathname.split('/').pop();
    const currentTheme = themeInfo[themeCode];

    if (currentTheme) {
        return (
            <PagePlaceholder
                title={currentTheme.title}
                description={currentTheme.description}
                links={[
                    { href: '/courses/themes', label: '테마 전체보기' },
                    { href: '/', label: '메인으로 돌아가기' },
                ]}
            />
        );
    }

    return (
        <PagePlaceholder
            title="전체 코스 목록"
            description="추천 코스, 인기 코스, 저장 가능한 코스 목록을 보여줄 자리입니다."
            links={[
                { href: '/courses/recommendations', label: '추천받은 코스 보기' },
                { href: '/courses/themes', label: '테마 코스 보기' },
            ]}
        />
    );
}

export default CourseListPage;
