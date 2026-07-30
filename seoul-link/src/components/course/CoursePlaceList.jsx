import { Fragment } from 'react';
import {
    BedDouble,
    Clock3,
    Coffee,
    Landmark,
    MapPin,
    Utensils,
} from 'lucide-react';

import CourseTransportIcon from './CourseTransportIcon';
import { getTravelLegMeta } from '../../utils/courseTransport';

const categoryMeta = {
    TOUR: { label: '관광지', tone: 'tour', Icon: Landmark },
    RESTAURANT: { label: '맛집', tone: 'restaurant', Icon: Utensils },
    CAFE: { label: '카페', tone: 'cafe', Icon: Coffee },
    HOTEL: { label: '숙소', tone: 'hotel', Icon: BedDouble },
};

function formatMinutes(value) {
    const minutes = Math.max(0, Math.round(Number(value) || 0));
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;

    if (hours === 0) return `${restMinutes}분`;
    return restMinutes === 0 ? `${hours}시간` : `${hours}시간 ${restMinutes}분`;
}

/** 선택한 일차의 장소와 장소 사이 이동 정보를 시간 순 타임라인으로 표시합니다. */
function CoursePlaceList({ day, transportMode }) {
    const places = Array.isArray(day?.places) ? day.places : [];

    if (places.length === 0) {
        return (
            <div className="course-detail-empty-schedule">
                이 날짜에 등록된 장소가 아직 없습니다.
            </div>
        );
    }

    return (
        <ol className="course-detail-timeline">
            {places.map((place, index) => {
                const meta = categoryMeta[place.category] || categoryMeta.TOUR;
                const Icon = meta.Icon;
                const address = place.roadAddress || place.address;
                const score = Number(place.recommendationScore);
                const hasScore = Number.isFinite(score);
                const isLast = index === places.length - 1;
                // 백엔드는 이전 장소→현재 장소의 이동 정보를 현재 장소에 함께 내려줍니다.
                const legTransport = getTravelLegMeta(
                    transportMode,
                    place.transitPathType,
                );

                return (
                    <Fragment key={`${place.detailId ?? place.placeId}-${place.visitOrder ?? index}`}>
                        {index > 0 && (
                            <li className="course-detail-transfer">
                                <span aria-hidden="true" />
                                <span className="course-detail-transfer-line" aria-hidden="true" />
                                <p>
                                    <CourseTransportIcon
                                        transportMode={transportMode}
                                        transitPathType={place.transitPathType}
                                        size={14}
                                        aria-hidden="true"
                                    />
                                    {legTransport?.label || '이동'}
                                    <b>·</b>
                                    {Number(place.distanceFromPreviousKm || 0).toFixed(1)}km
                                    <b>·</b>
                                    약 {Math.round(Number(place.travelTimeFromPreviousMinutes) || 0)}분
                                    {place.routeEstimated && (
                                        <em className="course-route-estimated-badge">예상</em>
                                    )}
                                </p>
                            </li>
                        )}

                        <li className={`course-detail-stop${isLast ? ' is-last' : ''}`}>
                            <time dateTime={place.displayVisitTime || undefined}>
                                {place.displayVisitTime || '--:--'}
                            </time>

                            <span className="course-detail-stop-rail" aria-hidden="true">
                                <b className={meta.tone}>{place.visitOrder ?? index + 1}</b>
                            </span>

                            <article className="course-detail-place-card">
                                <div className="course-detail-place-copy">
                                    <div className="course-detail-place-title-row">
                                        <h3>{place.placeName || '장소 정보 준비 중'}</h3>
                                        <span className={`course-detail-category ${meta.tone}`}>
                                            <Icon size={13} aria-hidden="true" />
                                            {meta.label}
                                        </span>
                                    </div>

                                    <p className="course-detail-place-description">
                                        {place.databaseDescription
                                            || place.memo
                                            || (hasScore
                                                ? `취향과 이동 동선을 반영한 추천 장소예요. 추천 점수 ${Math.round(score)}점`
                                                : '코스 이동 동선을 고려해 선택된 장소예요.')}
                                    </p>

                                    {address && (
                                        <p className="course-detail-place-address">
                                            <MapPin size={14} aria-hidden="true" />
                                            {address}
                                        </p>
                                    )}
                                </div>

                                <div className="course-detail-place-side">
                                    <img
                                        src={place.displayImageUrl}
                                        alt={`${place.placeName || '추천 장소'} 사진`}
                                        onError={(event) => {
                                            if (
                                                place.fallbackImageUrl
                                                && event.currentTarget.src !== place.fallbackImageUrl
                                            ) {
                                                event.currentTarget.src = place.fallbackImageUrl;
                                            }
                                        }}
                                    />

                                    <span className="course-detail-stay-time">
                                        <Clock3 size={15} aria-hidden="true" />
                                        <small>소요시간</small>
                                        <strong>{formatMinutes(place.expectedVisitMinutes)}</strong>
                                    </span>
                                </div>
                            </article>
                        </li>
                    </Fragment>
                );
            })}
        </ol>
    );
}

export default CoursePlaceList;

