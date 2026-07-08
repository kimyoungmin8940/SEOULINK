package com.seoulink.backend.repository;

import com.seoulink.backend.entity.CourseDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseDetailRepository extends JpaRepository<CourseDetail, Long> {
    List<CourseDetail> findByCourseIdOrderByDayNoAscPlaceOrderAsc(Long courseId);
    Integer countByCourseId(Long courseId);
    void deleteByCourseIdAndDetailId(Long courseId, Long detailId);
}
