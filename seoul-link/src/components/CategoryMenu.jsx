// CategoryMenu는 hero 아래에 떠 있는 카테고리 바로가기 박스
// 지도, 궁궐, 자연, 데이트, 맛집, 카페 등 주요 기능/테마로 빠르게 이동하기 위한 메뉴

// 모든 SVG 아이콘에서 공통으로 사용할 속성
// width/height/viewBox를 통일하면 아이콘 크기와 선 굵기 느낌이 일정해짐
const iconProps = {
    width: 28,
    height: 28,
    viewBox: '0 0 32 32',
    fill: 'none',
    xmlns: 'http://www.w3.org/2000/svg',
};

// 궁궐/문화 카테고리 아이콘
function PalaceIcon() {
    return (
        <svg {...iconProps}>
            <path d="M5.5 14.2H26.5" />
            <path d="M7.8 14.2L10.1 9.1H21.9L24.2 14.2" />
            <path d="M11.2 9.1L16 5.8L20.8 9.1" />
            <path d="M8.2 18.2H23.8" />
            <path d="M10.5 18.2V25.4" />
            <path d="M16 18.2V25.4" />
            <path d="M21.5 18.2V25.4" />
            <path d="M7.2 25.4H24.8" />
        </svg>
    );
}

// 자연/한강 카테고리 아이콘
function NatureIcon() {
    return (
        <svg {...iconProps}>
            <path d="M16 5.5L10.4 13.2H13.8L9.2 19.9H14.2V25.8" />
            <path d="M16 5.5L21.6 13.2H18.2L22.8 19.9H17.8V25.8" />
            <path d="M11.5 25.8H20.5" />
        </svg>
    );
}

// 데이트 카테고리 아이콘
function DateIcon() {
    return (
        <svg {...iconProps}>
            <path d="M16 25.2C16 25.2 7.4 20.2 7.4 13.2C7.4 9.9 9.5 7.7 12.4 7.7C14.1 7.7 15.3 8.5 16 9.7C16.7 8.5 17.9 7.7 19.6 7.7C22.5 7.7 24.6 9.9 24.6 13.2C24.6 20.2 16 25.2 16 25.2Z" />
        </svg>
    );
}

// 맛집 탐방 카테고리 아이콘
function FoodIcon() {
    return (
        <svg {...iconProps}>
            <path d="M10 6.5V15" />
            <path d="M7.8 6.5V12.5C7.8 14 8.8 15 10 15C11.2 15 12.2 14 12.2 12.5V6.5" />
            <path d="M10 15V25.5" />
            <path d="M21.6 6.8C19.5 8.1 18.4 10.7 18.4 14.3V17.4H22.5" />
            <path d="M22.5 6.8V25.5" />
        </svg>
    );
}

// 카페 투어 카테고리 아이콘
function CafeIcon() {
    return (
        <svg {...iconProps}>
            <path d="M9.2 13.2H20.2V19.1C20.2 21.5 18.2 23.5 15.8 23.5H13.6C11.2 23.5 9.2 21.5 9.2 19.1V13.2Z" />
            <path d="M20.2 15.2H22.3C23.7 15.2 24.6 16.1 24.6 17.4C24.6 18.7 23.7 19.6 22.3 19.6H20.2" />
            <path d="M8.5 25.8H23.5" />
            <path d="M12.2 9.8C11.3 8.7 11.3 7.6 12.2 6.5" />
            <path d="M16 9.8C15.1 8.7 15.1 7.6 16 6.5" />
            <path d="M19.8 9.8C18.9 8.7 18.9 7.6 19.8 6.5" />
        </svg>
    );
}

// 쇼핑/핫플 카테고리 아이콘
function ShoppingIcon() {
    return (
        <svg {...iconProps}>
            <path d="M9.3 12.6H22.7L21.8 25.2H10.2L9.3 12.6Z" />
            <path d="M12.5 12.6V10.4C12.5 8.3 14 6.8 16 6.8C18 6.8 19.5 8.3 19.5 10.4V12.6" />
        </svg>
    );
}

