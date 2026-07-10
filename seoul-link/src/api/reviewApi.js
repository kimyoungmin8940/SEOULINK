import { apiClient } from './apiClient';

export const getReviews = () => apiClient.get('/reviews');
export const getReviewDetail = (reviewId) => apiClient.get(`/reviews/${reviewId}`);
export const createReview = (data) => apiClient.post('/reviews', data);
export const updateReview = (reviewId, data) => apiClient.patch(`/reviews/${reviewId}`, data);
export const deleteReview = (reviewId) => apiClient.delete(`/reviews/${reviewId}`);
export const likeReview = (reviewId) => apiClient.post(`/reviews/${reviewId}/likes`);
