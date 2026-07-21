/**
 * @typedef {Object} PlaceCandidate
 * @property {number} placeId
 * @property {string} placeName
 * @property {string} category
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
 * @property {?string} visitTime
 * @property {number} expectedVisitMinutes
 * @property {number} distanceFromPreviousKm
 * @property {number} travelTimeFromPreviousMinutes
 */

/**
 * @typedef {Object} CourseDay
 * @property {number} dayNo
 * @property {string} visitDate YYYY-MM-DD
 * @property {number=} dailyDistanceKm
 * @property {number=} dailyTravelTimeMinutes
 * @property {number=} dailyVisitTimeMinutes
 * @property {number=} dailyCourseTimeMinutes
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
 * @property {number} resultId
 * @property {string=} travelCode 영문 대문자 5자리
 * @property {string=} dailyStartTime HH:mm, 프론트 기본 10:00
 * @property {string=} weatherStatus
 * @property {number=} temperature
 * @property {number=} rainProbability 0~100
 * @property {string[]=} excludedRecommendationKeys 다시 추천할 때 제외할 이전 코스 조합 키
 * @property {DailyCoursePlan[]} dailyPlans
 */

/**
 * @typedef {PlaceCandidate & {
 *   expectedVisitMinutes: number,
 *   visitOrder: number,
 *   distanceFromPreviousKm: number,
 *   travelTimeFromPreviousMinutes: number
 * }} OptimizedPlace
 */

/**
 * @typedef {Object} CourseOptimizeRequest
 * @property {PlaceCandidate[]} placeCandidates
 * @property {PlaceCandidate[]=} alternativeCandidates
 */

/**
 * @typedef {Object} CourseOptimizeResponse
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
 * @property {string=} recommendationKey 재추천 시 같은 코스를 제외하기 위한 서버 발급 키
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number=} totalTravelTimeMinutes
 * @property {number=} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {CourseDay[]} days
 */

/**
 * @typedef {Object} CourseRecommendResponse
 * @property {number} resultId
 * @property {string=} travelCode
 * @property {string} dailyStartTime HH:mm
 * @property {?string} weatherStatus
 * @property {?number} temperature
 * @property {?number} rainProbability
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
 * @property {string[]} regions
 * @property {string[]} tags
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {boolean} liked
 */

/**
 * @typedef {Object} CourseSavePlace
 * @property {number} placeId
 * @property {string} visitDate
 * @property {number} visitOrder
 * @property {?string} visitTime HH:mm
 * @property {number} expectedVisitMinutes
 * @property {number} distanceFromPreviousKm
 * @property {number} travelTimeFromPreviousMinutes
 */

/**
 * @typedef {Object} CourseSaveRequest
 * @property {number} memberId
 * @property {number=} resultId
 * @property {number=} paymentId
 * @property {string} title
 * @property {string=} description
 * @property {string=} travelCode
 * @property {'CUSTOM'|'SURVEY'|'CHATBOT'=} courseType
 * @property {string=} region
 * @property {boolean=} publicCourse
 * @property {CourseSavePlace[]} places
 */

/**
 * @typedef {Object} CourseSaveResponse
 * @property {number} courseId
 * @property {string} title
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
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
