import palaceImg from '../assets/images/moods/mood-hanok-photo.png';
import cafeImg from '../assets/images/moods/mood-rainy-cafe.png';
import sunsetImg from '../assets/images/moods/mood-sunset-seoul.png';
import walkingImg from '../assets/images/moods/mood-walking-alley.png';
import foodImg from '../assets/images/moods/mood-local-food.png';
import dateNightImg from '../assets/images/moods/mood-date-night.png';

export const travelCodeDimensions = [
    {
        key: 'activity',
        title: '활동 성향',
        question: '여행할 때 나는?',
        fallback: 'A',
        options: {
            A: {
                label: '활동형',
                answer: '많이 돌아다니는 활동형',
                short: '많이 걷고 탐방하기',
                description: '다양한 장소를 직접 돌아다니며 새로운 경험을 즐겨요.',
                icon: 'walk',
                color: 'blue',
            },
            R: {
                label: '휴식형',
                answer: '천천히 머무는 휴식형',
                short: '느긋하게 머물기',
                description: '이동을 줄이고 여유롭게 머무는 여행을 선호해요.',
                icon: 'leaf',
                color: 'green',
            },
        },
    },
    {
        key: 'culture',
        title: '문화 취향',
        question: '어떤 장소가 끌리나요?',
        fallback: 'T',
        options: {
            T: {
                label: '역사형',
                answer: '역사와 전통을 좋아해요',
                short: '역사와 전통 즐기기',
                description: '궁궐, 한옥, 전통 마을처럼 이야기가 있는 장소에 의미를 찾아요.',
                icon: 'landmark',
                color: 'purple',
            },
            M: {
                label: '현대형',
                answer: '트렌디하고 현대적인 장소가 좋아요',
                short: '트렌드 따라가기',
                description: '핫플레이스, 전시, 쇼핑 거리처럼 지금의 서울을 느끼고 싶어해요.',
                icon: 'spark',
                color: 'orange',
            },
        },
    },
    {
        key: 'budget',
        title: '소비 성향',
        question: '여행에서 중요한 것은?',
        fallback: 'B',
        options: {
            B: {
                label: '가성비형',
                answer: '가성비를 중요하게 생각해요',
                short: '합리적인 소비',
                description: '비용 대비 만족도가 높은 맛집과 체험을 찾아 알뜰하게 여행해요.',
                icon: 'wallet',
                color: 'green',
            },
            L: {
                label: '프리미엄형',
                answer: '퀄리티와 특별함을 중요하게 생각해요',
                short: '특별한 경험',
                description: '조금 더 비용을 쓰더라도 완성도 높은 공간과 서비스를 선호해요.',
                icon: 'star',
                color: 'pink',
            },
        },
    },
    {
        key: 'mood',
        title: '자극 성향',
        question: '어떤 분위기의 여행을 선호하나요?',
        fallback: 'S',
        options: {
            S: {
                label: '안정형',
                answer: '안정적이고 무난한 코스를 선호해요',
                short: '안정적인 일정',
                description: '실패 확률이 낮고 누구와 가도 만족하기 쉬운 코스를 좋아해요.',
                icon: 'shield',
                color: 'orange',
            },
            D: {
                label: '도파민형',
                answer: '새롭고 자극적인 코스를 선호해요',
                short: '새로운 자극',
                description: '인기 급상승 공간, 액티비티, 색다른 동선을 적극적으로 즐겨요.',
                icon: 'heart',
                color: 'pink',
            },
        },
    },
    {
        key: 'density',
        title: '일정 밀도',
        question: '하루 일정을 어떻게 보내고 싶나요?',
        fallback: 'P',
        options: {
            P: {
                label: '빽빽한 일정형',
                answer: '여러 곳을 충분히 다니고 싶어요',
                short: '알찬 하루 구성',
                description: '하루 안에 관광지, 식당, 카페를 촘촘하게 엮어 다니는 걸 선호해요.',
                icon: 'calendar',
                color: 'pink',
            },
            R: {
                label: '여유 일정형',
                answer: '적은 장소를 여유롭게 보고 싶어요',
                short: '여유 있는 하루',
                description: '장소 수보다 체류 시간과 컨디션을 더 중요하게 생각해요.',
                icon: 'clock',
                color: 'blue',
            },
        },
    },
];

