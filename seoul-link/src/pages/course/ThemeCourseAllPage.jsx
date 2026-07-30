import { useMemo, useState } from 'react';
import {
    Bookmark,
    Clock3,
    Grid2X2,
    List,
    MapPinned,
    Route,
    Sparkles,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import useThemeCourseCatalog from '../../hooks/useThemeCourseCatalog';
import useThemeCourseBookmarks from '../../hooks/useThemeCourseBookmarks';
import { themeCourseDefinitions } from '../../data/themeCourseData';
import { requireLogin } from '../../utils/authGuard';
import { getCurrentMemberId } from '../../utils/courseHistory';

const themeFilters = [
    { slug: 'all', label: '전체' },
    ...Object.values(themeCourseDefinitions).map((theme) => ({
        slug: theme.slug,
        label: theme.title,
    })),
];

function formatDuration(minutes) {
    const roundedMinutes = Math.max(0, Math.round(Number(minutes) || 0));
    const hours = Math.floor(roundedMinutes / 60);
    const rest = roundedMinutes % 60;

    if (!hours) return `${rest}분`;
    return rest ? `${hours}시간 ${rest}분` : `${hours}시간`;
}

function ThemeCourseCard({
    course,
    viewMode,
    saved,
    busy,
    onBookmark,
}) {
    const theme = themeCourseDefinitions[course.themeSlug];
    const detailPath = `/courses/themes/${course.themeSlug}/${course.courseId}`;

    const moveToDetail = () => window.location.assign(detailPath);

    return (
        <article
            className={`theme-all-card theme-all-card--${viewMode}`}
            tabIndex={0}
            role="link"
            aria-label={`${course.title} 상세보기`}
            onClick={moveToDetail}
            onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    moveToDetail();
                }
            }}
        >
            <div className="theme-all-card-image">
                <img src={course.coverImageUrl || theme?.image} alt="" />
                <span>{theme?.title || '테마 코스'}</span>
                <button
                    className={`theme-all-bookmark${saved ? ' is-saved' : ''}`}
                    type="button"
                    disabled={busy}
                    aria-label={saved ? '북마크 해제' : '북마크 저장'}
                    aria-pressed={saved}
                    onClick={(event) => {
                        event.stopPropagation();
                        onBookmark(course);
                    }}
                >
                    <Bookmark size={22} aria-hidden="true" />
                </button>
            </div>

            <div className="theme-all-card-content">
                <div>
                    <p>{course.region}</p>
                    <h2>{course.title}</h2>
                    <span className="theme-all-description">{course.description}</span>
                </div>

                <div className="theme-all-meta">
                    <span><MapPinned size={14} aria-hidden="true" />{Number(course.totalDistanceKm || 0).toFixed(1)}km</span>
                    <span><Clock3 size={14} aria-hidden="true" />{formatDuration(course.totalCourseTimeMinutes)}</span>
                    <span><Route size={14} aria-hidden="true" />{course.placeCount}곳</span>
                </div>
            </div>
        </article>
    );
}

