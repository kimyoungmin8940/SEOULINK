import { apiClient } from './apiClient';

export const getMyTravelType = () => apiClient.get('/mypage/travel-type');
export const getMyCourses = () => apiClient.get('/mypage/courses');
export const getMyFavorites = () => apiClient.get('/mypage/favorites');
export const getMyReviews = () => apiClient.get('/mypage/reviews');
