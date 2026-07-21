package com.seoulink.backend.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 최적화가 끝난 여행 코스의 기본 정보와 전체 계산값을 저장한다.
 *
 * <p>회원·설문·결제 엔티티가 아직 팀 통합 전이므로 현재는 외래키 ID만 매핑한다.
 * 대상 엔티티가 완성되어도 이 방식으로 저장할 수 있으며, 필요하면 통합 단계에서
 * {@code @ManyToOne} 연관관계로 변경할 수 있다.</p>
 */
@Entity
@Table(name = "TRAVEL_COURSES")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TravelCourse {

    // 코스 식별자와 다른 도메인에 연결되는 외래키 ID이다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COURSE_ID")
    private Long courseId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "RESULT_ID")
    private Long resultId;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    // 코스 상세·카드 화면에 표시할 기본 정보이다.
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "TRAVEL_CODE", length = 5)
    private String travelCode;

    @Builder.Default
    @Column(name = "COURSE_TYPE", nullable = false, length = 20)
    private String courseType = "CUSTOM";

    @Column(name = "REGION", length = 100)
    private String region;

    // Oracle DB의 Y/N 형식에 맞춰 공개 여부를 문자열 한 글자로 보관한다.
    @Builder.Default
    @Column(name = "IS_PUBLIC", nullable = false, length = 1)
    private String publicStatus = "N";

    @Builder.Default
    @Column(name = "VIEW_COUNT", nullable = false)
    private Long viewCount = 0L;

    // COURSE_DETAILS의 장소별 값을 합산한 코스 전체 거리·시간 정보이다.
    @Builder.Default
    @Column(name = "TOTAL_DISTANCE_KM", nullable = false)
    private Double totalDistanceKm = 0.0;

    @Builder.Default
    @Column(name = "TOTAL_TRAVEL_MINUTES", nullable = false)
    private Double totalTravelTimeMinutes = 0.0;

    @Builder.Default
    @Column(name = "TOTAL_VISIT_MINUTES", nullable = false)
    private Integer totalVisitTimeMinutes = 0;

    @Builder.Default
    @Column(name = "TOTAL_COURSE_MINUTES", nullable = false)
    private Double totalCourseTimeMinutes = 0.0;

    // 생성·수정 시각은 애플리케이션 코드가 아닌 Hibernate가 자동으로 관리한다.
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
