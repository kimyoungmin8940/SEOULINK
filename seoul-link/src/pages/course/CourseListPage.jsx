import PagePlaceholder from '../../components/common/PagePlaceholder';

const themeInfo = {
    sunset: {
        title: '노을이 예쁜 서울 테마 코스',
        description:
            '노을 명소와 산책하기 좋은 장소를 중심으로 미리 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'rainy-cafe': {
        title: '비 오는 날의 카페 테마 코스',
        description:
            '비 오는 날 가기 좋은 실내 공간, 감성 카페, 조용한 동선을 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'walking-alley': {
        title: '혼자 걷기 좋은 골목 테마 코스',
        description:
            '혼자 천천히 걷기 좋은 골목, 산책길, 감성 장소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'night-date': {
        title: '데이트하기 좋은 밤 테마 코스',
        description:
            '야경, 분위기 좋은 식당, 밤 산책 장소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'hanok-photo': {
        title: '사진 찍기 좋은 한옥길 테마 코스',
        description:
            '한옥 거리, 전통 분위기, 사진 명소를 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
    'local-food': {
        title: '로컬처럼 먹는 하루 테마 코스',
        description:
            '서울의 로컬 맛집, 시장, 동네 식당을 중심으로 구성된 테마별 추천 코스 목록을 보여줄 자리입니다.',
    },
};

function CourseListPage() {
    const pathname = window.location.pathname;

    // 무드 섹션의 전체 보기
    // /courses/themes
    if (pathname === '/courses/themes') {
        return (
            <PagePlaceholder
                title="테마별 추천 코스 전체보기"
                description="노을, 비 오는 날의 카페, 골목 산책, 야간 데이트, 한옥길, 로컬 맛집처럼 미리 만들어진 모든 테마 코스를 모아 보여줄 자리입니다."
                links={[{ href: '/', label: '메인으로 돌아가기' }]}
            />
        );
    }

    // 무드 카드 클릭
    // /courses/themes/sunset
    const themeCode = pathname.split('/').pop();
    const currentTheme = themeInfo[themeCode];

    if (currentTheme) {
        return (
            <PagePlaceholder
                title={currentTheme.title}
                description={currentTheme.description}
                links={[
                    { href: '/courses/themes', label: '테마 전체보기' },
                    { href: '/', label: '메인으로 돌아가기' },
                ]}
            />
        );
    }

    // 기존 /courses/list용
    return (
        <PagePlaceholder
            title="전체 코스 목록"
            description="추천 코스, 인기 코스, 저장 가능한 코스 목록을 보여줄 자리입니다."
            links={[
                { href: '/courses/recommendations', label: '추천받은 코스 보기' },
                { href: '/courses/themes', label: '테마 코스 보기' },
            ]}
        />
    );
}

export default CourseListPage;