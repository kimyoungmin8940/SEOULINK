import { apiClient } from './apiClient';

export const getSurveyQuestions = () => apiClient.get('/surveys/questions');
export const submitSurvey = (answers) => apiClient.post('/surveys/submit', { answers });
export const getMySurveyResult = () => apiClient.get('/surveys/result/me');

/** 비회원 여행 정보와 설문 답변을 저장하고 설문 번호·결과를 받습니다. */
export const submitGuestSurvey = (requestData) => apiClient.post(
    '/surveys/guest',
    requestData,
);

/** 설문 번호로 저장된 5자리 여행 유형 결과를 다시 조회합니다. */
export const getSurveyResult = (surveyId) => apiClient.get(
    `/surveys/${surveyId}/result`,
);

export const claimGuestSurvey = (guestToken, memberId) => apiClient.post(
    '/surveys/claim',
    { guestToken, memberId },
);
