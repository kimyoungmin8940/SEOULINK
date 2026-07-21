import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';

function SurveyPage() {
    return (
        <SurveyFlowLayout currentStep={2}>
            <section className="survey-stage-card">
                <p className="survey-flow-eyebrow">STEP 2</p>
                <h1>여행 취향 검사</h1>
                <p>로그인 없이 10개의 질문에 답하고 나의 여행 유형을 확인할 수 있습니다.</p>

                <div className="survey-stage-actions">
                    <a className="survey-stage-secondary-btn" href="/travel-info">
                        여행 정보 수정
                    </a>
                    <a className="survey-stage-primary-btn" href="/survey/result">
                        검사 결과 화면 보기
                    </a>
                </div>
            </section>
        </SurveyFlowLayout>
    );
}

export default SurveyPage;
