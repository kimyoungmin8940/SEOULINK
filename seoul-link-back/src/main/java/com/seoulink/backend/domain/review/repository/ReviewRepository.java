package com.seoulink.backend.domain.review.repository;

/**
 * 후기 엔티티의 저장·조회 기능을 담당할 Repository이다.
 * 삭제되지 않은 후기, 장소별 후기, 회원별 후기 조회 메서드를 정의한다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface ReviewRepository {
    // TODO: 엔티티 매핑 완료 후 JpaRepository 상속 및 필요한 조회 메서드를 선언한다.
}
