package com.seoulink.backend.domain.course.controller;
import com.seoulink.backend.domain.course.service.CourseBuilderService;

import com.seoulink.backend.domain.course.dto.response.CourseBuilderPlaceResponse;
import com.seoulink.backend.domain.course.dto.request.CourseRouteRequest;
import com.seoulink.backend.domain.course.dto.response.CourseRouteResponse;
import com.seoulink.backend.domain.course.dto.request.CourseBuilderSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course-builder")
public class CourseBuilderController {

    private final CourseBuilderService courseBuilderService;

    public CourseBuilderController(CourseBuilderService courseBuilderService) {
        this.courseBuilderService = courseBuilderService;
    }

    @GetMapping("/places")
    public ResponseEntity<List<CourseBuilderPlaceResponse>> findPlaces(
            @RequestParam(defaultValue = "ALL") String theme,
            @RequestParam(defaultValue = "서울") String region,
            @RequestParam(defaultValue = "120") Integer limit
    ) {
        return ResponseEntity.ok(courseBuilderService.findPlaces(theme, region, limit));
    }

    @PostMapping("/routes")
    public ResponseEntity<CourseRouteResponse> calculateRoutes(
            @RequestBody CourseRouteRequest request
    ) {
        return ResponseEntity.ok(courseBuilderService.calculateRoutes(request));
    }

    @PostMapping("/courses")
    public ResponseEntity<CourseSaveResponse> saveCourse(
            @RequestBody CourseBuilderSaveRequest request
    ) {
        return ResponseEntity.ok(courseBuilderService.saveCourse(request));
    }

    @PutMapping("/courses/{courseId}")
    public ResponseEntity<CourseSaveResponse> updateCourse(
            @PathVariable Long courseId,
            @RequestBody CourseBuilderSaveRequest request
    ) {
        return ResponseEntity.ok(courseBuilderService.updateCourse(courseId, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("message", safeErrorMessage(e, "잘못된 요청입니다."));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleServerError(Exception e) {
        return Map.of("message", safeErrorMessage(e, "서버 오류가 발생했습니다."));
    }

    private String safeErrorMessage(Exception e, String defaultMessage) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return defaultMessage;
        }

        return e.getMessage();
    }
}
