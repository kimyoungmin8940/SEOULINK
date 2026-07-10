package com.seoulink.backend.domain.domain.review.repository;

/**
 * 후기 좋아요 엔티티의 저장·조회·삭제 기능을 담당할 Repository이다.
 * 회원 ID와 후기 ID 조합으로 좋아요 존재 여부를 확인하는 메서드가 필요하다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface ReviewLikeRepository {
    // TODO: 엔티티 매핑 완료 후 JpaRepository 상속 및 필요한 조회 메서드를 선언한다.
}
