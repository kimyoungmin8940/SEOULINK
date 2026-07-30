import { useMemo, useState } from 'react';
import {
    ArrowRight,
    Grid2X2,
    List,
    Route,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import {
    themeCourseDefinitions,
    themeCourses,
} from '../../data/themeCourseData';

function ThemeCard({ theme, courseCount, viewMode }) {
    const themePath = `/courses/themes/${theme.slug}`;
    const moveToTheme = () => window.location.assign(themePath);

    return (
        <article
            className={`theme-all-card theme-all-card--${viewMode}`}
            tabIndex={0}
            role="link"
            aria-label={`${theme.title} 추천 코스 ${courseCount}개 보기`}
            onClick={moveToTheme}
            onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    moveToTheme();
                }
            }}
        >
            <div className="theme-all-card-image">
                <img src={theme.image} alt="" />
                <span>{theme.tags[0]}</span>
            </div>

            <div className="theme-all-card-content">
                <div>
                    <p>SEOUL THEME</p>
                    <h2>{theme.title}</h2>
                    <span className="theme-all-description">{theme.description}</span>
                </div>

                <div className="theme-all-card-footer">
                    <div className="theme-all-meta">
                        <span><Route size={14} aria-hidden="true" />추천 코스 {courseCount}개</span>
                        <span>당일치기 · 1박 2일</span>
                    </div>
                    <span className="theme-all-card-arrow" aria-hidden="true">
                        <ArrowRight size={18} />
                    </span>
                </div>
            </div>
        </article>
    );
}

function ThemeCourseAllPage() {
    const [viewMode, setViewMode] = useState('grid');
    const [activeKeyword, setActiveKeyword] = useState('전체');

    const themes = useMemo(() => (
        Object.values(themeCourseDefinitions).map((theme) => ({
            ...theme,
            courseCount: themeCourses.filter(
                (course) => course.themeSlug === theme.slug,
            ).length,
        }))
    ), []);

    const keywords = useMemo(() => [
        '전체',
        ...new Set(themes.map((theme) => theme.tags[0])),
    ], [themes]);

    const visibleThemes = useMemo(
        () => (activeKeyword === '전체'
            ? themes
            : themes.filter((theme) => theme.tags.includes(activeKeyword))),
        [activeKeyword, themes],
    );

    return (
        <div className="page theme-all-page">
            <Header variant="default" />

            <main className="theme-all-shell">
                <section className="theme-all-heading">
                    <p>THEME COURSE</p>
                    <h1>테마별 추천 코스 전체보기</h1>
                    <span>
                        노을, 비 오는 날의 카페, 골목 산책, 야간 데이트, 한옥길, 로컬 맛집까지
                        <br />
                        원하는 테마를 고르면 테마별 서울 코스 5개를 확인할 수 있어요
                    </span>
                </section>

                <section className="theme-all-toolbar" aria-label="테마 선택과 보기 설정">
                    <div className="theme-all-filters" role="group" aria-label="테마 키워드 필터">
                        {keywords.map((keyword) => (
                            <button
                                className={activeKeyword === keyword ? 'is-active' : ''}
                                type="button"
                                key={keyword}
                                aria-pressed={activeKeyword === keyword}
                                onClick={() => setActiveKeyword(keyword)}
                            >
                                {keyword}
                            </button>
                        ))}
                    </div>

                    <div className="theme-all-controls">
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

                <section
                    className={`theme-all-courses theme-all-courses--${viewMode}`}
                    aria-label="서울 추천 테마 목록"
                >
                    {visibleThemes.map((theme) => (
                        <ThemeCard
                            theme={theme}
                            courseCount={theme.courseCount}
                            viewMode={viewMode}
                            key={theme.slug}
                        />
                    ))}
                </section>
            </main>

            <Footer />
        </div>
    );
}

export default ThemeCourseAllPage;
