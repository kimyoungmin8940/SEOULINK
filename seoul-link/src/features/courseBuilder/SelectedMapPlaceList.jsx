import { useState } from "react";
import { ChevronDown, ChevronUp, Plus, X } from "lucide-react";
import { getIndoorOutdoorLabel, getPlaceDisplayLabel, getPlaceKey } from "./placeUtils";

function SelectedMapPlaceList({
    selectedMapPlaces,
    onClearSelectedMapPlaces,
    onMoveToPlace,
    onClickableNameKeyDown,
    onAddPlaceToCourse,
    onRemoveSelectedMapPlace,
}) {
    const [isCollapsed, setIsCollapsed] = useState(false);

    if (selectedMapPlaces.length === 0) return null;

    return (
        <aside
            className={`course-builder-map-selection${isCollapsed ? " is-collapsed" : ""}`}
            aria-label="지도에서 선택한 장소"
        >
            <div className="course-builder-map-selection-header">
                <strong>선택한 장소 {selectedMapPlaces.length}</strong>
                <div className="course-builder-map-selection-actions">
                    {!isCollapsed && (
                        <button
                            type="button"
                            className="course-builder-map-selection-clear"
                            onClick={onClearSelectedMapPlaces}
                        >
                            전체 해제
                        </button>
                    )}
                    <button
                        type="button"
                        className="course-builder-map-selection-toggle"
                        onClick={() => setIsCollapsed((current) => !current)}
                        aria-expanded={!isCollapsed}
                        aria-label={isCollapsed ? "선택한 장소 펼치기" : "선택한 장소 최소화"}
                        title={isCollapsed ? "펼치기" : "최소화"}
                    >
                        {isCollapsed
                            ? <ChevronUp size={17} aria-hidden="true" />
                            : <ChevronDown size={17} aria-hidden="true" />}
                    </button>
                </div>
            </div>

            {!isCollapsed && (
                <div className="course-builder-map-selection-list">
                    {selectedMapPlaces.map((place) => {
                        const placeKey = getPlaceKey(place);

                        return (
                            <article key={placeKey} className="course-builder-place-card">
                                <button
                                    type="button"
                                    className="course-builder-selection-remove"
                                    onClick={() => onRemoveSelectedMapPlace(placeKey)}
                                    aria-label={`${place.name} 선택 해제`}
                                >
                                    <X size={15} aria-hidden="true" />
                                </button>

                                <strong
                                    className="course-builder-place-name course-builder-clickable-place-name"
                                    role="button"
                                    tabIndex={0}
                                    onClick={() => onMoveToPlace(place)}
                                    onKeyDown={(event) => onClickableNameKeyDown(event, place)}
                                >
                                    {place.name}
                                </strong>
                                <p>
                                    {getPlaceDisplayLabel(place)} · {place.region} · {getIndoorOutdoorLabel(place)}
                                </p>

                                <button
                                    type="button"
                                    className="course-builder-add-course-button"
                                    onClick={() => onAddPlaceToCourse(place)}
                                >
                                    <Plus size={15} aria-hidden="true" />
                                    코스 추가
                                </button>
                            </article>
                        );
                    })}
                </div>
            )}
        </aside>
    );
}

export default SelectedMapPlaceList;
