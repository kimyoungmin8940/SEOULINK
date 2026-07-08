package com.seoulink.backend.repository;

import com.seoulink.backend.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelCourseRepository extends JpaRepository<TravelCourse, Long> {
    List<TravelCourse> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<TravelCourse> findByIsPublicOrderByCreatedAtDesc(String isPublic);
}