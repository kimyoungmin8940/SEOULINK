package com.seoulink.backend.domain.course.repository;

import com.seoulink.backend.domain.course.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 여행 코스 기본 정보의 저장과 회원별 조회를 담당한다. */
public interface TravelCourseRepository extends JpaRepository<TravelCourse, Long> {

    /** 내 코스 화면을 위해 실제 저장된 코스만 최근 생성 순으로 조회한다. */
    List<TravelCourse> findByMemberIdAndSavedStatusOrderByCreatedAtDesc(
            Long memberId,
            String savedStatus
    );

    /** 비공개 저장 코스 상세를 조회할 때 코스 소유 회원까지 함께 검증한다. */
    Optional<TravelCourse> findByCourseIdAndMemberId(
            Long courseId,
            Long memberId
    );

    /** 추천 목록처럼 특정 생성 유형의 코스만 최근 생성 순으로 조회한다. */
    List<TravelCourse> findByMemberIdAndCourseTypeOrderByCreatedAtDesc(
            Long memberId,
            String courseType
    );

    /**
     * 같은 설문 결과에서 이미 저장한 추천 코스가 있는지 확인할 때 사용한다.
     *
     * <p>장소 구성까지 CourseSaveService에서 비교해 같은 추천 옵션만
     * 재사용하고, 같은 설문의 다른 옵션은 별도 코스로 저장한다.</p>
     */
    List<TravelCourse> findByMemberIdAndResultIdAndCourseTypeOrderByCreatedAtDesc(
            Long memberId,
            Long resultId,
            String courseType
    );
}
