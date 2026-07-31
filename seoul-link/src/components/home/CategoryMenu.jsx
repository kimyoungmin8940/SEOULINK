// CategoryMenu는 hero 아래에 떠 있는 지도 코스 카테고리 바로가기 박스
// 첫 번째 지도 버튼은 모든 카테고리를 포함한 지도 코스 고르기,
// 나머지 8개 버튼은 해당 카테고리만 지도에 띄우는 지도 코스 고르기와 연결됩니다.

import { handleProtectedLinkClick } from '../../utils/authGuard';

// 모든 카테고리 아이콘에서 공통으로 사용할 속성
// 선을 그리지 않고 색면과 부드러운 명암만 사용해 2.5D 느낌을 냅니다.
const iconProps = {
    viewBox: '0 0 64 64',
    fill: 'none',
    xmlns: 'http://www.w3.org/2000/svg',
    'aria-hidden': true,
    focusable: false,
};

// 궁궐/문화 카테고리 아이콘
export function PalaceIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="palace-roof" x1="14" y1="14" x2="49" y2="32" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#23B9A9" />
                    <stop offset="1" stopColor="#168A90" />
                </linearGradient>
                <linearGradient id="palace-body" x1="18" y1="31" x2="44" y2="52" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#FFD574" />
                    <stop offset="1" stopColor="#F2A840" />
                </linearGradient>
            </defs>
            <path d="M9 27.5C17.6 25.3 24.6 20 30 14.2C31.1 13 32.9 13 34 14.2C39.4 20 46.4 25.3 55 27.5L51.8 33H12.2L9 27.5Z" fill="url(#palace-roof)" />
            <path d="M14 31H50L46.8 35.8H17.2L14 31Z" fill="#126F79" opacity="0.72" />
            <rect x="18" y="35" width="28" height="17" rx="4" fill="url(#palace-body)" />
            <rect x="22" y="37" width="5" height="15" rx="2.5" fill="#E5624D" />
            <rect x="37" y="37" width="5" height="15" rx="2.5" fill="#E5624D" />
            <rect x="29" y="40" width="6" height="12" rx="3" fill="#2D7186" />
            <rect x="13" y="50" width="38" height="6" rx="3" fill="#D98A38" />
            <ellipse cx="24" cy="20" rx="5" ry="2" fill="#7EE0CE" opacity="0.72" transform="rotate(-21 24 20)" />
        </svg>
    );
}

// 자연/한강 카테고리 아이콘
export function NatureIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="nature-leaf" x1="16" y1="10" x2="42" y2="43" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#76DA84" />
                    <stop offset="1" stopColor="#24A866" />
                </linearGradient>
                <linearGradient id="nature-water" x1="12" y1="43" x2="53" y2="55" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#70CEFA" />
                    <stop offset="1" stopColor="#3289ED" />
                </linearGradient>
            </defs>
            <rect x="28" y="31" width="8" height="18" rx="4" fill="#A96B42" />
            <circle cx="24" cy="27" r="12" fill="url(#nature-leaf)" />
            <circle cx="38" cy="25" r="14" fill="url(#nature-leaf)" />
            <circle cx="32" cy="16" r="11" fill="#8CE493" />
            <ellipse cx="26" cy="14" rx="5.5" ry="3.2" fill="#C5F3B7" opacity="0.78" transform="rotate(-28 26 14)" />
            <path d="M7 46.5C14.5 40.6 22.4 40.6 30.5 46.5C38.7 52.5 46.8 52.5 57 44V54C47 61.1 38.6 60.9 30.5 55.1C22.3 49.2 14.4 49.1 7 55V46.5Z" fill="url(#nature-water)" />
        </svg>
    );
}

// 데이트 카테고리 아이콘
export function DateIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="date-heart" x1="15" y1="15" x2="49" y2="52" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#FF7E92" />
                    <stop offset="1" stopColor="#E93F62" />
                </linearGradient>
            </defs>
            <path d="M32 55C28.8 51.6 12 41.2 12 26.7C12 17.6 18 12 25.3 12C29 12 32.1 13.8 34 16.8C36 13.8 39.1 12 42.8 12C50 12 56 17.6 56 26.7C56 41.2 39.2 51.6 36 55C34.9 56.2 33.1 56.2 32 55Z" fill="#C92F54" opacity="0.3" transform="translate(-2 2)" />
            <path d="M30 53C26.8 49.6 10 39.2 10 24.7C10 15.6 16 10 23.3 10C27 10 30.1 11.8 32 14.8C34 11.8 37.1 10 40.8 10C48 10 54 15.6 54 24.7C54 39.2 37.2 49.6 34 53C32.9 54.2 31.1 54.2 30 53Z" fill="url(#date-heart)" />
            <ellipse cx="21" cy="19" rx="6.8" ry="3.8" fill="#FFCED5" opacity="0.7" transform="rotate(-37 21 19)" />
        </svg>
    );
}

