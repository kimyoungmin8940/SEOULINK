import { useEffect, useMemo, useState } from 'react';
import {
    ArrowRight,
    BedDouble,
    CalendarDays,
    Check,
    ChevronDown,
    ChevronUp,
    Clock3,
    Coffee,
    Info,
    Landmark,
    MapPinned,
    Plus,
    Route,
    Sparkles,
    TriangleAlert,
    Utensils,
} from 'lucide-react';

import CourseTransportIcon from './CourseTransportIcon';
import { getTransportMeta, getTravelLegMeta } from '../../utils/courseTransport';

const categoryMeta = {
    TOUR: { label: '관광', Icon: Landmark, tone: 'tour' },
    RESTAURANT: { label: '맛집', Icon: Utensils, tone: 'restaurant' },
    CAFE: { label: '카페', Icon: Coffee, tone: 'cafe' },
    HOTEL: { label: '숙소', Icon: BedDouble, tone: 'hotel' },
};

const optionMeta = {
    PREFERENCE: {
        badge: 'BEST',
        tone: 'preference',
        shortLabel: '취향 집중',
        tags: ['취향 점수 우선', '인기 장소', '알찬 일정'],
    },
    MIN_DISTANCE: {
        badge: 'EASY',
        tone: 'distance',
        shortLabel: '이동 최소',
        tags: ['짧은 동선', '이동 부담 적음', '여유 일정'],
    },
    BALANCED: {
        badge: 'PICK',
        tone: 'balanced',
        shortLabel: '균형 추천',
        tags: ['취향·거리 균형', '대표 명소', '추천 코스'],
    },
};

function isHotelCategory(category) {
    const normalized = String(category || '').trim().toUpperCase();
    return ['HOTEL', '숙소', '호텔', 'ACCOMMODATION', 'LODGING'].includes(normalized);
}

function getScheduleOrderLabel(place, index) {
    if (place?.routeOrigin || isHotelCategory(place?.category)) {
        return 'H';
    }

    const visitOrder = Number(place?.visitOrder);
    return Number.isFinite(visitOrder) && visitOrder > 0
        ? visitOrder
        : index + 1;
}

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours === 0) {
        return `${restMinutes}분`;
    }

    if (restMinutes === 0) {
        return `${hours}시간`;
    }

    return `${hours}시간 ${restMinutes}분`;
}

function normalizeImageUrl(value) {
    if (typeof value !== 'string') {
        return null;
    }

    const normalized = value.trim();
    const lowerCaseValue = normalized.toLowerCase();

    if (
        !normalized
        || lowerCaseValue === 'null'
        || lowerCaseValue === 'undefined'
        || lowerCaseValue === 'none'
    ) {
        return null;
    }

    return normalized;
}

function getPlaceImage(place) {
    return normalizeImageUrl(
        place?.imageUrl || place?.placeImageUrl || place?.thumbnailUrl,
    );
}

/**
 * 코스 방문 순서대로 실제 장소 사진 후보를 만든다.
 * 서버가 내려 준 대표 사진은 장소별 사진 정보가 없는 구버전 응답에서만 마지막 후보로 쓴다.
 */
function getCoverImageCandidates(option, places) {
    const candidates = [];
    const added = new Set();

    const addCandidate = (value) => {
        const imageUrl = normalizeImageUrl(value);
        if (!imageUrl || added.has(imageUrl)) {
            return;
        }

        added.add(imageUrl);
        candidates.push(imageUrl);
    };

    places.forEach((place) => addCandidate(getPlaceImage(place)));
    addCandidate(option?.coverImageUrl);

    return candidates;
}

function getAverageScore(places) {
    const scores = places
        .map((place) => Number(place.recommendationScore))
        .filter(Number.isFinite);

    if (scores.length === 0) {
        return null;
    }

    return Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length);
}

function getThemeTags(option, places) {
    const themeFields = [
        ['themePalaceCultureYn', '궁궐·문화'],
        ['themeNatureHangangYn', '자연·한강'],
        ['themeDateYn', '데이트'],
        ['themeFoodTourYn', '맛집 탐방'],
        ['themeCafeTourYn', '카페 투어'],
        ['themeShoppingHotplaceYn', '쇼핑·핫플'],
        ['themeNightViewYn', '야경'],
        ['themeHotelStayYn', '숙소'],
    ];

    const tags = themeFields
        .filter(([field]) => places.some((place) => place?.[field] === 'Y'))
        .map(([, label]) => label);

    return (tags.length > 0 ? tags : optionMeta[option.optionType]?.tags || ['서울 여행'])
        .slice(0, 4);
}

