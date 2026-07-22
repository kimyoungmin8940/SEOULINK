package com.seoulink.backend.domain.course.entity;

import com.seoulink.backend.domain.course.model.TransitPathType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // COURSE_DETAILS 행 자체의 식별자와 상위 코스·장소 연결 ID이다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COURSE_DETAIL_ID")
    private Long detailId;

    @Column(name = "COURSE_ID", nullable = false)
    private Long courseId;

    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    // dayNo는 코스의 N일차, placeOrder는 해당 날짜 안에서의 방문 순서이다.
    @Column(name = "DAY_NO", nullable = false)
    private Integer dayNo;

    @Column(name = "PLACE_ORDER", nullable = false)
    private Integer placeOrder;

    // 사용자가 직접 작성하거나 화면에서 선택적으로 사용하는 일정 부가 정보이다.
    @Column(name = "MEMO", length = 500)
    private String memo;

    @Column(name = "VISIT_TIME", length = 50)
    private String visitTime;

    @Column(name = "STAY_MINUTES")
    private Integer stayMinutes;

    @Column(name = "VISIT_DATE")
    private LocalDate visitDate;

    // 같은 날짜의 첫 장소는 이전 지점이 없으므로 두 값을 기본값 0으로 저장한다.
    @Builder.Default
    @Column(name = "DISTANCE_FROM_PREV_KM", nullable = false)
    private Double distanceFromPreviousKm = 0.0;

    @Builder.Default
    @Column(name = "TRAVEL_MINUTES_FROM_PREV", nullable = false)
    private Double travelTimeFromPreviousMinutes = 0.0;

    // 대중교통 코스만 ODsay 최적 경로의 지하철·버스·혼합 종류를 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSIT_PATH_TYPE", length = 20)
    private TransitPathType transitPathType;

    // Hibernate가 최초 저장 시각을 자동으로 기록한다.
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
