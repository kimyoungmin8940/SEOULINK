import hanokPhotoImage from '../assets/images/moods/mood-hanok-photo.png';
import walkingAlleyImage from '../assets/images/moods/mood-walking-alley.png';
import nightDateImage from '../assets/images/moods/mood-date-night.png';
import sunsetImage from '../assets/images/moods/mood-sunset-seoul.png';
import localFoodImage from '../assets/images/moods/mood-local-food.png';
import rainyCafeImage from '../assets/images/moods/mood-rainy-cafe.png';

const categoryStayMinutes = {
    TOUR: 80,
    RESTAURANT: 60,
    CAFE: 55,
    HOTEL: 30,
};

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
        description: '해 질 무렵부터 서울의 야경과 감성 공간을 함께 즐기는 데이트 코스예요.',
        image: nightDateImage,
        tags: ['데이트', '야경', '감성'],
    },
    'hanok-photo': {
        themeCode: 'HANOK_PHOTO',
        slug: 'hanok-photo',
        title: '사진 찍기 좋은 한옥길',
        description: '궁궐과 한옥 골목, 오래된 동네의 풍경을 사진에 담는 코스예요.',
        image: hanokPhotoImage,
        tags: ['한옥', '사진명소', '문화'],
    },
    'local-food': {
        themeCode: 'LOCAL_FOOD',
        slug: 'local-food',
        title: '로컬처럼 먹는 하루',
        description: '시장과 오래된 맛집, 동네 카페를 엮어 서울의 일상을 맛보는 코스예요.',
        image: localFoodImage,
        tags: ['로컬', '맛집', '시장'],
    },
};

