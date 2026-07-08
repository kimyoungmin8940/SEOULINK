package com.seoulink.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CourseCreateRequest {
    @NotNull
    private Long memberId;
    private Long resultId;

    @NotEmpty
    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    @Size(min = 5, max = 5)
    private String travelCode;

    @Pattern(regexp = "CUSTOM|SURVEY|CHATBOT")
    private String courseType;

    @Size(max = 100)
    private String region;

    @Valid
    @NotEmpty
    private List<CoursePlaceRequest> places;

    public static class CoursePlaceRequest {
        @NotNull
        private Long placeId;

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

        public Long getPlaceId() { return placeId; }
        public void setPlaceId(Long placeId) { this.placeId = placeId; }
        public Integer getDayNo() { return dayNo; }
        public void setDayNo(Integer dayNo) { this.dayNo = dayNo; }
        public Integer getPlaceOrder() { return placeOrder; }
        public void setPlaceOrder(Integer placeOrder) { this.placeOrder = placeOrder; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public String getVisitTime() { return visitTime; }
        public void setVisitTime(String visitTime) { this.visitTime = visitTime; }
        public Integer getStayMinutes() { return stayMinutes; }
        public void setStayMinutes(Integer stayMinutes) { this.stayMinutes = stayMinutes; }
    }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTravelCode() { return travelCode; }
    public void setTravelCode(String travelCode) { this.travelCode = travelCode; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public List<CoursePlaceRequest> getPlaces() { return places; }
    public void setPlaces(List<CoursePlaceRequest> places) { this.places = places; }
}
