import { apiClient } from './apiClient';

export const getMyPage = (memberId) => apiClient.get(`/mypage/${memberId}`);
export const getMyCourses = async (memberId) => (await getMyPage(memberId))?.courses || [];
export const getMyReviews = async (memberId) => (await getMyPage(memberId))?.reviews || [];
export const getMyComments = (memberId) => apiClient.get(`/mypage/${memberId}/comments`);
export const getMyTravelType = async (memberId) => (await getMyPage(memberId))?.travelType || null;
export const getMyPayments = async (memberId) => (await getMyPage(memberId))?.payments || [];
export const getMyChatbotHistories = async (memberId) => (await getMyPage(memberId))?.chatbotHistories || [];
