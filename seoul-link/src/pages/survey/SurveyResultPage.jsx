import PagePlaceholder from '../../components/common/PagePlaceholder';

function SurveyResultPage() {
    return (
        <PagePlaceholder
            title="여행 유형 결과"
            description="ATBSP, HMLDR 같은 5글자 여행 유형 코드와 해석을 보여줄 자리입니다."
            links={[{ href: '/courses', label: '추천 코스 보기' }, { href: '/mypage/travel-type', label: '내 유형 보관함' }]}
        />
    );
}

export default SurveyResultPage;
