import { Heart, MapPin, Star } from 'lucide-react';

function ReviewCard({ review, onOpen, onLike }) {
    const imageUrl = review.imageUrl || 'https://images.unsplash.com/photo-1538485399081-7c897b8c333d?auto=format&fit=crop&w=900&q=80';
    const createdAt = review.createdAt ? new Date(review.createdAt).toLocaleDateString('ko-KR') : '';

    return (
        <article className="review-list-card">
            <button type="button" className="review-card-image" onClick={() => onOpen(review.reviewId)} aria-label={`${review.reviewTitle} 상세 보기`}>
                <img src={imageUrl} alt="" />
            </button>
            <div className="review-card-body">
                <div className="review-card-meta"><MapPin size={15} /> {review.placeName || `장소 #${review.placeId}`}</div>
                <button type="button" className="review-card-title" onClick={() => onOpen(review.reviewId)}>{review.reviewTitle}</button>
                <p>{review.reviewContent}</p>
                <div className="review-card-author">
                    <span>{review.authorName || `여행자 ${review.memberId}`}</span>
                    <span className="review-card-rating"><Star size={16} fill="currentColor" /> {Number(review.rating).toFixed(1)}</span>
                </div>
                <div className="review-card-footer">
                    <button type="button" onClick={() => onLike(review.reviewId)}><Heart size={16} /> {review.likeCount ?? 0}</button>
                    <span>조회 {review.viewCount ?? 0}</span>
                    <time>{createdAt}</time>
                </div>
            </div>
        </article>
    );
}

export default ReviewCard;
