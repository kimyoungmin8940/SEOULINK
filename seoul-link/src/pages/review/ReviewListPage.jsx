import PagePlaceholder from '../../components/common/PagePlaceholder';

function ReviewListPage() {
    return (
        <PagePlaceholder
            title="방문 후기 게시판"
            description="장소와 코스에 대한 사용자 후기를 목록으로 보여줄 자리입니다."
            links={[{ href: '/reviews/write', label: '후기 작성하기' }]}
        />
    );
}

export default ReviewListPage;
