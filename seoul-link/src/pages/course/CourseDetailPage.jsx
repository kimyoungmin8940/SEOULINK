import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
    ArrowLeft,
    Tag,
    CalendarDays,
    Compass,
    Check,
    ChevronLeft,
    ChevronRight,
    Clock3,
    Heart,
    Info,
    Lightbulb,
    MapPin,
    MapPinned,
    RefreshCw,
    Route,
    Save,
    Share2,
    Sparkles,
    Timer,
    X,
} from 'lucide-react';

import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import CoursePlaceList from '../../components/course/CoursePlaceList';
import CourseImage from '../../components/course/CourseImage';
import KakaoCourseMap from '../../components/course/KakaoCourseMap';
import CourseTransportIcon from '../../components/course/CourseTransportIcon';
import {
    getCourseDetail,
    getMyCourses,
    recordRecommendedCourses,
    resolveCourseRouteDetails,
    saveCourse,
} from '../../api/courseApi';
import useThemeCourseBookmarks from '../../hooks/useThemeCourseBookmarks';
import {
    getCurrentMemberId,
    normalizeMyCourseList,
} from '../../utils/courseHistory';
import { requireLogin } from '../../utils/authGuard';
import {
    getTransportMeta,
    normalizeTransitPathType,
    normalizeTransportMode,
} from '../../utils/courseTransport';
import {
    getCurrentTravelCode,
    getRememberedCourseTravelCode,
    normalizeTravelCode,
    rememberCourseTravelCode,
} from '../../utils/courseTravelCode';
import {
    getCourseCoverImageUrls,
    getCourseFallbackImage,
    getPlaceImageUrl,
} from '../../utils/courseImage';
import recommendationPreview from '../../mocks/courseRecommendation.json';
import { mockThemeCourseListResponse } from '../../mocks/homeMockData';
import { getThemeCourseById } from '../../data/themeCourseData';
import { getThemePlacesByNames } from '../../api/placeApi';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';
import localFoodImage from '../../assets/images/moods/mood-local-food.png';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';
import '../../styles/course-detail.css';

const neutralPlaceFallbackImages = [
    hanokImage,
    walkingImage,
    sunsetImage,
];

const placeFallbackImagesByCategory = {
    TOUR: neutralPlaceFallbackImages,
    RESTAURANT: [localFoodImage],
    CAFE: [rainyCafeImage],
};

const themeFields = [
    ['themePalaceCultureYn', '역사·문화'],
    ['themeNatureHangangYn', '자연·한강'],
    ['themeDateYn', '감성 여행'],
    ['themeFoodTourYn', '맛집 탐방'],
    ['themeCafeTourYn', '카페 투어'],
    ['themeShoppingHotplaceYn', '쇼핑·핫플'],
    ['themeNightViewYn', '야경'],
    ['themeHotelStayYn', '숙소'],
];

const travelTypeLabels = {
    A: '활동형',
    H: '휴식형',
    T: '역사형',
    M: '현대형',
    L: '럭셔리형',
    B: '가성비형',
    S: '안정형',
    D: '도전형',
    P: '빽빽한 일정형',
    R: '여유 일정형',
};

const compactScheduleNotices = Object.freeze({
    WALKING: '도보 이동 기준 추천 동선입니다. 보행 환경과 장소 운영 시간에 따라 달라질 수 있습니다.',
    PUBLIC_TRANSIT: '대중교통 이동 기준 추천 동선입니다. 운행·환승 대기와 장소 운영 시간에 따라 달라질 수 있습니다.',
    DRIVING: '자동차 이동 기준 추천 동선입니다. 교통·주차와 장소 운영 시간에 따라 달라질 수 있습니다.',
});

const estimatedScheduleNotices = Object.freeze({
    WALKING: '경로를 불러오지 못한 일부 도보 구간은 예상값입니다. 실제 보행 상황에 따라 달라질 수 있습니다.',
    PUBLIC_TRANSIT: '경로를 불러오지 못한 일부 대중교통 구간은 예상값입니다. 실제 운행 상황에 따라 달라질 수 있습니다.',
    DRIVING: '경로를 불러오지 못한 일부 자동차 구간은 예상값입니다. 실제 교통 상황에 따라 달라질 수 있습니다.',
});
const COURSE_DETAIL_ENTRY_KEY = 'seoulinkCourseDetailEntry';

/** 지원하는 상세 경로 전체가 정확히 일치할 때만 조회할 courseId를 구합니다. */
function getCourseId() {
    const match = window.location.pathname.match(
        /^(?:\/courses\/themes\/[^/]+\/|\/courses\/(?:recommendations\/)?|\/mypage\/courses\/)([1-9]\d*)\/?$/,
    );
    const courseId = Number(match?.[1]);

    return Number.isInteger(courseId) && courseId > 0 ? courseId : null;
}

/** 현재 상세 경로의 종류에 맞는 안전한 목록 복귀 경로를 반환합니다. */
function getCourseListPath() {
    const themeMatch = window.location.pathname.match(
        /^\/courses\/themes\/([^/]+)\/[1-9]\d*\/?$/,
    );

    if (themeMatch) {
        return `/courses/themes/${themeMatch[1]}`;
    }

    if (window.location.pathname.startsWith('/mypage/courses/')) {
        return '/mypage/courses';
    }

    return window.location.pathname.startsWith('/courses/recommendations/')
        ? '/courses/recommendations'
        : '/courses/list';
}

function toFiniteNumber(value, fallback = 0) {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
}

function isHotelCategory(category) {
    const normalized = String(category || '').trim().toUpperCase();
    return ['HOTEL', '숙소', '호텔', 'ACCOMMODATION', 'LODGING'].includes(normalized);
}

