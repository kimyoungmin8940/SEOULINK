package com.seoulink.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEW_COMMENT")
@Getter
@Setter
@NoArgsConstructor
/**
 * 데이터베이스에 저장되는 도메인 엔티티입니다.
 */
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_ID")
    private Long commentId;

    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "IS_DELETED", nullable = false)
    private String isDeleted = "N";

    public ReviewComment(Long reviewId, Long memberId, String content) {
        this.reviewId = reviewId;
        this.memberId = memberId;
        this.content = content;
    }
}