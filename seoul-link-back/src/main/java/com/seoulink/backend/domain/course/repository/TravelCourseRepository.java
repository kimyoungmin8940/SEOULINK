package com.seoulink.backend.domain.course.repository;

import com.seoulink.backend.domain.course.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 여행 코스 기본 정보의 저장과 회원별 조회를 담당한다. */
public interface TravelCourseRepository extends JpaRepository<TravelCourse, Long> {

    List<TravelCourse> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
