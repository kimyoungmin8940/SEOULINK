import { apiClient } from './apiClient';

export const getPlaces = (queryString = '') => apiClient.get(`/places${queryString}`);
export const getPlaceDetail = (placeId) => apiClient.get(`/places/${placeId}`);

/** 여행 유형에 맞는 장소 후보와 대체 후보를 결과 화면용으로 조회합니다. */
export const getRecommendedPlaces = (travelCode, options = {}) => apiClient.get(
    `/places/recommend?travelCode=${encodeURIComponent(travelCode)}`
    + '&limitPerCategory=20&alternativeLimit=3',
    options,
);