function normalizePlaceCategory(category) {
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

/** 현재 DAY에서 실제 이동 구간으로 표시되는 항목만 예상값인지 확인합니다. */
function hasEstimatedTravelLeg(day) {
    return (day?.places || []).some((place, index) => (
        (index > 0 || Boolean(day?.routeOriginPlace))
        && Boolean(place?.routeEstimated)
    ));
}

/** 같은 장소는 다시 렌더링되어도 동일한 예시 사진을 사용합니다. */
function pickStableFallbackImage(images, seed) {
    if (!Array.isArray(images) || images.length === 0) return null;

    const normalizedSeed = String(seed ?? 'seoulink-example-image');
    let hash = 0;

    for (let index = 0; index < normalizedSeed.length; index += 1) {
        hash = ((hash * 31) + normalizedSeed.charCodeAt(index)) | 0;
    }

    return images[(hash >>> 0) % images.length];
}

/**
 * 음식·카페 사진은 각각 식당·카페에만 배정하고,
 * 전용 예시가 없는 카테고리만 중립적인 예시 사진 중 한 장을 사용합니다.
 */
function getPlaceFallbackImage(category, seed) {
    const normalizedCategory = normalizePlaceCategory(category);
    const categoryImages = placeFallbackImagesByCategory[normalizedCategory];
    const candidates = categoryImages?.length > 0
        ? categoryImages
        : neutralPlaceFallbackImages;

    return pickStableFallbackImage(candidates, seed);
}

/** 목록에서 상세로 이동할 때 함께 저장한 요약과 돌아갈 경로를 읽습니다. */
function readCourseDetailEntry(courseId) {
    try {
        const entry = JSON.parse(sessionStorage.getItem(COURSE_DETAIL_ENTRY_KEY));
        return entry?.detailId === courseId ? entry : null;
    } catch {
        return null;
    }
}

function parseDurationMinutes(value) {
    if (!value) return null;

    const hours = Number(String(value).match(/(\d+)\s*시간/)?.[1] || 0);
    const minutes = Number(String(value).match(/(\d+)\s*분/)?.[1] || 0);
    const totalMinutes = (hours * 60) + minutes;
    return totalMinutes > 0 ? totalMinutes : null;
}

function extractTime(value) {
    if (!value) return null;

    const match = String(value).match(/(?:^|T|\s)([01]\d|2[0-3]):([0-5]\d)/);
    return match ? `${match[1]}:${match[2]}` : null;
}

function timeToMinutes(value) {
    const normalizedTime = extractTime(value);
    if (!normalizedTime) return null;

    const [hours, minutes] = normalizedTime.split(':').map(Number);
    return (hours * 60) + minutes;
}

function minutesToTime(value) {
    const normalizedMinutes = ((Math.round(value) % 1440) + 1440) % 1440;
    const hours = Math.floor(normalizedMinutes / 60);
    const minutes = normalizedMinutes % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

/** 날짜별 장소를 방문 순서로 정렬하고 누락된 표시 시각·이미지·합계를 보완합니다. */
function normalizeDays(rawDays) {
    return (Array.isArray(rawDays) ? rawDays : []).map((day, dayIndex) => {
        let timeCursor = 10 * 60;
        const rawRouteOrigin = day?.routeOriginPlace || null;
        const hasRouteOrigin = Boolean(rawRouteOrigin?.placeId);
        const sortedPlaces = [...(Array.isArray(day?.places) ? day.places : [])]
            .sort((first, second) => (
                toFiniteNumber(first.visitOrder, 999) - toFiniteNumber(second.visitOrder, 999)
            ));

        const places = sortedPlaces.map((place, placeIndex) => {
            const explicitTime = extractTime(place.expectedVisitTimeHHmm || place.visitTime);
            const expectedVisitMinutes = isHotelCategory(place.category)
                ? 0
                : toFiniteNumber(place.expectedVisitMinutes);
            const actualImageUrl = getPlaceImageUrl(place);
            const fallbackImageUrl = getPlaceFallbackImage(
                place.category,
                place.placeId || place.placeName || `${dayIndex}-${placeIndex}`,
            );

            // 서버가 예상 시각을 주지 않으면 10:00부터 이동·체류시간을 누적해 표시합니다.
            if (explicitTime) {
                timeCursor = timeToMinutes(explicitTime);
            } else if (placeIndex > 0 || hasRouteOrigin) {
                timeCursor += toFiniteNumber(place.travelTimeFromPreviousMinutes);
            }

            const displayVisitTime = explicitTime || minutesToTime(timeCursor);
            timeCursor = (timeToMinutes(displayVisitTime) ?? timeCursor)
                + toFiniteNumber(place.expectedVisitMinutes);

            // 거리·시간·경로 종류는 모두 이전 장소에서 현재 장소로 들어오는 한 구간의 값입니다.
            return {
                ...place,
                visitOrder: toFiniteNumber(place.visitOrder, placeIndex + 1),
                expectedVisitMinutes: toFiniteNumber(place.expectedVisitMinutes),
                distanceFromPreviousKm: toFiniteNumber(place.distanceFromPreviousKm),
                travelTimeFromPreviousMinutes: toFiniteNumber(
                    place.travelTimeFromPreviousMinutes,
                ),
                transitPathType: normalizeTransitPathType(place.transitPathType),
                routeEstimated: Boolean(place.routeEstimated),
                displayVisitTime,
                fallbackImageUrl,
                displayImageUrl: actualImageUrl,
                displayImageIsExample: !actualImageUrl,
            };
        });

        const firstPlace = places[0] || null;
        const firstVisitMinutes = timeToMinutes(firstPlace?.displayVisitTime);
        const routeOriginVisitMinutes = firstVisitMinutes == null
            ? 10 * 60
            : firstVisitMinutes - toFiniteNumber(
                firstPlace?.travelTimeFromPreviousMinutes,
            );
        const routeOriginActualImageUrl = getPlaceImageUrl(rawRouteOrigin);
        const routeOriginFallbackImageUrl = getPlaceFallbackImage(
            rawRouteOrigin?.category,
            rawRouteOrigin?.placeId || rawRouteOrigin?.placeName || `origin-${dayIndex}`,
        );
        const routeOriginPlace = hasRouteOrigin
            ? {
                ...rawRouteOrigin,
                routeOrigin: true,
                visitOrder: 1,
                expectedVisitMinutes: 0,
                distanceFromPreviousKm: 0,
                travelTimeFromPreviousMinutes: 0,
                transitPathType: null,
                routeEstimated: false,
                displayVisitTime: minutesToTime(routeOriginVisitMinutes),
                fallbackImageUrl: routeOriginFallbackImageUrl,
                displayImageUrl: routeOriginActualImageUrl,
                displayImageIsExample: !routeOriginActualImageUrl,
            }
            : null;

        const derivedDistance = places.reduce(
            (sum, place) => sum + place.distanceFromPreviousKm,
            0,
        );
        const derivedTravelTime = places.reduce(
            (sum, place) => sum + place.travelTimeFromPreviousMinutes,
            0,
        );
        const derivedVisitTime = places.reduce(
            (sum, place) => sum + place.expectedVisitMinutes,
            0,
        );
        const dailyTravelTimeMinutes = toFiniteNumber(
            day.dailyTravelTimeMinutes,
            derivedTravelTime,
        );

        return {
            ...day,
            dayNo: toFiniteNumber(day.dayNo, dayIndex + 1),
            visitDate: day.visitDate || places[0]?.visitDate || null,
            dailyDistanceKm: toFiniteNumber(day.dailyDistanceKm, derivedDistance),
            dailyTravelTimeMinutes,
            dailyVisitTimeMinutes: derivedVisitTime,
            dailyCourseTimeMinutes: dailyTravelTimeMinutes + derivedVisitTime,
            routeOriginPlace,
            places,
        };
    });
}

/** 상세 API와 미리보기 데이터를 하나의 안전한 상세 화면 모델로 변환합니다. */
function normalizeCourseDetail(rawCourse) {
    const days = normalizeDays(rawCourse?.days);
    const places = days.flatMap((day) => day.places);
    const sumDays = (field) => days.reduce(
        (sum, day) => sum + toFiniteNumber(day[field]),
        0,
    );
    const totalTravelTimeMinutes = toFiniteNumber(
        rawCourse?.totalTravelTimeMinutes,
        sumDays('dailyTravelTimeMinutes'),
    );
    const totalVisitTimeMinutes = sumDays('dailyVisitTimeMinutes');
    const coverImageUrls = getCourseCoverImageUrls({
        ...rawCourse,
        placeImageUrls: places.map((place) => place.imageUrl),
    });
    const coverFallbackImageUrl = rawCourse?.coverFallbackImageUrl
        || getCourseFallbackImage(rawCourse, places);

    return {
        ...rawCourse,
        title: rawCourse?.title || '서울 맞춤 추천 코스',
        description: rawCourse?.description
            || '취향과 장소 간 이동 거리를 반영해 만든 서울 여행 코스입니다.',
        coverImageUrl: coverImageUrls[0] || null,
        coverImageUrls,
        coverFallbackImageUrl,
        travelCode: normalizeTravelCode(
            rawCourse?.travelCode
            || rawCourse?.preferenceCode
            || rawCourse?.typeCode
            || rawCourse?.surveyTypeCode,
        ),
        transportMode: normalizeTransportMode(rawCourse?.transportMode),
        // 요약 응답의 이전 true 값이 남아 실제 경로에도 예상 안내가 뜨는 것을 방지합니다.
        estimatedTravelTimes: days.some(hasEstimatedTravelLeg),
        courseType: rawCourse?.courseType || 'SURVEY',
        placeCount: toFiniteNumber(rawCourse?.placeCount, places.length),
        dayCount: toFiniteNumber(rawCourse?.dayCount, days.length),
        totalDistanceKm: toFiniteNumber(rawCourse?.totalDistanceKm, sumDays('dailyDistanceKm')),
        totalTravelTimeMinutes: toFiniteNumber(
            rawCourse?.totalTravelTimeMinutes,
            sumDays('dailyTravelTimeMinutes'),
        ),
        totalVisitTimeMinutes: toFiniteNumber(
            rawCourse?.totalVisitTimeMinutes,
            sumDays('dailyVisitTimeMinutes'),
        ),
        totalCourseTimeMinutes: toFiniteNumber(
            rawCourse?.totalCourseTimeMinutes,
            sumDays('dailyCourseTimeMinutes'),
        ),
        days,
    };
}

/**
 * 현재 백엔드의 평면 details 응답을 상세 화면이 사용하는 날짜별 days 구조로 변환합니다.
 * 이미 days 구조로 내려오는 응답은 그대로 사용합니다.
 */
function adaptCurrentCourseResponse(response) {
    if (Array.isArray(response?.days)) {
        return response;
    }

    const details = Array.isArray(response?.details)
        ? response.details
        : [];
    const groupedDays = new Map();

    details.forEach((detail) => {
        const dayNo = toFiniteNumber(detail?.dayNo, 1);

        if (!groupedDays.has(dayNo)) {
            groupedDays.set(dayNo, []);
        }

        groupedDays.get(dayNo).push({
            ...detail,
            visitOrder:
                detail?.visitOrder ?? detail?.placeOrder,
            expectedVisitMinutes:
                detail?.expectedVisitMinutes ?? detail?.stayMinutes,
            distanceFromPreviousKm:
                detail?.distanceFromPreviousKm ??
                detail?.distanceFromPrevKm,
            travelTimeFromPreviousMinutes:
                detail?.travelTimeFromPreviousMinutes ??
                detail?.travelMinutesFromPrev,
        });
    });

    const days = [...groupedDays.entries()]
        .sort(([firstDayNo], [secondDayNo]) => firstDayNo - secondDayNo)
        .map(([dayNo, places]) => ({
            dayNo,
            visitDate: places[0]?.visitDate?.slice?.(0, 10) || null,
            places,
        }));

    return {
        ...response,
        coverImageUrl:
            response?.coverImageUrl ||
            details.find((detail) => detail?.imageUrl)?.imageUrl ||
            null,
        publicCourse:
            response?.publicCourse ??
            response?.isPublic === 'Y',
        placeCount: response?.placeCount ?? details.length,
        dayCount: response?.dayCount ?? days.length,
        totalTravelTimeMinutes:
            response?.totalTravelTimeMinutes ??
            response?.totalTravelMinutes,
        totalVisitTimeMinutes:
            response?.totalVisitTimeMinutes ??
            response?.totalVisitMinutes,
        totalCourseTimeMinutes:
            response?.totalCourseTimeMinutes ??
            response?.totalCourseMinutes,
        days,
    };
}

/** 목록 카드에서만 유지되는 이미지·태그는 상세 API의 null 값을 덮지 않는 범위에서 보존합니다. */
function normalizeApiCourseDetail(response, courseId) {
    const summary = readCourseDetailEntry(courseId)?.summary || {};
    const rememberedTravelCode = getRememberedCourseTravelCode(courseId);
    const responseTags = Array.isArray(response?.tags) ? response.tags.filter(Boolean) : [];
    const summaryTags = Array.isArray(summary?.tags) ? summary.tags.filter(Boolean) : [];
    const summaryRegions = Array.isArray(summary?.regions)
        ? summary.regions.filter(Boolean)
        : [];

    return normalizeCourseDetail({
        ...summary,
        ...response,
        travelCode: response?.travelCode
            || response?.preferenceCode
            || response?.typeCode
            || response?.surveyTypeCode
            || summary?.travelCode
            || summary?.preferenceCode
            || summary?.typeCode
            || rememberedTravelCode
            || getCurrentTravelCode(recommendationPreview.travelCode),
        coverImageUrl: response?.coverImageUrl
            || summary?.coverImageUrl
            || summary?.imageUrl
            || null,
        coverImageUrls: Array.isArray(response?.coverImageUrls)
            && response.coverImageUrls.length > 0
            ? response.coverImageUrls
            : summary?.coverImageUrls,
        region: response?.region
            || summary?.region
            || summary?.area
            || summaryRegions.join(' · ')
            || null,
        transportMode: response?.transportMode || summary?.transportMode || null,
        estimatedTravelTimes: response?.estimatedTravelTimes
            ?? summary?.estimatedTravelTimes
            ?? false,
        tags: responseTags.length > 0 ? responseTags : summaryTags,
        liked: response?.liked ?? summary?.liked ?? false,
    });
}

/** 상세 화면의 코스를 추천 결과 화면과 같은 POST /courses 저장 요청으로 변환합니다. */
function buildDetailSaveRequest(course, memberId) {
    const transportMode = normalizeTransportMode(course?.transportMode);
    if (!transportMode) {
        throw new Error('코스의 이동수단을 확인할 수 없어 저장하지 못했습니다.');
    }

    const rawCourseType = String(course?.courseType || '').trim().toUpperCase();
    const courseType = rawCourseType === 'SURVEY'
        ? 'SURVEY'
        : rawCourseType === 'CHATBOT'
            ? 'CHATBOT'
            : 'CUSTOM';
    const resultId = Number(course?.resultId);

    if (
        courseType === 'SURVEY'
        && (!Number.isInteger(resultId) || resultId < 1)
    ) {
        throw new Error('설문 추천 결과 정보를 확인할 수 없어 저장하지 못했습니다.');
    }

    const places = (course?.days || []).flatMap((day) => (
        (day?.places || []).map((place, index) => ({
            placeId: place.placeId,
            category: place.category,
            visitDate: place.visitDate || day.visitDate,
            visitOrder: place.visitOrder ?? index + 1,
            visitTime: place.visitTime
                || place.expectedVisitTimeHHmm
                || place.displayVisitTime
                || null,
            expectedVisitMinutes: Number(place.expectedVisitMinutes) || 0,
            distanceFromPreviousKm: Number(place.distanceFromPreviousKm) || 0,
            travelTimeFromPreviousMinutes: Number(
                place.travelTimeFromPreviousMinutes,
            ) || 0,
            transitPathType: normalizeTransitPathType(place.transitPathType),
            routeEstimated: Boolean(place.routeEstimated),
        }))
    ));

    if (places.length === 0) {
        throw new Error('저장할 장소가 없어 코스를 저장하지 못했습니다.');
    }

    const courseId = Number(course?.courseId);

    return {
        memberId,
        ...(courseType === 'SURVEY'
            && Number.isInteger(courseId)
            && courseId > 0
            ? { courseId, resultId }
            : courseType === 'SURVEY'
                ? { resultId }
                : {}),
        title: course.title || '서울 맞춤 추천 코스',
        description: course.description
            || '서울 여행 장소와 이동 동선을 반영한 추천 코스입니다.',
        travelCode: course.travelCode || null,
        transportMode,
        courseType,
        region: course.region || null,
        publicCourse: false,
        places,
    };
}

/** 상세 API를 사용할 수 없을 때 사용자가 선택해서 확인할 수 있는 UI 미리보기를 만듭니다. */
function buildPreviewCourse(courseId) {
    const entrySummary = readCourseDetailEntry(courseId)?.summary || null;
    const mockSummary = mockThemeCourseListResponse.data.find(
        (candidate) => candidate.courseId === courseId,
    );
    const summary = entrySummary || mockSummary;
    const optionIndexByTheme = {
        SUNSET: 2,
        RAINY_CAFE: 1,
        HANOK_PHOTO: 0,
        WALKING_ALLEY: 0,
        NIGHT_DATE: 2,
        LOCAL_FOOD: 2,
    };
    const previewOption = recommendationPreview.courseOptions[
        optionIndexByTheme[summary?.themeCode] ?? 0
    ];
    const summaryDurationMinutes = parseDurationMinutes(summary?.duration);

    return normalizeCourseDetail({
        ...previewOption,
        courseId: courseId || summary?.courseId || null,
        title: summary?.title || previewOption.title,
        description: summary?.description || previewOption.description,
        coverImageUrl: summary?.coverImageUrl || summary?.imageUrl || null,
        coverImageUrls: summary?.coverImageUrls || null,
        travelCode: summary?.travelCode
            || summary?.preferenceCode
            || summary?.typeCode
            || getRememberedCourseTravelCode(courseId)
            || getCurrentTravelCode(recommendationPreview.travelCode),
        // 목록 요약에 이동수단이 없는 예전/테마 미리보기에서도 상세 화면 표기가 사라지지 않게 합니다.
        transportMode: summary?.transportMode || recommendationPreview.transportMode,
        estimatedTravelTimes: summary
            ? summary.estimatedTravelTimes
            : recommendationPreview.estimatedTravelTimes,
        courseType: summary?.themeCode ? 'THEME' : 'SURVEY',
        optionType: summary?.optionType || (summary ? null : previewOption.optionType),
        optionName: summary?.optionName || (summary ? null : previewOption.optionName),
        region: summary?.area || summary?.region || '서울',
        tags: summary?.tags || [],
        liked: summary?.liked ?? false,
        publicCourse: true,
        totalCourseTimeMinutes: summaryDurationMinutes
            ?? previewOption.totalCourseTimeMinutes,
    });
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(toFiniteNumber(value)));
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours === 0) return `${restMinutes}분`;
    return restMinutes === 0 ? `${hours}시간` : `${hours}시간 ${restMinutes}분`;
}

