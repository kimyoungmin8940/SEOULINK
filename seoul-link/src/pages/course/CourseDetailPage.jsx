import PagePlaceholder from '../../components/common/PagePlaceholder';

function CourseDetailPage() {
    return (
        <PagePlaceholder
            title="코스 상세"
            description="코스에 포함된 관광지, 식당, 카페, 숙소를 순서대로 보여줄 자리입니다."
            links={[{ href: '/courses', label: '코스 목록으로' }]}
        />
    );
}

export default CourseDetailPage;
