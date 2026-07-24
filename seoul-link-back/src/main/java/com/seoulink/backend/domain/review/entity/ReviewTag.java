package com.seoulink.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REVIEW_TAG")
@Getter
@NoArgsConstructor
/**
 * 데이터베이스에 저장되는 도메인 엔티티입니다.
 */
public class ReviewTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_TAG_ID") private Long reviewTagId;
    @Column(name = "REVIEW_ID", nullable = false) private Long reviewId;
    @Column(name = "TAG_NAME", nullable = false) private String tagName;
    public ReviewTag(Long reviewId, String tagName) { this.reviewId = reviewId; this.tagName = tagName; }
}
