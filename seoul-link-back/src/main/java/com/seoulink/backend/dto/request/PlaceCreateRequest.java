package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceCreateRequest {

    @NotBlank
    @Size(max = 30)
    private String apiProvider;

    @NotBlank
    @Size(max = 100)
    private String apiPlaceId;
    private Long contentId;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Pattern(regexp = "TOUR|RESTAURANT|CAFE|HOTEL")
    private String category;

    @Size(max = 200)
    private String apiCategory;

    @NotBlank
    @Size(max = 100)
    private String region;

    @NotBlank
    @Size(max = 500)
    private String address;

    @Size(max = 500)
    private String roadAddress;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @Size(max = 50)
    private String phone;

    @Size(max = 1000)
    private String placeUrl;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private Double rating;

    @Min(0)
    private Integer reviewCount;

    private String description;

    @Size(max = 1000)
    private String imageUrl;

    @Pattern(regexp = "Y|N")
    private String tagHistory;

    @Pattern(regexp = "Y|N")
    private String tagModern;

    @Pattern(regexp = "Y|N")
    private String tagBudget;

    @Pattern(regexp = "Y|N")
    private String tagLuxury;

    @Pattern(regexp = "Y|N")
    private String tagStable;

    @Pattern(regexp = "Y|N")
    private String tagDopamine;

    @Pattern(regexp = "Y|N")
    private String tagRelax;

    @Pattern(regexp = "Y|N")
    private String tagPacked;
}
