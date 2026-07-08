package com.seoulink.backend.controller;

import com.seoulink.backend.dto.CourseDto;
import com.seoulink.backend.dto.request.CourseCreateRequest;
import com.seoulink.backend.dto.request.CourseDetailCreateRequest;
import com.seoulink.backend.dto.request.CourseDetailUpdateRequest;
import com.seoulink.backend.dto.response.CourseResponse;
import com.seoulink.backend.dto.response.CourseSummaryResponse;
import com.seoulink.backend.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.seoulink.backend.dto.request.CourseUpdateRequest;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public CourseDto createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return courseService.createCourse(request);
    }

    @GetMapping("/members/{memberId}")
    public List<CourseSummaryResponse> getMyCourses(@PathVariable Long memberId) {
        return courseService.getMyCourses(memberId);
    }

    @GetMapping("/{courseId}")
    public CourseResponse getCourse(@PathVariable Long courseId) {
        return courseService.getCourse(courseId);
    }

    @PutMapping("/{courseId}")
    public CourseResponse updateCourse(@PathVariable Long courseId, @Valid @RequestBody CourseUpdateRequest request) {
        return courseService.updateCourse(courseId, request);
    }

    @PostMapping("/{courseId}/details")
    public CourseResponse addCourseDetail(@PathVariable Long courseId, @Valid @RequestBody CourseDetailCreateRequest request) {
        return courseService.addCourseDetail(courseId, request);
    }

    @PutMapping("/{courseId}/details")
    public CourseResponse updateCourseDetails(@PathVariable Long courseId, @Valid @RequestBody CourseDetailUpdateRequest request) {
        return courseService.updateCourseDetails(courseId, request);
    }

    @DeleteMapping("/{courseId}/details/{detailId}")
    public void deleteCourseDetail(@PathVariable Long courseId, @PathVariable Long detailId) {
        courseService.deleteCourseDetail(courseId, detailId);
    }

    @DeleteMapping("/{courseId}")
    public void deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
    }

    @GetMapping("/public")
    public List<CourseSummaryResponse> getPublicCourses() {
        return courseService.getPublicCourses();
    }
}
