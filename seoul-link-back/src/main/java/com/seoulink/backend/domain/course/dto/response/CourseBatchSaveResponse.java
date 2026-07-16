package com.seoulink.backend.domain.course.dto.response;

import java.util.ArrayList;
import java.util.List;

/** 복수 코스가 하나의 트랜잭션으로 저장된 결과를 반환한다. */
public class CourseBatchSaveResponse {

    private Integer savedCount;
    private List<CourseSaveResponse> savedCourses = new ArrayList<>();

    public CourseBatchSaveResponse() {
    }

    public CourseBatchSaveResponse(
            Integer savedCount,
            List<CourseSaveResponse> savedCourses
    ) {
        this.savedCount = savedCount;
        this.savedCourses = savedCourses;
    }

    public Integer getSavedCount() {
        return savedCount;
    }

    public void setSavedCount(Integer savedCount) {
        this.savedCount = savedCount;
    }

    public List<CourseSaveResponse> getSavedCourses() {
        return savedCourses;
    }

    public void setSavedCourses(List<CourseSaveResponse> savedCourses) {
        this.savedCourses = savedCourses;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer savedCount;
        private List<CourseSaveResponse> savedCourses = new ArrayList<>();

        public Builder savedCount(Integer savedCount) {
            this.savedCount = savedCount;
            return this;
        }

        public Builder savedCourses(List<CourseSaveResponse> savedCourses) {
            this.savedCourses = savedCourses;
            return this;
        }

        public CourseBatchSaveResponse build() {
            return new CourseBatchSaveResponse(savedCount, savedCourses);
        }
    }
}
