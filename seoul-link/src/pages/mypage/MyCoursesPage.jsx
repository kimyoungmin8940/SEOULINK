import { useEffect, useState } from 'react';
import {
    ArrowLeft,
    CalendarDays,
    ChevronLeft,
    ChevronRight,
    Clock3,
    Info,
    MapPin,
    RefreshCw,
    Route,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { getMyCourses } from '../../api/courseApi';
import {
    getCurrentMemberId,
    normalizeMyCourseList,
} from '../../utils/courseHistory';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';

const COURSES_PER_PAGE = 8;
const COURSE_DETAIL_ENTRY_KEY = 'seoulinkCourseDetailEntry';
const fallbackImages = [hanokImage, sunsetImage, rainyCafeImage, walkingImage];

function getInitialPage() {
    const page = Number(new URLSearchParams(window.location.search).get('page'));
    return Number.isInteger(page) && page > 0 ? page : 1;
}

function getSelectedCourseType() {
    const courseType = new URLSearchParams(window.location.search).get('type');
    return ['SURVEY', 'CUSTOM', 'CHATBOT'].includes(courseType)
        ? courseType
        : null;
}

function getInitialState() {
    const memberId = getCurrentMemberId();

    return {
        memberId,
        courses: [],
        status: memberId ? 'loading' : 'member-error',
    };
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours === 0) return `${restMinutes}분`;
    return restMinutes === 0 ? `${hours}시간` : `${hours}시간 ${restMinutes}분`;
}

function formatDate(value) {
    if (!value) return null;

    const date = new Date(`${value}T00:00:00`);
    if (Number.isNaN(date.getTime())) return null;

    return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).format(date);
}

function getTravelPeriod(course) {
    const startDate = formatDate(course.startDate);
    const endDate = formatDate(course.endDate);

    if (!startDate) return '여행 날짜 미정';
    if (!endDate || startDate === endDate) return startDate;
    return `${startDate} ~ ${endDate}`;
}

function getCourseTypeLabel(courseType) {
    return {
        SURVEY: '맞춤 추천',
        CUSTOM: '직접 만든 코스',
        CHATBOT: '챗봇 추천',
    }[courseType] || '저장 코스';
}

function MyCourseCard({ course }) {
    const moveToDetail = () => {
        sessionStorage.setItem(COURSE_DETAIL_ENTRY_KEY, JSON.stringify({
            detailId: course.courseId,
            returnPath: `${window.location.pathname}${window.location.search}`,
            summary: course,
        }));
        window.location.href = `/mypage/courses/${course.courseId}`;
    };

    const handleKeyDown = (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            moveToDetail();
        }
    };

    return (
        <article
            className="my-course-card"
            role="link"
            tabIndex={0}
            aria-label={`${course.title} 상세 일정 보기`}
            onClick={moveToDetail}
            onKeyDown={handleKeyDown}
        >
            <div className="my-course-card-image">
                <img
                    src={course.imageUrl}
                    alt={`${course.title} 대표 이미지`}
                    onError={(event) => {
                        if (event.currentTarget.src !== hanokImage) {
                            event.currentTarget.src = hanokImage;
                        }
                    }}
                />
                <span>{getCourseTypeLabel(course.courseType)}</span>
            </div>

            <div className="my-course-card-body">
                <div className="my-course-card-title">
                    <h2>{course.title}</h2>
                    <p>{course.description}</p>
                </div>

                <dl className="my-course-card-meta">
                    <div>
                        <dt><MapPin size={15} aria-hidden="true" /> 지역</dt>
                        <dd>{course.area || '서울'}</dd>
                    </div>
                    <div>
                        <dt><CalendarDays size={15} aria-hidden="true" /> 여행 기간</dt>
                        <dd>{getTravelPeriod(course)}</dd>
                    </div>
                    <div>
                        <dt><Route size={15} aria-hidden="true" /> 총 거리</dt>
                        <dd>{Number(course.totalDistanceKm || 0).toFixed(1)}km</dd>
                    </div>
                    <div>
                        <dt><Clock3 size={15} aria-hidden="true" /> 전체 시간</dt>
                        <dd>{formatMinutes(course.totalCourseTimeMinutes)}</dd>
                    </div>
                </dl>

                <div className="my-course-card-footer">
                    <span>{course.dayCount || 0}일 · {course.placeCount || 0}곳</span>
                    <strong>상세 일정 보기 <ChevronRight size={16} aria-hidden="true" /></strong>
                </div>
            </div>
        </article>
    );
}

