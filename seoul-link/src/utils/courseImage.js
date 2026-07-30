const INVALID_IMAGE_VALUES = new Set([
    '',
    'null',
    'undefined',
    'n/a',
    'none',
]);

/** 빈 값과 문자열 null을 제거하고 공공데이터 HTTP 이미지는 HTTPS로 통일합니다. */
export function normalizeCourseImageUrl(value) {
    if (typeof value !== 'string') return null;

    const normalized = value.trim();
    if (INVALID_IMAGE_VALUES.has(normalized.toLowerCase())) return null;

    return /^http:\/\//i.test(normalized)
        ? `https://${normalized.slice(7)}`
        : normalized;
}

/** 이미지 후보 배열의 순서를 유지하면서 빈 값과 중복을 제거합니다. */
export function normalizeCourseImageUrls(values) {
    const source = Array.isArray(values) ? values : [values];
    return [...new Set(source
        .flat()
        .map(normalizeCourseImageUrl)
        .filter(Boolean))];
}

/**
 * 목록과 상세 대표 사진이 같은 실제 장소 사진 후보를 같은 순서로 사용하게 합니다.
 */
export function getCourseCoverImageUrls(course) {
    return normalizeCourseImageUrls([
        course?.coverImageUrls,
        course?.imageUrls,
        course?.placeImageUrls,
        course?.coverImageUrl,
        course?.imageUrl,
        course?.thumbnailUrl,
    ]);
}

/** 장소 카드에서 사용할 실제 장소 사진 한 장만 정규화합니다. */
export function getPlaceImageUrl(place) {
    return normalizeCourseImageUrls([
        place?.imageUrl,
        place?.placeImageUrl,
        place?.thumbnailUrl,
    ])[0] || null;
}
