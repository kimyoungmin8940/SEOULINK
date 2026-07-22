import { useEffect, useState } from 'react';

import { getCourseDraft } from '../../api/courseApi';
import { getRecommendedPlaces } from '../../api/placeApi';
import { getSurveyResult } from '../../api/surveyApi';
import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';
import { storeCourseRecommendRequest } from '../../utils/courseRecommendationHandoff';
import { isLoggedIn } from '../../utils/authGuard';
import PreferenceResultPage from '../PreferenceResultPage';

const SURVEY_ID_STORAGE_KEY = 'seoulinkSurveyId';

const surveyTransportModeMap = Object.freeze({
    PUBLIC: 'PUBLIC_TRANSIT',
    PUBLIC_TRANSIT: 'PUBLIC_TRANSIT',
    WALKING: 'WALKING',
    CAR: 'DRIVING',
    DRIVING: 'DRIVING',
});

/** 설문에서 저장한 이동수단 값을 코스 최적화 API의 enum 값으로 맞춥니다. */
function getCourseTransportMode(transportType) {
    if (typeof transportType !== 'string') {
        return null;
    }

    return surveyTransportModeMap[transportType.trim().toUpperCase()] || null;
}

function SurveyResultPage() {
    const [result, setResult] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState('');
    const [isPreparingCourse, setIsPreparingCourse] = useState(false);
    const [courseErrorMessage, setCourseErrorMessage] = useState('');

    useEffect(() => {
        const loadSurveyResult = async () => {
            const surveyId = Number(sessionStorage.getItem(SURVEY_ID_STORAGE_KEY));

            if (!Number.isInteger(surveyId) || surveyId < 1) {
                setErrorMessage('검사 정보를 찾을 수 없습니다. 취향 검사를 다시 진행해주세요.');
                setIsLoading(false);
                return;
            }

            try {
                const response = await getSurveyResult(surveyId);
                let recommendedPlaces = [];

                try {
                    const placeResponse = await getRecommendedPlaces(response.travelCode);
                    recommendedPlaces = (placeResponse?.recommendedPlaces || []).map((place) => ({
                        ...place,
                        // 결과 화면에서 공통으로 사용하는 장소명 필드도 함께 제공합니다.
                        name: place.placeName,
                    }));
                } catch (placeError) {
                    // 장소 미리보기가 실패해도 저장된 여행 유형 결과는 계속 보여줍니다.
                    console.error('추천 장소 조회 실패:', placeError);
                }

                setResult({
                    ...response,
                    travelTitle: response.typeTitle,
                    description: response.typeDescription,
                    recommendedPlaces,
                });
            } catch (error) {
                console.error('설문 결과 조회 실패:', error);
                setErrorMessage('검사 결과를 불러오는 중 오류가 발생했습니다.');
            } finally {
                setIsLoading(false);
            }
        };

        loadSurveyResult();
    }, []);

    /** 설문 결과 → 날짜별 후보 초안 → 코스 최적화 화면의 입력값으로 연결합니다. */
    const handleRecommend = async () => {
        if (isPreparingCourse) {
            return;
        }

        const surveyId = Number(
            result?.surveyId || sessionStorage.getItem(SURVEY_ID_STORAGE_KEY),
        );

        if (!Number.isInteger(surveyId) || surveyId < 1) {
            setCourseErrorMessage('추천에 필요한 검사 정보를 찾을 수 없습니다.');
            return;
        }

        try {
            setIsPreparingCourse(true);
            setCourseErrorMessage('');

            const draft = await getCourseDraft(surveyId);
            const transportMode = getCourseTransportMode(draft?.transportType);

            if (!transportMode) {
                throw new Error('선택한 이동수단을 코스 추천에 연결할 수 없습니다.');
            }

            storeCourseRecommendRequest({
                ...draft,
                transportMode,
                excludedRecommendationKeys: [],
            });

            if (isLoggedIn()) {
                window.location.assign('/courses');
                return;
            }

            // 회원가입·로그인 후 방금 만든 추천 입력으로 코스 화면을 계속 엽니다.
            localStorage.setItem('loginReturnUrl', '/courses');
            window.location.assign('/signup');
        } catch (error) {
            console.error('추천 코스 초안 생성 실패:', error);
            setCourseErrorMessage(
                error instanceof Error
                    ? error.message
                    : '추천 코스를 준비하는 중 오류가 발생했습니다.',
            );
        } finally {
            setIsPreparingCourse(false);
        }
    };

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
                    <p className="survey-question-error">{errorMessage}</p>
                    <a className="survey-stage-primary-btn" href="/travel-info">
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
                onRestart={() => window.location.assign('/travel-info')}
                isRecommending={isPreparingCourse}
                recommendError={courseErrorMessage}
            />
        </SurveyFlowLayout>
    );
}

export default SurveyResultPage;
