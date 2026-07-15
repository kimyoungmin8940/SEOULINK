// 사이트의 모든 콘텐츠 사진에 동일한 메인 색 오버레이를 적용하기 위한 SVG 필터입니다.
// 실제 색상과 투명도는 variables.css의 --photo-filter-color,
// --photo-filter-opacity 값만 바꾸면 전체 사진에 함께 반영됩니다.
function PhotoFilterDefinitions() {
    return (
        <svg
            className="photo-filter-definitions"
            aria-hidden="true"
            focusable="false"
        >
            <defs>
                <filter
                    id="seoulink-photo-filter"
                    x="0"
                    y="0"
                    width="100%"
                    height="100%"
                    colorInterpolationFilters="sRGB"
                >
                    {/* 메인 색을 원본 이미지의 불투명한 영역에만 10%로 겹침 */}
                    <feFlood className="photo-filter-color" result="photoTint" />
                    <feComposite
                        in="photoTint"
                        in2="SourceAlpha"
                        operator="in"
                        result="clippedPhotoTint"
                    />
                    <feMerge>
                        <feMergeNode in="SourceGraphic" />
                        <feMergeNode in="clippedPhotoTint" />
                    </feMerge>
                </filter>
            </defs>
        </svg>
    );
}

export default PhotoFilterDefinitions;