export const sampleSurveyResult = {
    surveyId: 1,
    resultId: 1,
    travelCode: 'ATBSP',
    travelTitle: '활동적이고 역사적인 가성비 여행을 선호하는 당신!',
    description:
        '많이 돌아다니며 역사와 전통을 즐기고, 가성비를 중요하게 생각하며 안정적인 코스 안에서 하루 일정을 알차게 보내고 싶은 타입이에요.',
    imageUrl: palaceImg,
    recommendedPlaces: [
        {
            placeId: 101,
            name: '경복궁',
            category: 'TOUR',
            region: '종로구',
            address: '서울 종로구 사직로 161',
            latitude: 37.5796,
            longitude: 126.977,
            rating: 4.8,
            reviewCount: 128,
            description: '조선의 중심 궁궐을 따라 서울의 역사와 전통을 느낄 수 있는 대표 명소예요.',
            imageUrl: palaceImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
        {
            placeId: 102,
            name: '북촌 한옥마을',
            category: 'TOUR',
            region: '종로구',
            address: '서울 종로구 계동길 37',
            latitude: 37.5826,
            longitude: 126.9832,
            rating: 4.7,
            reviewCount: 96,
            description: '한옥 골목을 걸으며 전통적인 서울 풍경을 자연스럽게 만날 수 있어요.',
            imageUrl: walkingImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
        {
            placeId: 103,
            name: '익선동 한식 골목',
            category: 'RESTAURANT',
            region: '종로구',
            address: '서울 종로구 수표로28길 일대',
            latitude: 37.5744,
            longitude: 126.9898,
            rating: 4.6,
            reviewCount: 84,
            description: '한옥 분위기 속에서 부담 없는 가격대의 한식을 고르기 좋은 식당 밀집 지역이에요.',
            imageUrl: foodImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
        {
            placeId: 104,
            name: '서촌 카페거리',
            category: 'CAFE',
            region: '종로구',
            address: '서울 종로구 필운대로 일대',
            latitude: 37.5805,
            longitude: 126.9687,
            rating: 4.5,
            reviewCount: 77,
            description: '궁궐 관람 후 쉬어가기 좋고 골목 산책까지 이어지는 안정적인 카페 코스예요.',
            imageUrl: cafeImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
        {
            placeId: 105,
            name: '창덕궁',
            category: 'TOUR',
            region: '종로구',
            address: '서울 종로구 율곡로 99',
            latitude: 37.5794,
            longitude: 126.991,
            rating: 4.8,
            reviewCount: 132,
            description: '궁궐과 후원을 함께 둘러보며 전통 건축의 깊이를 느낄 수 있어요.',
            imageUrl: palaceImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
        {
            placeId: 106,
            name: '광장시장',
            category: 'RESTAURANT',
            region: '종로구',
            address: '서울 종로구 창경궁로 88',
            latitude: 37.5701,
            longitude: 126.9996,
            rating: 4.6,
            reviewCount: 203,
            description: '부담 없는 가격으로 다양한 서울 먹거리를 빠르게 즐길 수 있는 시장 맛집 코스예요.',
            imageUrl: foodImg,
            tagHistory: 'Y',
            tagBudget: 'Y',
            tagStable: 'Y',
            tagPacked: 'Y',
        },
    ],
    recommendedItinerary: [
        {
            dayNo: 1,
            places: [
                {
                    placeId: 101,
                    name: '경복궁',
                    category: 'TOUR',
                    region: '종로구',
                    address: '서울 종로구 사직로 161',
                    latitude: 37.5796,
                    longitude: 126.977,
                    dayNo: 1,
                    placeOrder: 1,
                    visitTime: '09:00',
                    stayMinutes: 120,
                },
                {
                    placeId: 102,
                    name: '북촌 한옥마을',
                    category: 'TOUR',
                    region: '종로구',
                    address: '서울 종로구 계동길 37',
                    latitude: 37.5826,
                    longitude: 126.9832,
                    dayNo: 1,
                    placeOrder: 2,
                    visitTime: '11:00',
                    stayMinutes: 90,
                },
                {
                    placeId: 103,
                    name: '익선동 한식 골목',
                    category: 'RESTAURANT',
                    region: '종로구',
                    address: '서울 종로구 수표로28길 일대',
                    latitude: 37.5744,
                    longitude: 126.9898,
                    dayNo: 1,
                    placeOrder: 3,
                    visitTime: '13:00',
                    stayMinutes: 60,
                },
                {
                    placeId: 104,
                    name: '서촌 카페거리',
                    category: 'CAFE',
                    region: '종로구',
                    address: '서울 종로구 필운대로 일대',
                    latitude: 37.5805,
                    longitude: 126.9687,
                    dayNo: 1,
                    placeOrder: 4,
                    visitTime: '15:00',
                    stayMinutes: 60,
                },
                {
                    placeId: 105,
                    name: '창덕궁',
                    category: 'TOUR',
                    region: '종로구',
                    address: '서울 종로구 율곡로 99',
                    latitude: 37.5794,
                    longitude: 126.991,
                    dayNo: 1,
                    placeOrder: 5,
                    visitTime: '16:30',
                    stayMinutes: 90,
                },
                {
                    placeId: 106,
                    name: '광장시장',
                    category: 'RESTAURANT',
                    region: '종로구',
                    address: '서울 종로구 창경궁로 88',
                    latitude: 37.5701,
                    longitude: 126.9996,
                    dayNo: 1,
                    placeOrder: 6,
                    visitTime: '18:00',
                    stayMinutes: 75,
                },
            ],
        },
    ],
};

export const categoryMeta = {
    TOUR: { label: '관광지', badge: '관광', tone: 'blue' },
    RESTAURANT: { label: '식당', badge: '식사', tone: 'orange' },
    CAFE: { label: '카페', badge: '카페', tone: 'purple' },
    HOTEL: { label: '숙소', badge: '숙소', tone: 'green' },
};

export const supplementalCourseVariants = [
    {
        courseId: 'han-river-relax',
        badge: 'NEW',
        title: '한강 따라 여유롭게 즐기는 힐링 코스',
        description: '한강의 자연과 감성적인 공간에서 여유로운 시간을 보내는 코스',
        imageUrl: sunsetImg,
        travelCode: 'AHBRR',
        duration: '약 6시간',
        region: '마포구',
        visitCount: 4,
        rating: 4.6,
        reviewCount: 96,
        tags: ['휴식/힐링', '감성 카페', '한강 뷰', '여유 일정'],
        steps: [
            { name: '여의도 한강공원', category: 'TOUR', visitTime: '10:00' },
            { name: '더현대 서울', category: 'TOUR', visitTime: '12:00' },
            { name: '망원동 카페', category: 'CAFE', visitTime: '14:30' },
            { name: '선유도 공원', category: 'TOUR', visitTime: '16:30' },
        ],
    },
    {
        courseId: 'seongsu-modern',
        badge: 'TREND',
        title: '성수동 감성 핫플레이스 투어',
        description: '트렌디한 공간과 핫플레이스를 하루 가득 즐기는 코스',
        imageUrl: dateNightImg,
        travelCode: 'AMDDP',
        duration: '약 7시간',
        region: '성동구',
        visitCount: 5,
        rating: 4.7,
        reviewCount: 203,
        tags: ['핫플레이스', '감성 맛집', '쇼핑', '활기찬 분위기'],
        steps: [
            { name: '성수연방', category: 'TOUR', visitTime: '10:00' },
            { name: '성수동 거리', category: 'TOUR', visitTime: '11:30' },
            { name: '연무장길 맛집', category: 'RESTAURANT', visitTime: '13:00' },
            { name: '성수 카페거리', category: 'CAFE', visitTime: '15:00' },
            { name: '뚝섬 한강공원', category: 'TOUR', visitTime: '17:00' },
        ],
    },
];

export function getCodeTraits(travelCode = sampleSurveyResult.travelCode) {
    return travelCodeDimensions.map((dimension, index) => {
        const codeLetter = travelCode[index] || dimension.fallback;
        const option = dimension.options[codeLetter] || dimension.options[dimension.fallback];

        return {
            ...option,
            code: codeLetter,
            dimensionKey: dimension.key,
            title: dimension.title,
            question: dimension.question,
        };
    });
}

export function getTravelTags(travelCode) {
    return getCodeTraits(travelCode).map((trait) => trait.label);
}

export function getPreferredRegions(places = []) {
    const regions = [...new Set(places.map((place) => place.region).filter(Boolean))];
    return regions.length > 0 ? regions : ['종로구', '중구', '성북구', '마포구', '용산구'];
}

export function getPlaceByIdMap(places = []) {
    return new Map(places.map((place) => [place.placeId, place]));
}

export function flattenItinerary(itinerary = []) {
    return itinerary.flatMap((day) => day.places || []);
}

export function enrichItineraryPlaces(result = sampleSurveyResult) {
    const placeMap = getPlaceByIdMap(result.recommendedPlaces || []);

    return flattenItinerary(result.recommendedItinerary || []).map((place) => ({
        ...placeMap.get(place.placeId),
        ...place,
        imageUrl: placeMap.get(place.placeId)?.imageUrl || palaceImg,
    }));
}

export function buildPrimaryCourse(result = sampleSurveyResult) {
    const steps = enrichItineraryPlaces(result);
    const regions = getPreferredRegions(result.recommendedPlaces);

    return {
        courseId: 'survey-best',
        badge: 'BEST',
        title: '역사와 전통을 따라 걷는 서울 하루 코스',
        description: '조선의 역사와 전통을 즐기며 알찬 일정을 보내는 코스',
        imageUrl: result.imageUrl || steps[0]?.imageUrl || palaceImg,
        travelCode: result.travelCode,
        duration: '약 8시간',
        region: regions[0],
        visitCount: steps.length || 6,
        rating: 4.8,
        reviewCount: 128,
        tags: ['역사/전통', '가성비 맛집', '도보 이동 중심', '인기 코스'],
        steps,
    };
}

export function buildCourseRecommendations(result = sampleSurveyResult) {
    return [buildPrimaryCourse(result), ...supplementalCourseVariants];
}
