import { useEffect, useState } from 'react';
import {isLoggedIn} from '../../utils/authGuard';

import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';
import PreferenceResultPage from '../PreferenceResultPage';
import { getSurveyResult } from '../../api/surveyApi';

const SURVEY_ID_STORAGE_KEY = 'seoulinkSurveyId';

function SurveyResultPage() {
    const [result, setResult] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState('');

    const handleRecommend = () => {
        const returnUrl = '/courses';

        if (isLoggedIn()) {
            window.location.assign(returnUrl);
            return;
        }

        localStorage.setItem(
            'loginReturnUrl',
            returnUrl
        );

        window.location.assign('/signup');
    };

    useEffect(() => {
        const loadSurveyResult = async () => {
            const surveyId =
                sessionStorage.getItem(SURVEY_ID_STORAGE_KEY);

            if (!surveyId) {
                setErrorMessage(
                    '검사 정보를 찾을 수 없습니다. 취향 검사를 다시 진행해주세요'
                );
                setIsLoading(false);
                return;
            }

            try {
                const response =
                    await getSurveyResult(surveyId);

                const preferenceResult = {
                    ...response,

                    // 결과 페이지가 사용하는 필드 이름에 맞춰 변환
                    travelTitle: response.typeTitle,
                    description: response.typeDescription,

                    // 추천 장소 기능을 연결하기 전까지 빈 배열 사용
                    recommendedPlaces: [],
                    recommendedItinerary: [],
                };

                setResult(preferenceResult);
            } catch (error) {
                console.error(
                    '설문 결과 조회 실패:',
                    error
                );

                setErrorMessage(
                    '검사 결과를 불러오는 중 오류가 발생했습니다'
                );
            } finally {
                setIsLoading(false);
            }
        };

        loadSurveyResult();
    }, []);

    if (isLoading) {
        return (
            <SurveyFlowLayout currentStep={3}>
                <section className="survey-stage-card">
                    <p>검사 결과를 불러오고 있습니다.</p>
                </section>
            </SurveyFlowLayout>
        );
    }

    if (errorMessage) {
        return (
            <SurveyFlowLayout currentStep={3}>
                <section className="survey-stage-card">
                    <p className="survey-question-error">
                        {errorMessage}
                    </p>

                    <a
                        className="survey-stage-primary-btn"
                        href="/travel-info"
                    >
                        취향 검사 다시 시작하기
                    </a>
                </section>
            </SurveyFlowLayout>
        );
    }

    return (
        <SurveyFlowLayout currentStep={3}>
            <PreferenceResultPage
                result={result}
                onRecommend={handleRecommend}
            />
        </SurveyFlowLayout>
    );
}

export default SurveyResultPage;