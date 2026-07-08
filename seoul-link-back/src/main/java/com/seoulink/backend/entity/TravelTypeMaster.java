package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TRAVEL_TYPE_MASTER")
@Getter
@Setter
@NoArgsConstructor
public class TravelTypeMaster {

    @Id
    @Column(name = "TRAVEL_CODE", length = 5)
    private String travelCode;

    @Column(name = "TYPE_TITLE", nullable = false)
    private String typeTitle;

    @Column(name = "TYPE_DESCRIPTION", nullable = false)
    private String typeDescription;

    @Column(name = "IMAGE_URL")
    private String imageUrl;
}