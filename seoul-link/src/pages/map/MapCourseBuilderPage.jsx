import PagePlaceholder from '../../components/common/PagePlaceholder';

const mapCategoryInfo = {
    'palace-culture': {
        title: '궁궐 · 문화 지도 코스 고르기',
        description: '궁궐, 전시, 문화 공간처럼 궁궐 · 문화 카테고리에 맞는 장소만 지도에 보여줄 자리입니다.',
    },
    'nature-hangang': {
        title: '자연 · 한강 지도 코스 고르기',
        description: '한강, 공원, 산책길처럼 자연 · 한강 카테고리에 맞는 장소만 지도에 보여줄 자리입니다.',
    },
    date: {
        title: '데이트 지도 코스 고르기',
        description: '데이트하기 좋은 장소만 지도에 보여주고, 원하는 장소를 골라 코스로 만들 수 있는 자리입니다.',
    },
    food: {
        title: '맛집 탐방 지도 코스 고르기',
        description: '맛집 탐방 카테고리에 맞는 식당과 먹거리 장소만 지도에 보여줄 자리입니다.',
    },
    cafe: {
        title: '카페 투어 지도 코스 고르기',
        description: '카페 투어 카테고리에 맞는 카페 장소만 지도에 보여줄 자리입니다.',
    },
    'shopping-hotplace': {
        title: '쇼핑 · 핫플 지도 코스 고르기',
        description: '쇼핑, 편집숍, 핫플레이스처럼 쇼핑 · 핫플 카테고리에 맞는 장소만 지도에 보여줄 자리입니다.',
    },
    'night-view': {
        title: '야경 지도 코스 고르기',
        description: '야경 명소와 밤 산책 장소처럼 야경 카테고리에 맞는 장소만 지도에 보여줄 자리입니다.',
    },
    stay: {
        title: '숙소 지도 코스 고르기',
        description: '숙소 카테고리에 맞는 호텔, 게스트하우스, 숙박 장소만 지도에 보여줄 자리입니다.',
    },
};

function MapCourseBuilderPage() {
    const searchParams = new URLSearchParams(window.location.search);
    const selectedCategory = searchParams.get('category');
    const categoryInfo = mapCategoryInfo[selectedCategory];

    if (categoryInfo) {
        return (
            <PagePlaceholder
                title={categoryInfo.title}
                description={categoryInfo.description}
                links={[
                    { href: '/map-course', label: '전체 지도 보기' },
                    { href: '/mypage/courses', label: '내 코스 보기' },
                ]}
            />
        );
    }

    return (
        <PagePlaceholder
            title="전체 지도 코스 고르기"
            description="궁궐 · 문화, 자연 · 한강, 데이트, 맛집, 카페, 쇼핑 · 핫플, 야경, 숙소 카테고리를 모두 포함해 지도에서 장소를 고르는 화면입니다."
            links={[{ href: '/mypage/courses', label: '내 코스 보기' }]}
        />
    );
}

export default MapCourseBuilderPage;