const courseRecipes = [
    {
        courseId: 1101,
        themeSlug: 'night-date',
        title: '정동 문화 데이트와 서울로 야경',
        description: '정동의 미술관과 고궁을 걷고 서울로의 불빛으로 마무리하는 도심 데이트 코스예요.',
        optionName: '정동 야경 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '정동 · 서울역',
        tags: ['미술관', '고궁', '야경', '데이트'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '13:00',
                center: [37.5655, 126.975],
                places: [
                    ['서울시립미술관 서소문본관', 'TOUR'],
                    ['덕수궁·돌담길', 'TOUR'],
                    ['르풀', 'CAFE'],
                    ['국립정동극장', 'TOUR'],
                    ['서울로7017', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1102,
        themeSlug: 'night-date',
        title: '성수 감성에서 응봉산 야경까지',
        description: '전시와 서울숲, 성수의 맛과 카페를 즐긴 뒤 응봉산 야경을 만나는 코스예요.',
        optionName: '성수 감성 코스',
        badge: 'PICK',
        tone: 'balanced',
        region: '성수 · 응봉',
        tags: ['서울숲', '성수', '카페', '야경'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '12:30',
                center: [37.5445, 127.044],
                places: [
                    ['D뮤지엄', 'TOUR'],
                    ['서울숲', 'TOUR'],
                    ['난포', 'RESTAURANT'],
                    ['카페 어니언 성수', 'CAFE'],
                    ['성수동 카페거리', 'TOUR'],
                    ['응봉산 팔각정', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1103,
        themeSlug: 'night-date',
        title: '잠실 뮤지컬 기념일 데이트',
        description: '책과 호수 산책, 뮤지컬과 서울의 높은 야경을 차례로 즐기는 기념일 코스예요.',
        optionName: '잠실 기념일 코스',
        badge: 'SPECIAL',
        tone: 'distance',
        region: '잠실 · 송파',
        tags: ['석촌호수', '뮤지컬', '서울스카이', '기념일'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '11:30',
                center: [37.512, 127.102],
                places: [
                    ['서울책보고', 'TOUR'],
                    ['석촌호수·송리단길', 'TOUR'],
                    ['카페 캠프통', 'CAFE'],
                    ['샤롯데씨어터', 'TOUR'],
                    ['봉피양 방이점', 'RESTAURANT'],
                    ['서울스카이', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1104,
        themeSlug: 'night-date',
        title: '망원에서 선유도까지 노을 데이트',
        description: '망원의 로컬 분위기에서 출발해 선유도의 노을과 야간 산책으로 이어지는 코스예요.',
        optionName: '한강 노을 코스',
        badge: 'SUNSET',
        tone: 'balanced',
        region: '망원 · 선유도',
        tags: ['한강', '노을', '시장', '산책'],
        coverImageUrl: sunsetImage,
        days: [
            {
                startTime: '14:00',
                center: [37.552, 126.91],
                places: [
                    ['서울함공원', 'TOUR'],
                    ['망원시장', 'RESTAURANT'],
                    ['카페꼼마 합정점', 'CAFE'],
                    ['선유도공원', 'TOUR'],
                    ['선유교', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1105,
        themeSlug: 'night-date',
        title: '반포 달빛 호캉스',
        description: '예술과 한강 야경, 쇼핑을 여유롭게 연결한 1박 2일 기념일 여행이에요.',
        optionName: '반포 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '서초 · 반포',
        tags: ['호캉스', '한강', '예술', '쇼핑'],
        coverImageUrl: nightDateImage,
        days: [
            {
                startTime: '12:00',
                center: [37.505, 127.005],
                places: [
                    ['예술의전당', 'TOUR'],
                    ['테라로사 예술의전당점', 'CAFE'],
                    ['봉산옥', 'RESTAURANT'],
                    ['반포한강공원', 'TOUR'],
                    ['세빛섬', 'TOUR'],
                    ['JW 메리어트 호텔 서울', 'HOTEL'],
                ],
            },
            {
                startTime: '10:30',
                center: [37.5065, 127.004],
                places: [
                    ['고투몰', 'TOUR'],
                    ['카페 노티드 서래', 'CAFE'],
                    ['스타벅스 서울웨이브아트센터점', 'CAFE'],
                    ['잠수교', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1201,
        themeSlug: 'hanok-photo',
        title: '북촌 건축과 한옥 셔터 투어',
        description: '공예와 건축을 보고 북촌에서 익선동까지 한옥의 표정을 담는 사진 코스예요.',
        optionName: '북촌 사진 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '안국 · 북촌 · 익선동',
        tags: ['북촌', '한옥', '공예', '사진명소'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.579, 126.985],
                places: [
                    ['서울공예박물관', 'TOUR'],
                    ['아라리오뮤지엄 인 스페이스', 'TOUR'],
                    ['프릳츠 원서점', 'CAFE'],
                    ['북촌한옥마을', 'TOUR'],
                    ['깡통만두', 'RESTAURANT'],
                    ['익선동 한옥거리', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1202,
        themeSlug: 'hanok-photo',
        title: '서촌 궁궐과 인왕산 프레임',
        description: '경복궁의 선과 서촌 골목, 인왕산 아래 풍경을 한 장면씩 담는 코스예요.',
        optionName: '서촌 풍경 코스',
        badge: 'FRAME',
        tone: 'balanced',
        region: '경복궁 · 서촌',
        tags: ['경복궁', '서촌', '인왕산', '산책'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '09:30',
                center: [37.577, 126.968],
                places: [
                    ['경복궁', 'TOUR'],
                    ['토속촌삼계탕', 'RESTAURANT'],
                    ['스태픽스', 'CAFE'],
                    ['아키비스트 서촌', 'CAFE'],
                    ['청운공원·윤동주 시인의 언덕', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1203,
        themeSlug: 'hanok-photo',
        title: '은평한옥과 북한산 뷰',
        description: '한옥의 단정한 선과 북한산 능선을 한 화면에 담을 수 있는 여유로운 코스예요.',
        optionName: '은평 한옥 코스',
        badge: 'VIEW',
        tone: 'distance',
        region: '은평 · 북한산',
        tags: ['은평한옥마을', '북한산', '전망', '여유'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.64, 126.94],
                places: [
                    ['은평역사한옥박물관', 'TOUR'],
                    ['은평한옥마을', 'TOUR'],
                    ['롱브레드 은평한옥마을점', 'CAFE'],
                    ['북한산국립공원', 'TOUR'],
                    ['북한산아래첫마을 카페', 'CAFE'],
                ],
            },
        ],
    },
    {
        courseId: 1204,
        themeSlug: 'hanok-photo',
        title: '도봉 한옥 정원과 산자락',
        description: '도봉산의 자연과 한옥 정원, 지역 문화 공간을 천천히 잇는 사진 산책 코스예요.',
        optionName: '도봉 산자락 코스',
        badge: 'GREEN',
        tone: 'balanced',
        region: '도봉 · 수락',
        tags: ['도봉산', '한옥정원', '자연', '사진'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '09:30',
                center: [37.68, 127.04],
                places: [
                    ['도봉산', 'TOUR'],
                    ['무수옥', 'RESTAURANT'],
                    ['무수아취', 'TOUR'],
                    ['서울창포원', 'TOUR'],
                    ['평화문화진지', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1205,
        themeSlug: 'hanok-photo',
        title: '한옥에서 자는 북촌 여행',
        description: '궁궐과 골목을 충분히 걷고 북촌 한옥 숙소에서 밤을 보내는 1박 2일 코스예요.',
        optionName: '북촌 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '서촌 · 북촌 · 익선동',
        tags: ['한옥숙소', '궁궐', '인사동', '익선동'],
        coverImageUrl: hanokPhotoImage,
        days: [
            {
                startTime: '10:00',
                center: [37.58, 126.98],
                places: [
                    ['경복궁', 'TOUR'],
                    ['토속촌삼계탕', 'RESTAURANT'],
                    ['스태픽스', 'CAFE'],
                    ['북촌한옥마을', 'TOUR'],
                    ['락고재 서울 북촌', 'HOTEL'],
                ],
            },
            {
                startTime: '10:00',
                center: [37.574, 126.987],
                places: [
                    ['카페 레이어드 안국', 'CAFE'],
                    ['서울공예박물관', 'TOUR'],
                    ['꽃, 밥에피다', 'RESTAURANT'],
                    ['인사동 쌈지길', 'TOUR'],
                    ['익선동 한옥거리', 'TOUR'],
                    ['세운상가 옥상', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1301,
        themeSlug: 'local-food',
        title: '종로·을지로 시장 노포 여행',
        description: '오래된 설렁탕집에서 시작해 시장과 골목 맛집을 걷는 서울 중심부 미식 코스예요.',
        optionName: '시장 노포 코스',
        badge: 'BEST',
        tone: 'preference',
        region: '종로 · 을지로',
        tags: ['노포', '광장시장', '을지로', '청계천'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '09:30',
                center: [37.57, 126.998],
                places: [
                    ['이문설농탕', 'RESTAURANT'],
                    ['광장시장', 'RESTAURANT'],
                    ['카페 어니언 광장시장', 'CAFE'],
                    ['방산시장', 'TOUR'],
                    ['은주정', 'RESTAURANT'],
                    ['청계천', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1302,
        themeSlug: 'local-food',
        title: '마포 한 그릇 미식 여행',
        description: '설렁탕과 평양냉면, 돼지갈비 사이에 마포의 오래된 카페를 곁들인 코스예요.',
        optionName: '마포 미식 코스',
        badge: 'TASTE',
        tone: 'balanced',
        region: '마포 · 도화',
        tags: ['마포', '설렁탕', '평양냉면', '돼지갈비'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:30',
                center: [37.54, 126.94],
                places: [
                    ['마포옥', 'RESTAURANT'],
                    ['프릳츠 도화점', 'CAFE'],
                    ['을밀대 본점', 'RESTAURANT'],
                    ['비로소커피', 'CAFE'],
                    ['조박집', 'RESTAURANT'],
                ],
            },
        ],
    },
    {
        courseId: 1303,
        themeSlug: 'local-food',
        title: '성수 로컬과 트렌드 사이',
        description: '성수의 새로운 맛과 오래된 족발집, 한강 산책을 균형 있게 즐기는 코스예요.',
        optionName: '성수 로컬 코스',
        badge: 'TREND',
        tone: 'distance',
        region: '성수 · 뚝섬',
        tags: ['성수', '서울숲', '카페', '족발'],
        coverImageUrl: rainyCafeImage,
        days: [
            {
                startTime: '11:30',
                center: [37.544, 127.045],
                places: [
                    ['난포', 'RESTAURANT'],
                    ['서울숲', 'TOUR'],
                    ['로우커피스탠드', 'CAFE'],
                    ['성수동 카페거리', 'TOUR'],
                    ['성수족발', 'RESTAURANT'],
                    ['뚝섬한강공원', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1304,
        themeSlug: 'local-food',
        title: '성북동 오래된 맛과 풍경',
        description: '성북동의 오래된 식당과 찻집을 지나 전망 좋은 언덕까지 오르는 코스예요.',
        optionName: '성북동 한 끼 코스',
        badge: 'LOCAL',
        tone: 'balanced',
        region: '성북동 · 안암',
        tags: ['성북동', '전통찻집', '빵집', '전망'],
        coverImageUrl: walkingAlleyImage,
        days: [
            {
                startTime: '11:00',
                center: [37.59, 127.0],
                places: [
                    ['금왕돈까스', 'RESTAURANT'],
                    ['수연산방', 'CAFE'],
                    ['나폴레옹과자점 본점', 'CAFE'],
                    ['성북동누룽지백숙', 'RESTAURANT'],
                    ['개운산 전망대', 'TOUR'],
                ],
            },
        ],
    },
    {
        courseId: 1305,
        themeSlug: 'local-food',
        title: '서울 서남권 로컬 미식',
        description: '문래와 영등포, 여의도와 노량진의 서로 다른 동네 맛을 경험하는 1박 2일 코스예요.',
        optionName: '서남권 1박 2일',
        badge: '1N2D',
        tone: 'preference',
        region: '양천 · 문래 · 노량진',
        tags: ['문래', '영등포', '노량진', '로컬미식'],
        coverImageUrl: localFoodImage,
        days: [
            {
                startTime: '10:30',
                center: [37.52, 126.88],
                places: [
                    ['양천옥설렁탕', 'RESTAURANT'],
                    ['카페 이목', 'CAFE'],
                    ['문래창작촌', 'TOUR'],
                    ['부일갈비', 'RESTAURANT'],
                    ['코트야드 바이 메리어트 서울 타임스퀘어', 'HOTEL'],
                ],
            },
            {
                startTime: '10:30',
                center: [37.51, 126.95],
                places: [
                    ['정인면옥', 'RESTAURANT'],
                    ['카페 진정성 종점', 'CAFE'],
                    ['노량진수산시장', 'RESTAURANT'],
                    ['용양봉저정공원', 'TOUR'],
                ],
            },
        ],
    },
];

function parseTime(value) {
    const [hours, minutes] = value.split(':').map(Number);
    return (hours * 60) + minutes;
}

function formatTime(value) {
    const normalized = ((value % 1440) + 1440) % 1440;
    return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

function getThemeFlags(themeSlug, placeName, category) {
    const normalizedName = placeName.replace(/\s+/g, '');
    const flags = {
        themePalaceCultureYn: themeSlug === 'hanok-photo' ? 'Y' : 'N',
        themeNatureHangangYn: /(공원|숲|산|한강|청계천|선유교)/.test(normalizedName) ? 'Y' : 'N',
        themeDateYn: themeSlug === 'night-date' ? 'Y' : 'N',
        themeFoodTourYn: themeSlug === 'local-food' || category === 'RESTAURANT' ? 'Y' : 'N',
        themeCafeTourYn: category === 'CAFE' ? 'Y' : 'N',
        themeShoppingHotplaceYn: /(시장|거리|고투몰|쌈지길|문래창작촌)/.test(normalizedName) ? 'Y' : 'N',
        themeNightViewYn: themeSlug === 'night-date' && /(서울로|산|스카이|공원|섬|교)/.test(normalizedName) ? 'Y' : 'N',
        themeHotelStayYn: category === 'HOTEL' ? 'Y' : 'N',
    };

    return flags;
}

function buildDay(course, rawDay, dayIndex) {
    let timeCursor = parseTime(rawDay.startTime);
    const center = rawDay.center;
    const places = rawDay.places.map(([placeName, category], placeIndex) => {
        const travelTime = placeIndex === 0 ? 0 : 16 + ((placeIndex + dayIndex) % 3) * 4;
        const distance = placeIndex === 0 ? 0 : 0.8 + ((placeIndex + dayIndex) % 4) * 0.35;
        const stayMinutes = categoryStayMinutes[category] || 60;
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

