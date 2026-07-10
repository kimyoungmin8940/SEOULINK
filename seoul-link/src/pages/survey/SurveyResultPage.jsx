import PagePlaceholder from '../../components/common/PagePlaceholder';

function SurveyResultPage() {
    return (
        <PagePlaceholder
            title="여행 유형 결과"
            description="검사 결과는 로그인 없이 확인할 수 있습니다. 맞춤 추천 코스를 받거나 결과를 저장할 때는 로그인이 필요합니다."
            links={[{ href: '/courses', label: '맞춤 추천 코스 받기' }, { href: '/mypage/travel-type', label: '내 유형 보관함' }]}
        />
    );
}

export default SurveyResultPage;
