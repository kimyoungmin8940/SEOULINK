import { apiClient } from './apiClient';

// conversationId는 새 대화 단위 식별자다. 같은 대화에서 오간 여러 질문은
// 하나의 최근 대화 항목으로 묶기 위해 모든 요청에 함께 전달한다.

/** AI 여행 플래너에 질문과 여행 조건을 전달한다. */
export const askChatbot = (payload) =>
  apiClient.post(
    '/chatbot/ask',
    typeof payload === 'string' ? { question: payload } : payload,
  );

/** 회원의 최근 AI 여행 플래너 대화를 조회한다. */
export const getChatbotHistory = (memberId) =>
  apiClient.get(`/chatbot/histories?memberId=${memberId}`);

export const deleteChatbotConversation = (memberId, conversationId) =>
  apiClient.delete(`/chatbot/histories/${encodeURIComponent(conversationId)}?memberId=${memberId}`);
