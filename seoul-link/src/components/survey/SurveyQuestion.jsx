import {
    Check,
    Lightbulb,
    ChevronLeft,
    ChevronRight,
} from 'lucide-react';

function SurveyQuestion({
                            question,
                            currentNumber,
                            totalCount,
                            selectedOptionId,
                            onSelect,
                            onPrevious,
                            onForward,
                            canGoPrevious,
                            canGoForward,
                        }) {
    const options = question.options ?? [];

    return (
        <section className="survey-question-section">
            <div className="survey-question-header">
                <span className="survey-count">
                    질문 {currentNumber} / {totalCount}
                </span>

                <h1>{question.questionText}</h1>

                <p>
                    가장 가까운 선택지를 골라주세요
                </p>
            </div>

            <div className="survey-question-navigation">
                <button
                    className="survey-question-nav-btn previous"
                    type="button"
                    onClick={onPrevious}
                    disabled={!canGoPrevious}
                    aria-label="이전 질문 보기"
                >
                    <ChevronLeft
                        size={32}
                        strokeWidth={2.3}
                    />
                </button>

                <div
                    className="survey-option-grid"
                    role="radiogroup"
                    aria-label={question.questionText}
                >
                    {options.map((option) => {
                        const isSelected =
                            selectedOptionId ===
                            option.optionId;

                        return (
                            <button
                                key={option.optionId}
                                className={
                                    isSelected
                                        ? 'survey-option-card selected'
                                        : 'survey-option-card'
                                }
                                type="button"
                                role="radio"
                                aria-checked={isSelected}
                                onClick={() =>
                                    onSelect(option.optionId)
                                }
                            >
                                {isSelected && (
                                    <span
                                        className="survey-selected-check"
                                        aria-hidden="true"
                                    >
                                    <Check
                                        size={14}
                                        strokeWidth={3}
                                    />
                                </span>
                                )}

                                <div className="survey-option-image">
                                    {option.imageUrl ? (
                                        <img
                                            src={option.imageUrl}
                                            alt={
                                                option.optionText
                                            }
                                        />
                                    ) : (
                                        <div
                                            className="survey-option-image-placeholder"
                                            aria-hidden="true"
                                        />
                                    )}
                                </div>

                                <div className="survey-option-content">
                                    <strong>
                                        {option.optionText}
                                    </strong>
                                </div>
                            </button>
                        );
                    })}
                </div>

                <button
                    className="survey-question-nav-btn next"
                    type="button"
                    onClick={onForward}
                    disabled={!canGoForward}
                    aria-label="다음 답변 질문 보기"
                >
                    <ChevronRight
                        size={32}
                        strokeWidth={2.3}
                    />
                </button>
            </div>

            <div className="survey-tip-box">
                <Lightbulb
                    size={21}
                    aria-hidden="true"
                />

                <div>
                    <strong>선택 팁</strong>

                    <p>
                        정답은 없어요! 당신의 솔직한
                        취향을 선택해주세요
                    </p>
                </div>
            </div>
        </section>
    );
}

export default SurveyQuestion;
