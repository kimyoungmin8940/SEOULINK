import PagePlaceholder from '../../components/common/PagePlaceholder';

function ReviewEditPage() {
    return (
        <PagePlaceholder
            title="후기 수정"
            description="내가 작성한 후기를 수정하는 화면입니다."
            links={[{ href: '/reviews', label: '후기 목록으로' }]}
        />
    );
}

export default ReviewEditPage;