// 맛집 탐방 카테고리 아이콘
export function FoodIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="food-bowl" x1="14" y1="30" x2="49" y2="54" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#FF8D54" />
                    <stop offset="1" stopColor="#E94B42" />
                </linearGradient>
                <linearGradient id="food-soup" x1="14" y1="22" x2="51" y2="36" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#FFE58A" />
                    <stop offset="1" stopColor="#F7B844" />
                </linearGradient>
            </defs>
            <rect x="47" y="8" width="5" height="35" rx="2.5" fill="#6B4F43" transform="rotate(8 47 8)" />
            <rect x="39" y="7" width="5" height="35" rx="2.5" fill="#8B6858" transform="rotate(8 39 7)" />
            <ellipse cx="31" cy="31" rx="23" ry="10" fill="#D9443E" opacity="0.32" />
            <ellipse cx="31" cy="28" rx="23" ry="10" fill="url(#food-soup)" />
            <circle cx="23" cy="27" r="4" fill="#FFF4C9" />
            <circle cx="35" cy="25" r="4.5" fill="#F36C52" />
            <path d="M8 29H54C52.8 44.4 43.8 54 31 54C18.2 54 9.2 44.4 8 29Z" fill="url(#food-bowl)" />
            <path d="M14 34C20.5 38.5 39.2 41.3 49 34.7C46.6 44.4 39.9 49.8 31 49.8C22.3 49.8 16.2 44.5 14 34Z" fill="#FFAC67" opacity="0.48" />
            <rect x="21" y="53" width="20" height="4" rx="2" fill="#D8403D" />
        </svg>
    );
}

// 카페 투어 카테고리 아이콘
export function CafeIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="cafe-cup" x1="13" y1="24" x2="45" y2="50" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#A77663" />
                    <stop offset="1" stopColor="#704C48" />
                </linearGradient>
            </defs>
            <rect x="20" y="5" width="5" height="15" rx="2.5" fill="#D6E4ED" transform="rotate(-13 20 5)" />
            <rect x="33" y="5" width="5" height="15" rx="2.5" fill="#E5EEF3" transform="rotate(9 33 5)" />
            <path d="M46 27H50C56.1 27 59 31 59 36C59 41.2 55.7 45 50 45H45V39H50C52.3 39 53.5 37.7 53.5 36C53.5 34.2 52.4 33 50 33H46V27Z" fill="#8A5C53" />
            <path d="M9 22H48V35.5C48 47.1 40.4 54 29 54C17.6 54 10 47.1 10 35.5L9 22Z" fill="url(#cafe-cup)" />
            <ellipse cx="28.5" cy="22.5" rx="19.5" ry="7.5" fill="#4D3434" />
            <ellipse cx="24" cy="19.5" rx="9" ry="4.5" fill="#F3D9BC" opacity="0.85" />
            <path d="M15 31C19.8 39.5 31.5 45 43.5 40.7C41.5 47.2 36.4 50.5 29 50.5C20.5 50.5 15.8 44.3 15 31Z" fill="#C9977C" opacity="0.35" />
            <rect x="7" y="53" width="47" height="5" rx="2.5" fill="#C7D6E0" />
        </svg>
    );
}

