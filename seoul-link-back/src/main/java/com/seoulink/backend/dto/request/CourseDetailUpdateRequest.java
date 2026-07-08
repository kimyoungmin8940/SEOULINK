package com.seoulink.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CourseDetailUpdateRequest {
    @Valid
    @NotEmpty
    private List<CourseDetailItem> details;

    @Getter
    @Setter
    public static class CourseDetailItem {
        @NotNull
        private Long detailId;

        @Min(1)
        private Integer dayNo;

        @Min(1)
        private Integer placeOrder;

        @Size(max = 1000)
        private String memo;

        @Size(max = 20)
        private String visitTime;

        @Min(0)
        private Integer stayMinutes;
    }
}