function parseDate(value) {
    if (!value) return null;

    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value, { compact = false } = {}) {
    const date = parseDate(value);
    if (!date) return '날짜 미정';

    if (compact) {
        return new Intl.DateTimeFormat('ko-KR', {
            month: '2-digit',
            day: '2-digit',
            weekday: 'short',
        }).format(date);
    }

    return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        weekday: 'short',
    }).format(date);
}

function getDateRange(days) {
    const dates = days.map((day) => day.visitDate).filter(Boolean).sort();
    if (dates.length === 0) return '여행 날짜 미정';
    if (dates.length === 1) return formatDate(dates[0]);

    return `${formatDate(dates[0])} ~ ${formatDate(dates[dates.length - 1])}`;
}

/** 목록에서 전달한 태그를 우선 사용하고, 없으면 장소별 8개 테마 값으로 생성합니다. */
function getThemeTags(course) {
    const summaryTags = Array.isArray(course.tags)
        ? [...new Set(course.tags.filter(Boolean))]
        : [];

    if (summaryTags.length > 0) return summaryTags;

    const places = course.days.flatMap((day) => day.places);
    const tags = themeFields
        .filter(([field]) => places.some((place) => place?.[field] === 'Y'))
        .map(([, label]) => label);

    if (tags.length > 0) return tags;
    return course.region ? [course.region, '서울 여행'] : ['서울 여행'];
}

