import { useEffect, useRef, useState } from 'react';
import html2canvas from 'html2canvas';
import { CalendarDays, Camera, Download, Heart, MapPin, MessageCircle, Pencil, Route, Star, Trash2, UsersRound } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { createComment, deleteReview, deleteReviewComment, getReviewComments, getReviewDetail, likeReview, recordReviewView } from '../../api/reviewApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';
const dateText = value => value ? new Date(`${value}T00:00:00`).toLocaleDateString('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
}) : '여행 기록';
// URL의 후기 식별자로 API 데이터 또는 데모 데이터를 선택해 화면을 구성한다.
function ReviewDetailPage() {
  const reviewId = window.location.pathname.split('/').pop();
  const member = authStore.getMember();
  const [review, setReview] = useState(null);
  const [comments, setComments] = useState([]);
  const [comment, setComment] = useState('');
  const [error, setError] = useState('');
  // StrictMode의 개발용 재실행과 좋아요·댓글 후 재조회에도 같은 글의 조회 등록은 한 번만 허용한다.
  const viewedReviewIds = useRef(new Set());
  const courseSummaryRef = useRef(null);
  const [isSavingCourseSummary, setIsSavingCourseSummary] = useState(false);


  // 리뷰 본문과 댓글을 병렬로 요청해 상세 화면의 모든 데이터를 한 번에 갱신한다.
  const load = async () => {
    try {
      const [reviewData, commentData] = await Promise.all([getReviewDetail(reviewId, member?.memberId), getReviewComments(reviewId)]);
      setReview(reviewData);
      setComments(commentData);
    } catch (err) {
      setError(err.message || '후기를 불러오지 못했습니다.');
    }
  };
  useEffect(() => {
    const loadAndRecordView = async () => {
      await load();
      if (viewedReviewIds.current.has(reviewId)) return;

      viewedReviewIds.current.add(reviewId);
      try {
        const viewedReview = await recordReviewView(reviewId, member?.memberId);
        setReview(viewedReview);
      } catch (err) {
        setError(err.message || '조회수를 기록하지 못했습니다.');
      }
    };
    loadAndRecordView();
  }, [reviewId]);
  // 로그인한 회원만 좋아요를 토글한 뒤 최신 상태를 다시 불러온다.
  const toggleLike = async () => {
    if (!member?.memberId) {
      window.location.href = '/login';
      return;
    }
    await likeReview(reviewId, member.memberId);
    load();
  };
  // 댓글 등록 후 목록을 재조회해 즉시 화면에 반영한다.
  const submitComment = async event => {
    event.preventDefault();
    if (!member?.memberId) {
      window.location.href = '/login';
      return;
    }
    await createComment(reviewId, {
      memberId: member.memberId,
      content: comment
    });
    setComment('');
    load();
  };
  const removeComment = async commentId => {
    if (!member?.memberId || !window.confirm('이 댓글을 삭제할까요?')) return;
    try {
      await deleteReviewComment(reviewId, commentId, member.memberId);
      setComments(current => current.filter(item => item.commentId !== commentId));
    } catch (err) {
      setError(err.message || '댓글을 삭제하지 못했습니다.');
    }
  };
  const removeReview = async () => {
    if (!member?.memberId || !window.confirm('이 후기를 삭제할까요? 삭제한 후기는 복구할 수 없습니다.')) return;
    try {
      await deleteReview(reviewId, member.memberId);
      window.location.href = '/reviews';
    } catch (err) {
      setError(err.message || '후기 삭제에 실패했습니다.');
    }
  };

  // 데이터 로딩 전·후 상태를 분리해 빈 화면이 표시되지 않도록 한다.
  const saveCourseSummaryImage = async () => {
    if (!courseSummaryRef.current || isSavingCourseSummary) return;

    setIsSavingCourseSummary(true);
    try {
      const canvas = await html2canvas(courseSummaryRef.current, {
        backgroundColor: '#ffffff',
        scale: 2,
        useCORS: true
      });
      const link = document.createElement('a');
      link.download = `seoulink-course-summary-${reviewId}.png`;
      link.href = canvas.toDataURL('image/png');
      link.click();
    } catch (err) {
      console.error('Failed to save course summary image:', err);
      window.alert('코스 요약 이미지를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsSavingCourseSummary(false);
    }
  };

  if (error) return <><Header /><p className="review-message">{error}</p></>;
  if (!review) return <><Header /><p className="review-message">후기를 불러오는 중입니다.</p></>;
  const photos = review.imageUrls?.length ? review.imageUrls : [review.placeImageUrl].filter(Boolean);
  const heroImage = photos[0] || '/review-seed/bukchon-sunrise.png';
  const course = review.courseSummary;
  return <><Header />
        <main className="review-story-page">
            <div className="review-story-breadcrumb">홈　›　여행 후기　›　{review.reviewTitle}</div>
            <section className="review-story-hero" style={{
        backgroundImage: `linear-gradient(90deg, rgba(7,31,68,.76) 0%, rgba(7,31,68,.24) 63%, rgba(7,31,68,.08)), url("${heroImage}")`
      }}>
                <div className="review-story-hero-content">
                    <div className="story-tag-row">{review.tags?.slice(0, 2).map(tag => <span key={tag}>#{tag}</span>)}</div>
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
                        <div className="story-reaction-bar"><button className={review.likedByMe ? 'liked' : ''} onClick={toggleLike}><Heart fill={review.likedByMe ? 'currentColor' : 'none'} /> 도움이 됐어요 <b>{review.likeCount}</b></button><span>조회 {review.viewCount}</span>{member?.memberId === review.memberId && <span className="review-owner-actions"><a href={`/reviews/${reviewId}/edit`}><Pencil size={15} /> 수정</a><button type="button" className="review-delete" onClick={removeReview}><Trash2 size={15} /> 삭제</button></span>}</div>
                    </article>
                    <section className="story-comments"><h2>댓글 <em>{comments.length}</em></h2><form onSubmit={submitComment}><div className="comment-avatar">{member?.nickname?.slice(0, 1) || '나'}</div><input required value={comment} onChange={event => setComment(event.target.value)} maxLength="500" placeholder="따뜻한 댓글을 남겨주세요 :)" /><button>등록</button></form><div className="comment-list">{comments.map(item => <article key={item.commentId}><div className="comment-avatar muted">여</div><div className="comment-content"><div><strong>서울 여행자</strong><time>{item.createdAt?.slice(0, 10)}</time>{member?.memberId === item.memberId && <button type="button" className="comment-delete" onClick={() => removeComment(item.commentId)}>삭제</button>}</div><p>{item.content}</p></div></article>)}</div></section>
                </div>
                <aside className="review-course-panel" ref={courseSummaryRef}>
                    <button type="button" className="course-summary-download" onClick={saveCourseSummaryImage} disabled={isSavingCourseSummary} data-html2canvas-ignore="true"><Download size={15} /><span>{isSavingCourseSummary ? '저장 중' : '코스 저장'}</span></button>
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