// 쇼핑/핫플 카테고리 아이콘
export function ShoppingIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="shopping-bag" x1="12" y1="20" x2="52" y2="55" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#8C7BF6" />
                    <stop offset="1" stopColor="#5A52D8" />
                </linearGradient>
            </defs>
            <path fillRule="evenodd" d="M20 23V19C20 12.4 25.4 7 32 7C38.6 7 44 12.4 44 19V23H39V19C39 15.1 35.9 12 32 12C28.1 12 25 15.1 25 19V23H20Z" fill="#564EBD" />
            <path d="M11 21H53L50 54C49.8 56.3 47.9 58 45.6 58H18.4C16.1 58 14.2 56.3 14 54L11 21Z" fill="url(#shopping-bag)" />
            <path d="M16 26H48L46.7 39.5C38.8 44.3 25.4 43.3 17.3 36L16 26Z" fill="#A99CFF" opacity="0.45" />
            <path d="M32 29L34.6 34.2L40.3 35L36.2 39L37.2 44.7L32 42L26.8 44.7L27.8 39L23.7 35L29.4 34.2L32 29Z" fill="#FFD365" />
            <ellipse cx="20" cy="25" rx="5" ry="2.4" fill="#D5D0FF" opacity="0.7" transform="rotate(-18 20 25)" />
        </svg>
    );
}

// 야경 카테고리 아이콘
export function NightIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="night-moon" x1="11" y1="8" x2="47" y2="53" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#FFD66D" />
                    <stop offset="1" stopColor="#F4A93C" />
                </linearGradient>
            </defs>
            <path d="M45.9 45.9C41.8 50 36.2 52.5 30 52.5C17.6 52.5 7.5 42.4 7.5 30C7.5 19.7 14.4 11 23.9 8.3C20.6 12.2 18.6 17.3 18.6 22.8C18.6 35.2 28.7 45.3 41.1 45.3C42.8 45.3 44.4 45.1 45.9 44.8V45.9Z" fill="url(#night-moon)" />
            <path d="M47 9L49.4 14.2L55 16.1L49.9 18.8L48.2 24.4L45.5 19.2L40 17.6L45.1 14.9L47 9Z" fill="#6E8FF7" />
            <path d="M54 28L55.4 31L58.6 32.1L55.7 33.6L54.8 36.8L53.2 33.9L50 33L52.9 31.4L54 28Z" fill="#A9BFFF" />
            <ellipse cx="17" cy="20" rx="5.5" ry="3" fill="#FFF0B2" opacity="0.7" transform="rotate(-43 17 20)" />
        </svg>
    );
}

// 지도 카테고리 아이콘
// 전체 카테고리를 포함한 지도 코스 고르기 기능과 연결됨
export function MapCategoryIcon({ className, ...svgProps }) {
    return (
        <svg {...iconProps} className={className} {...svgProps}>
            <defs>
                <linearGradient id="map-left" x1="8" y1="11" x2="25" y2="55" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#75D8F2" />
                    <stop offset="1" stopColor="#2A9AD8" />
                </linearGradient>
                <linearGradient id="map-center" x1="24" y1="8" x2="41" y2="54" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#A5E8D1" />
                    <stop offset="1" stopColor="#51BE98" />
                </linearGradient>
            </defs>
            <path d="M7 15.3C7 13.7 8 12.3 9.5 11.8L24 7V50.5L10.8 56.4C9 57.2 7 55.9 7 53.9V15.3Z" fill="url(#map-left)" />
            <path d="M24 7L41 13.5V57L24 50.5V7Z" fill="url(#map-center)" />
            <path d="M41 13.5L54.1 8.1C56 7.3 58 8.7 58 10.7V49.1C58 50.7 57 52.2 55.5 52.8L41 57V13.5Z" fill="#5D83EB" />
            <path d="M39 25.5C39 19 43.9 14.5 50 14.5C56.1 14.5 61 19 61 25.5C61 33.2 53.5 39.2 51.1 41C50.4 41.5 49.6 41.5 48.9 41C46.5 39.2 39 33.2 39 25.5Z" fill="#F25168" />
            <circle cx="50" cy="25" r="4.5" fill="#FFF5F3" />
            <ellipse cx="14" cy="17" rx="5.5" ry="2.6" fill="#C9F4FC" opacity="0.7" transform="rotate(-28 14 17)" />
        </svg>
    );
}

