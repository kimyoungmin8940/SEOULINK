import { useEffect, useState } from 'react';
import {
    ArrowRight,
    RotateCcw,
} from 'lucide-react';

import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';
import SurveyQuestion from '../../components/survey/SurveyQuestion';

import {
    getSurveyQuestions,
    submitGuestSurvey,
} from '../../api/surveyApi';

const TRAVEL_INFO_STORAGE_KEY = 'seoulinkTravelInfo';
const SURVEY_RESULT_STORAGE_KEY = 'seoulinkSurveyResult';
const GUEST_TOKEN_STORAGE_KEY = 'seoulinkGuestToken';
const SURVEY_ID_STORAGE_KEY = 'seoulinkSurveyId';

//TravelInfoPage에서 저장한 여행 정보를 읽는다.
function getStoredTravelInfo() {
    try {
        const storedTravelInfo = sessionStorage.getItem(TRAVEL_INFO_STORAGE_KEY);

        if (!storedTravelInfo) {
            return null;
        }

        const travelInfo = JSON.parse(storedTravelInfo);

        if (
            !travelInfo.startDate ||
            !travelInfo.endDate ||
            !travelInfo.companionType ||
            !travelInfo.transportType
        ) {
            return null;
        }
        return travelInfo;
    } catch (error) {
        console.error('여행 정보 읽기 실패:', error);
        return null;
    }
}

