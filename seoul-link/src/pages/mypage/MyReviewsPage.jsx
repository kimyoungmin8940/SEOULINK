import { useEffect, useState } from 'react';
import { Bookmark, BriefcaseBusiness, CreditCard, MessageCircle, RefreshCw, Route, Sparkles, UserRound } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { getMyReviews } from '../../api/mypageApi';
import { authStore } from '../../store/authStore';
import { demoMyReviews } from '../../mocks/myReviewDemo';
import '../../styles/mypage.css';
import '../../styles/my-reviews.css';

const menuItems = [
  { label: '내 여행 정보', path: '/mypage', Icon: BriefcaseBusiness },
  { label: '저장한 추천 코스', path: '/mypage/courses', Icon: Bookmark },
  { label: '직접 만든 코스', path: '/mypage/courses', Icon: Route },
  { label: '내가 쓴 후기와 댓글', path: '/mypage/reviews', Icon: MessageCircle },
  { label: '취향 검사 결과', path: '/mypage/travel-type', Icon: Sparkles },
  { label: '결제 내역', path: '/mypage/payments', Icon: CreditCard },
];

function MyReviewsPage() {
  const member = authStore.getMember() || {};
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!member.memberId) {
      setReviews(demoMyReviews);
      setLoading(false);
      return;
    }

    getMyReviews(member.memberId)
      .then((items) => {
        const nextItems = Array.isArray(items) ? items : [];
        setReviews(nextItems.length > 0 ? nextItems : demoMyReviews);
        if (nextItems.length === 0) {
          setNotice('아직 작성한 후기가 없어 예시 후기를 보여드리고 있어요.');
        }
      })
      .catch(() => {
        setReviews(demoMyReviews);
        setNotice('후기를 불러오지 못해 예시 후기를 보여드리고 있어요.');
      })
      .finally(() => setLoading(false));
  }, [member.memberId]);

  const userName = member.nickname || member.name || '여행자';

  return (
    <main className="my-reviews-page">
      <Header variant="simple" />
      <section className="my-reviews-shell">
        <aside className="mypage-v3-sidebar my-reviews-sidebar">
          <section className="mypage-v3-profile">
            <div className="mypage-v3-avatar"><UserRound size={54} strokeWidth={1.5} /></div>
            <strong>{userName}님</strong>
            <span>{member.email || 'user@seoulink.com'}</span>
          </section>
          <nav className="mypage-v3-menu" aria-label="마이페이지 메뉴">
            {menuItems.map(({ label, path, Icon }) => (
              <a key={label} className={path === '/mypage/reviews' ? 'active' : ''} href={path}>
                <Icon size={20} strokeWidth={1.8} /><span>{label}</span>
              </a>
            ))}
          </nav>
          <a className="mypage-retest" href="/survey"><RefreshCw size={17} /> 취향 검사 다시하기</a>
        </aside>

        <section className="my-reviews-content">
          <header className="my-reviews-heading">
            <div><p>MY TRAVEL STORY</p><h1>내 후기</h1><span>내가 작성한 방문 후기를 확인하고 상세 페이지에서 댓글도 볼 수 있어요.</span></div>
            <a className="my-reviews-write" href="/reviews/write">후기 작성</a>
          </header>
          {notice && <p className="my-reviews-notice">{notice}</p>}
          {loading ? <p className="my-reviews-state">후기를 불러오는 중입니다.</p> : <div className="my-review-grid">
            {reviews.map((review) => <a className="my-review-card" href={`/reviews/${review.reviewId}`} key={review.reviewId}>
              <img src={review.imageUrls?.[0] || review.placeImageUrl} alt="" />
              <div className="my-review-card-body"><div className="my-review-card-meta"><span>{review.placeName || '서울 여행지'}</span><span>★ {Number(review.rating || 0).toFixed(1)}</span></div><h2>{review.reviewTitle}</h2><p>{review.reviewContent}</p><footer><span>좋아요 {review.likeCount || 0}</span><span>댓글 {review.commentCount || 0}</span><time>{review.createdAt?.slice(0, 10)}</time></footer></div>
            </a>)}
          </div>}
        </section>
      </section>
      <Footer />
    </main>
  );
}

export default MyReviewsPage;
