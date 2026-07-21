function getCoordinate(place, primaryField, aliases) {
    const value = [primaryField, ...aliases]
        .map((field) => place?.[field])
        .find((candidate) => candidate !== null
            && candidate !== undefined
            && String(candidate).trim() !== '');
    const coordinate = Number(value);

    return Number.isFinite(coordinate) ? coordinate : null;
}

/** 백엔드가 숫자 또는 문자열 좌표를 내려줘도 지도에서 쓸 수 있도록 정규화합니다. */
export function getMappableCoursePlaces(places) {
    return (Array.isArray(places) ? places : []).flatMap((place, index) => {
        const latitude = getCoordinate(place, 'latitude', ['lat']);
        const longitude = getCoordinate(place, 'longitude', ['lng', 'lon']);
        const hasValidCoordinates = latitude !== null
            && longitude !== null
            && latitude >= -90
            && latitude <= 90
            && longitude >= -180
            && longitude <= 180;

        if (!hasValidCoordinates) return [];

        return [{
            ...place,
            latitude,
            longitude,
            placeName: place?.placeName || place?.name || `장소 ${index + 1}`,
        }];
    });
}
