import PagePlaceholder from '../../components/common/PagePlaceholder';

function MyFavoritesPage() {
    return (
        <PagePlaceholder
            title="찜 목록"
            description="찜한 관광지, 식당, 카페, 숙소, 코스를 보여줄 자리입니다."
            links={[{ href: '/mypage', label: '마이페이지' }]}
        />
    );
}

export default MyFavoritesPage;
