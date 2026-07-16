package com.seoulink.backend.domain.course.repository;

import com.seoulink.backend.domain.course.entity.CourseDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 코스에 포함된 장소의 날짜별 방문 순서 정보를 저장하고 조회한다. */
public interface CourseDetailRepository extends JpaRepository<CourseDetail, Long> {

    /** 상세 화면에 바로 사용할 수 있도록 일차와 방문 순서 기준으로 정렬해 조회한다. */
    List<CourseDetail> findByCourseIdOrderByDayNoAscPlaceOrderAsc(Long courseId);
}
