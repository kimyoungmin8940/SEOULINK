import PagePlaceholder from '../../components/common/PagePlaceholder';

function MyTravelTypePage() {
    return (
        <PagePlaceholder
            title="내 여행 유형"
            description="사용자의 5글자 여행 유형 코드와 해석을 보여줄 자리입니다."
            links={[{ href: '/survey', label: '다시 검사하기' }, { href: '/mypage', label: '마이페이지' }]}
        />
    );
}

export default MyTravelTypePage;
