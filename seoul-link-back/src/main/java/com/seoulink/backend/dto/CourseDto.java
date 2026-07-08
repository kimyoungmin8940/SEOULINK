package com.seoulink.backend.dto;

import java.util.List;

public class CourseDto {
    private Long id;
    private String title;
    private String description;
    private String typeCode;
    private List<String> places;

    public CourseDto() {
    }

    public CourseDto(Long id, String title, String description, String typeCode, List<String> places) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.typeCode = typeCode;
        this.places = places;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public List<String> getPlaces() {
        return places;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public void setPlaces(List<String> places) {
        this.places = places;
    }
}