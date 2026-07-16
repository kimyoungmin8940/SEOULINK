/**
 * @typedef {Object} PlaceCandidate
 * @property {number} placeId
 * @property {string} placeName
 * @property {string} category
 * @property {number} recommendationScore
 * @property {number} latitude
 * @property {number} longitude
 * @property {string} visitDate YYYY-MM-DD
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
 * @property {CoursePlace[]} places
 */

/**
 * @typedef {Object} CourseRecommendRequest
 * @property {number} memberId
 * @property {number=} resultId
 * @property {number=} paymentId
 * @property {string} title
 * @property {string=} description
 * @property {string=} travelCode
 * @property {string=} region
 * @property {boolean=} publicCourse
 * @property {PlaceCandidate[]} placeCandidates
 * @property {PlaceCandidate[]=} alternativeCandidates
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
 * @typedef {Object} CourseRecommendResponse
 * @property {number} courseId
 * @property {string} title
 * @property {?string} description
 * @property {?string} travelCode
 * @property {'SURVEY'} courseType
 * @property {?string} region
 * @property {boolean} publicCourse
 * @property {number} placeCount
 * @property {number} dayCount
 * @property {number} totalDistanceKm
 * @property {number} totalTravelTimeMinutes
 * @property {number} totalVisitTimeMinutes
 * @property {number} totalCourseTimeMinutes
 * @property {CourseDay[]} days
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
