import { ChevronRight } from 'lucide-react';

const recommendationSteps = [
    {
        number: 1,
        title: '여행 정보 입력',
        description: '기본 정보를 입력해주세요',
    },
    {
        number: 2,
        title: '취향 검사',
        description: '10개의 질문에 답해주세요',
    },
    {
        number: 3,
        title: '결과 분석',
        description: '당신의 여행 취향을 분석해요',
    },
    {
        number: 4,
        title: '맞춤 추천',
        description: '당신에게 딱 맞는 코스를 추천해요',
    },
];

function RecommendationSteps({ currentStep }) {
    return (
        <nav className="recommendation-steps" aria-label="맞춤 코스 추천 진행 단계">
            <ol>
                {recommendationSteps.map((step, index) => {
                    const isActive = step.number === currentStep;
                    const isCompleted = step.number < currentStep;

                    return (
                        <li
                            className={`recommendation-step${isActive ? ' active' : ''}${isCompleted ? ' completed' : ''}`}
                            key={step.number}
                            aria-current={isActive ? 'step' : undefined}
                        >
                            <div className="recommendation-step-content">
                                <span className="recommendation-step-number">{step.number}</span>

                                <span className="recommendation-step-copy">
                                    <strong>{step.title}</strong>
                                    <small>{step.description}</small>
                                </span>
                            </div>

                            {index < recommendationSteps.length - 1 && (
                                <ChevronRight
                                    className="recommendation-step-arrow"
                                    size={22}
                                    strokeWidth={2.2}
                                    aria-hidden="true"
                                />
                            )}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}

export default RecommendationSteps;
