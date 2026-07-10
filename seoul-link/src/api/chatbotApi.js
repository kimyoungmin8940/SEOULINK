import { apiClient } from './apiClient';
export const askChatbot = (payload) => apiClient.post('/chatbot/ask', typeof payload === 'string' ? { question: payload } : payload);
export const getChatbotHistory = (memberId) => apiClient.get(`/chatbot/histories?memberId=${memberId}`);
