import { apiClient } from './apiClient';
export { getMyCourses } from './courseApi';

export const getMyTravelType = () => apiClient.get('/mypage/travel-type');
export const getMyFavorites = () => apiClient.get('/mypage/favorites');
export const getMyReviews = () => apiClient.get('/mypage/reviews');
