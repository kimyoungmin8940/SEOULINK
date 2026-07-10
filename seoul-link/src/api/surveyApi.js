import { apiClient } from './apiClient';

export const getSurveyQuestions = () => apiClient.get('/surveys/questions');
export const submitSurvey = (answers) => apiClient.post('/surveys/submit', { answers });
export const getMySurveyResult = () => apiClient.get('/surveys/result/me');
