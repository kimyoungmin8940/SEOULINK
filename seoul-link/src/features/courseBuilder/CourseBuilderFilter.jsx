import { CalendarDays, ChevronDown, MapPin } from "lucide-react";
import { SEOUL_REGIONS } from "./courseBuilderConstants";
import { COURSE_THEMES, DEFAULT_FOOD_SUBCATEGORY, FOOD_SUBCATEGORIES } from "./courseThemes";
import {
    CafeIcon,
    DateIcon,
    FoodIcon,
    NatureIcon,
    NightIcon,
    PalaceIcon,
    ShoppingIcon,
    StayIcon,
} from "./CourseCategoryIcons";

const COURSE_CATEGORY_THEMES = COURSE_THEMES;
const COURSE_CATEGORY_ICONS = {
    PALACE_CULTURE: PalaceIcon,
    NATURE_HANGANG: NatureIcon,
    DATE: DateIcon,
    FOOD_TOUR: FoodIcon,
    CAFE_TOUR: CafeIcon,
    SHOPPING_HOTPLACE: ShoppingIcon,
    NIGHT_VIEW: NightIcon,
    HOTEL_STAY: StayIcon,
};
const COURSE_CATEGORY_TYPES = {
    PALACE_CULTURE: "palace",
    NATURE_HANGANG: "nature",
    DATE: "date",
    FOOD_TOUR: "food",
    CAFE_TOUR: "cafe",
    SHOPPING_HOTPLACE: "shopping",
    NIGHT_VIEW: "night",
    HOTEL_STAY: "stay",
};
const COURSE_CATEGORY_LABELS = {
    PALACE_CULTURE: "궁궐 · 문화",
    NATURE_HANGANG: "자연 · 한강",
    DATE: "데이트",
    FOOD_TOUR: "맛집 탐방",
    CAFE_TOUR: "카페 투어",
    SHOPPING_HOTPLACE: "쇼핑 · 핫플",
    NIGHT_VIEW: "야경",
    HOTEL_STAY: "숙소",
};

const TRIP_DAY_OPTIONS = Array.from({ length: 7 }, (_, index) => index + 1);

function CourseBuilderFilter({
    region,
    activeTheme,
    activeFoodSubcategory,
    tripDayCount,
    isLoadingPlaces,
    onRegionChange,
    onThemeClick,
    onFoodSubcategoryClick,
    onTripDayCountChange,
}) {
    return (
        <aside className="course-builder-filter" aria-label="여행 조건 선택">
            <div className="course-builder-filter-heading">
                <strong>어떤 여행인가요?</strong>
            </div>

            <div className="course-builder-region-group">
                <span className="course-builder-field-label">여행 지역</span>
                <div className="course-builder-region-select">
                    <MapPin size={17} aria-hidden="true" />
                    <select value={region} onChange={onRegionChange} aria-label="여행 지역">
                        {SEOUL_REGIONS.map((regionName) => (
                            <option key={regionName} value={regionName}>
                                {regionName === "서울" ? "서울 전체" : regionName}
                            </option>
                        ))}
                    </select>
                    <ChevronDown size={16} aria-hidden="true" />
                </div>
            </div>

            <div className="course-builder-trip-days-section">
                <span className="course-builder-field-label">여행 일수</span>
                <div className="course-builder-trip-days-select">
                    <CalendarDays size={17} aria-hidden="true" />
                    <select
                        value={tripDayCount}
                        onChange={onTripDayCountChange}
                        aria-label="여행 일수"
                    >
                        {TRIP_DAY_OPTIONS.map((dayCount) => (
                            <option key={dayCount} value={dayCount}>
                                {dayCount}일
                            </option>
                        ))}
                    </select>
                    <ChevronDown size={16} aria-hidden="true" />
                </div>
            </div>

            <div className="course-builder-category-section">
                <span className="course-builder-field-label">카테고리 선택</span>
                <div className="course-builder-category-buttons">
                    {COURSE_CATEGORY_THEMES.map((theme) => {
                        const Icon = COURSE_CATEGORY_ICONS[theme.value];
                        const categoryType = COURSE_CATEGORY_TYPES[theme.value];
                        const isActive = activeTheme === theme.value;

                        return (
                            <button
                                key={theme.value}
                                type="button"
                                className={
                                    `${isActive ? "course-builder-category-button active" : "course-builder-category-button"}`
                                    + `${theme.value === "ALL" ? " course-builder-category-button-all" : ""}`
                                }
                                onClick={() => onThemeClick(theme.value)}
                                aria-pressed={isActive}
                            >
                                {Icon && (
                                    <span className={`course-builder-category-icon course-builder-category-icon-${categoryType}`}>
                                        <Icon />
                                    </span>
                                )}
                                <span>
                                    {theme.value === "ALL" ? "전체" : COURSE_CATEGORY_LABELS[theme.value] || theme.label}
                                </span>
                            </button>
                        );
                    })}
                </div>
            </div>

            {activeTheme === "FOOD_TOUR" && (
                <div className="course-builder-food-subcategory-buttons" aria-label="맛집 세부 카테고리">
                    {FOOD_SUBCATEGORIES.map((subcategory) => (
                        <button
                            key={subcategory.value}
                            type="button"
                            className={
                                activeFoodSubcategory === subcategory.value
                                    ? "course-builder-food-subcategory-button active"
                                    : "course-builder-food-subcategory-button"
                            }
                            onClick={() => onFoodSubcategoryClick(subcategory.value)}
                        >
                            {subcategory.label}
                        </button>
                    ))}
                </div>
            )}

            {isLoadingPlaces && (
                <p className="course-builder-loading" role="status">장소를 불러오는 중입니다...</p>
            )}
        </aside>
    );
}

CourseBuilderFilter.defaultProps = {
    activeFoodSubcategory: DEFAULT_FOOD_SUBCATEGORY,
    tripDayCount: 1,
};

export default CourseBuilderFilter;
