package com.seoulink.backend.domain.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 유형별 이름, 설명, 대표 이미지를 저장하는 엔티티이다.
 *
 * <p>여행 취향 검사의 다섯 가지 기준으로 만들어진
 * 5글자 여행 유형 코드를 기본키로 사용한다.</p>
 */

@Entity
@Table(name = "TRAVEL_TYPE_MASTER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelTypeMaster {

    //5글자 여행 유형 코드
    @Id
    @Column(
            name = "TRAVEL_CODE",
            nullable = false,
            length = 5,
            columnDefinition = "CHAR(5)"
    )
    private String travelCode;

    //사용자에게 보여줄 여행 유형 제목
    @Column(
            name = "TYPE_TITLE",
            nullable = false,
            length = 100
    )
    private String typeTitle;

    //여행 유형에 대한 상세 설명
    @Column(
            name = "TYPE_DESCRIPTION",
            nullable = false,
            length = 2000
    )
    private String typeDescription;

    //여행 유형 결과 화면에 표시할 대표 이미지 경로
    @Column(
            name = "IMAGE_URL",
            length = 500
    )
    private String imageUrl;
}
