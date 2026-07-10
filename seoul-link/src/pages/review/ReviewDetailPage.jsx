import PagePlaceholder from '../../components/common/PagePlaceholder';

function ReviewDetailPage() {
    return (
        <PagePlaceholder
            title="후기 상세"
            description="후기 본문, 이미지, 별점, 댓글을 보여줄 자리입니다."
            links={[{ href: '/reviews', label: '후기 목록으로' }]}
        />
    );
}

export default ReviewDetailPage;
