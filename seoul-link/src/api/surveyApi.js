import { apiClient } from './apiClient';

export const getSurveyQuestions = () => apiClient.get('/surveys/questions');
export const submitGuestSurvey = (requestData) => apiClient.post('/surveys/guest', requestData);
export const getSurveyResult = (surveyId) => apiClient.get(`/surveys/${surveyId}/result`);