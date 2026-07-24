import { apiClient } from './apiClient';

/** AI 여행 플래너에 질문과 여행 조건을 전달한다. */
export const askChatbot = (payload) =>
  apiClient.post(
    '/chatbot/ask',
    typeof payload === 'string' ? { question: payload } : payload,
  );

/** 회원의 최근 AI 여행 플래너 대화를 조회한다. */
export const getChatbotHistory = (memberId) =>
  apiClient.get(`/chatbot/histories?memberId=${memberId}`);