function getCourseTypeLabel(courseType) {
    return {
        SURVEY: '취향 맞춤 코스',
        CUSTOM: '직접 만든 코스',
        CHATBOT: 'AI 추천 코스',
        THEME: '테마 추천 코스',
    }[courseType] || '서울 여행 코스';
}

/** 현재 선택된 일차의 장소만 지도에 전달하는 상세 화면 지도 카드입니다. */
function CourseRouteMap({ day }) {
    const places = day?.routeOriginPlace
        ? [
            {
                ...day.routeOriginPlace,
                routeOrigin: true,
            },
            ...(day?.places || []),
        ]
        : day?.places || [];

    return (
        <section className="course-detail-map-card">
            <div className="course-detail-side-heading">
                <div>
                    <MapPinned size={20} aria-hidden="true" />
                    <h2>코스 지도</h2>
                </div>
            </div>

            <div className="course-detail-map-canvas">
                <KakaoCourseMap
                    places={places}
                    ariaLabel={`DAY ${day?.dayNo || 1} 장소 방문 순서가 표시된 카카오 지도`}
                />
            </div>
        </section>
    );
}


/** 상세 진입 중 동일 코스의 실제 경로 보정 요청이 중복 실행되지 않게 공유합니다. */
const detailRouteRefreshPromises = new Map();

function hasPublicTransitDayLimitViolation(day) {
    let longLegCount = 0;

    return (day?.places || []).some((place, index) => {
        if (index === 0 && !day?.routeOriginPlace) return false;
        if (Boolean(place?.routeEstimated)) return false;

        const travelMinutes = Number(place?.travelTimeFromPreviousMinutes);
        if (!Number.isFinite(travelMinutes)) return false;
        if (travelMinutes > 40) return true;
        if (travelMinutes > 30) {
            longLegCount += 1;
            return longLegCount > 1;
        }
        return false;
    });
}

function hasResolvedPublicTransitRouteViolation(routeDetails) {
    const places = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];
    let longLegCount = 0;

    return places.some((place, index) => {
        if (index === 0 || Boolean(place?.routeEstimated)) return false;

        const travelMinutes = Number(place?.travelTimeFromPreviousMinutes);
        if (!Number.isFinite(travelMinutes)) return false;
        if (travelMinutes > 40) return true;
        if (travelMinutes > 30) {
            longLegCount += 1;
            return longLegCount > 1;
        }
        return false;
    });
}

function needsCourseRouteRefresh(course) {
    const transportMode = normalizeTransportMode(course?.transportMode);

    if (!['PUBLIC_TRANSIT', 'DRIVING'].includes(transportMode)) return false;
    if ((course?.days || []).some(hasEstimatedTravelLeg)) return true;
    return transportMode === 'PUBLIC_TRANSIT'
        && (course?.days || []).some(hasPublicTransitDayLimitViolation);
}

function toDetailRouteCandidate(place, visitDate) {
    return {
        placeId: Number(place?.placeId),
        placeName: place?.placeName || '',
        category: place?.category || 'TOUR',
        region: place?.region || null,
        address: place?.address || null,
        roadAddress: place?.roadAddress || null,
        imageUrl: place?.imageUrl || null,
        recommendationScore: Number(place?.recommendationScore) || 0,
        latitude: Number(place?.latitude),
        longitude: Number(place?.longitude),
        visitDate,
        themePalaceCultureYn: place?.themePalaceCultureYn || 'N',
        themeNatureHangangYn: place?.themeNatureHangangYn || 'N',
        themeDateYn: place?.themeDateYn || 'N',
        themeFoodTourYn: place?.themeFoodTourYn || 'N',
        themeCafeTourYn: place?.themeCafeTourYn || 'N',
        themeShoppingHotplaceYn: place?.themeShoppingHotplaceYn || 'N',
        themeNightViewYn: place?.themeNightViewYn || 'N',
        themeHotelStayYn: place?.themeHotelStayYn || 'N',
        alternativeCandidates: [],
    };
}

function recalculateDetailDayTimes(day, places) {
    const originStart = timeToMinutes(day?.routeOriginPlace?.displayVisitTime);
    const firstStart = timeToMinutes(places[0]?.displayVisitTime);
    let cursor = originStart ?? firstStart ?? (10 * 60);

    return places.map((place, index) => {
        if (index > 0 || day?.routeOriginPlace) {
            cursor += toFiniteNumber(place.travelTimeFromPreviousMinutes);
        }

        const visitTime = minutesToTime(cursor);
        cursor += toFiniteNumber(place.expectedVisitMinutes);

        return {
            ...place,
            visitTime,
            expectedVisitTimeHHmm: visitTime,
            displayVisitTime: visitTime,
        };
    });
}

function recalculateDetailCourse(course, nextDays) {
    const days = nextDays.map((day) => {
        const places = recalculateDetailDayTimes(day, day.places || []);
        const dailyDistanceKm = places.reduce(
            (sum, place) => sum + toFiniteNumber(place.distanceFromPreviousKm),
            0,
        );
        const dailyTravelTimeMinutes = places.reduce(
            (sum, place) => sum + toFiniteNumber(place.travelTimeFromPreviousMinutes),
            0,
        );
        const dailyVisitTimeMinutes = places.reduce(
            (sum, place) => sum + toFiniteNumber(place.expectedVisitMinutes),
            0,
        );

        return {
            ...day,
            places,
            dailyDistanceKm,
            dailyTravelTimeMinutes,
            dailyVisitTimeMinutes,
            dailyCourseTimeMinutes:
                dailyTravelTimeMinutes + dailyVisitTimeMinutes,
            routeDetailsAttempted: true,
        };
    });
    const totalDistanceKm = days.reduce(
        (sum, day) => sum + toFiniteNumber(day.dailyDistanceKm),
        0,
    );
    const totalTravelTimeMinutes = days.reduce(
        (sum, day) => sum + toFiniteNumber(day.dailyTravelTimeMinutes),
        0,
    );
    const totalVisitTimeMinutes = days.reduce(
        (sum, day) => sum + toFiniteNumber(day.dailyVisitTimeMinutes),
        0,
    );
    const estimatedTravelTimes = days.some(hasEstimatedTravelLeg);

    return normalizeCourseDetail({
        ...course,
        days,
        totalDistanceKm,
        totalTravelTimeMinutes,
        totalVisitTimeMinutes,
        totalCourseTimeMinutes:
            totalTravelTimeMinutes + totalVisitTimeMinutes,
        estimatedTravelTimes,
    });
}

function mergeDetailRouteResponse(course, dayNo, routeDetails) {
    const day = (course?.days || []).find((candidate) => candidate.dayNo === dayNo);
    const originalPlaces = day?.places || [];
    const resolvedPlaces = Array.isArray(routeDetails?.optimizedPlaces)
        ? routeDetails.optimizedPlaces
        : [];
    const routeOriginOffset = day?.routeOriginPlace ? 1 : 0;

    if (resolvedPlaces.length !== originalPlaces.length + routeOriginOffset) {
        return course;
    }

    const places = originalPlaces.map((place, index) => {
        const resolved = resolvedPlaces[index + routeOriginOffset];
        return {
            ...place,
            distanceFromPreviousKm:
                Number(resolved?.distanceFromPreviousKm) || 0,
            travelTimeFromPreviousMinutes:
                Number(resolved?.travelTimeFromPreviousMinutes) || 0,
            transitPathType: normalizeTransitPathType(resolved?.transitPathType),
            routeEstimated: Boolean(resolved?.routeEstimated),
        };
    });
    const nextDays = course.days.map((candidate) => (
        candidate.dayNo === dayNo
            ? { ...candidate, places, routeDetailsAttempted: true }
            : candidate
    ));

    return recalculateDetailCourse(course, nextDays);
}

