import PagePlaceholder from '../../components/common/PagePlaceholder';

function MyCoursesPage() {
    return (
        <PagePlaceholder
            title="내 코스"
            description="자동 추천받고 저장한 코스와 직접 만든 코스를 보여줄 자리입니다."
            links={[{ href: '/map-course', label: '직접 코스 만들기' }, { href: '/mypage', label: '마이페이지' }]}
        />
    );
}

export default MyCoursesPage;
