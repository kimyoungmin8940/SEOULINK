import hanokImage from '../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../assets/images/moods/mood-walking-alley.png';
import localFoodImage from '../assets/images/moods/mood-local-food.png';
import rainyCafeImage from '../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../assets/images/moods/mood-sunset-seoul.png';

const INVALID_IMAGE_VALUES = new Set([
    '',
    'null',
    'undefined',
    'n/a',
    'none',
]);

const neutralCourseFallbackImages = [
    hanokImage,
    walkingImage,
    sunsetImage,
];

const courseFallbackImagesByCategory = {
    TOUR: neutralCourseFallbackImages,
    RESTAURANT: [localFoodImage],
    CAFE: [rainyCafeImage],
};

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

/** 상세 화면과 같은 기준으로 장소 카테고리를 정규화합니다. */
export function normalizeCourseImageCategory(category) {
    const normalized = String(category || '').trim().toUpperCase();

    if (['RESTAURANT', '식당', '음식점', '맛집', 'FOOD'].includes(normalized)) {
        return 'RESTAURANT';
    }

    if (['CAFE', 'CAFÉ', '카페', 'COFFEE'].includes(normalized)) {
        return 'CAFE';
    }

    if (['HOTEL', '숙소', '호텔', 'ACCOMMODATION', 'LODGING'].includes(normalized)) {
        return 'HOTEL';
    }

    if (['TOUR', '관광', '관광지', 'ATTRACTION', 'SIGHTSEEING'].includes(normalized)) {
        return 'TOUR';
    }

    return normalized || null;
}

/** 동일한 코스·장소는 화면을 다시 열어도 같은 예시 사진을 사용합니다. */
export function pickStableCourseFallbackImage(images, seed) {
    if (!Array.isArray(images) || images.length === 0) return null;

    const normalizedSeed = String(seed ?? 'seoulink-example-image');
    let hash = 0;

    for (let index = 0; index < normalizedSeed.length; index += 1) {
        hash = ((hash * 31) + normalizedSeed.charCodeAt(index)) | 0;
    }

    return images[(hash >>> 0) % images.length];
}

/** 상세 화면에서 쓰던 카테고리별 예시 사진 선택 규칙을 공통으로 사용합니다. */
export function getPlaceFallbackImage(category, seed) {
    const normalizedCategory = normalizeCourseImageCategory(category);
    const categoryImages = courseFallbackImagesByCategory[normalizedCategory];
    const candidates = categoryImages?.length > 0
        ? categoryImages
        : neutralCourseFallbackImages;

    return pickStableCourseFallbackImage(candidates, seed);
}

/**
 * 코스의 첫 장소와 코스 식별값을 이용해 대표 예시 사진을 고릅니다.
 * 실제 사진이 없을 때만 목록·추천 결과·상세 화면에서 사용합니다.
 */
export function getCourseFallbackImage(course, places = []) {
    const normalizedPlaces = Array.isArray(places)
        ? places.filter(Boolean)
        : [];
    const firstPlace = normalizedPlaces[0]
        || course?.places?.[0]
        || course?.days?.flatMap?.((day) => day?.places || [])?.[0]
        || null;
    const seed = course?.recommendationKey
        || course?.sourceCourseKey
        || firstPlace?.placeId
        || course?.courseId
        || course?.savedCourseId
        || course?.recommendationId
        || course?.id
        || course?.optionNo
        || course?.optionType
        || course?.title
        || course?.optionName;

    // 코스 대표 이미지는 음식·카페 전용 사진 대신 중립적인 서울 예시 사진을 사용합니다.
    // recommendationKey를 우선 사용해 추천 결과·메인·상세에서 같은 코스가 같은 사진을 갖게 합니다.
    return pickStableCourseFallbackImage(neutralCourseFallbackImages, seed);
}
