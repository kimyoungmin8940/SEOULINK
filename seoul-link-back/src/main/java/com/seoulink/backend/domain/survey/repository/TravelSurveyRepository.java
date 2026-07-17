package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 설문 실행 정보({@code TravelSurvey})의 저장·조회 기능을 담당할 Repository이다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface TravelSurveyRepository
        extends JpaRepository<TravelSurvey, Long> {

    /**
     * 비회원 토큰으로 설문 기록을 조회한다.
     */
    Optional<TravelSurvey> findByGuestToken(
            String guestToken
    );

    /**
     * 아직 회원에게 연결되지 않은 비회원 설문을 조회한다.
     */
    Optional<TravelSurvey>
    findByGuestTokenAndMemberIdIsNull(
            String guestToken
    );

    /**
     * 해당 비회원 토큰이 이미 존재하는지 확인한다.
     */
    boolean existsByGuestToken(
            String guestToken
    );

    /**
     * 회원의 전체 설문 기록을 최근 순으로 조회한다.
     */
    List<TravelSurvey>
    findByMemberIdOrderByCreatedAtDesc(
            Long memberId
    );

    /**
     * 회원의 가장 최근 설문 기록을 조회한다.
     */
    Optional<TravelSurvey>
    findFirstByMemberIdOrderByCreatedAtDesc(
            Long memberId
    );

    /**
     * 아직 회원에게 연결되지 않았으며 만료된
     * 비회원 설문 기록을 조회한다.
     */
    List<TravelSurvey>
    findByMemberIdIsNullAndExpiresAtBefore(
            LocalDateTime currentTime
    );
}
