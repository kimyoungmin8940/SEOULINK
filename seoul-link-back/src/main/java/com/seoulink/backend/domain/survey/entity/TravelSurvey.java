package com.seoulink.backend.domain.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * ERD의 {@code TRAVEL_SURVEY} 테이블과 매핑될 설문 실행 정보 엔티티이다.
 *
 * <p>설문을 수행한 회원, 여행 지역, 여행 기간, 인원수,
 * 설문 진행 상태와 생성·종료 시각 등을 저장한다.</p>
 */
@Entity
@Table(name = "TRAVEL_SURVEY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SURVEY_ID")
    private Long surveyId;

    //회원 검사이면 회원 번호를 저장
    //비회원 검사이면 null
    @Column(name = "MEMBER_ID")
    private Long memberId;

    //비회원 검사 결과를 구분하기 위한 임시 토큰
    @Column(
            name = "GUEST_TOKEN",
            length = 100,
            unique = true
    )
    private String guestToken;

    @Column(
            name = "REGION",
            nullable = false,
            length = 50
    )
    private String region;

    @Column(
            name = "START_DATE",
            nullable = false
    )
    private LocalDate startDate;

    @Column(
            name = "END_DATE",
            nullable = false
    )
    private LocalDate endDate;

    @Column(
            name = "COMPANION_TYPE",
            nullable = false,
            length = 30
    )
    private String companionType;

    @Column(
            name = "TRANSPORT_TYPE",
            nullable = false,
            length = 30
    )
    private String transportType;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    //비회원 검사 결과가 만료되는 시각.
    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

    //비회원 검사 결과가 회원에게 연결된 시각
    @Column(name = "CLAIMED_AT")
    private LocalDateTime claimedAt;

    //비회원 검사 기록 저장
    public static TravelSurvey createGuestSurvey(
            String guestToken,
            String region,
            LocalDate startDate,
            LocalDate endDate,
            String companionType,
            String transportType,
            LocalDateTime expiresAt
    ) {
        TravelSurvey survey = new TravelSurvey();

        survey.guestToken = guestToken;
        survey.region = region;
        survey.startDate = startDate;
        survey.endDate = endDate;
        survey.companionType = companionType;
        survey.transportType = transportType;
        survey.createdAt = LocalDateTime.now();
        survey.expiresAt = expiresAt;

        return survey;
    }

    //비회원 검사 결과를 가입한 회원에게 연결
    public void claimByMember(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException(
                    "회원 번호는 null일 수 없습니다"
            );
        }

        if (claimedAt != null) {
            throw new IllegalStateException(
                    "이미 회원에게 연결된 검사 결과입니다"
            );
        }

        if (expiresAt != null &&
                expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "만료된 검사 결과입니다"
            );
        }

        this.memberId = memberId;
        this.claimedAt = LocalDateTime.now();
    }

    //비회원 검사 결과의 만료 여부 확인
    public boolean isExpired() {
        return expiresAt != null &&
                expiresAt.isBefore(LocalDateTime.now());
    }

    //회원에게 연결된 검사 결과인지 확인
    public boolean isClaimed() {
        return memberId != null && claimedAt != null;
    }
}
