import hanokPhotoImage from '../assets/images/moods/mood-hanok-photo.png';
import walkingAlleyImage from '../assets/images/moods/mood-walking-alley.png';
import nightDateImage from '../assets/images/moods/mood-date-night.png';
import sunsetImage from '../assets/images/moods/mood-sunset-seoul.png';
import localFoodImage from '../assets/images/moods/mood-local-food.png';
import rainyCafeImage from '../assets/images/moods/mood-rainy-cafe.png';
import {sunsetCourseRecipes,} from './themeCourses/sunsetCourses';
import {rainyCafeCourseRecipes,} from './themeCourses/rainyCafeCourses';
import {walkingAlleyCourseRecipes,} from './themeCourses/walkingAlleyCourses';
import {dateNightCourseRecipes,} from './themeCourses/dateNightCourses';
import {hanokCourseRecipes,} from './themeCourses/hanokCourses';
import {localFoodCourseRecipes,} from './themeCourses/localFoodCourses';
import { getThemePlaceStayMinutes } from './themePlaceStayMinutes';

const coordinateOffsets = [
    [0, 0],
    [0.0024, 0.0021],
    [-0.0017, 0.0038],
    [0.0031, -0.0026],
    [-0.0025, -0.0037],
    [0.0043, 0.0008],
];

export const themeCourseDefinitions = {
    'night-date': {
        themeCode: 'NIGHT_DATE',
        slug: 'night-date',
        title: '데이트하기 좋은 밤',
        description: '해 질 무렵부터 서울의 야경과 감성 공간을 함께 즐기는 데이트 코스예요',
        image: nightDateImage,
        tags: ['데이트', '야경', '감성'],
    },
    'hanok-photo': {
        themeCode: 'HANOK_PHOTO',
        slug: 'hanok-photo',
        title: '사진 찍기 좋은 한옥길',
        description: '궁궐과 한옥 골목, 오래된 동네의 풍경을 사진에 담는 코스예요',
        image: hanokPhotoImage,
        tags: ['한옥', '사진명소', '문화'],
    },
    'local-food': {
        themeCode: 'LOCAL_FOOD',
        slug: 'local-food',
        title: '로컬처럼 먹는 하루',
        description: '시장과 오래된 맛집, 동네 카페를 엮어 서울의 일상을 맛보는 코스예요',
        image: localFoodImage,
        tags: ['로컬', '맛집', '시장'],
    },
    'sunset': {
        themeCode: 'SUNSET',
        slug: 'sunset',
        title: '노을이 예쁜 서울',
        description:
            '한강과 서울의 전망 명소에서 붉게 물드는 노을과 야경을 즐기는 코스예요',
        image: sunsetImage,
        tags: ['노을', '한강', '전망'],
    },

    'rainy-cafe': {
        themeCode: 'RAINY_CAFE',
        slug: 'rainy-cafe',
        title: '비 오는 날의 카페',
        description:
            '빗소리와 젖은 서울의 풍경을 바라보며 전시와 카페를 여유롭게 즐기는 코스예요',
        image: rainyCafeImage,
        tags: ['비 오는 날', '카페', '실내'],
    },

    'walking-alley': {
        themeCode: 'WALKING_ALLEY',
        slug: 'walking-alley',
        title: '혼자 걷기 좋은 골목',
        description:
            '서울의 오래된 골목과 개성 있는 동네를 혼자 천천히 둘러보는 산책 코스예요',
        image: walkingAlleyImage,
        tags: ['골목', '산책', '혼자 여행'],
    },
};

const courseRecipes = [
    ...dateNightCourseRecipes,
    ...hanokCourseRecipes,
    ...localFoodCourseRecipes,
    ...sunsetCourseRecipes,
    ...rainyCafeCourseRecipes,
    ...walkingAlleyCourseRecipes,
];

function parseTime(value) {
    const [hours, minutes] = value.split(':').map(Number);
    return (hours * 60) + minutes;
}