/** 로그인 회원이 저장한 전체 코스를 최신순으로 조회하는 마이페이지 화면입니다. */
function MyCoursesPage() {
    const [initialState] = useState(getInitialState);
    const [courses, setCourses] = useState(initialState.courses);
    const [status, setStatus] = useState(initialState.status);
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [requestedPage, setRequestedPage] = useState(getInitialPage);
    const selectedCourseType = getSelectedCourseType();

    useEffect(() => {
        if (!initialState.memberId) return undefined;

        const controller = new AbortController();

        getMyCourses(initialState.memberId, { signal: controller.signal })
            .then((response) => {
                const normalizedCourses = normalizeMyCourseList(response, {
                    fallbackImages,
                });

                setCourses(normalizedCourses);
                setStatus(normalizedCourses.length > 0 ? 'success' : 'empty');
                setErrorMessage('');
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;

                setErrorMessage(error?.message || '내 코스 목록을 불러오지 못했습니다.');
                setStatus('error');
            });

        return () => controller.abort();
    }, [initialState, reloadKey]);

    const filteredCourses = selectedCourseType
        ? courses.filter((course) => course.courseType === selectedCourseType)
        : courses;
    const hasCourses = filteredCourses.length > 0;
    const totalPages = Math.max(1, Math.ceil(filteredCourses.length / COURSES_PER_PAGE));
    const currentPage = Math.min(requestedPage, totalPages);
    const pageStartIndex = (currentPage - 1) * COURSES_PER_PAGE;
    const visibleCourses = filteredCourses.slice(
        pageStartIndex,
        pageStartIndex + COURSES_PER_PAGE,
    );

    const retry = () => {
        setStatus('loading');
        setErrorMessage('');
        setReloadKey((value) => value + 1);
    };

    const moveToPage = (page) => {
        const nextPage = Math.min(Math.max(page, 1), totalPages);
        setRequestedPage(nextPage);

        const nextUrl = new URL(window.location.href);
        if (nextPage === 1) nextUrl.searchParams.delete('page');
        else nextUrl.searchParams.set('page', String(nextPage));
        window.history.replaceState(null, '', nextUrl);
        document.querySelector('.my-courses-heading')?.scrollIntoView({
            behavior: 'smooth',
            block: 'start',
        });
    };

    return (
        <div className="page my-courses-page">
            <Header variant="default" />

            <main className="my-courses-shell">
                <a className="my-courses-back" href="/mypage">
                    <ArrowLeft size={18} aria-hidden="true" /> 마이페이지로 돌아가기
                </a>

                <section className="my-courses-heading">
                    <div>
                        <p><Sparkles size={15} aria-hidden="true" /> MY COURSES</p>
                        <h1>내 코스</h1>
                        <span>추천받아 저장한 코스와 직접 만든 코스를 한곳에서 확인해요.</span>
                    </div>
                    {status === 'success' && <strong>총 {filteredCourses.length}개</strong>}
                </section>

                {status === 'loading' && (
                    <div className="my-courses-loading" aria-label="내 코스 목록을 불러오는 중">
                        {[1, 2, 3, 4].map((item) => <span key={item} />)}
                    </div>
                )}

                {status === 'member-error' && (
                    <section className="my-courses-state" role="alert">
                        <Info size={30} aria-hidden="true" />
                        <h2>회원 정보를 확인할 수 없어요</h2>
                        <p>다시 로그인한 뒤 내 코스를 열어주세요.</p>
                        <a href="/login">로그인하기</a>
                    </section>
                )}

                {status === 'error' && (
                    <section className="my-courses-state" role="alert">
                        <Info size={30} aria-hidden="true" />
                        <h2>내 코스를 불러오지 못했어요</h2>
                        <p>{errorMessage}</p>
                        <button type="button" onClick={retry}>
                            <RefreshCw size={16} aria-hidden="true" /> 다시 불러오기
                        </button>
                    </section>
                )}

                {(status === 'empty' || (status === 'success' && !hasCourses)) && (
                    <section className="my-courses-state">
                        <Route size={31} aria-hidden="true" />
                        <h2>아직 저장한 코스가 없어요</h2>
                        <p>취향에 맞는 코스를 추천받거나 나만의 코스를 직접 만들어보세요.</p>
                        <div>
                            <a href="/travel-info">코스 추천받기</a>
                            <a className="secondary" href="/map-course">직접 만들기</a>
                        </div>
                    </section>
                )}

                {status === 'success' && hasCourses && (
                    <>
                        <section className="my-courses-grid" aria-label="저장한 내 코스 목록">
                            {visibleCourses.map((course) => (
                                <MyCourseCard course={course} key={course.courseId} />
                            ))}
                        </section>

                        {totalPages > 1 && (
                            <nav className="my-courses-pagination" aria-label="내 코스 페이지 이동">
                                <button
                                    type="button"
                                    aria-label="이전 페이지"
                                    disabled={currentPage === 1}
                                    onClick={() => moveToPage(currentPage - 1)}
                                >
                                    <ChevronLeft size={18} aria-hidden="true" />
                                </button>

                                <div>
                                    {Array.from({ length: totalPages }, (_, index) => index + 1)
                                        .map((page) => (
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

export default MyCoursesPage;
