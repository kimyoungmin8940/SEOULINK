import PagePlaceholder from '../../components/common/PagePlaceholder';

function SurveyPage() {
    return (
        <PagePlaceholder
            title="여행 취향 검사"
            description="5가지 기준으로 사용자의 여행 유형 코드를 만드는 질문 화면입니다."
            links={[{ href: '/survey/result', label: '검사 결과 화면 보기' }]}
        />
    );
}

export default SurveyPage;