function SurveyPage() {
    const [travelInfo] = useState(getStoredTravelInfo);
    const [questions, setQuestions] = useState([]);

    const [
        currentQuestionIndex,
        setCurrentQuestionIndex,
    ] = useState(0);

    const [selectedOptionId, setSelectedOptionId] = useState(null);
    const [answers, setAnswers] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [loadError, setLoadError] = useState('');

    const [
        selectionError,
        setSelectionError,
    ] = useState('');

    //여행 정보 없이 설문 주소로 직접 접근하면 여행 정보 입력 페이지로 돌려보냄
    useEffect(() => {
        if (!travelInfo) {
            window.location.replace('/travel-info');
        }
    }, [travelInfo]);

    //DB에 저장된 질문과 선택지를 조회
    useEffect(() => {
        if (!travelInfo) {
            return;
        }

        const loadQuestions = async () => {
            try {
                setIsLoading(true);
                setLoadError('');

                const data = await getSurveyQuestions();

                if (!Array.isArray(data)) {
                    throw new Error(
                        '질문 데이터 형식이 올바르지 않습니다'
                    );
                }

                setQuestions(data);
            } catch (error) {
                console.error('설문 질문 조회 실패:', error);

                setLoadError('설문 질문을 불러오는 중 오류가 발생했습니다');
            } finally {
                setIsLoading(false);
            }
        };

        loadQuestions();
    }, [travelInfo]);

    //선택지를 선택
    const handleOptionSelect = (
        optionId
    ) => {
        if (isSubmitting) {
            return;
        }

        setSelectedOptionId(optionId);
        setSelectionError('');
    };

    //설문 답변을 처음부터 다시 시작한다.
    const handleReset = () => {
        if (isSubmitting) {
            return;
        }

        setCurrentQuestionIndex(0);
        setSelectedOptionId(null);
        setAnswers([]);
        setSelectionError('');
    };

    if (!travelInfo) {
        return (
            <SurveyFlowLayout currentStep={2}>
                <section className="survey-stage-card">
                    <p>
                        여행 정보 입력 페이지로
                        이동하고 있습니다
                    </p>
                </section>
            </SurveyFlowLayout>
        );
    }

    if (isLoading) {
        return (
            <SurveyFlowLayout currentStep={2}>
                <section className="survey-stage-card">
                    <p>
                        설문 질문을 불러오는
                        중입니다
                    </p>
                </section>
            </SurveyFlowLayout>
        );
    }

    if (loadError) {
        return (
            <SurveyFlowLayout currentStep={2}>
                <section className="survey-stage-card">
                    <p>{loadError}</p>
                </section>
            </SurveyFlowLayout>
        );
    }

    if (questions.length === 0) {
        return (
            <SurveyFlowLayout currentStep={2}>
                <section className="survey-stage-card">
                    <p>
                        등록된 설문 질문이 없습니다
                    </p>
                </section>
            </SurveyFlowLayout>
        );
    }

    const currentQuestion = questions[currentQuestionIndex];
    const currentNumber = currentQuestionIndex + 1;
    const totalCount = questions.length;
    const progress = (currentNumber / totalCount) * 100;
    const isLastQuestion = currentQuestionIndex === totalCount - 1;

    //다음 질문으로 이동하거나 마지막 답변을 백엔드에 제출

    const handleNextQuestion =
        async () => {
            if (isSubmitting) {
                return;
            }

            if (selectedOptionId === null) {
                setSelectionError(
                    '선택지를 선택해주세요.'
                );
                return;
            }

            const nextAnswers = [
                ...answers.filter(
                    (answer) =>
                        answer.questionId !==
                        currentQuestion.questionId
                ),
                {
                    questionId:
                    currentQuestion.questionId,
                    optionId:
                    selectedOptionId,
                },
            ];

            setAnswers(nextAnswers);

            if (!isLastQuestion) {
                setCurrentQuestionIndex(
                    (previousIndex) =>
                        previousIndex + 1
                );
                setSelectedOptionId(null);
                setSelectionError('');

                return;
            }

            try {
                setIsSubmitting(true);
                setSelectionError('');

                const requestData = {
                    region: travelInfo.region || '서울',
                    startDate: travelInfo.startDate,
                    endDate: travelInfo.endDate,
                    companionType:
                        travelInfo
                            .companionType
                            .toUpperCase(),

                    transportType:
                        travelInfo
                            .transportType
                            .toUpperCase(),

                    answers:
                    nextAnswers,
                };

                console.log('설문 제출 데이터:', requestData);

                const response = await submitGuestSurvey(requestData);

                if (
                    !response ||
                    !response.surveyId ||
                    !response.guestToken ||
                    !response.result
                ) {
                    throw new Error('설문 결과 응답 형식이 올바르지 않습니다.');
                }

                //결과 페이지에서 사용할 정보를 현재 탭에 임시로 저장
                sessionStorage.setItem(
                    SURVEY_RESULT_STORAGE_KEY,
                    JSON.stringify(
                        response.result
                    )
                );

                sessionStorage.setItem(
                    GUEST_TOKEN_STORAGE_KEY,
                    response.guestToken
                );

                sessionStorage.setItem(
                    SURVEY_ID_STORAGE_KEY,
                    String(
                        response.surveyId
                    )
                );

                //백엔드 DB 저장이 성공했으므로 여행 정보 임시값은 제거
                sessionStorage.removeItem(TRAVEL_INFO_STORAGE_KEY);
                window.location.assign('/survey/result');
            } catch (error) {
                console.error('설문 제출 실패:', error);
                setSelectionError('검사 결과를 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
            } finally {setIsSubmitting(false);
            }
        };

    return (
        <SurveyFlowLayout currentStep={2}>
            <SurveyQuestion
                question={currentQuestion}
                currentNumber={currentNumber}
                totalCount={totalCount}
                selectedOptionId={
                    selectedOptionId
                }
                onSelect={
                    handleOptionSelect
                }
            />

            {selectionError && (
                <p className="survey-question-error" role="alert">
                    {selectionError}
                </p>
            )}

            <section className="survey-bottom-section" aria-busy={isSubmitting}>
                <button
                    className="survey-reset-btn"
                    type="button"
                    onClick={handleReset}
                    disabled={isSubmitting}
                >
                    <RotateCcw size={17} />

                    <span>
                        취소하고 처음부터
                    </span>
                </button>

                <div className="survey-progress">
                    <div className="survey-progress-track">
                        <div
                            className="survey-progress-fill"
                            style={{
                                width:
                                    `${progress}%`,
                            }}
                        />
                    </div>

                    <span>
                        {Math.round(progress)}%
                    </span>
                </div>

                <button
                    className="survey-next-btn"
                    type="button"
                    onClick={
                        handleNextQuestion
                    }
                    disabled={isSubmitting}
                >
                    <span>
                        {isSubmitting
                            ? '결과 분석 중...'
                            : isLastQuestion
                                ? '검사 완료'
                                : '다음 질문'}
                    </span>

                    <ArrowRight size={19} />
                </button>
            </section>
        </SurveyFlowLayout>
    );
}

export default SurveyPage;