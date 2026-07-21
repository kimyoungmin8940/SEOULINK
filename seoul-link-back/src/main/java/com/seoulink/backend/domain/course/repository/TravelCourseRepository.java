package com.seoulink.backend.domain.course.repository;

import com.seoulink.backend.domain.course.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 여행 코스 기본 정보의 저장과 회원별 조회를 담당한다. */
public interface TravelCourseRepository extends JpaRepository<TravelCourse, Long> {

    /** 내 코스 화면을 위해 회원의 전체 코스를 최근 생성 순으로 조회한다. */
    List<TravelCourse> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /** 추천 목록처럼 특정 생성 유형의 코스만 최근 생성 순으로 조회한다. */
    List<TravelCourse> findByMemberIdAndCourseTypeOrderByCreatedAtDesc(
            Long memberId,
            String courseType
    );
}
