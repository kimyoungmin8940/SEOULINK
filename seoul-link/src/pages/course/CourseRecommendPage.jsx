import PagePlaceholder from '../../components/common/PagePlaceholder';

function CourseRecommendPage() {
    return (
        <PagePlaceholder
            title="맞춤형 추천 코스"
            description="취향 검사 결과와 장소 태그를 바탕으로 자동 추천 코스를 보여줄 자리입니다."
            links={[{ href: '/survey', label: '취향 검사하기' }, { href: '/courses/list', label: '전체 코스 보기' }]}
        />
    );
}

export default CourseRecommendPage;
