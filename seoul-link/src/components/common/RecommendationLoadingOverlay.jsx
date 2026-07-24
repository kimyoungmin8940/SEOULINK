
import { LoaderCircle, Sparkles } from 'lucide-react';
import { useEffect, useState } from 'react';

/**
 * 추천 준비가 길어질 때 화면 전체에 진행 상태를 안내합니다.
 * 짧은 요청에서는 화면이 번쩍이지 않도록 약간의 지연 후 표시합니다.
 */
function RecommendationLoadingOverlay({
    active,
    title,
    description,
    longWaitDescription = '장소 조합이 많아 평소보다 조금 더 걸리고 있어요. 계산은 계속 진행 중입니다.',
    delay = 450,
}) {
    const [isVisible, setIsVisible] = useState(false);
    const [isLongWait, setIsLongWait] = useState(false);

    useEffect(() => {
        if (!active) {
            setIsVisible(false);
            setIsLongWait(false);
            return undefined;
        }

        const visibleTimer = window.setTimeout(() => {
            setIsVisible(true);
        }, delay);
        const longWaitTimer = window.setTimeout(() => {
            setIsLongWait(true);
        }, 8_000);

        return () => {
            window.clearTimeout(visibleTimer);
            window.clearTimeout(longWaitTimer);
        };
    }, [active, delay]);

    if (!isVisible) {
        return null;
    }

    return (
        <div
            className="recommendation-loading-overlay"
            role="status"
            aria-live="polite"
            aria-atomic="true"
            aria-label={title}
        >
            <div className="recommendation-loading-backdrop" aria-hidden="true" />

            <section className="recommendation-loading-panel" aria-busy="true">
                <div className="recommendation-loading-spinner" aria-hidden="true">
                    <LoaderCircle size={62} strokeWidth={1.65} />
                    <span>
                        <Sparkles size={24} strokeWidth={2.1} />
                    </span>
                </div>

                <p className="recommendation-loading-kicker">SEOULINK COURSE</p>
                <h2>{title}</h2>
                <p>{isLongWait ? longWaitDescription : description}</p>

                <div className="recommendation-loading-dots" aria-hidden="true">
                    <i />
                    <i />
                    <i />
                </div>

                <small>화면을 닫지 않고 잠시만 기다려 주세요.</small>
            </section>
        </div>
    );
}

export default RecommendationLoadingOverlay;
