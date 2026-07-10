package com.seoulink.backend.domain.chatbot.repository;

/**
 * 챗봇 대화 이력 엔티티의 저장·조회 기능을 담당할 Repository이다.
 * 회원별 최신 대화 이력을 조회할 수 있도록 정렬 조건을 정의한다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface ChatbotHistoryRepository {
    // TODO: 엔티티 매핑 완료 후 JpaRepository 상속 및 필요한 조회 메서드를 선언한다.
}
