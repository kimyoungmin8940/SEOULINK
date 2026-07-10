import { apiClient } from './apiClient';

export const getPlaces = (queryString = '') => apiClient.get(`/places${queryString}`);
export const getPlaceDetail = (placeId) => apiClient.get(`/places/${placeId}`);
