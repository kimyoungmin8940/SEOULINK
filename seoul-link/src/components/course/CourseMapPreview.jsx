import { startTransition, useEffect, useState } from 'react';
import { MapPin } from 'lucide-react';
import KakaoCourseMap from './KakaoCourseMap';

/** 카드 DAY가 먼저 그려진 뒤 지도 DAY만 별도 렌더하도록 다음 페인트까지 기다립니다. */
function scheduleMapDayAfterPaint(callback) {
    if (typeof window === 'undefined') {
        return () => {};
    }

    if (typeof window.requestAnimationFrame !== 'function') {
        const timeoutId = window.setTimeout(callback, 0);
        return () => window.clearTimeout(timeoutId);
    }

    let secondFrameId = null;
    const firstFrameId = window.requestAnimationFrame(() => {
        secondFrameId = window.requestAnimationFrame(callback);
    });

    return () => {
        window.cancelAnimationFrame(firstFrameId);
        if (secondFrameId !== null) {
            window.cancelAnimationFrame(secondFrameId);
        }
    };
}

/** 선택하거나 가리킨 추천 옵션에서 현재 선택한 날짜의 장소만 지도에 전달합니다. */
function CourseMapPreview({ option, activeDayNo }) {
    const [displayedDayNo, setDisplayedDayNo] = useState(activeDayNo);

    useEffect(() => {
        if (displayedDayNo === activeDayNo) {
            return undefined;
        }

        return scheduleMapDayAfterPaint(() => {
            startTransition(() => {
                setDisplayedDayNo(activeDayNo);
            });
        });
    }, [activeDayNo, displayedDayNo]);

    const days = Array.isArray(option?.days) ? option.days : [];
    const activeDay = days.find((day) => day?.dayNo === displayedDayNo)
        || days[0]
        || { places: [] };
    const routeOriginPlace = activeDay?.routeOriginPlace || null;
    const visitPlaces = (activeDay?.places || []).slice(0, 35);
    const places = routeOriginPlace
        ? [
            {
                ...routeOriginPlace,
                routeOrigin: true,
            },
            ...visitPlaces,
        ].slice(0, 35)
        : visitPlaces;

    return (
        <div className="course-result-map-card">
            <div className="course-result-side-heading">
                <div>
                    <span className="course-result-side-icon"><MapPin size={17} aria-hidden="true" /></span>
                    <h2>추천 동선</h2>
                </div>
                <span>
                    {option?.optionName || '코스 미리보기'}
                    {activeDay?.dayNo ? ` · DAY ${activeDay.dayNo}` : ''}
                </span>
            </div>

            <div className="course-result-map-canvas" aria-label="선택한 코스의 간단한 동선 미리보기">
                <KakaoCourseMap
                    places={places}
                    ariaLabel={`선택한 추천 코스 DAY ${activeDay?.dayNo || 1}의 카카오 지도 동선`}
                    showZoomControl
                />
            </div>
        </div>
    );
}

export default CourseMapPreview;
