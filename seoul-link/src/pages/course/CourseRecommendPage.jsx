import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';

function CourseRecommendPage() {
    return (
        <SurveyFlowLayout currentStep={4}>
            <section className="survey-stage-card">
                <p className="survey-flow-eyebrow">STEP 4</p>
                <h1>맞춤형 추천 코스</h1>
                <p>여행 정보와 취향 검사 결과, 장소 태그를 바탕으로 자동 추천 코스를 보여줄 자리입니다.</p>

                <div className="survey-stage-actions">
                    <a className="survey-stage-secondary-btn" href="/travel-info">
                        처음부터 다시하기
                    </a>
                    <a className="survey-stage-primary-btn" href="/courses/list">
                        전체 코스 보기
                    </a>
                </div>
            </section>
        </SurveyFlowLayout>
    );
}

export default CourseRecommendPage;
