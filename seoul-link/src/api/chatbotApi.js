import { apiClient } from './apiClient';

export const askChatbot = (question) => apiClient.post('/chatbot/ask', { question });
export const getChatbotHistory = () => apiClient.get('/chatbot/history/me');

