/**
 * @typedef {'WALKING'|'PUBLIC_TRANSIT'|'DRIVING'} TransportMode
 */

/**
 * @typedef {'WALKING'|'SUBWAY'|'BUS'|'BUS_SUBWAY'} TransitPathType
 */

/**
 * @typedef {Object} PlaceCandidate
 * @property {number} placeId
 * @property {string} placeName
 * @property {string} category
 * @property {string=} region
 * @property {string=} address
 * @property {string=} roadAddress
 * @property {string=} imageUrl 추천 결과 화면에 사용할 장소 이미지
 * @property {number} recommendationScore
 * @property {number} latitude
 * @property {number} longitude
 * @property {string=} visitDate YYYY-MM-DD, 추천 요청에서는 상위 dailyPlans의 날짜 사용
 * @property {'Y'|'N'=} themePalaceCultureYn
 * @property {'Y'|'N'=} themeNatureHangangYn
 * @property {'Y'|'N'=} themeDateYn
 * @property {'Y'|'N'=} themeFoodTourYn
 * @property {'Y'|'N'=} themeCafeTourYn
 * @property {'Y'|'N'=} themeShoppingHotplaceYn
 * @property {'Y'|'N'=} themeNightViewYn
 * @property {'Y'|'N'=} themeHotelStayYn
 * @property {PlaceCandidate[]=} alternativeCandidates
 */

/**
 * @typedef {Object} CoursePlace
 * @property {?number} detailId
 * @property {number} placeId
 * @property {?string} placeName
 * @property {?string} category
 * @property {?string} region
 * @property {?string} address
 * @property {?string} roadAddress
 * @property {?string} imageUrl
 * @property {?number} latitude
 * @property {?number} longitude
 * @property {?number} recommendationScore
 * @property {?string} themePalaceCultureYn
 * @property {?string} themeNatureHangangYn
 * @property {?string} themeDateYn
 * @property {?string} themeFoodTourYn
 * @property {?string} themeCafeTourYn
 * @property {?string} themeShoppingHotplaceYn
 * @property {?string} themeNightViewYn
 * @property {?string} themeHotelStayYn
 * @property {number} visitOrder
 * @property {?string} memo
 * @property {?string=} databaseDescription PLACES.DESCRIPTION에서 가져온 장소 설명
 * @property {?string} visitTime
 * @property {number} expectedVisitMinutes
 * @property {number} distanceFromPreviousKm
 * @property {number} travelTimeFromPreviousMinutes
 * @property {?TransitPathType} transitPathType ODsay 최적 경로 종류, 추정 구간은 null
 * @property {boolean=} routeEstimated 외부 경로 대신 추정값을 사용한 구간인지 여부
 */

/**
 * @typedef {Object} CourseDay
 * @property {number} dayNo
 * @property {string} visitDate YYYY-MM-DD
 * @property {number=} dailyDistanceKm
 * @property {number=} dailyTravelTimeMinutes
 * @property {number=} dailyVisitTimeMinutes
 * @property {number=} dailyCourseTimeMinutes
 * @property {boolean=} routeDetailsAttempted 실제 교통편 상세 조회를 이미 시도했는지 여부
 * @property {?string=} routeDetailsError 상세 조회 자체가 실패한 경우의 화면 메시지
 * @property {CoursePlace[]} places
 */

/**
 * @typedef {Object} DailyCoursePlan
 * @property {string} visitDate YYYY-MM-DD
 * @property {number} targetPlaceCount 최종 코스에 선발할 장소 수
 * @property {Object.<string, number>} categoryTargets TOUR/RESTAURANT/CAFE/HOTEL 목표 수
 * @property {PlaceCandidate[]} placeCandidates
 */

/**
 * @typedef {Object} CourseRecommendRequest
 * @property {number=} surveyId
 * @property {number} resultId
 * @property {string=} travelCode 영문 대문자 5자리
 * @property {'SOLO'|'COUPLE'|'FRIENDS'|'FAMILY'=} companionType 동행 유형 가중치 기준
 * @property {string[]=} preferredRegions 추천 점수로 계산한 상위 구 순서
 * @property {TransportMode} transportMode 전체 추천 옵션에 동일하게 적용할 이동수단
 * @property {string=} startDate YYYY-MM-DD
 * @property {string=} endDate YYYY-MM-DD
 * @property {number=} travelDays
 * @property {string=} dailyStartTime HH:mm, P형 11:00 / R형 13:00
 * @property {string[]=} excludedRecommendationKeys 다시 추천할 때 제외할 이전 코스 조합 키
 * @property {number[]=} previouslyRecommendedPlaceIds 이전 장소 감점용 장소 ID
 * @property {PlaceCandidate[]=} hotelCandidates 2일 이상 일정에서 옵션별 숙소를 고를 후보 풀
 * @property {DailyCoursePlan[]} dailyPlans
 */

/**
 * @typedef {PlaceCandidate & {
 *   expectedVisitMinutes: number,
 *   visitOrder: number,
 *   distanceFromPreviousKm: number,
 *   travelTimeFromPreviousMinutes: number,
 *   transitPathType: ?TransitPathType,
 *   routeEstimated: boolean
 * }} OptimizedPlace
 */

