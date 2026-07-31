package com.seoulink.backend.domain.course.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CourseUpdateRequest {

    private String title;
    private String description;
    private String region;
    private String isPublic;
}
