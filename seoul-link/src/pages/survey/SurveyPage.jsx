import PagePlaceholder from '../../components/common/PagePlaceholder';

function SurveyPage() {
    return (
        <PagePlaceholder
            title="여행 취향 검사"
            description="로그인 없이 5가지 기준의 질문에 답하고 나의 여행 유형을 확인할 수 있습니다."
            links={[{ href: '/survey/result', label: '검사 결과 화면 보기' }]}
        />
    );
}

export default SurveyPage;
