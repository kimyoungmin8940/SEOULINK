/**
 * 서울 여행 코스의 최적화, 저장, 조회를 담당하는 도메인이다.
 *
 * <p>처음 코드를 확인하는 팀원은 아래 세 가지 요청 흐름을 기준으로 파일을 따라가면 된다.</p>
 *
 * <ol>
 *   <li>최적화만 실행:
 *       {@code CourseController -> CourseOptimizationService
 *       -> DistanceService / VisitDurationService}</li>
 *   <li>취향 추천 코스 생성:
 *       {@code CourseController -> CourseRecommendationService
 *       -> CourseOptimizationService}</li>
 *   <li>선택한 추천 코스 저장:
 *       {@code CourseController -> CourseSaveService
 *       -> TravelCourseRepository / CourseDetailRepository}</li>
 *   <li>저장 코스 조회:
 *       {@code CourseController / MemberCourseController -> CourseService
 *       -> TravelCourseRepository / CourseDetailRepository}</li>
 * </ol>
 *
 * <p>{@code dto.request}는 프론트·추천 담당자가 보내는 입력값,
 * {@code dto.response}는 최적화·저장·조회 결과, {@code entity}는 Oracle 테이블 매핑,
 * {@code repository}는 저장·정렬 조회를 담당한다.</p>
 *
 * <p>현재 요청의 {@code memberId}는 회원 도메인 통합 전 임시 연결 방식이다. 장소 이름,
 * 주소, 이미지 등 화면 표시 정보는 추천 입력에서 결과까지 보존하며, PLACES 도메인 통합
 * 후에는 {@code placeId} 조회값으로 대체한다.</p>
 */
package com.seoulink.backend.domain.course;
