// ReviewSection은 실제 여행자 후기 미리보기 영역
// 현재는 화면 확인용 임시 후기 데이터를 사용하고 있음
// API 응답 형태와 동일한 mockReviewListResponse.data를 사용하고 있음
import { useState } from 'react';
import { ChevronRight, Heart } from 'lucide-react';
import { requireLogin } from '../../utils/authGuard';

import { mockReviewListResponse } from '../../mocks/homeMockData';

const reviews = mockReviewListResponse.data;

// 별점을 그리기 위한 별 모양 SVG 컴포넌트
// filled 값이 true면 채워진 별, false면 빈 별처럼 보이도록 CSS 클래스 is-empty를 붙임
function RoundedStarIcon({ filled }) {
    return (
        <svg
            className={`review-star${filled ? '' : ' is-empty'}`}
            viewBox="0 0 24 24"
            aria-hidden="true"
            focusable="false"
        >
            <path d="M12 3.35L14.55 8.63L20.36 9.4L16.12 13.43L17.18 19.2L12 16.43L6.82 19.2L7.88 13.43L3.64 9.4L9.45 8.63L12 3.35Z" />
        </svg>
    );
}

function ReviewSection() {
    const [likedReviewIds, setLikedReviewIds] = useState(
        reviews.filter((review) => review.liked).map((review) => review.reviewId),
    );

    // 후기 카드 전체를 클릭하면 해당 후기 상세 임시 페이지로 이동
    const moveToReviewDetail = (reviewId) => {
        window.location.assign(`/reviews/${reviewId}`);
    };

    // 키보드 접근성: Enter 또는 Space로도 상세 페이지 이동
    const handleReviewKeyDown = (event, reviewId) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            moveToReviewDetail(reviewId);
        }
    };

    // 좋아요 버튼 클릭 시 카드 이동이 같이 실행되지 않도록 막고,
    // 로그인하지 않은 사용자는 좋아요를 누를 수 없게 안내함
    const handleReviewLikeClick = (event, reviewId) => {
        event.stopPropagation();

        if (!requireLogin('후기 좋아요는 로그인 후 이용할 수 있습니다.')) {
            return;
        }

        setLikedReviewIds((prev) => {
            if (prev.includes(reviewId)) {
                return prev.filter((id) => id !== reviewId);
            }

            return [...prev, reviewId];
        });
    };

    return (
        <section className="section review-section">
            {/* 섹션 제목과 전체 보기 버튼 영역*/}
            <div className="review-heading">
                <div className="review-heading-left">
                    <h2>실제 여행자들의 이야기</h2>
                    <p>SEOULINK와 함께한 여행자들의 생생한 후기</p>
                </div>

                {/* 추후 후기 게시판 전체 목록 페이지로 연결하면 됨*/}
                <a className="review-more-btn" href="/reviews">
                    전체 보기
                    <ChevronRight size={15} strokeWidth={2.2} />
                </a>
            </div>

            {/* 후기 카드 목록*/}
            <div className="review-grid">
                {reviews.map((review) => (
                    <article
                        className="review-card"
                        key={review.reviewId}
                        role="link"
                        tabIndex={0}
                        onClick={() => moveToReviewDetail(review.reviewId)}
                        onKeyDown={(event) => handleReviewKeyDown(event, review.reviewId)}
                    >
                        {/* 후기 카드 상단: 프로필 이미지, 닉네임, 별점, 좋아요 버튼 */}
                        <div className="review-top">
                            <img
                                className="avatar"
                                src={review.profileImageUrl}
                                alt={review.nickname}
                            />

                            <div>
                                <strong>{review.nickname}</strong>
                                <div className="review-stars" aria-label={`별점 ${review.rating}점`}>
                                    {/*
                                        길이 5짜리 배열을 만들어 별 5개를 출력
                                        index가 rating보다 작으면 채워진 별로 표시됨
                                    */}
                                    {Array.from({ length: 5 }, (_, index) => (
                                        <RoundedStarIcon
                                            key={index}
                                            filled={index < review.rating}
                                        />
                                    ))}
                                </div>
                            </div>

                            {/* 로그인한 사용자만 누를 수 있는 후기 좋아요 버튼 */}
                            <button
                                className={`review-heart-btn${likedReviewIds.includes(review.reviewId) ? ' is-liked' : ''}`}
                                type="button"
                                aria-label="후기 좋아요"
                                onClick={(event) => handleReviewLikeClick(event, review.reviewId)}
                            >
                                <Heart className="review-heart-icon" size={22} strokeWidth={1.85} />
                            </button>
                        </div>

                        {/* 후기 본문*/}
                        <p className="review-text">“{review.content}”</p>

                        {/* 후기 이미지 2장을 그리드 형태로 보여줌 */}
                        <div className="review-images">
                            {(review.imageUrls || []).map((url, index) => (
                                <img key={index} src={url} alt="후기 이미지" />
                            ))}
                        </div>
                    </article>
                ))}
            </div>
        </section>
    );
}

export default ReviewSection;
