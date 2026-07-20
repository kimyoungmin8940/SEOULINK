package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REVIEW_TAG")
@Getter
@NoArgsConstructor
public class ReviewTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_TAG_ID") private Long reviewTagId;
    @Column(name = "REVIEW_ID", nullable = false) private Long reviewId;
    @Column(name = "TAG_NAME", nullable = false) private String tagName;
    public ReviewTag(Long reviewId, String tagName) { this.reviewId = reviewId; this.tagName = tagName; }
}
