package com.seoulink.backend.domain.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentCreateRequest {
    @NotNull
    private Long memberId;

    @NotBlank
    @Size(max = 1000)
    private String content;
}
