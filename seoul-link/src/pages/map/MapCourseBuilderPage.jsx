import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import CourseBuilderPage from '../../features/courseBuilder/CourseBuilderPage';
import './MapCourseBuilderPage.css';

const THEME_BY_CATEGORY = {
    'palace-culture': 'PALACE_CULTURE',
    'nature-hangang': 'NATURE_HANGANG',
    date: 'DATE',
    food: 'FOOD_TOUR',
    cafe: 'CAFE_TOUR',
    'shopping-hotplace': 'SHOPPING_HOTPLACE',
    'night-view': 'NIGHT_VIEW',
    stay: 'HOTEL_STAY',
};

function MapCourseBuilderPage() {
    const searchParams = new URLSearchParams(window.location.search);
    const selectedCategory = searchParams.get('category');
    const initialTheme = THEME_BY_CATEGORY[selectedCategory] ?? 'ALL';

    return (
        <div className="map-course-page">
            <Header variant="simple" />

            <main className="map-course-content">
                <CourseBuilderPage initialTheme={initialTheme} />
            </main>

            <Footer />
        </div>
    );
}

export default MapCourseBuilderPage;
