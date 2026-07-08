package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCreateRequest {
    @NotNull
    private Long memberId;

    @NotNull
    private Long placeId;

    @NotBlank
    @Size(max = 200)
    private String reviewTitle;

    @NotBlank
    @Size(max = 4000)
    private String reviewContent;

    @NotNull
    @DecimalMin("1.0")
    @DecimalMax("5.0")
    private Double rating;

    @Size(max = 1000)
    private String imageUrl;
}
