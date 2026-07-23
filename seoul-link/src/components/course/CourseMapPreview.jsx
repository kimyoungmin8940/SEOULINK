import { MapPin, Route } from 'lucide-react';
import KakaoCourseMap from './KakaoCourseMap';
import { getMappableCoursePlaces } from '../../utils/courseMap';

/** 선택하거나 가리킨 추천 옵션에서 현재 선택한 날짜의 장소만 지도에 전달합니다. */
function CourseMapPreview({ option, activeDayNo }) {
    const days = Array.isArray(option?.days) ? option.days : [];
    const activeDay = days.find((day) => day?.dayNo === activeDayNo)
        || days[0]
        || { places: [] };
    const places = (activeDay?.places || []).slice(0, 35);
    const mappablePlaceCount = getMappableCoursePlaces(places).length;

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

                {mappablePlaceCount > 0 && (
                    <div className="course-result-map-legend">
                        <Route size={14} aria-hidden="true" />
                        <span>
                            DAY {activeDay?.dayNo || 1} · {mappablePlaceCount}개 장소를 순서대로 연결했어요
                        </span>
                    </div>
                )}
            </div>
        </div>
    );
}

export default CourseMapPreview;
