package com.seoulink.backend.domain.domain.course.repository;

/**
 * 코스에 포함되는 상세 장소({@code CourseDetail})의 저장·조회 기능을 담당할 Repository이다.
 * 코스 ID와 방문 순서를 기준으로 정렬 조회한다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface CourseDetailRepository {
    // TODO: 엔티티 매핑 완료 후 JpaRepository 상속 및 필요한 조회 메서드를 선언한다.
}
