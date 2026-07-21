import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';

function SurveyResultPage() {
    return (
        <SurveyFlowLayout currentStep={3}>
            <section className="survey-stage-card">
                <p className="survey-flow-eyebrow">STEP 3</p>
                <h1>여행 유형 결과</h1>
                <p>검사 결과는 로그인 없이 확인할 수 있습니다. 맞춤 코스를 받거나 결과를 저장할 때는 로그인이 필요합니다.</p>

                <div className="survey-stage-actions">
                    <a className="survey-stage-secondary-btn" href="/survey">
                        다시 검사하기
                    </a>
                    <a className="survey-stage-primary-btn" href="/courses">
                        맞춤 추천 코스 받기
                    </a>
                </div>
            </section>
        </SurveyFlowLayout>
    );
}

export default SurveyResultPage;
