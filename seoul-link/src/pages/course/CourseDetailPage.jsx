import { useEffect, useMemo, useState } from 'react';
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
import KakaoCourseMap from '../../components/course/KakaoCourseMap';
import CourseTransportIcon from '../../components/course/CourseTransportIcon';
import {
    getCourseDetail,
    getMyCourses,
    saveCourse,
} from '../../api/courseApi';
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
import recommendationPreview from '../../mocks/courseRecommendation.json';
import { mockThemeCourseListResponse } from '../../mocks/homeMockData';
import hanokImage from '../../assets/images/moods/mood-hanok-photo.png';
import walkingImage from '../../assets/images/moods/mood-walking-alley.png';
import localFoodImage from '../../assets/images/moods/mood-local-food.png';
import rainyCafeImage from '../../assets/images/moods/mood-rainy-cafe.png';
import sunsetImage from '../../assets/images/moods/mood-sunset-seoul.png';

const placeFallbackImages = [
    hanokImage,
    walkingImage,
    localFoodImage,
    rainyCafeImage,
    sunsetImage,
];

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
const COURSE_DETAIL_ENTRY_KEY = 'seoulinkCourseDetailEntry';

/** 지원하는 상세 경로 전체가 정확히 일치할 때만 조회할 courseId를 구합니다. */
function getCourseId() {
    const match = window.location.pathname.match(
        /^(?:\/courses\/(?:recommendations\/)?|\/mypage\/courses\/)([1-9]\d*)\/?$/,
    );
    const courseId = Number(match?.[1]);

    return Number.isInteger(courseId) && courseId > 0 ? courseId : null;
}

