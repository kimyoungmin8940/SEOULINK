import { Plus, X } from "lucide-react";
import { getIndoorOutdoorLabel, getPlaceDisplayLabel, getPlaceKey } from "./placeUtils";

function SelectedMapPlaceList({
    selectedMapPlaces,
    onClearSelectedMapPlaces,
    onMoveToPlace,
    onClickableNameKeyDown,
    onAddPlaceToCourse,
    onRemoveSelectedMapPlace,
}) {
    if (selectedMapPlaces.length === 0) return null;

    return (
        <aside className="course-builder-map-selection" aria-label="지도에서 선택한 장소">
            <div className="course-builder-map-selection-header">
                <strong>선택한 장소 {selectedMapPlaces.length}</strong>
                <button type="button" onClick={onClearSelectedMapPlaces}>전체 해제</button>
            </div>

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
        </aside>
    );
}

export default SelectedMapPlaceList;