function toHistoryPlace(place) {
    return {
        placeId: place.placeId,
        placeName: place.placeName,
        category: place.category,
        region: place.region,
        address: place.address,
        roadAddress: place.roadAddress,
        imageUrl: place.imageUrl,
        latitude: place.latitude,
        longitude: place.longitude,
        recommendationScore: place.recommendationScore,
        themePalaceCultureYn: place.themePalaceCultureYn,
        themeNatureHangangYn: place.themeNatureHangangYn,
        themeDateYn: place.themeDateYn,
        themeFoodTourYn: place.themeFoodTourYn,
        themeCafeTourYn: place.themeCafeTourYn,
        themeShoppingHotplaceYn: place.themeShoppingHotplaceYn,
        themeNightViewYn: place.themeNightViewYn,
        themeHotelStayYn: place.themeHotelStayYn,
        visitOrder: place.visitOrder,
        visitTime: place.visitTime || place.displayVisitTime,
        expectedVisitMinutes: place.expectedVisitMinutes,
        distanceFromPreviousKm: place.distanceFromPreviousKm,
        travelTimeFromPreviousMinutes: place.travelTimeFromPreviousMinutes,
        transitPathType: normalizeTransitPathType(place.transitPathType),
        routeEstimated: Boolean(place.routeEstimated),
    };
}

function buildDetailHistoryRefreshPayload(course) {
    return {
        resultId: course.resultId,
        travelCode: course.travelCode,
        transportMode: course.transportMode,
        estimatedTravelTimes: Boolean(course.estimatedTravelTimes),
        optionCount: 1,
        courseOptions: [{
            courseId: course.courseId,
            optionNo: course.optionNo || 1,
            optionType: course.optionType || 'PREFERENCE',
            optionName: course.optionName || course.title,
            title: course.title,
            description: course.description,
            region: course.region,
            placeCount: course.placeCount,
            dayCount: course.dayCount,
            totalDistanceKm: course.totalDistanceKm,
            totalTravelTimeMinutes: course.totalTravelTimeMinutes,
            totalVisitTimeMinutes: course.totalVisitTimeMinutes,
            totalCourseTimeMinutes: course.totalCourseTimeMinutes,
            estimatedTravelTimes: Boolean(course.estimatedTravelTimes),
            days: (course.days || []).map((day) => ({
                dayNo: day.dayNo,
                visitDate: day.visitDate,
                dailyDistanceKm: day.dailyDistanceKm,
                dailyTravelTimeMinutes: day.dailyTravelTimeMinutes,
                dailyVisitTimeMinutes: day.dailyVisitTimeMinutes,
                dailyCourseTimeMinutes: day.dailyCourseTimeMinutes,
                routeDetailsAttempted: true,
                routeOriginPlace: day.routeOriginPlace
                    ? toHistoryPlace(day.routeOriginPlace)
                    : null,
                places: (day.places || []).map(toHistoryPlace),
            })),
        }],
    };
}

async function refreshEstimatedDetailRoutes(course, memberId) {
    let refreshedCourse = course;
    const transportMode = normalizeTransportMode(course?.transportMode);
    const transportLabel = getTransportMeta(transportMode)?.label || '이동';
    let refreshedAnyDay = false;

    for (const day of course.days || []) {
        const needsRefresh = hasEstimatedTravelLeg(day)
            || (
                transportMode === 'PUBLIC_TRANSIT'
                && hasPublicTransitDayLimitViolation(day)
            );
        if (!needsRefresh) continue;

        const routePlaces = day.routeOriginPlace
            ? [day.routeOriginPlace, ...(day.places || [])]
            : (day.places || []);
        const candidates = routePlaces.map((place) => (
            toDetailRouteCandidate(place, day.visitDate)
        ));
        const hasInvalidCoordinates = candidates.some((candidate) => (
            !Number.isFinite(candidate.latitude)
            || !Number.isFinite(candidate.longitude)
        ));
        if (hasInvalidCoordinates || candidates.length < 2) continue;

        try {
            const routeDetails = await resolveCourseRouteDetails({
                resultId: course.resultId,
                travelCode: course.travelCode,
                transportMode,
                dailyStartTime:
                    day.routeOriginPlace?.displayVisitTime
                    || day.places?.[0]?.displayVisitTime
                    || '10:00',
                enforcePublicTransitLimit: true,
                allowPublicTransitPlaceReduction: true,
                placeCandidates: candidates,
                alternativeCandidates: [],
            });
            if (
                transportMode === 'PUBLIC_TRANSIT'
                && hasResolvedPublicTransitRouteViolation(routeDetails)
            ) {
                throw new Error(
                    '대중교통 실제 경로가 30분 우선·40분 제한을 벗어났습니다.',
                );
            }
            refreshedCourse = mergeDetailRouteResponse(
                refreshedCourse,
                day.dayNo,
                routeDetails,
            );
            refreshedAnyDay = true;
        } catch (error) {
            console.warn(`DAY ${day.dayNo} 실제 ${transportLabel} 경로 재조회 실패:`, error);
        }
    }

    if (refreshedAnyDay) {
        await recordRecommendedCourses(
            buildDetailHistoryRefreshPayload(refreshedCourse),
            { memberId },
        );
    }

    return refreshedCourse;
}

