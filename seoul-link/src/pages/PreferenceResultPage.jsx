import {
    Activity,
    ArrowRight,
    CalendarDays,
    Download,
    Heart,
    Landmark,
    MapPin,
    RefreshCw,
    ShieldCheck,
    Sparkles,
    Star,
    Wallet,
} from 'lucide-react';

import { useRef } from 'react';
import html2canvas from 'html2canvas';

import RecommendationLoadingOverlay from '../components/common/RecommendationLoadingOverlay';

import {
    getCodeTraits,
    getTravelGuideItems,
    getTravelTags,
} from '../data/travelPreferenceData';

const traitIconMap = {
    walk: Activity,
    landmark: Landmark,
    wallet: Wallet,
    shield: ShieldCheck,
    calendar: CalendarDays,
    leaf: Sparkles,
    spark: Sparkles,
    star: Star,
    heart: Heart,
    clock: CalendarDays,
};

function PreferenceResultPage({
    result,
    onRecommend,
    onRestart,
    isRecommending = false,
    recommendError = '',
}) {
    const resultPageRef = useRef(null);
    const traits = getCodeTraits(result.travelCode);
    const regions = Array.isArray(result.preferredRegions)
        ? result.preferredRegions
        : [];
    const tags = getTravelTags(result.travelCode);
    const guideItems = getTravelGuideItems(result.travelCode);

    const handleSaveResult = async () => {
        if (!resultPageRef.current) {
            return;
        }

        try {
            const canvas = await html2canvas(resultPageRef.current, {
                scale: 2,
                backgroundColor: '#f4f8ff',
                useCORS: true,
            });

            const imageUrl = canvas.toDataURL('image/png');
            const link = document.createElement('a');

            link.href = imageUrl;
            link.download = `seoulink_${result.travelCode}_여행취향검사결과.png`;
            link.click();
        } catch (error) {
            console.error('결과 이미지 저장 실패:', error);
            alert('결과 이미지를 저장하지 못했습니다.');
        }
    };

    const handleRecommend = onRecommend || (() => {
        window.location.assign('/courses/recommendations');
    });

    const handleRestart = onRestart || (() => {
        window.location.assign('/survey');
    });

    return (
        <div className="travel-analysis-page travel-analysis-page--embedded">
            <RecommendationLoadingOverlay
                active={isRecommending}
                title="맞춤 코스를 준비하고 있어요"
                description="여행 일정과 장소 후보를 정리한 뒤 최적화 화면으로 이동할게요."
                longWaitDescription="장소 후보를 날짜별로 정리하고 있어 평소보다 조금 더 걸리고 있어요. 준비가 끝나면 자동으로 이동합니다."
                delay={300}
            />
            <main className="travel-analysis-main" ref={resultPageRef}>
                <section className="preference-title-block">
                    <p className="page-kicker">TRAVEL TYPE CODE</p>
                    <h1>당신의 여행 취향 분석 결과</h1>
                    <p>나만의 여행 유형 코드를 확인하고, 맞춤 여행을 준비해보세요</p>
                </section>

                <section className="preference-dashboard" aria-label="여행 취향 분석 결과">
                    <article className="preference-card preference-code-card">
                        <div className="preference-card-heading">
                            <h2>나의 여행 유형 코드</h2>
                        </div>

                        <div className="travel-code-row">
                            {traits.map((trait) => (
                                <div key={`${trait.dimensionKey}-${trait.code}`} className={`travel-code-box ${trait.color}`}>
                                    <strong>{trait.code}</strong>
                                    <span>{trait.label}</span>
                                </div>
                            ))}
                        </div>

                        <div className="preference-hero-note">
                            <div className="preference-hero-copy">
                                <h3>{result.travelTitle}</h3>
                                <p>{result.description}</p>
                            </div>
                        </div>
                    </article>

                    <article className="preference-card preference-summary-card">
                        <div className="preference-card-heading">
                            <h2>취향 분석 요약</h2>
                        </div>

                        <div className="analysis-list">
                            {traits.map((trait) => {
                                const Icon = traitIconMap[trait.icon] || Sparkles;

                                return (
                                    <div key={trait.dimensionKey} className="analysis-row">
                                        <span className={`analysis-icon ${trait.color}`}>
                                            <Icon size={22} strokeWidth={2.1} />
                                        </span>
                                        <div className="analysis-copy">
                                            <strong>{trait.title}</strong>
                                            <span>{trait.question}</span>
                                        </div>
                                        <p>{trait.answer}</p>
                                        <span className={`analysis-code ${trait.color}`}>{trait.code}</span>
                                    </div>
                                );
                            })}
                        </div>
                    </article>
                </section>

                <section className="preference-lower-grid">
                    <article className="preference-card preference-style-card">
                        <div className="preference-card-heading compact-heading">
                            <h2>나와 잘 맞는 여행 스타일</h2>
                            <div className="tag-strip">
                                {tags.map((tag) => (
                                    <span key={tag}>#{tag}</span>
                                ))}
                            </div>
                        </div>

                        <div className="style-tile-grid">
                            {traits.slice(0, 4).map((trait) => {
                                const Icon = traitIconMap[trait.icon] || Sparkles;

                                return (
                                    <div key={trait.dimensionKey} className={`style-tile ${trait.color}`}>
                                        <Icon size={28} strokeWidth={2.05} />
                                        <strong>{trait.short}</strong>
                                        <p>{trait.description}</p>
                                    </div>
                                );
                            })}
                        </div>
                    </article>

                    <article className="preference-card preference-theme-card">
                        <div className="preference-card-heading compact-heading">
                            <h2>나를 위한 여행 가이드</h2>
                        </div>

                        <div className="travel-guide-row">
                            {guideItems.map((guide) => {
                                const GuideIcon =
                                    traitIconMap[guide.icon] || Sparkles;

                                return (
                                    <div
                                        className={`travel-guide-item ${guide.color}`}
                                        key={guide.category}
                                    >
                                        <div className="travel-guide-icon">
                                            <GuideIcon
                                                size={25}
                                                strokeWidth={2}
                                            />
                                        </div>

                                        <span>{guide.category}</span>
                                        <strong>{guide.title}</strong>
                                        <p>{guide.description}</p>
                                    </div>
                                );
                            })}
                        </div>
                    </article>

                    <article className="preference-card matched-region-card">
                        <div className="preference-card-heading compact-heading">
                            <h2>이런 여행지가 잘 맞아요!</h2>
                        </div>

                        <div className="region-chip-row">
                            {regions.slice(0, 5).map((region) => (
                                <span key={region}>
                                    <MapPin size={16} strokeWidth={2.2} />
                                    {region}
                                </span>
                            ))}
                        </div>
                    </article>

                    <article className="preference-card recommendation-cta-card">
                        <div>
                            <p className="page-kicker">NEXT STEP</p>
                            <h2>이제 맞춤 코스를 추천받아보세요!</h2>
                            <p>취향 코드와 장소 태그를 바탕으로 서울 여행 코스를 준비해드릴게요</p>
                        </div>

                        <button
                            className="recommend-primary-button"
                            type="button"
                            onClick={handleRecommend}
                            disabled={isRecommending}
                        >
                            <Sparkles size={20} strokeWidth={2.2} />
                            {isRecommending ? '맞춤 코스 준비 중...' : '맞춤 코스 추천하기'}
                            <ArrowRight size={19} strokeWidth={2.4} />
                        </button>
                    </article>
                </section>

                {recommendError && (
                    <p className="preference-recommend-error" role="alert">
                        {recommendError}
                    </p>
                )}

                <section className="preference-action-bar" aria-label="결과 화면 작업" data-html2canvas-ignore="true">
                    <button className="preference-ghost-button" type="button" onClick={handleRestart}>
                        <RefreshCw size={18} strokeWidth={2.1} />
                        검사 다시하기
                    </button>
                    <button className="preference-ghost-button" type="button" onClick={handleSaveResult}>
                        <Download size={18} strokeWidth={2.1} />
                        결과 저장하기
                    </button>
                    <button
                        className="preference-solid-button"
                        type="button"
                        onClick={handleRecommend}
                        disabled={isRecommending}
                    >
                        <Sparkles size={18} strokeWidth={2.1} />
                        {isRecommending ? '맞춤 코스 준비 중...' : '맞춤 코스 추천하기'}
                        <ArrowRight size={18} strokeWidth={2.3} />
                    </button>
                </section>
            </main>
        </div>
    );
}

export default PreferenceResultPage;