/** 카드에 항상 표시하는 하루 동선의 장소명과 실제 경로 기준 도착 시각입니다. */
function RouteStop({ place, hasPrevious }) {
    const meta = categoryMeta[place.category] || categoryMeta.TOUR;
    const Icon = meta.Icon;
    const imageUrl = getPlaceImage(place);
    const [imageFailed, setImageFailed] = useState(false);

    useEffect(() => {
        setImageFailed(false);
    }, [imageUrl]);

    return (
        <li className="course-result-stop">
            {hasPrevious && (
                <ArrowRight
                    className="course-result-stop-arrow"
                    size={17}
                    strokeWidth={2}
                    aria-hidden="true"
                />
            )}

            <div className="course-result-stop-main">
                <span className={`course-result-stop-visual ${meta.tone}`}>
                    {imageUrl && !imageFailed ? (
                        <img
                            src={imageUrl}
                            alt=""
                            loading="lazy"
                            decoding="async"
                            fetchPriority="low"
                            data-photo-filter="off"
                            onError={() => setImageFailed(true)}
                        />
                    ) : (
                        <Icon size={20} strokeWidth={1.9} aria-hidden="true" />
                    )}
                </span>

                <span className="course-result-stop-copy">
                    <strong>{place.placeName || '장소 정보 준비 중'}</strong>
                    <small>
                        {place.routeOrigin
                            ? `숙소 출발 · ${place.expectedVisitTimeHHmm || place.visitTime || '시간 미정'}`
                            : place.expectedVisitTimeHHmm || place.visitTime || '시간 미정'}
                    </small>
                </span>
            </div>

        </li>
    );
}

/** 상세보기 버튼을 눌렀을 때 현재 일차의 이동·체류 정보를 펼쳐 보여줍니다. */
function ExpandedSchedule({ days, transportMode }) {
    return (
        <div className="course-result-expanded">
            {days.map((day) => {
                const routeOriginPlace = day?.routeOriginPlace
                    ? { ...day.routeOriginPlace, routeOrigin: true }
                    : null;
                const schedulePlaces = routeOriginPlace
                    ? [routeOriginPlace, ...(day.places || [])]
                    : (day.places || []);
                const hasEstimatedLeg = (day.places || []).some(
                    (place, index) => (index > 0 || Boolean(routeOriginPlace))
                        && place.routeEstimated,
                );
                const routeDetailsUnavailable = day.routeDetailsAttempted
                    && Boolean(day.routeDetailsError);
                const routeDetailsErrorMessage = String(
                    day.routeDetailsError || '',
                ).trim();

                return (
                    <section className="course-result-expanded-day" key={`${day.dayNo}-${day.visitDate}`}>
                    <div className="course-result-expanded-heading">
                        <span>DAY {day.dayNo}</span>
                        <strong>{day.visitDate || '날짜 미정'}</strong>
                    </div>

                    {routeDetailsUnavailable && (
                        <p className="course-route-detail-state unavailable" role="status">
                            {routeDetailsErrorMessage
                                || '이동 경로를 일시적으로 확인할 수 없습니다. 현재는 예상 거리와 시간으로 표시합니다.'}
                        </p>
                    )}
                    {!routeDetailsUnavailable
                        && day.routeDetailsAttempted
                        && hasEstimatedLeg
                        && (
                        <p className="course-route-detail-state unavailable" role="status">
                            실제 경로를 받지 못한 일부 구간만 예상값으로 표시합니다.
                        </p>
                    )}

                    <ol>
                        {schedulePlaces.map((place, index) => {
                            const meta = categoryMeta[place.category] || categoryMeta.TOUR;
                            const Icon = meta.Icon;
                            const isRouteOrigin = Boolean(place.routeOrigin);
                            // 거리·시간·경로 종류는 현재 장소로 들어오는 이전 구간의 정보입니다.
                            const legTransport = getTravelLegMeta(
                                transportMode,
                                place.transitPathType,
                            );

                            return (
                                <li
                                    className={index > 0 ? 'has-transfer' : undefined}
                                    key={`${day.dayNo}-${isRouteOrigin ? 'origin' : 'place'}-${place.placeId}-${place.visitOrder ?? index}`}
                                >
                                    {index > 0 && (
                                        <span className="course-result-expanded-move">
                                            <span className="course-result-expanded-move-icon">
                                                <CourseTransportIcon
                                                    transportMode={transportMode}
                                                    transitPathType={place.transitPathType}
                                                    size={14}
                                                    aria-hidden="true"
                                                />
                                            </span>
                                            <span className="course-result-expanded-move-summary">
                                                <span className="course-result-expanded-move-metrics">
                                                    {Number(place.distanceFromPreviousKm || 0).toFixed(1)}km · {Math.round(place.travelTimeFromPreviousMinutes || 0)}분
                                                </span>
                                                <span className="course-result-expanded-move-badge">
                                                    {legTransport?.label || '이동'}
                                                </span>
                                                {place.routeEstimated && (
                                                    <span className="course-route-estimated-badge">
                                                        예상
                                                    </span>
                                                )}
                                            </span>
                                        </span>
                                    )}
                                    <span className="course-result-expanded-order">
                                        {getScheduleOrderLabel(place, index)}
                                    </span>
                                    <span className={`course-result-expanded-icon ${meta.tone}`}>
                                        <Icon size={17} strokeWidth={1.9} aria-hidden="true" />
                                    </span>
                                    <span className="course-result-expanded-place">
                                        <strong>{place.placeName || '장소 정보 준비 중'}</strong>
                                        <small>
                                            {isRouteOrigin
                                                ? '숙소에서 일정 출발'
                                                : isHotelCategory(place.category)
                                                    ? meta.label
                                                    : `${meta.label} · 예상 체류 ${formatMinutes(place.expectedVisitMinutes)}`}
                                        </small>
                                    </span>
                                    <span className="course-result-expanded-time">
                                        {place.expectedVisitTimeHHmm || place.visitTime || '--:--'}
                                    </span>
                                </li>
                            );
                        })}
                    </ol>
                </section>
                );
            })}
        </div>
    );
}