function ThemeCourseAllPage() {
    const { courses, status, error, missingPlaceNames } = useThemeCourseCatalog('all');
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const bookmarks = useThemeCourseBookmarks(memberId, { courses });
    const [activeTheme, setActiveTheme] = useState('all');
    const [sortType, setSortType] = useState('recommended');
    const [viewMode, setViewMode] = useState('grid');
    const [notice, setNotice] = useState('');

    const visibleCourses = useMemo(() => {
        const filtered = activeTheme === 'all'
            ? courses
            : courses.filter((course) => course.themeSlug === activeTheme);

        return [...filtered].sort((a, b) => {
            if (sortType === 'distance') {
                return Number(a.totalDistanceKm || 0) - Number(b.totalDistanceKm || 0);
            }
            if (sortType === 'duration') {
                return Number(a.totalCourseTimeMinutes || 0) - Number(b.totalCourseTimeMinutes || 0);
            }
            return Number(Boolean(b.badge)) - Number(Boolean(a.badge))
                || Number(a.optionNo || 0) - Number(b.optionNo || 0);
        });
    }, [activeTheme, courses, sortType]);

    const toggleBookmark = async (course) => {
        if (!requireLogin('코스를 저장하려면 로그인이 필요해요.')) return;

        if (!memberId) {
            setNotice('회원 정보를 확인할 수 없어요. 다시 로그인해 주세요.');
            return;
        }

        try {
            const saved = await bookmarks.toggle(course);
            setNotice(saved ? '저장한 추천 코스에 추가했어요.' : '저장한 추천 코스에서 삭제했어요.');
        } catch (bookmarkError) {
            setNotice(bookmarkError?.message || '코스 저장 상태를 변경하지 못했어요.');
        }
    };

    return (
        <div className="page theme-all-page">
            <Header variant="default" />

            <main className="theme-all-shell">
                <section className="theme-all-heading">
                    <p>THEME COURSE</p>
                    <h1>테마별 추천 코스 전체보기</h1>
                    <span>
                        데이트하기 좋은 밤, 사진 찍기 좋은 한옥길, 로컬처럼 먹는 하루까지
                        <br />
                        서울의 분위기를 담은 테마 코스를 모아 보여드려요.
                    </span>
                </section>

                <section className="theme-all-toolbar" aria-label="테마 코스 필터와 보기 설정">
                    <div className="theme-all-filters" role="group" aria-label="테마 선택">
                        {themeFilters.map((filter) => (
                            <button
                                className={activeTheme === filter.slug ? 'is-active' : ''}
                                type="button"
                                key={filter.slug}
                                onClick={() => setActiveTheme(filter.slug)}
                            >
                                {filter.label}
                            </button>
                        ))}
                    </div>

                    <div className="theme-all-controls">
                        <select
                            value={sortType}
                            aria-label="코스 정렬"
                            onChange={(event) => setSortType(event.target.value)}
                        >
                            <option value="recommended">추천순</option>
                            <option value="distance">거리 짧은 순</option>
                            <option value="duration">소요시간 짧은 순</option>
                        </select>

                        <div className="theme-all-view-toggle" role="group" aria-label="보기 방식">
                            <button
                                className={viewMode === 'grid' ? 'is-active' : ''}
                                type="button"
                                aria-label="격자로 보기"
                                aria-pressed={viewMode === 'grid'}
                                onClick={() => setViewMode('grid')}
                            >
                                <Grid2X2 size={19} aria-hidden="true" />
                            </button>
                            <button
                                className={viewMode === 'list' ? 'is-active' : ''}
                                type="button"
                                aria-label="목록으로 보기"
                                aria-pressed={viewMode === 'list'}
                                onClick={() => setViewMode('list')}
                            >
                                <List size={21} aria-hidden="true" />
                            </button>
                        </div>
                    </div>
                </section>

                <div className="theme-all-result-heading">
                    <h2>추천 코스</h2>
                    <span>{visibleCourses.length}개 코스</span>
                </div>

                {(notice || missingPlaceNames.length > 0) && (
                    <p className="theme-all-notice" role="status">
                        {notice || `DB에서 찾지 못한 장소 ${missingPlaceNames.length}개는 임시 정보로 표시 중이에요.`}
                    </p>
                )}

                {status === 'loading' && (
                    <section className="theme-all-state" aria-live="polite">
                        <Sparkles size={25} aria-hidden="true" />
                        <h2>DB에서 장소와 사진을 불러오고 있어요</h2>
                    </section>
                )}

                {status === 'error' && (
                    <section className="theme-all-state" role="alert">
                        <h2>장소 데이터를 불러오지 못했어요</h2>
                        <p>{error?.message || '백엔드와 DB 연결 상태를 확인해 주세요.'}</p>
                        <button type="button" onClick={() => window.location.reload()}>다시 불러오기</button>
                    </section>
                )}

                {status === 'success' && (
                    <section
                        className={`theme-all-courses theme-all-courses--${viewMode}`}
                        aria-label="테마별 추천 코스 목록"
                    >
                        {visibleCourses.map((course) => (
                            <ThemeCourseCard
                                course={course}
                                viewMode={viewMode}
                                saved={bookmarks.isSaved(course.sourceCourseKey)}
                                busy={bookmarks.isBusy(course.sourceCourseKey)}
                                onBookmark={toggleBookmark}
                                key={course.sourceCourseKey}
                            />
                        ))}
                    </section>
                )}
            </main>

            <Footer />
        </div>
    );
}

export default ThemeCourseAllPage;
