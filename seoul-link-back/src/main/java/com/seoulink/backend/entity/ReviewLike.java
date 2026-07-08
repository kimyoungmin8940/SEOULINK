package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEW_LIKE")
@Getter
@Setter
@NoArgsConstructor
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LIKE_ID")
    private Long likeId;

    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReviewLike(Long reviewId, Long memberId) {
        this.reviewId = reviewId;
        this.memberId = memberId;
    }
}