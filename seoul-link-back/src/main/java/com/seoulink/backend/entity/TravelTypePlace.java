package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TRAVEL_TYPE_PLACE")
@Getter
@Setter
@NoArgsConstructor
public class TravelTypePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TYPE_PLACE_ID")
    private Long typePlaceId;

    @Column(name = "TRAVEL_CODE", nullable = false)
    private String travelCode;

    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    @Column(name = "WEIGHT_SCORE", nullable = false)
    private Integer weightScore;
}