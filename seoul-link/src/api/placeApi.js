import { apiClient } from './apiClient';

export const getPlaces = (queryString = '') => apiClient.get(`/places${queryString}`);
export const getPlaceDetail = (placeId) => apiClient.get(`/places/${placeId}`);

/** 여행 유형에 맞는 장소 후보와 대체 후보를 결과 화면용으로 조회합니다. */
export const getRecommendedPlaces = (
    travelCode,
    companionType = '',
    options = {},
) => {
    const companionQuery = companionType
        ? `&companionType=${encodeURIComponent(companionType)}`
        : '';

    return apiClient.get(
        `/places/recommend?travelCode=${encodeURIComponent(travelCode)}`
        + `${companionQuery}&limitPerCategory=20&alternativeLimit=3`,
        options,
    );
};

export const getPlacesByNames = (names) => {
    const params = new URLSearchParams();

    names.forEach((name) => {
        params.append('names', name);
    });

    return apiClient.get(
        `/places/by-names?${params.toString()}`
    );
};
