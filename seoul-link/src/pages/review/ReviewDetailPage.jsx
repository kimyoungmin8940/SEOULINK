import { useEffect, useState } from 'react';
import { CalendarDays, Camera, Heart, MapPin, MessageCircle, Route, Star, UsersRound } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { createComment, getReviewComments, getReviewDetail, likeReview } from '../../api/reviewApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';

const dateText = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }) : '여행 기록';

function ReviewDetailPage() {
    const reviewId = window.location.pathname.split('/').pop();
    const member = authStore.getMember();
    const [review, setReview] = useState(null);
    const [comments, setComments] = useState([]);
    const [comment, setComment] = useState('');
    const [error, setError] = useState('');

    const load = async () => {
        try {
            const [reviewData, commentData] = await Promise.all([
                getReviewDetail(reviewId, member?.memberId),
                getReviewComments(reviewId),
            ]);
            setReview(reviewData);
            setComments(commentData);
        } catch (err) { setError(err.message || '후기를 불러오지 못했습니다.'); }
    };
    useEffect(() => { load(); }, [reviewId]);
    const toggleLike = async () => {
        if (!member?.memberId) { window.location.href = '/login'; return; }
        await likeReview(reviewId, member.memberId); load();
    };
    const submitComment = async (event) => {
        event.preventDefault();
        if (!member?.memberId) { window.location.href = '/login'; return; }
        await createComment(reviewId, { memberId: member.memberId, content: comment });
        setComment(''); load();
    };

    if (error) return <><Header /><p className="review-message">{error}</p></>;
    if (!review) return <><Header /><p className="review-message">후기를 불러오는 중입니다.</p></>;
    const photos = review.imageUrls?.length ? review.imageUrls : [review.placeImageUrl].filter(Boolean);
    const heroImage = photos[0] || '/review-seed/bukchon-sunrise.png';
    const course = review.courseSummary;

    return <><Header />
        <main className="review-story-page">
            <div className="review-story-breadcrumb">홈　›　여행 후기　›　{review.reviewTitle}</div>
            <section className="review-story-hero" style={{ backgroundImage: `linear-gradient(90deg, rgba(7,31,68,.76) 0%, rgba(7,31,68,.24) 63%, rgba(7,31,68,.08)), url("${heroImage}")` }}>
                <div className="review-story-hero-content">
                    <div className="story-tag-row">{review.tags?.slice(0, 2).map((tag) => <span key={tag}>#{tag}</span>)}</div>
                    <h1>{review.reviewTitle.replace('[DEMO] ', '')}</h1>
                    <div className="story-author-row">
                        <div className="story-avatar">{review.authorName?.slice(0, 1)}</div>
                        <div><strong>{review.authorName}</strong><small>{review.placeName} · {review.companion || '서울 여행'}</small></div>
                        <div className="story-rating"><Star fill="currentColor" /> <b>{review.rating.toFixed(1)}</b><small>/ 5.0</small><time>{dateText(review.visitDate || review.createdAt?.slice(0, 10))} 작성</time></div>
                    </div>
                </div>
            </section>

            <div className="review-story-layout">
                <div className="review-story-main">
                    <article className="review-story-article">
                        <div className="story-place-line"><MapPin /> <strong>{review.placeName}</strong><span>{review.visitDate && dateText(review.visitDate)}</span></div>
                        <p className="story-content">{review.reviewContent}</p>
                        {photos.length > 0 && <div className={`story-photo-wall count-${Math.min(photos.length, 5)}`}>{photos.slice(0, 5).map((photo, index) => <figure key={`${photo}-${index}`}><img src={photo} alt={`${review.reviewTitle} 사진 ${index + 1}`} />{index === 4 && photos.length > 5 && <figcaption>+{photos.length - 5}<small>더보기</small></figcaption>}</figure>)}</div>}
                        <div className="story-reaction-bar"><button className={review.likedByMe ? 'liked' : ''} onClick={toggleLike}><Heart fill={review.likedByMe ? 'currentColor' : 'none'} /> 도움이 됐어요 <b>{review.likeCount}</b></button><span>조회 {review.viewCount}</span></div>
                    </article>
                    <section className="story-comments"><h2>댓글 <em>{comments.length}</em></h2><form onSubmit={submitComment}><div className="comment-avatar">{member?.nickname?.slice(0, 1) || '나'}</div><input required value={comment} onChange={(event) => setComment(event.target.value)} maxLength="500" placeholder="따뜻한 댓글을 남겨주세요 :)" /><button>등록</button></form><div className="comment-list">{comments.map((item) => <article key={item.commentId}><div className="comment-avatar muted">여</div><div><strong>서울 여행자</strong><time>{item.createdAt?.slice(0, 10)}</time><p>{item.content}</p></div></article>)}</div></section>
                </div>
                <aside className="review-course-panel">
                    <h2><Route /> 여행 코스 요약</h2>
                    <div className="course-summary-top"><p><CalendarDays /> {dateText(review.visitDate)}</p><p><UsersRound /> {review.companion || '서울 여행'}</p></div>
                    {course ? <><h3>{course.title}</h3><ol className="course-stop-list">{course.stops.map((stop, index) => <li key={`${stop.order}-${index}`}><b>{index + 1}</b><div><time>{stop.visitTime || '여행 중'}</time><strong>{stop.placeName}</strong>{stop.memo && <span>{stop.memo}</span>}</div></li>)}</ol><p className="course-count">총 {course.stops.length}개 장소</p></> : <div className="course-fallback"><Camera /><strong>{review.placeName}</strong><p>이 후기는 {review.companion || '여행'}과 함께 남긴 서울 여행 기록입니다.</p><span>장소 후기</span></div>}
                    <div className="course-spend"><h3>여행 기록</h3><p><span>평점</span><b>★ {review.rating.toFixed(1)}</b></p><p><span>사진</span><b>{photos.length}장</b></p><p><span>댓글</span><b>{comments.length}개</b></p></div>
                </aside>
            </div>
        </main><Footer />
    </>;
}

export default ReviewDetailPage;
