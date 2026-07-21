package com.seoulink.backend.domain.review.dto.response;

import lombok.Getter;
import java.util.List;

@Getter
public class ReviewCourseSummaryResponse {
    private final Long courseId;
    private final String title;
    private final String region;
    private final List<ReviewCourseStopResponse> stops;

    public ReviewCourseSummaryResponse(Long courseId, String title, String region, List<ReviewCourseStopResponse> stops) {
        this.courseId = courseId;
        this.title = title;
        this.region = region;
        this.stops = stops;
    }
}
