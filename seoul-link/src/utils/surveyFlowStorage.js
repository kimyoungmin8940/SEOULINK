const SESSION_SURVEY_KEYS = [
    'seoulinkTravelInfo',
    'seoulinkSurveyResult',
    'seoulinkSurveyId',
    'seoulinkCourseRecommendRequest',
    'seoulinkCourseRecommendResponse',
    'seoulinkCourseRecommendHistory',
    'courseRecommendRequest',
    'courseRecommendationRequest',
    'courseRecommendResponse',
    'courseRecommendationResponse',
    'seoulinkCourseRecommendationResponse',
];

/** 사용자가 검사를 다시 시작할 때 이전 설문·추천 탭 상태만 깨끗하게 비웁니다. */
export function clearSurveyFlowStorage() {
    SESSION_SURVEY_KEYS.forEach((key) => window.sessionStorage.removeItem(key));
    window.localStorage.removeItem('guestToken');
}

/** 이전 결과를 지운 뒤 새 여행 정보 입력 화면으로 이동합니다. */
export function restartSurveyFlow(path = '/travel-info') {
    clearSurveyFlowStorage();
    window.location.assign(path);
}