// 숙소 카테고리 아이콘
export function StayIcon() {
    return (
        <svg {...iconProps}>
            <defs>
                <linearGradient id="stay-blanket" x1="20" y1="27" x2="54" y2="48" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#68C8F0" />
                    <stop offset="1" stopColor="#3F77DF" />
                </linearGradient>
            </defs>
            <rect x="7" y="13" width="9" height="42" rx="4.5" fill="#4E6578" />
            <rect x="12" y="26" width="42" height="25" rx="7" fill="#DDE8EF" />
            <rect x="16" y="23" width="16" height="14" rx="6" fill="#FFF4D8" />
            <path d="M28 26H49C54 26 58 30 58 35V49H28V26Z" fill="url(#stay-blanket)" />
            <path d="M31 29H49C51.8 29 54 31.2 54 34V39C46.8 41.3 38.2 38.9 31 34V29Z" fill="#9EDCF5" opacity="0.52" />
            <rect x="8" y="48" width="51" height="8" rx="4" fill="#40566C" />
            <rect x="11" y="54" width="6" height="5" rx="2.5" fill="#33485C" />
            <rect x="50" y="54" width="6" height="5" rx="2.5" fill="#33485C" />
        </svg>
    );
}

// 카테고리 데이터 배열
// Icon: 화면에 보여줄 SVG 컴포넌트
// title: 사용자에게 보이는 카테고리 이름
// type: CSS 클래스명과 key 값으로 사용하는 고유 문자열
// href: 지도 코스 고르기 페이지로 이동하면서, category 쿼리로 필터를 전달
// requiresLogin: 지도 코스 고르기는 개인 기능이므로 전부 로그인 필요
const categories = [
    { Icon: MapCategoryIcon, title: '지도', type: 'map', href: '/map-course', requiresLogin: true },
    { Icon: PalaceIcon, title: '궁궐 · 문화', type: 'palace', href: '/map-course?category=palace-culture', requiresLogin: true },
    { Icon: NatureIcon, title: '자연 · 한강', type: 'nature', href: '/map-course?category=nature-hangang', requiresLogin: true },
    { Icon: DateIcon, title: '데이트', type: 'date', href: '/map-course?category=date', requiresLogin: true },
    { Icon: FoodIcon, title: '맛집 탐방', type: 'food', href: '/map-course?category=food', requiresLogin: true },
    { Icon: CafeIcon, title: '카페 투어', type: 'cafe', href: '/map-course?category=cafe', requiresLogin: true },
    { Icon: ShoppingIcon, title: '쇼핑 · 핫플', type: 'shopping', href: '/map-course?category=shopping-hotplace', requiresLogin: true },
    { Icon: NightIcon, title: '야경', type: 'night', href: '/map-course?category=night-view', requiresLogin: true },
    { Icon: StayIcon, title: '숙소', type: 'stay', href: '/map-course?category=stay', requiresLogin: true },
];

function CategoryMenu() {
    // 첫 번째 지도 메뉴는 왼쪽에 따로 강조하고, 뒤에 구분선을 넣기 위해 분리
    const mapCategory = categories[0];

    // 지도 메뉴를 제외한 나머지 카테고리
    const restCategories = categories.slice(1);

    return (
        <section className="category-box" aria-label="지도 코스 카테고리">
            {/*
                지도 카테고리
                전체 카테고리를 포함한 지도 코스 고르기로 이동
            */}
            <a
                className="category-item category-map-item"
                href={mapCategory.href}
                aria-label="전체 카테고리 지도 코스 고르기"
                onClick={(event) => {
                    if (mapCategory.requiresLogin) {
                        handleProtectedLinkClick(event, '지도 코스 고르기는 로그인 후 이용할 수 있습니다.');
                    }
                }}
            >
                <span className={`category-icon category-icon-${mapCategory.type}`}>
                    <mapCategory.Icon />
                </span>
                <span className="category-title">{mapCategory.title}</span>
            </a>

            {/* 지도 메뉴와 나머지 메뉴를 시각적으로 구분하는 세로선 */}
            <span className="category-divider" aria-hidden="true" />

            {/*
                나머지 카테고리를 반복 렌더링
                각 카테고리에 맞는 장소만 지도에 띄우는 지도 코스 고르기로 이동
            */}
            {restCategories.map(({ Icon, title, type, href, requiresLogin }) => (
                <a
                    className="category-item"
                    href={href}
                    key={type}
                    aria-label={`${title} 지도 코스 고르기`}
                    onClick={(event) => {
                        if (requiresLogin) {
                            handleProtectedLinkClick(event, '지도 코스 고르기는 로그인 후 이용할 수 있습니다.');
                        }
                    }}
                >
                    <span className={`category-icon category-icon-${type}`}>
                        <Icon />
                    </span>
                    <span className="category-title">{title}</span>
                </a>
            ))}
        </section>
    );
}

export default CategoryMenu;
