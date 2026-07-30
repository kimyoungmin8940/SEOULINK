import { apiClient } from './apiClient';

export const getMyPage = (memberId) => apiClient.get(`/mypage/${memberId}`);
export const getMyCourses = async (memberId) => (await getMyPage(memberId))?.courses || [];
export const getMyReviews = async (memberId) => (await getMyPage(memberId))?.reviews || [];
export const getMyComments = (memberId) => apiClient.get(`/mypage/${memberId}/comments`);
export const getMyTravelType = async (memberId) => (await getMyPage(memberId))?.travelType || null;
export const getMyPayments = async (memberId) => (await getMyPage(memberId))?.payments || [];
// 챗봇 이력은 마이페이지 통합 응답과 분리해, 챗봇 전용 API에서 조회한다.
export const getMyChatbotHistories = (memberId) =>
    apiClient.get(`/chatbot/histories?memberId=${memberId}`);
