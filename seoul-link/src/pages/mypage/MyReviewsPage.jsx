import PagePlaceholder from '../../components/common/PagePlaceholder';

function MyReviewsPage() {
    return (
        <PagePlaceholder
            title="내 후기"
            description="내가 작성한 후기와 댓글 기록을 보여줄 자리입니다."
            links={[{ href: '/reviews/write', label: '후기 작성하기' }, { href: '/mypage', label: '마이페이지' }]}
        />
    );
}

export default MyReviewsPage;
