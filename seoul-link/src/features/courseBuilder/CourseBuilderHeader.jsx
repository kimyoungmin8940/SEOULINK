function CourseBuilderHeader({ mapStatus }) {
    return (
        <header className="course-builder-header">
            <h1>지도 기반 직접 코스 만들기</h1>
            <p>{mapStatus}</p>
        </header>
    );
}

export default CourseBuilderHeader;
