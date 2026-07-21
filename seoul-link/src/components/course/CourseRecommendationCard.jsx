import { useMemo, useState } from 'react';
import {
    ArrowRight,
    BedDouble,
    Bookmark,
    CalendarDays,
    Check,
    ChevronDown,
    ChevronUp,
    Clock3,
    Coffee,
    Footprints,
    Landmark,
    MapPinned,
    Plus,
    Route,
    Sparkles,
    Utensils,
} from 'lucide-react';

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
        tags: ['짧은 동선', '도보 중심', '여유 일정'],
    },
    BALANCED: {
        badge: 'PICK',
        tone: 'balanced',
        shortLabel: '균형 추천',
        tags: ['취향·거리 균형', '대표 명소', '추천 코스'],
    },
};

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

function getPlaceImage(place) {
    return place?.imageUrl || place?.placeImageUrl || place?.thumbnailUrl || null;
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

/** 접히기 전 카드에 표시하는 하루 동선의 간단한 장소 한 칸입니다. */
function RouteStop({ place, fallbackImage, isLast }) {
    const meta = categoryMeta[place.category] || categoryMeta.TOUR;
    const Icon = meta.Icon;
    const imageUrl = getPlaceImage(place);

    return (
        <li className="course-result-stop">
            <div className="course-result-stop-main">
                <span className={`course-result-stop-visual ${meta.tone}`}>
                    {imageUrl ? (
                        <img
                            src={imageUrl}
                            alt=""
                            onError={(event) => {
                                if (fallbackImage && event.currentTarget.src !== fallbackImage) {
                                    event.currentTarget.src = fallbackImage;
                                }
                            }}
                        />
                    ) : (
                        <Icon size={20} strokeWidth={1.9} aria-hidden="true" />
                    )}
                </span>

                <span className="course-result-stop-copy">
                    <strong>{place.placeName || '장소 정보 준비 중'}</strong>
                    <small>{place.expectedVisitTimeHHmm || place.visitTime || '시간 미정'}</small>
                </span>
            </div>

            {!isLast && (
                <ArrowRight
                    className="course-result-stop-arrow"
                    size={17}
                    strokeWidth={2}
                    aria-hidden="true"
                />
            )}
        </li>
    );
}

/** 상세보기 버튼을 눌렀을 때 모든 일차의 이동·체류 정보를 펼쳐 보여줍니다. */
function ExpandedSchedule({ days }) {
    return (
        <div className="course-result-expanded">
            {days.map((day) => (
                <section className="course-result-expanded-day" key={`${day.dayNo}-${day.visitDate}`}>
                    <div className="course-result-expanded-heading">
                        <span>DAY {day.dayNo}</span>
                        <strong>{day.visitDate || '날짜 미정'}</strong>
                    </div>

                    <ol>
                        {(day.places || []).map((place, index) => {
                            const meta = categoryMeta[place.category] || categoryMeta.TOUR;
                            const Icon = meta.Icon;

                            return (
                                <li key={`${day.dayNo}-${place.placeId}-${place.visitOrder ?? index}`}>
                                    <span className={`course-result-expanded-icon ${meta.tone}`}>
                                        <Icon size={17} strokeWidth={1.9} aria-hidden="true" />
                                    </span>
                                    <span className="course-result-expanded-order">{index + 1}</span>
                                    <span className="course-result-expanded-place">
                                        <strong>{place.placeName || '장소 정보 준비 중'}</strong>
                                        <small>{meta.label} · 예상 체류 {formatMinutes(place.expectedVisitMinutes)}</small>
                                    </span>
                                    <span className="course-result-expanded-time">
                                        {place.expectedVisitTimeHHmm || place.visitTime || '--:--'}
                                    </span>
                                    {index > 0 && (
                                        <span className="course-result-expanded-move">
                                            <Footprints size={13} aria-hidden="true" />
                                            {Number(place.distanceFromPreviousKm || 0).toFixed(1)}km · {Math.round(place.travelTimeFromPreviousMinutes || 0)}분
                                        </span>
                                    )}
                                </li>
                            );
                        })}
                    </ol>
                </section>
            ))}
        </div>
    );
}

/** 추천 전략 한 개의 요약, 일차별 동선, 비교·저장 동작을 묶은 카드입니다. */
function CourseRecommendationCard({
    option,
    fallbackImage,
    isCompared,
    isSaving,
    isSaved,
    onToggleCompare,
    onSave,
    onFocusOption,
}) {
    const days = useMemo(
        () => (Array.isArray(option.days) ? option.days : []),
        [option.days],
    );
    const [activeDayNo, setActiveDayNo] = useState(days[0]?.dayNo ?? 1);
    // 북마크 API는 회원 기능 담당 범위이므로 연동 전까지 카드 안에서만 임시 토글합니다.
    const [isBookmarked, setIsBookmarked] = useState(false);
    const [isExpanded, setIsExpanded] = useState(false);
    const activeDay = days.find((day) => day.dayNo === activeDayNo) || days[0] || { places: [] };
    const allPlaces = useMemo(
        () => days.flatMap((day) => (Array.isArray(day.places) ? day.places : [])),
        [days],
    );
    const meta = optionMeta[option.optionType] || optionMeta.BALANCED;
    const coverImage = option.coverImageUrl
        || allPlaces.map(getPlaceImage).find(Boolean)
        || fallbackImage;
    const score = getAverageScore(allPlaces);
    const tags = getThemeTags(option, allPlaces);
    const totalCourseTime = option.totalCourseTimeMinutes
        ?? days.reduce((sum, day) => sum + Number(day.dailyCourseTimeMinutes || 0), 0);
    const totalDistance = Number(option.totalDistanceKm
        ?? days.reduce((sum, day) => sum + Number(day.dailyDistanceKm || 0), 0));

    return (
        <article
            className={`course-result-card ${meta.tone}${isExpanded ? ' expanded' : ''}`}
            onMouseEnter={() => onFocusOption(option)}
            onFocusCapture={() => onFocusOption(option)}
        >
            <div className="course-result-card-main">
                <div className="course-result-cover">
                    <span className="course-result-rank-badge">{meta.badge}</span>
                    <img
                        src={coverImage}
                        alt={`${option.title || option.optionName} 대표 이미지`}
                        onError={(event) => {
                            if (fallbackImage && event.currentTarget.src !== fallbackImage) {
                                event.currentTarget.src = fallbackImage;
                            }
                        }}
                    />
                    <span className="course-result-cover-gradient" aria-hidden="true" />
                    <span className="course-result-option-label">{meta.shortLabel}</span>
                </div>

                <div className="course-result-summary">
                    <div className="course-result-title-row">
                        <div>
                            <span className="course-result-option-name">{option.optionName || meta.shortLabel}</span>
                            <h2>{option.title || `${meta.shortLabel} 서울 맞춤 코스`}</h2>
                        </div>

                        <button
                            className={`course-result-bookmark-btn${isBookmarked ? ' bookmarked' : ''}`}
                            type="button"
                            aria-label={isBookmarked ? '북마크 해제' : '북마크 추가'}
                            aria-pressed={isBookmarked}
                            title={isBookmarked ? '북마크 해제' : '북마크 추가'}
                            onClick={() => setIsBookmarked((previous) => !previous)}
                        >
                            <Bookmark size={20} strokeWidth={1.9} aria-hidden="true" />
                        </button>
                    </div>

                    <p className="course-result-description">
                        {option.description || '취향 결과와 장소 간 이동 거리를 반영해 만든 맞춤 코스예요.'}
                    </p>

                    <div className="course-result-meta">
                        <span><Clock3 size={15} aria-hidden="true" />{formatMinutes(totalCourseTime)}</span>
                        <span><MapPinned size={15} aria-hidden="true" />{totalDistance.toFixed(1)}km</span>
                        <span><Route size={15} aria-hidden="true" />{allPlaces.length}곳 방문</span>
                        {days.length > 1 && (
                            <span><CalendarDays size={15} aria-hidden="true" />{days.length}일 코스</span>
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
                                    className={day.dayNo === activeDay.dayNo ? 'active' : ''}
                                    type="button"
                                    key={`${day.dayNo}-${day.visitDate}`}
                                    onClick={() => setActiveDayNo(day.dayNo)}
                                >
                                    DAY {day.dayNo}
                                </button>
                            ))}
                        </div>
                    )}

                    <ol className="course-result-stops">
                        {(activeDay.places || []).slice(0, 5).map((place, index, visiblePlaces) => (
                            <RouteStop
                                key={`${place.placeId}-${place.visitOrder ?? index}`}
                                place={place}
                                fallbackImage={fallbackImage}
                                isLast={index === visiblePlaces.length - 1}
                            />
                        ))}
                    </ol>
                </div>

                <div className="course-result-card-actions">
                    <button
                        className="course-result-detail-btn"
                        type="button"
                        aria-expanded={isExpanded}
                        onClick={() => setIsExpanded((previous) => !previous)}
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
                        className={`course-result-save-btn${isSaved ? ' saved' : ''}`}
                        type="button"
                        disabled={isSaving || isSaved}
                        onClick={() => onSave(option)}
                    >
                        {isSaved ? <Check size={16} aria-hidden="true" /> : null}
                        {isSaving ? '저장 중...' : isSaved ? '저장 완료' : '이 코스 담기'}
                    </button>
                </div>
            </div>

            {isExpanded && <ExpandedSchedule days={days} />}
        </article>
    );
}

export default CourseRecommendationCard;
