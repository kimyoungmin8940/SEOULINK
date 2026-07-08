package com.seoulink.backend.entity;

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