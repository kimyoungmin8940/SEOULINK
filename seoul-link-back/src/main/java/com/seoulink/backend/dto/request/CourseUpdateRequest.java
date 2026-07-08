package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CourseUpdateRequest {
    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    @Size(max = 100)
    private String region;

    @Pattern(regexp = "Y|N")
    private String isPublic;

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRegion() { return region; }
    public String getIsPublic() { return isPublic; }
}
