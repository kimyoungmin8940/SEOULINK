package com.seoulink.backend.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 한 코스에 포함된 장소의 날짜·방문 순서·체류시간·이동 정보를 저장한다.
 *
 * <p>장소 엔티티가 아직 팀 통합 전이므로 {@code PLACE_ID}를 직접 보관한다.
 * 장소명, 주소, 이미지 같은 화면 정보는 조회 단계에서 이 ID로 PLACES와 연결한다.</p>
 */
@Entity
@Table(name = "COURSE_DETAILS")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_ID")
    private Long detailId;

    @Column(name = "COURSE_ID", nullable = false)
    private Long courseId;

    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    @Column(name = "DAY_NO", nullable = false)
    private Integer dayNo;

    @Column(name = "PLACE_ORDER", nullable = false)
    private Integer placeOrder;

    @Column(name = "MEMO", length = 500)
    private String memo;

    @Column(name = "VISIT_TIME", length = 50)
    private String visitTime;

    @Column(name = "STAY_MINUTES")
    private Integer stayMinutes;

    @Column(name = "VISIT_DATE")
    private LocalDate visitDate;

    @Builder.Default
    @Column(name = "DISTANCE_FROM_PREV_KM", nullable = false)
    private Double distanceFromPreviousKm = 0.0;

    @Builder.Default
    @Column(name = "TRAVEL_MINUTES_FROM_PREV", nullable = false)
    private Double travelTimeFromPreviousMinutes = 0.0;

    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