/** 저장 코스를 조회해 날짜별 일정·합계·지도 정보를 보여주는 상세 화면입니다. */
function CourseDetailPage() {
    const courseId = useMemo(() => getCourseId(), []);
    const courseListPath = useMemo(() => getCourseListPath(), []);
    const isThemeCoursePath = useMemo(
        () => window.location.pathname.startsWith('/courses/themes/'),
        [],
    );
    const themeCourse = useMemo(
        () => (isThemeCoursePath ? getThemeCourseById(courseId) : null),
        [courseId, isThemeCoursePath],
    );
    const initialThemeCourse = useMemo(
        () => (themeCourse ? normalizeCourseDetail(themeCourse) : null),
        [themeCourse],
    );
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const previewCourse = useMemo(() => buildPreviewCourse(courseId), [courseId]);
    const [course, setCourse] = useState(initialThemeCourse);
    const [status, setStatus] = useState(
        courseId
            ? isThemeCoursePath
                ? initialThemeCourse
                    ? 'success'
                    : 'not-found'
                : 'loading'
            : 'redirecting',
    );
    const [source, setSource] = useState(isThemeCoursePath ? 'theme' : 'api');
    const [errorMessage, setErrorMessage] = useState(
        isThemeCoursePath && !initialThemeCourse
            ? '선택한 테마 코스를 찾을 수 없습니다.'
            : '',
    );
    const [reloadKey, setReloadKey] = useState(0);
    const [activeDayNo, setActiveDayNo] = useState(
        initialThemeCourse?.days[0]?.dayNo ?? 1,
    );
    const [isSavingCourse, setIsSavingCourse] = useState(false);
    const [savedCourseId, setSavedCourseId] = useState(null);
    const [toast, setToast] = useState(null);
    const heroMetaRef = useRef(null);
    const routeRefreshStartedRef = useRef(new Set());
    const [isHeroMetaWrapped, setIsHeroMetaWrapped] = useState(false);
    const themeBookmarkCourses = useMemo(
        () => (themeCourse ? [themeCourse] : []),
        [themeCourse],
    );
    const themeBookmarks = useThemeCourseBookmarks(memberId, {
        enabled: isThemeCoursePath,
        courses: themeBookmarkCourses,
    });

    useLayoutEffect(() => {
        if (status !== 'success' || !course) {
            setIsHeroMetaWrapped(false);
            return undefined;
        }

        const metaElement = heroMetaRef.current;

        if (!metaElement) {
            return undefined;
        }

        const updateWrapState = () => {
            const items = Array.from(metaElement.children);

            if (items.length < 2) {
                setIsHeroMetaWrapped(false);
                return;
            }

            const firstRowTop = items[0].offsetTop;
            const wrapped = items.some((item) => item.offsetTop > firstRowTop + 2);
            setIsHeroMetaWrapped((previous) => (previous === wrapped ? previous : wrapped));
        };

        updateWrapState();

        const resizeObserver = typeof ResizeObserver === 'function'
            ? new ResizeObserver(updateWrapState)
            : null;

        resizeObserver?.observe(metaElement);
        window.addEventListener('resize', updateWrapState);

        return () => {
            resizeObserver?.disconnect();
            window.removeEventListener('resize', updateWrapState);
        };
    }, [course, status]);

    useEffect(() => {
        if (!isThemeCoursePath || !themeCourse) {
            return;
        }

        const loadThemeCoursePlaces = async () => {
            try {
                const placeNames = [
                    ...new Set(
                        themeCourse.days.flatMap((day) =>
                            day.places
                                .map((place) => place.placeName)
                                .filter(Boolean)
                        )
                    ),
                ];

                if (placeNames.length === 0) {
                    return;
                }

                const dbPlaces = await getThemePlacesByNames(placeNames);

                const placeMap = new Map(
                    (Array.isArray(dbPlaces) ? dbPlaces : []).map(
                        (place) => [place.name?.trim(), place]
                    )
                );

                const courseWithDbPlaces = {
                    ...themeCourse,
                    days: themeCourse.days.map((day) => ({
                        ...day,
                        places: day.places.map((place) => {
                            const dbPlace = placeMap.get(
                                place.placeName?.trim()
                            );

                            if (!dbPlace) {
                                return place;
                            }

                            return {
                                ...place,
                                databaseMatched: true,
                                placeId:
                                    dbPlace.placeId ?? place.placeId,
                                placeName:
                                    dbPlace.name || place.placeName,
                                category:
                                    dbPlace.category || place.category,
                                imageUrl:
                                    dbPlace.imageUrl || place.imageUrl,
                                databaseDescription:
                                    dbPlace.description
                                    || place.databaseDescription,
                                address:
                                    dbPlace.address || place.address,
                                latitude:
                                    dbPlace.latitude ?? place.latitude,
                                longitude:
                                    dbPlace.longitude ?? place.longitude,
                            };
                        }),
                    })),
                };

                const normalizedCourse =
                    normalizeCourseDetail(courseWithDbPlaces);

                setCourse(normalizedCourse);
                setActiveDayNo(
                    normalizedCourse.days?.[0]?.dayNo ?? 1
                );
            } catch (error) {
                console.error(
                    '테마 코스 장소 이미지 조회 실패:',
                    error
                );
            }
        };

        loadThemeCoursePlaces();
    }, [isThemeCoursePath, themeCourse]);

    useEffect(() => {
        if (!courseId) {
            window.location.replace(courseListPath);
            return undefined;
        }

        if (isThemeCoursePath) {
            return undefined;
        }

        const controller = new AbortController();

        // 목록 요약이 아닌 저장 코스 상세 API를 기준으로 일정과 이동 정보를 갱신합니다.
        Promise.all([
            getCourseDetail(courseId, {
                memberId,
                signal: controller.signal,
            }),
            memberId
                ? getMyCourses(memberId, { signal: controller.signal })
                    .catch((error) => {
                        if (error?.name === 'AbortError') throw error;
                        return [];
                    })
                : Promise.resolve([]),
        ])
            .then(([response, myCoursesResponse]) => {
                const normalizedCourse = normalizeApiCourseDetail(
                    adaptCurrentCourseResponse(response),
                    courseId,
                );

                if (!normalizedCourse.days.some((day) => day.places.length > 0)) {
                    setCourse(null);
                    setStatus('empty');
                    return;
                }

                setCourse(normalizedCourse);
                setActiveDayNo(normalizedCourse.days[0]?.dayNo ?? 1);
                setIsSavingCourse(false);
                setSavedCourseId(
                    normalizeMyCourseList(myCoursesResponse).some(
                        (savedCourse) => savedCourse.courseId === courseId,
                    )
                        ? courseId
                        : null,
                );
                setSource('api');
                setErrorMessage('');
                setStatus('success');
            })
            .catch((error) => {
                if (error?.name === 'AbortError') return;
                setErrorMessage(error?.message || '코스 상세 정보를 불러오지 못했습니다.');
                setStatus(
                    error?.status === 404 || error?.code === 'COURSE_NOT_FOUND'
                        ? 'not-found'
                        : 'error',
                );
            });

        return () => controller.abort();
    }, [courseId, courseListPath, isThemeCoursePath, memberId, reloadKey]);

    useEffect(() => {
        const normalizedCourseId = Number(course?.courseId || courseId);
        const normalizedResultId = Number(course?.resultId);
        const refreshKey = `${normalizedCourseId}:${normalizedResultId}`;
        const refreshTransport = getTransportMeta(course?.transportMode);
        const isPublicTransitLimitRepair = normalizeTransportMode(
            course?.transportMode,
        ) === 'PUBLIC_TRANSIT'
            && (course?.days || []).some(hasPublicTransitDayLimitViolation);

        if (
            status !== 'success'
            || source !== 'api'
            || String(course?.courseType || '').toUpperCase() !== 'SURVEY'
            || !Number.isInteger(normalizedCourseId)
            || normalizedCourseId < 1
            || !Number.isInteger(normalizedResultId)
            || normalizedResultId < 1
            || !Number.isInteger(memberId)
            || memberId < 1
            || !needsCourseRouteRefresh(course)
            || routeRefreshStartedRef.current.has(refreshKey)
        ) {
            return;
        }

        routeRefreshStartedRef.current.add(refreshKey);
        setToast({
            tone: 'info',
            message: isPublicTransitLimitRepair
                ? '대중교통 실제 경로와 이동 제한을 다시 확인하고 있어요.'
                : `예상 구간의 실제 ${refreshTransport?.label || '이동'} 경로를 확인하고 있어요.`,
        });
        let refreshPromise = detailRouteRefreshPromises.get(refreshKey);

        if (!refreshPromise) {
            refreshPromise = refreshEstimatedDetailRoutes(course, memberId)
                .finally(() => detailRouteRefreshPromises.delete(refreshKey));
            detailRouteRefreshPromises.set(refreshKey, refreshPromise);
        }

        refreshPromise
            .then((refreshedCourse) => {
                setCourse(refreshedCourse);
                setToast(
                    !needsCourseRouteRefresh(refreshedCourse)
                        ? {
                            tone: 'success',
                            message: `${refreshTransport?.label || '이동'} 실제 경로로 다시 확인했어요.`,
                        }
                        : {
                            tone: 'info',
                            message: '일부 구간은 실제 경로나 이동 제한을 다시 확인하지 못했어요.',
                        },
                );
            })
            .catch((error) => {
                console.warn('상세 실제 경로 보정 실패:', error);
            });
    }, [course, courseId, memberId, source, status]);

    const activeDay = course?.days.find((day) => day.dayNo === activeDayNo)
        || course?.days[0]
        || null;
    const activeDayIndex = course?.days.findIndex(
        (day) => day.dayNo === activeDay?.dayNo,
    ) ?? -1;
    const themeTags = course ? getThemeTags(course) : [];
    const dateRange = course
        ? course.courseType === 'THEME'
            ? course.dayCount > 1
                ? `${course.dayCount - 1}박 ${course.dayCount}일`
                : '당일치기'
            : getDateRange(course.days)
        : '';

    const travelCodeLabels = course?.travelCode
        ? course.travelCode
            .split('')
            .map((letter) => travelTypeLabels[letter])
            .filter(Boolean)
        : [];
    const transport = getTransportMeta(course?.transportMode);
    const activeDayHasEstimatedTravelTimes = hasEstimatedTravelLeg(activeDay);
    const themeCourseSaved = isThemeCoursePath
        && themeBookmarks.isSaved(themeCourse?.sourceCourseKey);
    const themeCourseBookmarkBusy = isThemeCoursePath
        && themeBookmarks.isBusy(themeCourse?.sourceCourseKey);
    const scheduleNotice = activeDayHasEstimatedTravelTimes
        ? estimatedScheduleNotices[transport?.transportMode]
            || '경로를 불러오지 못한 일부 이동 구간은 예상값입니다. 실제 이동 상황에 따라 달라질 수 있습니다.'
        : compactScheduleNotices[transport?.transportMode]
            || '이동 거리와 시간을 기준으로 구성한 추천 동선입니다. 실제 상황에 따라 달라질 수 있습니다.';

    const moveActiveDay = (offset) => {
        const targetDay = course?.days[activeDayIndex + offset];
        if (targetDay) setActiveDayNo(targetDay.dayNo);
    };

    const showPreview = () => {
        setCourse(previewCourse);
        setActiveDayNo(previewCourse.days[0]?.dayNo ?? 1);
        setIsSavingCourse(false);
        setSavedCourseId(null);
        setSource('preview');
        setErrorMessage('');
        setStatus('success');
    };

    const retryCourseDetail = () => {
        setStatus('loading');
        setErrorMessage('');
        setReloadKey((value) => value + 1);
    };

    const handleShare = async () => {
        const shareData = {
            title: course?.title || 'SEOULINK 추천 코스',
            text: course?.description || '서울 여행 추천 코스를 확인해보세요.',
            url: window.location.href,
        };

        try {
            if (navigator.share) {
                await navigator.share(shareData);
                return;
            }

            await navigator.clipboard.writeText(window.location.href);
            setToast({ tone: 'success', message: '코스 링크를 복사했어요.' });
        } catch (error) {
            if (error?.name !== 'AbortError') {
                setToast({ tone: 'error', message: '링크를 복사하지 못했어요.' });
            }
        }
    };

    const handleSaveCourse = async () => {
        if (isSavingCourse || savedCourseId || !course) return;

        if (!requireLogin('코스 저장은 로그인 후 이용할 수 있습니다.')) {
            return;
        }

        if (source === 'preview') {
            setToast({
                tone: 'info',
                message: '현재는 화면 미리보기입니다. 실제 코스 정보를 불러온 뒤 저장해 주세요.',
            });
            return;
        }

        if (!memberId) {
            setToast({
                tone: 'error',
                message: '회원 ID를 확인할 수 없습니다. 다시 로그인한 뒤 저장해 주세요.',
            });
            return;
        }

        setIsSavingCourse(true);
        setToast(null);

        try {
            const savedCourse = await saveCourse(
                buildDetailSaveRequest(course, memberId),
            );
            const nextSavedCourseId = Number(savedCourse?.courseId);

            if (!Number.isInteger(nextSavedCourseId) || nextSavedCourseId < 1) {
                throw new Error('저장 결과의 코스 ID를 확인할 수 없습니다.');
            }

            rememberCourseTravelCode(nextSavedCourseId, course.travelCode);
            setSavedCourseId(nextSavedCourseId);
            window.location.href = '/mypage/courses';
        } catch (error) {
            setToast({
                tone: 'error',
                message: error?.message
                    || '코스를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.',
            });
        } finally {
            setIsSavingCourse(false);
        }
    };

    const handleThemeBookmark = async () => {
        if (!course || !themeCourse) return;

        if (!requireLogin('코스 저장은 로그인 후 이용할 수 있습니다.')) {
            return;
        }

        if (!memberId) {
            setToast({
                tone: 'error',
                message: '회원 ID를 확인할 수 없습니다. 다시 로그인한 뒤 저장해 주세요.',
            });
            return;
        }

        setToast(null);

        try {
            const saved = await themeBookmarks.toggle(course);
            setToast({
                tone: 'success',
                message: saved
                    ? '저장한 추천 코스에 추가했어요.'
                    : '저장한 추천 코스에서 삭제했어요.',
            });
        } catch (error) {
            setToast({
                tone: 'error',
                message: error?.message
                    || '코스 저장 상태를 변경하지 못했습니다.',
            });
        }
    };

    const handleReturnToCourseList = () => {
        const entry = readCourseDetailEntry(courseId);
        const returnPath = entry?.returnPath;

        if (typeof returnPath === 'string' && /^\/(?!\/)/.test(returnPath)) {
            window.location.href = returnPath;
            return;
        }

        if (document.referrer.startsWith(window.location.origin)) {
            window.history.back();
            return;
        }

        window.location.href = courseListPath;
    };

    return (
        <div className="page course-detail-page">
            <Header variant="default" />

            <main className="course-detail-shell">
                <div className="course-detail-toolbar">
                    <button type="button" className="course-detail-back-link" onClick={handleReturnToCourseList}>
                        <ArrowLeft size={18} aria-hidden="true" />
                        돌아가기
                    </button>

                    {status === 'success' && (
                        <div className="course-detail-toolbar-actions">
                            <button
                                className={
                                    isThemeCoursePath
                                        ? themeCourseSaved ? 'is-active' : ''
                                        : savedCourseId ? 'is-active' : ''
                                }
                                type="button"
                                disabled={
                                    isThemeCoursePath
                                        ? themeCourseBookmarkBusy
                                        : isSavingCourse || Boolean(savedCourseId)
                                }
                                aria-label={
                                    isThemeCoursePath
                                        ? themeCourseSaved ? '저장 해제' : '코스 저장'
                                        : savedCourseId ? '저장됨' : '코스 저장'
                                }
                                aria-pressed={
                                    isThemeCoursePath
                                        ? themeCourseSaved
                                        : Boolean(savedCourseId)
                                }
                                aria-busy={
                                    isThemeCoursePath
                                        ? themeCourseBookmarkBusy
                                        : isSavingCourse
                                }
                                onClick={
                                    isThemeCoursePath
                                        ? handleThemeBookmark
                                        : handleSaveCourse
                                }
                            >
                                {(isThemeCoursePath ? themeCourseSaved : savedCourseId)
                                    ? <Check size={17} aria-hidden="true" />
                                    : <Save size={17} aria-hidden="true" />}
                                {isThemeCoursePath
                                    ? themeCourseBookmarkBusy
                                        ? '처리 중...'
                                        : themeCourseSaved
                                            ? '저장됨'
                                            : '저장'
                                    : isSavingCourse
                                        ? '저장 중...'
                                        : savedCourseId
                                            ? '저장됨'
                                            : '저장'}
                            </button>
                            <button type="button" onClick={handleShare}>
                                <Share2 size={17} aria-hidden="true" />
                                공유하기
                            </button>
                        </div>
                    )}
                </div>

                {(status === 'loading' || status === 'redirecting') && (
                    <section className="course-detail-state-card" aria-live="polite">
                        <span className="course-detail-spinner" aria-hidden="true" />
                        <h1>
                            {status === 'redirecting'
                                ? '코스 목록으로 이동하고 있어요'
                                : '코스 상세 정보를 불러오고 있어요'}
                        </h1>
                        <p>
                            {status === 'redirecting'
                                ? '올바른 코스를 다시 선택할 수 있도록 목록으로 안내할게요.'
                                : '날짜별 일정과 이동 동선을 정리하는 중입니다.'}
                        </p>
                    </section>
                )}

                {status === 'not-found' && (
                    <section className="course-detail-state-card" role="alert">
                        <span className="course-detail-state-icon error"><Info size={25} aria-hidden="true" /></span>
                        <h1>코스를 찾을 수 없어요</h1>
                        <p>{errorMessage || '삭제되었거나 존재하지 않는 코스입니다.'}</p>
                        <div>
                            <button type="button" onClick={handleReturnToCourseList}>
                                내 코스로 돌아가기
                            </button>
                            <button className="secondary" type="button" onClick={showPreview}>
                                임시 화면 보기
                            </button>
                        </div>
                    </section>
                )}

                {status === 'empty' && (
                    <section className="course-detail-state-card">
                        <span className="course-detail-state-icon"><Route size={25} aria-hidden="true" /></span>
                        <h1>표시할 코스 일정이 없어요</h1>
                        <p>저장된 장소 정보가 비어 있습니다. 목록에서 다른 코스를 선택해주세요.</p>
                        <div>
                            <button type="button" onClick={handleReturnToCourseList}>
                                내 코스로 돌아가기
                            </button>
                            <button className="secondary" type="button" onClick={showPreview}>
                                임시 화면 보기
                            </button>
                        </div>
                    </section>
                )}

                {status === 'error' && (
                    <section className="course-detail-state-card" role="alert">
                        <span className="course-detail-state-icon error"><Info size={25} aria-hidden="true" /></span>
                        <h1>코스를 불러오지 못했어요</h1>
                        <p>{errorMessage}</p>
                        <div>
                            <button type="button" onClick={retryCourseDetail}>
                                <RefreshCw size={16} aria-hidden="true" /> 다시 불러오기
                            </button>
                            <button className="secondary" type="button" onClick={showPreview}>
                                임시 화면 보기
                            </button>
                        </div>
                    </section>
                )}

                {status === 'success' && course && (
                    <>
                        <div className="course-detail-content-grid">
                            <div className="course-detail-main-column">
                                <section className={`course-detail-hero${isHeroMetaWrapped ? ' is-meta-wrapped' : ''}`}>
                            <div className="course-detail-hero-copy">
                                {(source === 'preview' || (!isThemeCoursePath && course.travelCode)) && (
                                    <div className="course-detail-label-row">
                                        {source === 'preview' && (
                                            <span className="course-detail-preview-label">
                                                <Info size={13} aria-hidden="true" /> UI 미리보기
                                            </span>
                                        )}

                                        {!isThemeCoursePath && course.travelCode && (
                                            <span
                                                className="course-detail-preference-code-label"
                                                aria-label={`취향 코드 ${course.travelCode}`}
                                            >
                                                <Tag size={13} aria-hidden="true" />
                                                취향 코드 · <strong>{course.travelCode}</strong>
                                            </span>
                                        )}
                                    </div>
                                )}

                                <h1>{course.title}</h1>

                                <div className="course-detail-tags" aria-label="코스 테마">
                                    {themeTags.slice(0, 4).map((tag) => <span key={tag}>{tag}</span>)}
                                </div>

                                <p>{course.description}</p>

                                <div ref={heroMetaRef} className="course-detail-hero-meta">
                                    <span><CalendarDays size={17} aria-hidden="true" />{dateRange}</span>
                                    <span><Route size={17} aria-hidden="true" />{course.dayCount}일 코스</span>
                                    <span><Compass size={17} aria-hidden="true" />{getCourseTypeLabel(course.courseType)}</span>
                                    {transport && (
                                        <span className="course-detail-transport-badge">
                                            <CourseTransportIcon
                                                transportMode={course.transportMode}
                                                size={17}
                                                aria-hidden="true"
                                            />
                                            {transport.label} 이동
                                        </span>
                                    )}
                                </div>
                            </div>

                            <div className="course-detail-hero-image">
                                <CourseImage
                                    imageUrls={course.coverImageUrls}
                                    fallbackImageUrl={course.coverFallbackImageUrl}
                                    alt={`${course.title} 대표 이미지`}
                                    fallbackLabel="예시 사진"
                                    fallbackLabelClassName="course-detail-hero-image-label"
                                />
                            </div>
                                </section>

                                <section className="course-detail-metrics" aria-label="코스 요약">
                            <article>
                                <span><MapPinned size={21} aria-hidden="true" /></span>
                                <div><small>이동 거리</small><strong>약 {course.totalDistanceKm.toFixed(1)}km</strong></div>
                            </article>
                            <article>
                                <span><Clock3 size={21} aria-hidden="true" /></span>
                                <div><small>전체 소요 시간</small><strong>약 {formatMinutes(course.totalCourseTimeMinutes)}</strong></div>
                            </article>
                            <article>
                                <span>
                                    <CourseTransportIcon
                                        transportMode={course.transportMode}
                                        size={21}
                                        aria-hidden="true"
                                    />
                                </span>
                                <div>
                                    <small>{transport ? `${transport.label} 이동 시간` : '총 이동 시간'}</small>
                                    <strong>약 {formatMinutes(course.totalTravelTimeMinutes)}</strong>
                                </div>
                            </article>
                            <article>
                                <span><Timer size={21} aria-hidden="true" /></span>
                                <div><small>장소 체류 시간</small><strong>약 {formatMinutes(course.totalVisitTimeMinutes)}</strong></div>
                            </article>
                            <article>
                                <span><MapPin size={21} aria-hidden="true" /></span>
                                <div><small>방문 장소</small><strong>{course.placeCount}곳</strong></div>
                            </article>
                                </section>

                                <section className="course-detail-schedule-card">
                                <div className="course-detail-schedule-heading">
                                    <div>
                                        <span>
                                            {activeDay
                                                ? `DAY ${activeDay.dayNo}${activeDay.visitDate
                                                    ? ` · ${formatDate(activeDay.visitDate, { compact: true })}`
                                                    : ''}`
                                                : '저장된 코스'}
                                        </span>
                                        <h2>상세 일정</h2>
                                    </div>

                                    {course.days.length > 1 && (
                                        <div className="course-detail-day-selector">
                                            <button
                                                className="course-detail-day-arrow"
                                                type="button"
                                                onClick={() => moveActiveDay(-1)}
                                                disabled={activeDayIndex <= 0}
                                                aria-label="이전 날짜 일정"
                                            >
                                                <ChevronLeft size={18} aria-hidden="true" />
                                            </button>

                                            <div className="course-detail-day-tabs" aria-label="일차 선택">
                                                {course.days.slice(0, 7).map((day) => (
                                                    <button
                                                        className={day.dayNo === activeDay?.dayNo ? 'active' : ''}
                                                        type="button"
                                                        key={`${day.dayNo}-${day.visitDate}`}
                                                        onClick={() => setActiveDayNo(day.dayNo)}
                                                    >
                                                        DAY {day.dayNo}
                                                        {day.visitDate && (
                                                            <small>{formatDate(day.visitDate, { compact: true })}</small>
                                                        )}
                                                    </button>
                                                ))}
                                            </div>

                                            <button
                                                className="course-detail-day-arrow"
                                                type="button"
                                                onClick={() => moveActiveDay(1)}
                                                disabled={activeDayIndex >= Math.min(course.days.length, 7) - 1}
                                                aria-label="다음 날짜 일정"
                                            >
                                                <ChevronRight size={18} aria-hidden="true" />
                                            </button>
                                        </div>
                                    )}
                                </div>

                                <CoursePlaceList
                                    day={activeDay}
                                    transportMode={course.transportMode}
                                />

                                <p className={`course-detail-schedule-note${activeDayHasEstimatedTravelTimes ? ' is-estimated' : ''}`}>
                                    <Info size={14} aria-hidden="true" />
                                    <span className="course-detail-schedule-note-copy">
                                        {scheduleNotice}
                                    </span>
                                </p>
                                </section>
                            </div>

                            <aside className="course-detail-sidebar">
                                <CourseRouteMap day={activeDay} />

                                <section className="course-detail-overview-card">
                                    <div className="course-detail-side-heading">
                                        <div><Sparkles size={20} aria-hidden="true" /><h2>코스 한눈에 보기</h2></div>
                                    </div>

                                    <dl>
                                        <div>
                                            <dt><CalendarDays size={18} aria-hidden="true" />여행 기간</dt>
                                            <dd>{dateRange}</dd>
                                        </div>
                                        <div>
                                            <dt><Route size={18} aria-hidden="true" />코스 구성</dt>
                                            <dd>{course.dayCount}일 · {course.placeCount}곳</dd>
                                        </div>
                                        {!isThemeCoursePath && course.travelCode && (
                                            <div>
                                                <dt><Tag size={18} aria-hidden="true" />취향 코드</dt>
                                                <dd className="course-detail-preference-code-description">
                                                    <strong>{course.travelCode}</strong>
                                                    {travelCodeLabels.length > 0 && (
                                                        <span>{travelCodeLabels.join(' · ')}</span>
                                                    )}
                                                </dd>
                                            </div>
                                        )}
                                        {transport && (
                                            <div>
                                                <dt>
                                                    <CourseTransportIcon
                                                        transportMode={course.transportMode}
                                                        size={18}
                                                        aria-hidden="true"
                                                    />
                                                    이동수단
                                                </dt>
                                                <dd>{transport.label}</dd>
                                            </div>
                                        )}
                                        <div>
                                            <dt><Heart size={18} aria-hidden="true" />여행 테마</dt>
                                            <dd>{themeTags.join(', ')}</dd>
                                        </div>
                                        <div>
                                            <dt><Lightbulb size={18} aria-hidden="true" />추천 이유</dt>
                                            <dd>{course.description}</dd>
                                        </div>
                                    </dl>

                                </section>

                            </aside>
                        </div>
                    </>
                )}
            </main>

            <Footer />

            {toast && (
                <div className={`course-detail-toast ${toast.tone}`} role="status">
                    <span>{toast.tone === 'success' ? <Check size={17} aria-hidden="true" /> : <Info size={17} aria-hidden="true" />}</span>
                    <p>{toast.message}</p>
                    <button type="button" aria-label="알림 닫기" onClick={() => setToast(null)}>
                        <X size={15} aria-hidden="true" />
                    </button>
                </div>
            )}
        </div>
    );
}

export default CourseDetailPage;
