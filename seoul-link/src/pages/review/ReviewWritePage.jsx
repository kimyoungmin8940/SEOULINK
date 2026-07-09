import PagePlaceholder from '../../components/common/PagePlaceholder';

function ReviewWritePage() {
    return (
        <PagePlaceholder
            title="후기 작성"
            description="방문 장소, 평점, 이미지, 후기 내용을 입력하는 화면입니다."
            links={[{ href: '/reviews', label: '후기 목록으로' }]}
        />
    );
}

export default ReviewWritePage;
