import { ArrowDown, ArrowUp, Car, Clock3, X } from "lucide-react";
import { formatDistanceMeter, formatDurationMinute } from "./formatUtils";
import { getCoursePlaceKey, normalizeDayNo } from "./coursePlaceUtils";
import { getIndoorOutdoorLabel, getPlaceDisplayLabel } from "./placeUtils";

function CoursePlaceList({
    coursePlaces,
    activeDayNo,
    totalDayCount,
    courseTitle,
    courseDescription,
    dayTimeSummary,
    isCalculatingRoutes,
    onCourseTitleChange,
    onCourseDescriptionChange,
    onDayChange,
    onMoveToPlace,
    onClickableNameKeyDown,
    onUpdateCoursePlaceField,
    onMoveCoursePlace,
    onRemovePlaceFromCourse,
    onSaveCourse,
}) {
    const dayPlaces = coursePlaces
        .map((place, globalIndex) => ({ place, globalIndex }))
        .filter(({ place }) => normalizeDayNo(place.dayNo) === activeDayNo);
    const dayNumbers = Array.from({ length: totalDayCount }, (_, index) => index + 1);

    return (
        <section className="course-builder-selected-area">
            <div className="course-builder-course-header">
                <h2>나만의 서울 코스</h2>
                <span className="course-builder-current-day">DAY {activeDayNo}</span>
            </div>

            <div className="course-builder-day-buttons" aria-label="여행 일차 선택">
                {dayNumbers.map((dayNumber) => (
                    <button
                        key={dayNumber}
                        type="button"
                        className={dayNumber === activeDayNo ? "active" : ""}
                        onClick={() => onDayChange(dayNumber)}
                        aria-pressed={dayNumber === activeDayNo}
                        aria-label={`${dayNumber}일차`}
                    >
                        {dayNumber}
                    </button>
                ))}
            </div>

            <div className="course-builder-course-meta-fields">
                <input value={courseTitle} onChange={(event) => onCourseTitleChange(event.target.value)} placeholder="코스 제목" aria-label="코스 제목" />
                <textarea value={courseDescription} onChange={(event) => onCourseDescriptionChange(event.target.value)} placeholder="코스 설명" aria-label="코스 설명" />
            </div>

            <div className="course-builder-course-list">
                {dayPlaces.length === 0 && (
                    <div className="course-builder-empty-state">
                        <p>지도에서 장소를 선택해<br />이 날짜의 코스에 추가해주세요.</p>
                    </div>
                )}

                {dayPlaces.map(({ place, globalIndex }, dayIndex) => {
                    const placeKey = getCoursePlaceKey(place);
                    const hasNextPlace = Boolean(dayPlaces[dayIndex + 1]);

                    return (
                        <div key={placeKey} className="course-builder-course-segment">
                            <article className="course-builder-selected-card">
                                <div className="course-builder-place-card-top">
                                    <span className="course-builder-course-order">{dayIndex + 1}</span>
                                    <strong className="course-builder-place-name course-builder-clickable-place-name" role="button" tabIndex={0} onClick={() => onMoveToPlace(place)} onKeyDown={(event) => onClickableNameKeyDown(event, place)}>{place.name}</strong>
                                    <button type="button" className="course-builder-remove-course-place" onClick={() => onRemovePlaceFromCourse(placeKey)} aria-label={`${place.name} 코스에서 삭제`}><X size={17} aria-hidden="true" /></button>
                                </div>
                                <p className="course-builder-place-information">{getPlaceDisplayLabel(place)} · {place.region} · {getIndoorOutdoorLabel(place)}</p>
                                <div className="course-builder-card-controls">
                                    <label className="course-builder-stay-field"><Clock3 size={15} aria-hidden="true" /><span>체류</span><input type="number" min="0" step="10" value={place.stayMinutes ?? 0} onChange={(event) => onUpdateCoursePlaceField(globalIndex, "stayMinutes", event.target.value)} aria-label={`${place.name} 체류 시간`} /><span>분</span></label>
                                    <div className="course-builder-order-buttons">
                                        <button type="button" onClick={() => onMoveCoursePlace(globalIndex, -1)} disabled={dayIndex === 0} aria-label={`${place.name} 순서 위로`}><ArrowUp size={15} aria-hidden="true" /></button>
                                        <button type="button" onClick={() => onMoveCoursePlace(globalIndex, 1)} disabled={dayIndex === dayPlaces.length - 1} aria-label={`${place.name} 순서 아래로`}><ArrowDown size={15} aria-hidden="true" /></button>
                                    </div>
                                </div>
                                {place.routeStatusMessage && <p className="course-builder-route-error">경로 계산 안내: {place.routeStatusMessage}</p>}
                            </article>
                            {hasNextPlace && <div className="course-builder-route-info"><Car size={15} aria-hidden="true" /><span>차량 {formatDurationMinute(place.moveDurationMin)} · {formatDistanceMeter(place.moveDistanceM)}{place.routeToPlaceName ? ` → ${place.routeToPlaceName}` : ""}</span></div>}
                        </div>
                    );
                })}
            </div>

            <div className="course-builder-time-summary" aria-live="polite"><div className="course-builder-time-summary-title"><Clock3 size={20} aria-hidden="true" /><span>예상 시간</span><strong>{formatDurationMinute(dayTimeSummary.totalMinutes)}</strong></div><p>체류 {formatDurationMinute(dayTimeSummary.stayTotalMinutes)} + 차량 이동 {formatDurationMinute(dayTimeSummary.moveTotalMinutes)}</p></div>
            <button type="button" className="course-builder-save-button" onClick={onSaveCourse} disabled={isCalculatingRoutes}>코스 저장</button>
        </section>
    );
}

export default CoursePlaceList;