function formatTime(value) {
    const normalized = ((value % 1440) + 1440) % 1440;
    return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

function getThemeFlags(
    themeSlug,
    placeName,
    category
) {
    const normalizedName = placeName.replace(/\s+/g, '');
    const isSunsetTheme = themeSlug === 'sunset';
    const isRainyCafeTheme = themeSlug === 'rainy-cafe';

    return {
        // 한옥 테마에서만 적용
        themePalaceCultureYn:
            themeSlug === 'hanok-photo' ? 'Y' : 'N',

        // 노을·한강·공원·산 등
        themeNatureHangangYn:
            isSunsetTheme ||
            /(공원|숲|산|한강|청계천|선유교|노들섬|잠수교|세빛섬)/.test(
                normalizedName
            )
                ? 'Y'
                : 'N',

        // 야간 데이트 테마
        themeDateYn:
            themeSlug === 'night-date' ? 'Y' : 'N',

        // 음식 또는 식당
        themeFoodTourYn:
            themeSlug === 'local-food' || category === 'RESTAURANT' ? 'Y' : 'N',

        // 비 오는 날 카페 또는 카페 장소
        themeCafeTourYn:
            isRainyCafeTheme || category === 'CAFE' ? 'Y' : 'N',

        // 골목·시장·쇼핑·핫플레이스
        themeShoppingHotplaceYn:
            /(시장|거리|골목|고투몰|쌈지길|문래창작촌|더현대|코엑스|타임스퀘어|홍대)/.test(
                normalizedName
            )
                ? 'Y'
                : 'N',

        // 야경과 노을 전망
        themeNightViewYn:
            (
                themeSlug === 'night-date' ||
                isSunsetTheme
            ) &&
            /(서울로|산|스카이|공원|섬|교|팔각정|노을|크루즈|세빛섬)/.test(
                normalizedName
            )
                ? 'Y'
                : 'N',

        themeHotelStayYn:
            category === 'HOTEL' ? 'Y' : 'N',
    };
}
function buildDay(course, rawDay, dayIndex) {
    let timeCursor = parseTime(rawDay.startTime);
    const center = rawDay.center;
    const places = rawDay.places.map(([placeName, category], placeIndex) => {
        const travelTime = placeIndex === 0 ? 0 : 16 + ((placeIndex + dayIndex) % 3) * 4;
        const distance = placeIndex === 0 ? 0 : 0.8 + ((placeIndex + dayIndex) % 4) * 0.35;
        const stayMinutes = getThemePlaceStayMinutes(placeName, category);
        const [latitudeOffset, longitudeOffset] = coordinateOffsets[
            placeIndex % coordinateOffsets.length
        ];

        if (placeIndex > 0) timeCursor += travelTime;
        const visitTime = formatTime(timeCursor);
        timeCursor += stayMinutes;

        return {
            placeId: (course.courseId * 100) + (dayIndex * 10) + placeIndex + 1,
            placeName,
            category,
            visitOrder: placeIndex + 1,
            expectedVisitTimeHHmm: visitTime,
            expectedVisitMinutes: stayMinutes,
            distanceFromPreviousKm: Number(distance.toFixed(2)),
            travelTimeFromPreviousMinutes: travelTime,
            transitPathType: placeIndex === 0 ? null : 'BUS_SUBWAY',
            routeEstimated: placeIndex > 0,
            recommendationScore: 96 - (placeIndex * 2) - dayIndex,
            latitude: Number((center[0] + latitudeOffset).toFixed(6)),
            longitude: Number((center[1] + longitudeOffset).toFixed(6)),
            memo: `${themeCourseDefinitions[course.themeSlug].title} 분위기와 이동 동선을 함께 고려한 장소예요.`,
            ...getThemeFlags(course.themeSlug, placeName, category),
        };
    });
    const dailyTravelTimeMinutes = places.reduce(
        (sum, place) => sum + place.travelTimeFromPreviousMinutes,
        0,
    );
    const dailyVisitTimeMinutes = places.reduce(
        (sum, place) => sum + place.expectedVisitMinutes,
        0,
    );

    return {
        dayNo: dayIndex + 1,
        visitDate: null,
        dailyDistanceKm: Number(places.reduce(
            (sum, place) => sum + place.distanceFromPreviousKm,
            0,
        ).toFixed(2)),
        dailyTravelTimeMinutes,
        dailyVisitTimeMinutes,
        dailyCourseTimeMinutes: dailyTravelTimeMinutes + dailyVisitTimeMinutes,
        places,
    };
}

function buildCourse(recipe, index) {
    const definition = themeCourseDefinitions[recipe.themeSlug];
    const days = recipe.days.map((day, dayIndex) => buildDay(recipe, day, dayIndex));
    const places = days.flatMap((day) => day.places);
    const sumDays = (field) => days.reduce((sum, day) => sum + day[field], 0);

    return {
        courseId: recipe.courseId,
        optionNo: index + 1,
        optionType: 'THEME',
        optionName: recipe.optionName,
        badge: recipe.badge,
        tone: recipe.tone,
        themeCode: definition.themeCode,
        themeSlug: recipe.themeSlug,
        sourceCourseKey: `${definition.themeCode}_${recipe.courseId}`,
        title: recipe.title,
        description: recipe.description,
        region: recipe.region,
        tags: recipe.tags,
        coverImageUrl: recipe.coverImageUrl || definition.image,
        transportMode: 'PUBLIC_TRANSIT',
        estimatedTravelTimes: true,
        courseType: 'THEME',
        publicCourse: true,
        dayCount: days.length,
        placeCount: places.length,
        totalDistanceKm: Number(sumDays('dailyDistanceKm').toFixed(2)),
        totalTravelTimeMinutes: sumDays('dailyTravelTimeMinutes'),
        totalVisitTimeMinutes: sumDays('dailyVisitTimeMinutes'),
        totalCourseTimeMinutes: sumDays('dailyCourseTimeMinutes'),
        days,
    };
}

export const themeCourses = courseRecipes.map(buildCourse);

export function getThemeCourseDefinition(themeSlug) {
    return themeCourseDefinitions[themeSlug] || null;
}

export function getThemeCourses(themeSlug) {
    return themeCourses.filter((course) => course.themeSlug === themeSlug);
}

export function getThemeCourseById(courseId) {
    const normalizedCourseId = Number(courseId);
    return themeCourses.find((course) => course.courseId === normalizedCourseId) || null;
}

export function getThemeCoursePlaceNames(courses = themeCourses) {
    return [
        ...new Set(
            courses.flatMap((course) => course.days)
                .flatMap((day) => day.places)
                .map((place) => place.placeName)
                .filter(Boolean),
        ),
    ];
}

export function hydrateThemeCoursesWithPlaces(courses, places) {
    const placesByName = new Map(
        (Array.isArray(places) ? places : [])
            .filter((place) => place?.name)
            .map((place) => [place.name.trim(), place]),
    );
    const missingPlaceNames = new Set();

    const hydratedCourses = courses.map((course) => {
        const days = course.days.map((day) => ({
            ...day,
            places: day.places.map((place) => {
                const databasePlace = placesByName.get(place.placeName?.trim());

                if (!databasePlace) {
                    missingPlaceNames.add(place.placeName);
                    return place;
                }

                return {
                    ...place,
                    databaseMatched: true,
                    placeId: databasePlace.placeId,
                    placeName: databasePlace.name,
                    category: databasePlace.category,
                    databaseDescription: databasePlace.description,
                    address: databasePlace.address,
                    roadAddress: databasePlace.roadAddress,
                    imageUrl: databasePlace.imageUrl,
                    latitude: databasePlace.latitude,
                    longitude: databasePlace.longitude,
                    recommendationScore: databasePlace.rating,
                    expectedVisitMinutes:
                        databasePlace.avgStayMinutes ?? place.expectedVisitMinutes,
                    themePalaceCultureYn: databasePlace.themePalaceCultureYn,
                    themeNatureHangangYn: databasePlace.themeNatureHangangYn,
                    themeDateYn: databasePlace.themeDateYn,
                    themeFoodTourYn: databasePlace.themeFoodTourYn,
                    themeCafeTourYn: databasePlace.themeCafeTourYn,
                    themeShoppingHotplaceYn: databasePlace.themeShoppingHotplaceYn,
                    themeNightViewYn: databasePlace.themeNightViewYn,
                    themeHotelStayYn: databasePlace.themeHotelStayYn,
                };
            }),
        }));
        const databaseCoverImage = days
            .flatMap((day) => day.places)
            .find((place) => place.imageUrl)?.imageUrl;

        return {
            ...course,
            coverImageUrl: databaseCoverImage || course.coverImageUrl,
            days,
        };
    });

    return {
        courses: hydratedCourses,
        missingPlaceNames: [...missingPlaceNames],
    };
}

