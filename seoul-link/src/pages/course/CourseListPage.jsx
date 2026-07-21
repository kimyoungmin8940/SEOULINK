import { useEffect, useState } from 'react';
import {
    ArrowLeft,
    ChevronLeft,
    ChevronRight,
    Info,
    RefreshCw,
    Route,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import PagePlaceholder from '../../components/common/PagePlaceholder';
import CourseCard from '../../components/course/CourseCard';
import { getRecommendedCourses } from '../../api/courseApi';
import {
    getCurrentMemberId,
    normalizeRecommendedCourseList,
    readRecommendedCourseCache,
    writeRecommendedCourseCache,
} from '../../utils/courseHistory';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';

const fallbackImages = [hanokImage, sunsetImage, rainyCafeImage, walkingImage];
// 추천받은 코스는 한 페이지에 8개씩 보여주며, 9번째 코스부터 다음 페이지를 만듭니다.
const COURSES_PER_PAGE = 8;

/** URL의 page 쿼리를 읽고 잘못된 값은 첫 페이지로 보정합니다. */
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

/** 서버 조회 전에도 이전에 저장한 코스 요약이 있으면 즉시 표시할 초기 상태를 만듭니다. */
function getInitialHistoryState() {
    const memberId = getCurrentMemberId();
    const courses = normalizeRecommendedCourseList(readRecommendedCourseCache(), {
        fallbackImages,
    });

    return {
        memberId,
        courses,
        status: memberId ? 'loading' : courses.length > 0 ? 'success' : 'empty',
    };
}

/** 회원의 SURVEY 코스를 최신순으로 조회해 8개 단위로 보여주는 전체보기 화면입니다. */
function RecommendedCourseHistoryPage() {
    const [initialState] = useState(getInitialHistoryState);
    const [courses, setCourses] = useState(initialState.courses);
    const [status, setStatus] = useState(initialState.status);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [requestedPage, setRequestedPage] = useState(getInitialPage);

    useEffect(() => {
        if (!initialState.memberId) return undefined;

        const controller = new AbortController();

        // 서버 데이터를 기준으로 삼되, 아직 서버에 없는 이미지·태그만 로컬 캐시로 보완합니다.
        getRecommendedCourses(initialState.memberId, { signal: controller.signal })
            .then((response) => {
                const normalizedCourses = normalizeRecommendedCourseList(response, {
                    cachedCourses: readRecommendedCourseCache(),
                    fallbackImages,
                });
                setCourses(normalizedCourses);
                setStatus(normalizedCourses.length > 0 ? 'success' : 'empty');
                setErrorMessage('');
                writeRecommendedCourseCache(normalizedCourses);
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;

                // 네트워크 오류여도 이미 본 코스가 있으면 빈 화면 대신 캐시 목록을 유지합니다.
                if (initialState.courses.length > 0) {
                    setCourses(initialState.courses);
                    setStatus('success');
                    return;
                }

                setErrorMessage(error?.message || '추천받은 코스 목록을 불러오지 못했습니다.');
                setStatus('error');
            });

        return () => controller.abort();
    }, [initialState, reloadKey]);

    const retry = () => {
        setStatus('loading');
        setErrorMessage('');
        setReloadKey((value) => value + 1);
    };

    const totalPages = Math.max(1, Math.ceil(courses.length / COURSES_PER_PAGE));
    const currentPage = Math.min(requestedPage, totalPages);
    const pageStartIndex = (currentPage - 1) * COURSES_PER_PAGE;
    const visibleCourses = courses.slice(
        pageStartIndex,
        pageStartIndex + COURSES_PER_PAGE,
    );

    const moveToPage = (page) => {
        // 새로고침·주소 공유에도 현재 페이지가 유지되도록 URL 쿼리도 함께 갱신합니다.
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
                    <div>
                        <p><Sparkles size={15} aria-hidden="true" /> MY RECOMMENDATIONS</p>
                        <h1>추천받은 코스 전체보기</h1>
                        <span>취향 검사 후 선택해 담은 코스를 최신순으로 모아볼 수 있어요.</span>
                    </div>
                    {status === 'success' && <strong>총 {courses.length}개</strong>}
                </section>

                {status === 'loading' && (
                    <div className="recommended-history-loading" aria-label="추천 코스 목록을 불러오는 중">
                        {[1, 2, 3, 4].map((item) => <span key={item} />)}
                    </div>
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
                        <section className="recommended-history-grid" aria-label="추천받은 코스 목록">
                            {visibleCourses.map((course) => (
                                <CourseCard
                                    key={course.courseId}
                                    course={course}
                                    detailPath={`/courses/recommendations/${course.courseId}`}
                                    requiresLogin
                                />
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

/** 현재 경로에 맞춰 추천 이력, 테마 목록, 일반 목록 화면을 분기합니다. */
function CourseListPage() {
    const pathname = window.location.pathname;

    if (pathname === '/courses/recommendations') {
        return <RecommendedCourseHistoryPage />;
    }

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
