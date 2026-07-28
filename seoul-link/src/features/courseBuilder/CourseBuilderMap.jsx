import { Minus, Plus, RefreshCw, Search, X } from "lucide-react";

function CourseBuilderMap({
    mapContainerRef,
    placeSearchKeyword,
    isLoadingPlaces,
    onPlaceSearchKeywordChange,
    onPlaceSearchKeyDown,
    onClearPlaceSearchKeyword,
    onPlaceKeywordSearch,
    onReloadMapPlaces,
    onZoomIn,
    onZoomOut,
    children,
}) {
    const handleSubmit = (event) => {
        event.preventDefault();
        onPlaceKeywordSearch();
    };

    return (
        <section className="course-builder-map-area" aria-label="장소 지도">
            <div ref={mapContainerRef} className="course-builder-map" />

            <div className="course-builder-map-toolbar">
                <form className="course-builder-map-search" onSubmit={handleSubmit}>
                    <button type="submit" className="course-builder-search-submit" aria-label="장소 검색">
                        <Search size={19} aria-hidden="true" />
                    </button>
                    <input
                        value={placeSearchKeyword}
                        onChange={(event) => onPlaceSearchKeywordChange(event.target.value)}
                        onKeyDown={onPlaceSearchKeyDown}
                        placeholder="장소 검색 (예: 경복궁, 홍대)"
                        aria-label="장소 검색어"
                    />
                    {placeSearchKeyword && (
                        <button
                            type="button"
                            className="course-builder-search-clear"
                            onClick={onClearPlaceSearchKeyword}
                            aria-label="검색어 지우기"
                        >
                            <X size={17} aria-hidden="true" />
                        </button>
                    )}
                </form>

                <button
                    type="button"
                    className="course-builder-map-refresh"
                    onClick={onReloadMapPlaces}
                    disabled={isLoadingPlaces}
                >
                    <RefreshCw size={17} aria-hidden="true" />
                    <span>현재 지도</span>
                </button>
            </div>

            <div className="course-builder-zoom-controls" aria-label="지도 확대 축소">
                <button type="button" onClick={onZoomIn} aria-label="지도 확대">
                    <Plus size={20} aria-hidden="true" />
                </button>
                <button type="button" onClick={onZoomOut} aria-label="지도 축소">
                    <Minus size={20} aria-hidden="true" />
                </button>
            </div>

            {children}
        </section>
    );
}

export default CourseBuilderMap;

