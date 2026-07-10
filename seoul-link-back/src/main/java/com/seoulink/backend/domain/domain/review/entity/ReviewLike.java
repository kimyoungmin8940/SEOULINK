package com.seoulink.backend.domain.domain.review.entity;

/**
 * ERD의 {@code REVIEW_LIKE} 테이블과 매핑될 후기 좋아요 엔티티이다.
 *
 * <p>한 회원이 어떤 후기에 좋아요를 눌렀는지 기록한다.
 * 동일 회원이 같은 후기에 중복 좋아요를 저장하지 못하도록
 * {@code REVIEW_ID + MEMBER_ID} 조합에 UNIQUE 제약조건을 두는 것이 좋다.</p>
 */
public class ReviewLike {
    // TODO: 담당 기능의 요구사항과 API 명세가 확정되면 구현한다.
}
