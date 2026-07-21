import { apiClient } from './apiClient';

export const getPlaces = (queryString = '') => apiClient.get(`/places${queryString}`);
export const getPlaceDetail = (placeId) => apiClient.get(`/places/${placeId}`);
export const getRecommendedPlaces = (travelCode) => apiClient.get(`/places/recommend?travelCode=${encodeURIComponent(travelCode)}&limitPerCategory=20&alternativeLimit=3`);
