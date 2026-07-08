package com.seoulink.backend.service;

import com.seoulink.backend.dto.CourseDto;
import com.seoulink.backend.dto.request.CourseCreateRequest;
import com.seoulink.backend.dto.request.CourseDetailCreateRequest;
import com.seoulink.backend.dto.request.CourseDetailUpdateRequest;
import com.seoulink.backend.dto.request.CourseUpdateRequest;
import com.seoulink.backend.dto.response.CourseDetailResponse;
import com.seoulink.backend.dto.response.CourseResponse;
import com.seoulink.backend.dto.response.CourseSummaryResponse;
import com.seoulink.backend.entity.CourseDetail;
import com.seoulink.backend.entity.Place;
import com.seoulink.backend.entity.TravelCourse;
import com.seoulink.backend.repository.CourseDetailRepository;
import com.seoulink.backend.repository.MemberRepository;
import com.seoulink.backend.repository.PlaceRepository;
import com.seoulink.backend.repository.SurveyResultRepository;
import com.seoulink.backend.repository.TravelCourseRepository;
import com.seoulink.backend.repository.TravelTypeMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final TravelCourseRepository travelCourseRepository;
    private final CourseDetailRepository courseDetailRepository;
    private final PlaceRepository placeRepository;
    private final MemberRepository memberRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final TravelTypeMasterRepository travelTypeMasterRepository;

    public CourseService(
            TravelCourseRepository travelCourseRepository,
            CourseDetailRepository courseDetailRepository,
            PlaceRepository placeRepository,
            MemberRepository memberRepository,
            SurveyResultRepository surveyResultRepository,
            TravelTypeMasterRepository travelTypeMasterRepository
    ) {
        this.travelCourseRepository = travelCourseRepository;
        this.courseDetailRepository = courseDetailRepository;
        this.placeRepository = placeRepository;
        this.memberRepository = memberRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.travelTypeMasterRepository = travelTypeMasterRepository;
    }

    @Transactional
    public CourseDto createCourse(CourseCreateRequest request) {
        validateMemberExists(request.getMemberId());
        validateOptionalSurveyResultExists(request.getResultId());
        validateOptionalTravelTypeExists(request.getTravelCode());

        TravelCourse course = new TravelCourse();
        course.setMemberId(request.getMemberId());
        course.setResultId(request.getResultId());
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setTravelCode(request.getTravelCode());
        course.setCourseType(request.getCourseType() == null ? "CUSTOM" : request.getCourseType());
        course.setRegion(request.getRegion());
        course.setIsPublic("N");
        course.setViewCount(0);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());

        travelCourseRepository.save(course);

        for (CourseCreateRequest.CoursePlaceRequest placeRequest : request.getPlaces()) {
            validateActivePlaceExists(placeRequest.getPlaceId());

            CourseDetail detail = new CourseDetail();
            detail.setCourseId(course.getCourseId());
            detail.setPlaceId(placeRequest.getPlaceId());
            detail.setDayNo(placeRequest.getDayNo() == null ? 1 : placeRequest.getDayNo());
            detail.setPlaceOrder(placeRequest.getPlaceOrder());
            detail.setMemo(placeRequest.getMemo());
            detail.setVisitTime(placeRequest.getVisitTime());
            detail.setStayMinutes(placeRequest.getStayMinutes());
            courseDetailRepository.save(detail);
        }

        return new CourseDto(
                course.getCourseId(),
                course.getTitle(),
                course.getDescription(),
                course.getTravelCode(),
                request.getPlaces().stream()
                        .map(place -> String.valueOf(place.getPlaceId()))
                        .toList()
        );
    }

    public List<CourseSummaryResponse> getMyCourses(Long memberId) {
        return travelCourseRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(course -> new CourseSummaryResponse(
                        course,
                        courseDetailRepository.countByCourseId(course.getCourseId())
                ))
                .toList();
    }

    @Transactional
    public CourseResponse getCourse(Long courseId) {
        TravelCourse course = travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        course.increaseViewCount();

        List<CourseDetail> details =
                courseDetailRepository.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId);

        List<Long> placeIds = details.stream()
                .map(CourseDetail::getPlaceId)
                .toList();

        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds)
                .stream()
                .collect(Collectors.toMap(Place::getPlaceId, Function.identity()));

        List<CourseDetailResponse> detailResponses = details.stream()
                .map(detail -> new CourseDetailResponse(
                        detail,
                        placeMap.get(detail.getPlaceId())
                ))
                .toList();

        return new CourseResponse(course, detailResponses);
    }

    @Transactional
    public CourseResponse updateCourse(Long courseId, CourseUpdateRequest request) {
        TravelCourse course = travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        course.updateBasicInfo(
                request.getTitle(),
                request.getDescription(),
                request.getRegion(),
                request.getIsPublic()
        );

        return getCourse(courseId);
    }

    @Transactional
    public CourseResponse addCourseDetail(Long courseId, CourseDetailCreateRequest request) {
        TravelCourse course = travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        validateActivePlaceExists(request.getPlaceId());

        CourseDetail detail = new CourseDetail();
        detail.setCourseId(course.getCourseId());
        detail.setPlaceId(request.getPlaceId());
        detail.setDayNo(request.getDayNo() == null ? 1 : request.getDayNo());
        detail.setPlaceOrder(request.getPlaceOrder() == null
                ? courseDetailRepository.countByCourseId(courseId) + 1
                : request.getPlaceOrder());
        detail.setMemo(request.getMemo());
        detail.setVisitTime(request.getVisitTime());
        detail.setStayMinutes(request.getStayMinutes());
        courseDetailRepository.save(detail);

        return getCourse(courseId);
    }

    @Transactional
    public CourseResponse updateCourseDetails(Long courseId, CourseDetailUpdateRequest request) {
        travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        Map<Long, CourseDetail> detailMap = courseDetailRepository.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId)
                .stream()
                .collect(Collectors.toMap(CourseDetail::getDetailId, Function.identity()));

        for (CourseDetailUpdateRequest.CourseDetailItem item : request.getDetails()) {
            CourseDetail detail = detailMap.get(item.getDetailId());
            if (detail == null) {
                throw new IllegalArgumentException("코스 상세를 찾을 수 없습니다.");
            }

            detail.setDayNo(item.getDayNo() == null ? detail.getDayNo() : item.getDayNo());
            detail.setPlaceOrder(item.getPlaceOrder() == null ? detail.getPlaceOrder() : item.getPlaceOrder());
            detail.setMemo(item.getMemo());
            detail.setVisitTime(item.getVisitTime());
            detail.setStayMinutes(item.getStayMinutes());
        }

        return getCourse(courseId);
    }

    @Transactional
    public void deleteCourseDetail(Long courseId, Long detailId) {
        travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        CourseDetail detail = courseDetailRepository.findById(detailId)
                .orElseThrow(() -> new IllegalArgumentException("코스 상세를 찾을 수 없습니다."));

        if (!courseId.equals(detail.getCourseId())) {
            throw new IllegalArgumentException("해당 코스의 장소가 아닙니다.");
        }

        courseDetailRepository.delete(detail);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        TravelCourse course = travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다."));

        course.setIsPublic("N");
    }

    public List<CourseSummaryResponse> getPublicCourses() {
        return travelCourseRepository.findByIsPublicOrderByCreatedAtDesc("Y")
                .stream()
                .map(course -> new CourseSummaryResponse(
                        course,
                        courseDetailRepository.countByCourseId(course.getCourseId())
                ))
                .toList();
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }

    private void validateOptionalSurveyResultExists(Long resultId) {
        if (resultId != null && !surveyResultRepository.existsById(resultId)) {
            throw new IllegalArgumentException("설문 결과를 찾을 수 없습니다.");
        }
    }

    private void validateOptionalTravelTypeExists(String travelCode) {
        if (travelCode != null && !travelCode.isBlank() && !travelTypeMasterRepository.existsById(travelCode)) {
            throw new IllegalArgumentException("여행 유형을 찾을 수 없습니다.");
        }
    }

    private void validateActivePlaceExists(Long placeId) {
        placeRepository.findById(placeId)
                .filter(place -> "Y".equals(place.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));
    }
}