/** 추천 전략 한 개의 요약, 일차별 동선, 비교·저장 선택 동작을 묶은 카드입니다. */
function CourseRecommendationCard({
    option,
    transportMode,
    activeDayNo,
    isEstimatedTravelTime,
    isCompared,
    isSelectedForSave,
    isSelectionDisabled,
    isSaving,
    isSaved,
    onToggleCompare,
    onToggleSaveSelection,
    onFocusOption,
    onActiveDayChange,
}) {
    const days = useMemo(
        () => (Array.isArray(option.days) ? option.days : []),
        [option.days],
    );
    const [isExpanded, setIsExpanded] = useState(false);
    const activeDay = days.find(
        (day) => Number(day.dayNo) === Number(activeDayNo),
    ) || days[0] || { places: [] };
    const activeDayPlaces = Array.isArray(activeDay.places) ? activeDay.places : [];
    const activeRouteStops = activeDay?.routeOriginPlace
        ? [
            {
                ...activeDay.routeOriginPlace,
                routeOrigin: true,
            },
            ...activeDayPlaces,
        ]
        : activeDayPlaces;
    const allPlaces = useMemo(
        () => days.flatMap((day) => (Array.isArray(day.places) ? day.places : [])),
        [days],
    );
    const meta = optionMeta[option.optionType] || optionMeta.BALANCED;
    const transport = getTransportMeta(transportMode);
    const coverImageCandidates = useMemo(
        () => getCoverImageCandidates(option, allPlaces),
        [allPlaces, option],
    );
    const coverImageCandidatesKey = coverImageCandidates.join('\u001f');
    const [failedCoverImages, setFailedCoverImages] = useState([]);

    useEffect(() => {
        setFailedCoverImages([]);
    }, [coverImageCandidatesKey]);

    const coverImage = coverImageCandidates.find(
        (imageUrl) => !failedCoverImages.includes(imageUrl),
    ) || null;
    const hasCoverImage = Boolean(coverImage);
    const score = getAverageScore(allPlaces);
    const tags = getThemeTags(option, allPlaces);
    const totalCourseTime = option.totalCourseTimeMinutes
        ?? days.reduce((sum, day) => sum + Number(day.dailyCourseTimeMinutes || 0), 0);
    const totalDistance = Number(option.totalDistanceKm
        ?? days.reduce((sum, day) => sum + Number(day.dailyDistanceKm || 0), 0));
    const totalTravelTime = option.totalTravelTimeMinutes
        ?? days.reduce((sum, day) => sum + Number(day.dailyTravelTimeMinutes || 0), 0);
    // 여러 날짜 코스는 현재 선택한 DAY의 값만 카드·지도·비교 패널에서 함께 사용합니다.
    const displayCourseTime = days.length > 1
        ? Number(activeDay.dailyCourseTimeMinutes || 0)
        : totalCourseTime;
    const displayDistance = days.length > 1
        ? Number(activeDay.dailyDistanceKm || 0)
        : totalDistance;
    const displayTravelTime = days.length > 1
        ? Number(activeDay.dailyTravelTimeMinutes || 0)
        : totalTravelTime;
    const displayPlaceCount = days.length > 1
        ? (activeDay.places || []).length
        : allPlaces.length;
    const requestedPlaceCount = Number(activeDay?.requestedPlaceCount);
    const actualPlaceCount = activeDay?.actualPlaceCount != null
        && Number.isFinite(Number(activeDay.actualPlaceCount))
        ? Number(activeDay.actualPlaceCount)
        : activeDayPlaces.filter(
            (place) => !isHotelCategory(place?.category),
        ).length;
    const placeCountAdjusted = Boolean(activeDay?.placeCountAdjusted)
        || (
            Number.isFinite(requestedPlaceCount)
            && requestedPlaceCount > actualPlaceCount
        );
    const normalizedTransportMode = String(transportMode || '')
        .trim()
        .toUpperCase();
    const placeCountAdjustmentNotice = placeCountAdjusted
        ? normalizedTransportMode === 'WALKING'
            ? `도보 구간당 20분 제한과 코스 간 중복 제한을 함께 적용해 DAY ${activeDay.dayNo || 1}는 ${actualPlaceCount}곳으로 조정했어요.`
            : normalizedTransportMode === 'PUBLIC_TRANSIT'
                ? `대중교통 구간당 40분 제한과 코스 간 중복 제한을 함께 적용해 DAY ${activeDay.dayNo || 1}는 ${actualPlaceCount}곳으로 조정했어요.`
                : normalizedTransportMode === 'DRIVING' || normalizedTransportMode === 'CAR'
                    ? `자동차 이동 조건과 코스 간 중복 제한을 함께 적용해 DAY ${activeDay.dayNo || 1}는 ${actualPlaceCount}곳으로 조정했어요.`
                    : activeDay?.adjustmentNotice
                        || `이동시간과 중복 제한을 만족하는 장소가 부족해 DAY ${activeDay.dayNo || 1}는 ${actualPlaceCount}곳으로 조정했어요.`
        : '';

    return (
        <article
            className={`course-result-card ${meta.tone}${isExpanded ? ' expanded' : ''}`}
            onMouseEnter={() => onFocusOption(option)}
            onFocusCapture={() => onFocusOption(option)}
        >
            <div className="course-result-card-main">
                <div className={`course-result-cover${hasCoverImage ? ' has-image' : ''}`}>
                    <span className="course-result-rank-badge">{meta.badge}</span>
                    {hasCoverImage ? (
                        <img
                            src={coverImage}
                            alt={`${option.title || option.optionName} 대표 이미지`}
                            onError={() => {
                                setFailedCoverImages((previous) => (
                                    previous.includes(coverImage)
                                        ? previous
                                        : [...previous, coverImage]
                                ));
                            }}
                        />
                    ) : (
                        <span className="course-result-cover-empty" aria-label="등록된 코스 사진 없음">
                            <MapPinned size={30} strokeWidth={1.7} aria-hidden="true" />
                            <small>사진 준비 중</small>
                        </span>
                    )}
                    {hasCoverImage && (
                        <span className="course-result-cover-gradient" aria-hidden="true" />
                    )}
                    <span className="course-result-option-label">{meta.shortLabel}</span>
                </div>

                <div className="course-result-summary">
                    <div className="course-result-title-row">
                        <div>
                            <div className="course-result-option-name-row">
                                <span className="course-result-option-name">{option.optionName || meta.shortLabel}</span>
                                {isEstimatedTravelTime && (
                                    <span className="course-result-estimated-badge">
                                        <Info size={11} aria-hidden="true" />일부 예상값
                                    </span>
                                )}
                            </div>
                            <h2>{option.title || `${meta.shortLabel} 서울 맞춤 코스`}</h2>
                        </div>
                    </div>

                    <p className="course-result-description">
                        {option.description || '취향 결과와 장소 간 이동 거리를 반영해 만든 맞춤 코스예요.'}
                    </p>

                    {option.hotelNotice && (
                        <p className="course-result-hotel-notice" role="status">
                            <TriangleAlert size={14} aria-hidden="true" />
                            <span>{option.hotelNotice}</span>
                        </p>
                    )}

                    <div className="course-result-meta">
                        <span><Clock3 size={15} aria-hidden="true" />{formatMinutes(displayCourseTime)}</span>
                        <span>
                            <CourseTransportIcon transportMode={transportMode} size={15} aria-hidden="true" />
                            {transport ? `${transport.label} ${formatMinutes(displayTravelTime)}` : `이동 ${formatMinutes(displayTravelTime)}`}
                        </span>
                        <span><MapPinned size={15} aria-hidden="true" />{displayDistance.toFixed(1)}km</span>
                        <span><Route size={15} aria-hidden="true" />{displayPlaceCount}곳 방문</span>
                        {days.length > 1 && (
                            <span><CalendarDays size={15} aria-hidden="true" />DAY {activeDay.dayNo} 기준</span>
                        )}
                    </div>

                    <div className="course-result-tags" aria-label="코스 특징">
                        {tags.map((tag) => <span key={tag}>{tag}</span>)}
                    </div>

                    {score != null && (
                        <div className="course-result-score">
                            <Sparkles size={15} strokeWidth={2.2} aria-hidden="true" />
                            추천 점수 <strong>{score}</strong>
                        </div>
                    )}
                </div>
            </div>

            <div className="course-result-route-row">
                <div className="course-result-route-content">
                    {days.length > 1 && (
                        <div className="course-result-day-tabs" aria-label="일차 선택">
                            {days.map((day) => (
                                <button
                                    className={
                                        Number(day.dayNo) === Number(activeDay.dayNo)
                                            ? 'active'
                                            : ''
                                    }
                                    type="button"
                                    key={`${day.dayNo}-${day.visitDate}`}
                                    onClick={() => {
                                        onFocusOption(option);
                                        onActiveDayChange(day.dayNo);
                                    }}
                                >
                                    DAY {day.dayNo}
                                </button>
                            ))}
                        </div>
                    )}

                    {placeCountAdjustmentNotice && (
                        <div className="course-result-place-reduction-notice compact" role="status">
                            <TriangleAlert size={15} aria-hidden="true" />
                            <span>
                                <strong>장소 수가 조정되었어요</strong>
                                <small>{placeCountAdjustmentNotice}</small>
                            </span>
                        </div>
                    )}

                    <ol className="course-result-stops">
                        {activeRouteStops.map((place, index) => (
                            <RouteStop
                                key={`${place.routeOrigin ? 'origin' : 'place'}-${place.placeId}-${place.visitOrder ?? index}`}
                                place={place}
                                hasPrevious={index > 0}
                            />
                        ))}
                    </ol>
                </div>

                <div className="course-result-card-actions">
                    <button
                        className="course-result-detail-btn"
                        type="button"
                        aria-expanded={isExpanded}
                        onClick={() => {
                            const nextExpanded = !isExpanded;
                            setIsExpanded(nextExpanded);
                            onFocusOption(option);
                        }}
                    >
                        {isExpanded ? '일정 접기' : '코스 상세보기'}
                        {isExpanded
                            ? <ChevronUp size={16} aria-hidden="true" />
                            : <ChevronDown size={16} aria-hidden="true" />}
                    </button>

                    <button
                        className={`course-result-compare-btn${isCompared ? ' selected' : ''}`}
                        type="button"
                        aria-pressed={isCompared}
                        onClick={() => onToggleCompare(option.optionNo)}
                    >
                        {isCompared ? <Check size={15} aria-hidden="true" /> : <Plus size={15} aria-hidden="true" />}
                        {isCompared ? (
                            <span className="course-result-compare-label">
                                비교에
                                <br />
                                담김
                            </span>
                        ) : '비교 담기'}
                    </button>

                    <button
                        className={`course-result-save-btn${isSelectedForSave ? ' selected' : ''}${isSaved ? ' saved' : ''}`}
                        type="button"
                        disabled={isSelectionDisabled || isSaving || isSaved}
                        aria-pressed={isSelectedForSave || isSaved}
                        onClick={() => onToggleSaveSelection(option.optionNo)}
                    >
                        {isSelectedForSave || isSaved
                            ? <Check size={16} aria-hidden="true" />
                            : <Plus size={16} aria-hidden="true" />}
                        {isSaving && isSelectedForSave
                            ? '저장 중...'
                            : isSaved
                                ? '저장됨'
                                : isSelectedForSave
                                    ? (
                                        <span className="course-result-save-label">
                                            저장
                                            <br />
                                            선택됨
                                        </span>
                                    )
                                    : '저장 선택'}
                    </button>
                </div>
            </div>

            {isExpanded && (
                <ExpandedSchedule
                    days={[activeDay]}
                    transportMode={transportMode}
                />
            )}
        </article>
    );
}

export default CourseRecommendationCard;