/**
 * @typedef {Object} CourseOptimizeRequest
 * @property {number=} resultId
 * @property {string=} travelCode
 * @property {TransportMode} transportMode
 * @property {string=} dailyStartTime HH:mm
 * @property {PlaceCandidate[]} placeCandidates
 * @property {PlaceCandidate[]=} alternativeCandidates
 */

/**
 * @typedef {Object} CourseOptimizeResponse
 * @property {TransportMode} transportMode
 * @property {boolean} estimatedTravelTimes 외부 경로 API 대신 추정값인 구간 포함 여부
 * @property {OptimizedPlace[]} optimizedPlaces
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 */

/**
 * @typedef {Object} CourseOption
 * @property {number} optionNo
 * @property {'PREFERENCE'|'MIN_DISTANCE'|'BALANCED'} optionType
 * @property {string} optionName
 * @property {string=} title
 * @property {string=} description
 * @property {?string=} region 실제 방문 장소가 가장 많이 속한 대표 구
 * @property {string=} recommendationKey 재추천 시 같은 코스를 제외하기 위한 서버 발급 키
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number=} totalTravelTimeMinutes
 * @property {number=} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {boolean=} estimatedTravelTimes 이 옵션에 추정 이동 구간이 포함됐는지 여부
 * @property {boolean=} hotelIncluded 다일 일정에 숙소가 포함됐는지 여부
 * @property {string=} hotelNotice 도보 30분 이내 숙소가 없을 때 표시할 안내
 * @property {CourseDay[]} days
 */

/**
 * @typedef {Object} CourseRecommendResponse
 * @property {number} resultId
 * @property {string=} travelCode
 * @property {TransportMode} transportMode
 * @property {string[]} preferredRegions 화면과 코스가 함께 사용한 상위 구 순서
 * @property {boolean} estimatedTravelTimes 세 옵션 중 추정 이동 구간 포함 여부
 * @property {string} dailyStartTime HH:mm
 * @property {number} optionCount
 * @property {CourseOption[]} courseOptions
 */

/**
 * @typedef {Object} CourseDetailResponse
 * @property {number} courseId
 * @property {string} title
 * @property {?string} description
 * @property {?string} coverImageUrl
 * @property {?string} travelCode
 * @property {?TransportMode} transportMode
 * @property {boolean=} estimatedTravelTimes 추천 직후 미리보기에서만 사용하는 추정 구간 여부
 * @property {'CUSTOM'|'SURVEY'|'CHATBOT'} courseType
 * @property {?string} region
 * @property {boolean} publicCourse
 * @property {number} viewCount
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {string} createdAt
 * @property {string} updatedAt
 * @property {CourseDay[]} days
 */

/**
 * @typedef {Object} CourseSummaryResponse
 * @property {number} courseId
 * @property {string} title
 * @property {?string} description
 * @property {?string} coverImageUrl
 * @property {'CUSTOM'|'SURVEY'|'CHATBOT'} courseType
 * @property {?TransportMode} transportMode
 * @property {string[]} regions
 * @property {string[]} tags
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {?string} startDate YYYY-MM-DD
 * @property {?string} endDate YYYY-MM-DD
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {?string} createdAt
 * @property {boolean} liked
 */

/**
 * @typedef {Object} CourseSavePlace
 * @property {number} placeId
 * @property {string=} category 같은 숙소의 날짜별 반복 저장을 판별할 장소 카테고리
 * @property {number=} dayNo 날짜가 없는 THEME 코스의 일차 번호
 * @property {string=} visitDate THEME 이외 코스의 YYYY-MM-DD 방문 날짜
 * @property {number} visitOrder
 * @property {?string} visitTime HH:mm
 * @property {number} expectedVisitMinutes
 * @property {number} distanceFromPreviousKm
 * @property {number} travelTimeFromPreviousMinutes
 * @property {?TransitPathType} transitPathType
 * @property {boolean=} routeEstimated
 */

/**
 * @typedef {Object} CourseSaveRequest
 * @property {number} memberId
 * @property {number=} resultId SURVEY 코스는 필수
 * @property {number=} paymentId
 * @property {string} title
 * @property {string=} description
 * @property {string=} travelCode
 * @property {TransportMode} transportMode 추천 계산·저장 검증에 사용하며 SURVEY 조회는 연결된 설문값을 사용
 * @property {'CUSTOM'|'SURVEY'|'CHATBOT'|'THEME'=} courseType
 * @property {string} [sourceCourseKey] 테마 원본 코스 식별키
 * @property {string=} region
 * @property {boolean=} publicCourse
 * @property {CourseSavePlace[]} places
 */

/**
 * @typedef {Object} CourseSaveResponse
 * @property {number} courseId
 * @property {string} title
 * @property {TransportMode} transportMode
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 */

/**
 * @typedef {Object} CourseBatchSaveRequest
 * @property {CourseSaveRequest[]} courses 한 번에 저장할 1~3개 코스
 */

/**
 * @typedef {Object} CourseBatchSaveResponse
 * @property {number} savedCount
 * @property {CourseSaveResponse[]} savedCourses
 */

/**
 * @typedef {Object} CourseApiErrorResponse
 * @property {string} code INVALID_REQUEST, COURSE_NOT_FOUND 등
 * @property {string} message
 */

export const COURSE_TYPES = Object.freeze({
    CUSTOM: 'CUSTOM',
    SURVEY: 'SURVEY',
    CHATBOT: 'CHATBOT',
});
