package com.seoulink.backend.domain.review.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
/**
 * 클라이언트 요청 값을 검증하고 전달하는 DTO입니다.
 */
public class ReviewUpdateRequest {
    @NotNull private Long memberId;
    @NotBlank @Size(max = 200) private String reviewTitle;
    @NotBlank @Size(max = 4000) private String reviewContent;
    @NotNull @DecimalMin("0.0") @DecimalMax("5.0") private Double rating;
    private LocalDate visitDate;
    @Size(max = 30) private String companion;
    @Size(max = 8) private List<@Size(max = 1000) String> imageUrls;
    @Size(max = 8) private List<@NotBlank @Size(max = 30) String> tags;
}