// 야경 카테고리 아이콘
function NightIcon() {
    return (
        <svg {...iconProps}>
            <path d="M21.8 21.5C20.3 22.8 18.3 23.6 16.1 23.6C11.3 23.6 7.4 19.7 7.4 14.9C7.4 11.9 8.9 9.2 11.2 7.6C10.9 8.5 10.7 9.4 10.7 10.4C10.7 15.2 14.6 19.1 19.4 19.1C20.4 19.1 21.2 18.9 22 18.6C22.2 19.6 22.1 20.6 21.8 21.5Z" />
            <path d="M22.9 7.2V10.6" />
            <path d="M21.2 8.9H24.6" />
        </svg>
    );
}

// 지도 카테고리 아이콘
// 지도 코스 만들기 기능과 연결될 예정이라 첫 번째 메뉴로 따로 분리해서 강조
function MapIcon() {
    return (
        <svg {...iconProps}>
            <path d="M6.8 8.8L13.2 6.4L19 8.8L25.2 6.4V23.2L19 25.6L13.2 23.2L6.8 25.6V8.8Z" />
            <path d="M13.2 6.4V23.2" />
            <path d="M19 8.8V25.6" />
            <path d="M15.8 15.2C15.8 12.7 17.7 10.8 20.2 10.8C22.7 10.8 24.6 12.7 24.6 15.2C24.6 18.5 20.2 21.5 20.2 21.5C20.2 21.5 15.8 18.5 15.8 15.2Z" />
            <path d="M20.2 15.2H20.25" />
        </svg>
    );
}

// 숙소 카테고리 아이콘
function StayIcon() {
    return (
        <svg {...iconProps}>
            <path d="M7.5 11.2V24" />
            <path d="M24.5 15.2V24" />
            <path d="M7.5 16.1H24.5" />
            <path d="M10.2 12.7H15.2V16.1H10.2C9.3 16.1 8.6 15.4 8.6 14.6V14.3C8.6 13.4 9.3 12.7 10.2 12.7Z" />
            <path d="M15.2 12.7H21.8C23.3 12.7 24.5 13.9 24.5 15.4V16.1" />
            <path d="M6.5 24H25.5" />
        </svg>
    );
}

// 카테고리 데이터 배열
// Icon: 화면에 보여줄 SVG 컴포넌트
// title: 사용자에게 보이는 카테고리 이름
// type: CSS 클래스명과 key 값으로 사용하는 고유 문자열
const categories = [
    { Icon: MapIcon, title: '지도', type: 'map' },
    { Icon: PalaceIcon, title: '궁궐 · 문화', type: 'palace' },
    { Icon: NatureIcon, title: '자연 · 한강', type: 'nature' },
    { Icon: DateIcon, title: '데이트', type: 'date' },
    { Icon: FoodIcon, title: '맛집 탐방', type: 'food' },
    { Icon: CafeIcon, title: '카페 투어', type: 'cafe' },
    { Icon: ShoppingIcon, title: '쇼핑 · 핫플', type: 'shopping' },
    { Icon: NightIcon, title: '야경', type: 'night' },
    { Icon: StayIcon, title: '숙소', type: 'stay' },
];

function CategoryMenu() {
    // 첫 번째 지도 메뉴는 왼쪽에 따로 강조하고, 뒤에 구분선을 넣기 위해 분리
    const mapCategory = categories[0];

    // 지도 메뉴를 제외한 나머지 카테고리
    const restCategories = categories.slice(1);

    return (
        <section className="category-box" aria-label="여행 카테고리">
            {/*
                지도 카테고리
                category-map-item 클래스를 추가로 줘서 다른 카테고리와 조금 다르게 스타일링할 수 있음
            */}
            <button
                className="category-item category-map-item"
                type="button"
                aria-label={mapCategory.title}
            >
                <span className={`category-icon category-icon-${mapCategory.type}`}>
                    <mapCategory.Icon />
                </span>
                <span className="category-title">{mapCategory.title}</span>
            </button>

            {/* 지도 메뉴와 나머지 메뉴를 시각적으로 구분하는 세로선*/}
            <span className="category-divider" aria-hidden="true" />

            {/*
                나머지 카테고리를 반복 렌더링
                데이터만 추가하면 버튼이 자동으로 늘어나는 구조라 유지보수가 쉬움
            */}
            {restCategories.map(({ Icon, title, type }) => (
                <button className="category-item" type="button" key={type} aria-label={title}>
                    <span className={`category-icon category-icon-${type}`}>
                        <Icon />
                    </span>
                    <span className="category-title">{title}</span>
                </button>
            ))}
        </section>
    );
}

export default CategoryMenu;
