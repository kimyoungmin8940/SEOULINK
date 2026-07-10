package com.seoulink.backend.domain.member.repository;

/**
 * 회원 엔티티의 저장·조회 기능을 담당할 Spring Data JPA Repository이다.
 * 구현 시 {@code JpaRepository<Member, Long>}를 상속하고 이메일·닉네임 중복 조회 메서드를 선언한다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface MemberRepository {
    // TODO: 엔티티 매핑 완료 후 JpaRepository 상속 및 필요한 조회 메서드를 선언한다.
}
