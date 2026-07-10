import PagePlaceholder from '../../components/common/PagePlaceholder';

function MyPage() {
    return (
        <PagePlaceholder
            title="마이페이지"
            description="내 여행 유형, 저장한 코스, 찜, 후기 기록을 모아보는 공간입니다."
            links={[{ href: '/mypage/travel-type', label: '내 여행 유형' }, { href: '/mypage/courses', label: '내 코스' }, { href: '/mypage/favorites', label: '찜 목록' }, { href: '/mypage/reviews', label: '내 후기' }]}
        />
    );
}

export default MyPage;
