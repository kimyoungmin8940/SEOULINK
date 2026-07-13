package com.seoulink.backend.coursebuilder;

import com.seoulink.backend.coursebuilder.dto.CourseBuilderPlaceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/course-builder")
@CrossOrigin(origins = "http://localhost:5173")
public class PlaceColumnSearchController {

    private final PlaceColumnSearchService placeColumnSearchService;

    public PlaceColumnSearchController(PlaceColumnSearchService placeColumnSearchService) {
        this.placeColumnSearchService = placeColumnSearchService;
    }

    @GetMapping("/classified-places")
    public ResponseEntity<List<CourseBuilderPlaceResponse>> findClassifiedPlaces(
            @RequestParam(defaultValue = "ALL") String theme,
            @RequestParam(defaultValue = "서울") String region,
            @RequestParam(defaultValue = "30") Integer limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String indoorYn,
            @RequestParam(required = false) String rainOkYn,
            @RequestParam(required = false) String nightOkYn,
            @RequestParam(required = false) String priceLevel
    ) {
        List<CourseBuilderPlaceResponse> places = placeColumnSearchService.findPlaces(
                theme,
                region,
                limit,
                category,
                indoorYn,
                rainOkYn,
                nightOkYn,
                priceLevel
        );

        return ResponseEntity.ok(places);
    }
}
