package com.seoulink.backend.domain.course.dto.request;

import java.util.ArrayList;
import java.util.List;

/** 사용자가 추천 옵션 중 복수 코스를 한 번에 저장할 때 사용하는 요청 DTO이다. */
public class CourseBatchSaveRequest {

    /** 한 번의 요청으로 저장할 선택 코스 목록이다. 추천 옵션 수에 맞춰 최대 3개까지 허용한다. */
    private List<CourseSaveRequest> courses = new ArrayList<>();

    public CourseBatchSaveRequest() {
    }

    public CourseBatchSaveRequest(List<CourseSaveRequest> courses) {
        this.courses = courses;
    }

    public List<CourseSaveRequest> getCourses() {
        return courses;
    }

    public void setCourses(List<CourseSaveRequest> courses) {
        this.courses = courses;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<CourseSaveRequest> courses = new ArrayList<>();

        public Builder courses(List<CourseSaveRequest> courses) {
            this.courses = courses;
            return this;
        }

        public CourseBatchSaveRequest build() {
            return new CourseBatchSaveRequest(courses);
        }
    }
}
