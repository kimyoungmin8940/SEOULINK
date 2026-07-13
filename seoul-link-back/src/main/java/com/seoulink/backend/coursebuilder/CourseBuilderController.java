package com.seoulink.backend.coursebuilder;

import com.seoulink.backend.coursebuilder.dto.CourseBuilderPlaceResponse;
import com.seoulink.backend.coursebuilder.dto.CourseRouteRequest;
import com.seoulink.backend.coursebuilder.dto.CourseRouteResponse;
import com.seoulink.backend.coursebuilder.dto.CourseSaveRequest;
import com.seoulink.backend.coursebuilder.dto.CourseSaveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course-builder")
@CrossOrigin(origins = "http://localhost:5173")
public class CourseBuilderController {

    private final CourseBuilderService courseBuilderService;

    public CourseBuilderController(CourseBuilderService courseBuilderService) {
        this.courseBuilderService = courseBuilderService;
    }

    /*
     * DB에 저장된 장소 조회
     *
     * 예:
     * GET /api/course-builder/places?theme=ALL&region=서울&limit=30
     * GET /api/course-builder/places?theme=FOOD_TOUR&region=서울&limit=120
     */
    @GetMapping("/places")
    public ResponseEntity<List<CourseBuilderPlaceResponse>> findPlaces(
            @RequestParam(defaultValue = "ALL") String theme,
            @RequestParam(defaultValue = "서울") String region,
            @RequestParam(defaultValue = "120") Integer limit
    ) {
        List<CourseBuilderPlaceResponse> places = courseBuilderService.findPlaces(
                theme,
                region,
                limit
        );

        return ResponseEntity.ok(places);
    }

    /*
     * 선택한 코스 장소들의 이동거리 / 이동시간 계산
     *
     * 프론트에서 선택한 장소 목록을 보내면
     * 백엔드에서 OpenRouteService API로 도보 이동거리와 시간을 계산한다.
     *
     * 예:
     * POST /api/course-builder/routes
     */
    @PostMapping("/routes")
    public ResponseEntity<CourseRouteResponse> calculateRoutes(
            @RequestBody CourseRouteRequest request
    ) {
        CourseRouteResponse response = courseBuilderService.calculateRoutes(request);

        return ResponseEntity.ok(response);
    }

    /*
     * 코스 저장
     *
     * TRAVEL_COURSES 저장
     * PLACES에 없는 카카오 장소는 PLACES 저장
     * COURSE_DETAILS에 장소 순서, 방문 일차, 방문 시간, 체류시간, 이동거리 저장
     *
     * 예:
     * POST /api/course-builder/courses
     */
    @PostMapping("/courses")
    public ResponseEntity<CourseSaveResponse> saveCourse(
            @RequestBody CourseSaveRequest request
    ) {
        CourseSaveResponse response = courseBuilderService.saveCourse(request);

        return ResponseEntity.ok(response);
    }

    /*
     * 잘못된 요청 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of(
                "message",
                e.getMessage()
        );
    }

    /*
     * 서버 내부 오류 처리
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleServerError(Exception e) {
        e.printStackTrace();

        return Map.of(
                "message",
                e.getMessage()
        );
    }
}