/** 현재 상세 경로의 종류에 맞는 안전한 목록 복귀 경로를 반환합니다. */
function getCourseListPath() {
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
        const sortedPlaces = [...(Array.isArray(day?.places) ? day.places : [])]
            .sort((first, second) => (
                toFiniteNumber(first.visitOrder, 999) - toFiniteNumber(second.visitOrder, 999)
            ));

        const places = sortedPlaces.map((place, placeIndex) => {
            const explicitTime = extractTime(place.expectedVisitTimeHHmm || place.visitTime);
            const expectedVisitMinutes = isHotelCategory(place.category)
                ? 0
                : toFiniteNumber(place.expectedVisitMinutes);
            const actualImageUrl = place.imageUrl
                || place.placeImageUrl
                || place.thumbnailUrl
                || null;
            const fallbackImageUrl = placeFallbackImages[
                (placeIndex + dayIndex) % placeFallbackImages.length
            ];

            // 서버가 예상 시각을 주지 않으면 10:00부터 이동·체류시간을 누적해 표시합니다.
            if (explicitTime) {
                timeCursor = timeToMinutes(explicitTime);
            } else if (placeIndex > 0) {
                timeCursor += toFiniteNumber(place.travelTimeFromPreviousMinutes);
            }

            const displayVisitTime = explicitTime || minutesToTime(timeCursor);
            timeCursor = (timeToMinutes(displayVisitTime) ?? timeCursor)
                + expectedVisitMinutes;

            // 거리·시간·경로 종류는 모두 이전 장소에서 현재 장소로 들어오는 한 구간의 값입니다.
            return {
                ...place,
                visitOrder: toFiniteNumber(place.visitOrder, placeIndex + 1),
                expectedVisitMinutes,
                distanceFromPreviousKm: toFiniteNumber(place.distanceFromPreviousKm),
                travelTimeFromPreviousMinutes: toFiniteNumber(
                    place.travelTimeFromPreviousMinutes,
                ),
                transitPathType: normalizeTransitPathType(place.transitPathType),
                routeEstimated: Boolean(place.routeEstimated),
                displayVisitTime,
                fallbackImageUrl,
                displayImageUrl: actualImageUrl || fallbackImageUrl,
                displayImageIsExample: !actualImageUrl,
            };
        });

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

    return {
        ...rawCourse,
        title: rawCourse?.title || '서울 맞춤 추천 코스',
        description: rawCourse?.description
            || '취향과 장소 간 이동 거리를 반영해 만든 서울 여행 코스입니다.',
        coverImageUrl: rawCourse?.coverImageUrl
            || places.find((place) => place.imageUrl)?.imageUrl
            || hanokImage,
        travelCode: normalizeTravelCode(
            rawCourse?.travelCode
            || rawCourse?.preferenceCode
            || rawCourse?.typeCode
            || rawCourse?.surveyTypeCode,
        ),
        transportMode: normalizeTransportMode(rawCourse?.transportMode),
        estimatedTravelTimes: rawCourse?.estimatedTravelTimes == null
            ? places.some((place) => place.routeEstimated)
            : Boolean(rawCourse.estimatedTravelTimes),
        courseType: rawCourse?.courseType || 'SURVEY',
        placeCount: toFiniteNumber(rawCourse?.placeCount, places.length),
        dayCount: toFiniteNumber(rawCourse?.dayCount, days.length),
        totalDistanceKm: toFiniteNumber(rawCourse?.totalDistanceKm, sumDays('dailyDistanceKm')),
        totalTravelTimeMinutes,
        totalVisitTimeMinutes,
        totalCourseTimeMinutes: totalTravelTimeMinutes + totalVisitTimeMinutes,
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

    return {
        memberId,
        ...(courseType === 'SURVEY' ? { resultId } : {}),
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
        coverImageUrl: summary?.coverImageUrl || summary?.imageUrl || hanokImage,
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
                    places={day?.places || []}
                    ariaLabel={`DAY ${day?.dayNo || 1} 장소 방문 순서가 표시된 카카오 지도`}
                />
            </div>
        </section>
    );
}

/** 저장 코스를 조회해 날짜별 일정·합계·지도 정보를 보여주는 상세 화면입니다. */
function CourseDetailPage() {
    const courseId = useMemo(() => getCourseId(), []);
    const courseListPath = useMemo(() => getCourseListPath(), []);
    const memberId = useMemo(() => getCurrentMemberId(), []);
    const previewCourse = useMemo(() => buildPreviewCourse(courseId), [courseId]);
    const [course, setCourse] = useState(null);
    const [status, setStatus] = useState(courseId ? 'loading' : 'redirecting');
    const [source, setSource] = useState('api');
    const [errorMessage, setErrorMessage] = useState('');
    const [reloadKey, setReloadKey] = useState(0);
    const [activeDayNo, setActiveDayNo] = useState(1);
    const [isSavingCourse, setIsSavingCourse] = useState(false);
    const [savedCourseId, setSavedCourseId] = useState(null);
    const [toast, setToast] = useState(null);

    useEffect(() => {
        if (!courseId) {
            window.location.replace(courseListPath);
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
                const normalizedCourse = normalizeApiCourseDetail(response, courseId);

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
    }, [courseId, courseListPath, memberId, reloadKey]);

    const activeDay = course?.days.find((day) => day.dayNo === activeDayNo)
        || course?.days[0]
        || null;
    const activeDayIndex = course?.days.findIndex(
        (day) => day.dayNo === activeDay?.dayNo,
    ) ?? -1;
    const themeTags = course ? getThemeTags(course) : [];
    const dateRange = course ? getDateRange(course.days) : '';
    const travelCodeLabels = course?.travelCode
        ? course.travelCode.split('').map((letter) => travelTypeLabels[letter]).filter(Boolean)
        : [];
    const transport = getTransportMeta(course?.transportMode);
    const scheduleNotice = course?.estimatedTravelTimes
        ? `일부 ${transport?.label || '이동'} 구간은 경로 조회가 어려워 예상 거리와 시간으로 보완했습니다. ${transport?.scheduleNotice || '실제 이동 상황과 장소 운영 시간에 따라 일정은 달라질 수 있습니다.'}`
        : transport?.scheduleNotice
            || '위 일정은 예상 이동 거리와 시간을 바탕으로 구성된 추천 동선입니다. 실제 이동 상황과 장소 운영 시간에 따라 일정은 달라질 수 있습니다.';

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
                                className={savedCourseId ? 'is-active' : ''}
                                type="button"
                                disabled={isSavingCourse || Boolean(savedCourseId)}
                                aria-label={savedCourseId ? '저장됨' : '코스 저장'}
                                aria-pressed={Boolean(savedCourseId)}
                                onClick={handleSaveCourse}
                            >
                                {savedCourseId
                                    ? <Check size={17} aria-hidden="true" />
                                    : <Save size={17} aria-hidden="true" />}
                                {isSavingCourse
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
                                <section className="course-detail-hero">
                            <div className="course-detail-hero-copy">
                                <div className="course-detail-label-row">
                                    {source === 'preview' && (
                                        <span className="course-detail-preview-label">
                                            <Info size={13} aria-hidden="true" /> UI 미리보기
                                        </span>
                                    )}

                                    {course.travelCode && (
                                        <span
                                            className="course-detail-preference-code-label"
                                            aria-label={`취향 코드 ${course.travelCode}`}
                                        >
                                            <Tag size={13} aria-hidden="true" />
                                            취향 코드 · <strong>{course.travelCode}</strong>
                                        </span>
                                    )}
                                </div>

                                <h1>{course.title}</h1>

                                <div className="course-detail-tags" aria-label="코스 테마">
                                    {themeTags.slice(0, 4).map((tag) => <span key={tag}>{tag}</span>)}
                                </div>

                                <p>{course.description}</p>

                                <div className="course-detail-hero-meta">
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
                                <img
                                    src={course.coverImageUrl}
                                    alt={`${course.title} 대표 이미지`}
                                    onError={(event) => {
                                        if (event.currentTarget.src !== hanokImage) {
                                            event.currentTarget.src = hanokImage;
                                        }
                                    }}
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
                                                ? `DAY ${activeDay.dayNo} · ${formatDate(activeDay.visitDate, { compact: true })}`
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
                                                        <small>{formatDate(day.visitDate, { compact: true })}</small>
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

                                <p className={`course-detail-schedule-note${course.estimatedTravelTimes ? ' is-estimated' : ''}`}>
                                    <Info size={14} aria-hidden="true" />
                                    {scheduleNotice}
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
                                        {course.travelCode && (
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
