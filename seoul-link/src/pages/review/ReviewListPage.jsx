import { useEffect, useMemo, useState } from 'react';
import { Heart, MessageCircle, PenLine, Search, Star } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { getPopularTags, getReviews, likeReview } from '../../api/reviewApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';
import '../../styles/review-sticky-sidebar.css';
import '../../styles/review-companion-filter.css';
import '../../styles/review-primary-color.css';

// 동행 값으로 후기 목록을 구분한다. 부모님 카테고리는 기존 가족 동행 데이터도 함께 표시한다.
const companionCategories = [
  { id: 'all', label: '전체', values: null },
  { id: 'solo', label: '혼자', values: ['혼자'] },
  { id: 'friend', label: '친구', values: ['친구와 함께'] },
  { id: 'couple', label: '연인', values: ['연인과 함께'] },
  { id: 'parents', label: '부모님', values: ['부모님과 함께', '가족과 함께'] },
  { id: 'child', label: '아이', values: ['아이와 함께'] },
];

const fallback = 'https://images.unsplash.com/photo-1538485399081-7c897b8c333d?auto=format&fit=crop&w=900&q=80';

function ReviewListPage() {
  // 서버 목록과 동행·태그·검색 조건을 분리해 복합 필터링을 지원한다.
  const [data, setData] = useState({ content: [], number: 0, totalPages: 0 });
  const [sort, setSort] = useState('date');
  const [keyword, setKeyword] = useState('');
  const [companionFilter, setCompanionFilter] = useState('all');
  const [selectedTag, setSelectedTag] = useState(null);
  const [popular, setPopular] = useState([]);
  const [error, setError] = useState('');
  const member = authStore.getMember();

  // 검색어·정렬·로그인 회원 정보를 반영해 후기 목록을 불러온다.
  const load = async (page = 0, term = keyword) => {
    try {
      setError('');
      setData(await getReviews({ page, sort, keyword: term, memberId: member?.memberId }));
    } catch (requestError) {
      setError(requestError.message || '후기를 불러오지 못했습니다.');
    }
  };

  useEffect(() => {
    load();
    getPopularTags().then(setPopular).catch(() => setPopular([]));
  }, [sort]);

  const reviews = useMemo(() => {
    const category = companionCategories.find((item) => item.id === companionFilter);
    return (data.content || []).filter((review) => {
      const matchesCompanion = !category?.values || category.values.includes(review.companion);
      const matchesTag = !selectedTag || (review.tags || []).includes(selectedTag);
      return matchesCompanion && matchesTag;
    });
  }, [companionFilter, data.content, selectedTag]);

  const like = async (reviewId) => {
    if (!member) {
      window.location.href = '/login';
      return;
    }
    await likeReview(reviewId, member.memberId);
    load(data.number);
  };

  return <>
    <Header />
    <main className="review-hub">
      <div className="breadcrumbs">홈 › 여행 후기</div>
      <header className="review-hero">
        <div><h1>서울 여행 후기</h1><p>서울에서의 특별한 순간을 다른 여행자들과 공유해 보세요.</p></div>
        <a className="primary-action" href="/reviews/write"><PenLine size={18} /> 후기 작성하기</a>
      </header>

      <nav className="review-category-tabs" aria-label="동행별 후기 카테고리">
        {companionCategories.map((item) => <button key={item.id} className={companionFilter === item.id ? 'active' : ''} onClick={() => setCompanionFilter(item.id)}>{item.label}</button>)}
      </nav>

      <div className="review-layout">
        <section>
          <div className="review-controls">
            <form onSubmit={(event) => { event.preventDefault(); load(0); }}><Search size={18} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="후기 검색" /><button>검색</button></form>
            <select value={sort} onChange={(event) => setSort(event.target.value)}><option value="date">최신순</option><option value="likes">인기순</option><option value="views">조회순</option><option value="rating">평점순</option></select>
          </div>

          {error ? <p className="review-message">{error}</p> : <div className="travel-review-grid">
            {reviews.map((review) => <article className="travel-review-card" key={review.reviewId}>
              <button className="review-cover" onClick={() => { window.location.href = `/reviews/${review.reviewId}`; }}><img src={review.imageUrls?.[0] || review.placeImageUrl || fallback} alt="" /><span>{review.companion || review.tags?.[0] || '서울 여행'}</span></button>
              <div className="travel-review-content">
                <button className="review-title-link" onClick={() => { window.location.href = `/reviews/${review.reviewId}`; }}>{review.reviewTitle}</button>
                <div className="review-author-line"><span>{review.authorName}</span><span><Star size={15} fill="currentColor" /> {review.rating?.toFixed(1)}</span></div>
                <p>{review.reviewContent}</p>
                <div className="review-card-info"><button onClick={() => like(review.reviewId)} className={review.likedByMe ? 'liked' : ''}><Heart size={16} fill={review.likedByMe ? 'currentColor' : 'none'} /> {review.likeCount}</button><span><MessageCircle size={16} /> {review.commentCount}</span><time>{review.createdAt?.slice(0, 10)}</time></div>
              </div>
            </article>)}
          </div>}
          {!error && reviews.length === 0 && <p className="review-message">선택한 동행 조건에 맞는 후기가 없습니다.</p>}
          <div className="review-pagination">{Array.from({ length: data.totalPages || 0 }, (_, index) => <button className={index === data.number ? 'active' : ''} onClick={() => load(index)} key={index}>{index + 1}</button>)}</div>
        </section>

        <aside className="review-sidebar">
          <h3>인기 태그</h3>
          <div className="tag-cloud">{popular.map((popularTag) => <button className={selectedTag === popularTag ? 'active' : ''} onClick={() => setSelectedTag((currentTag) => currentTag === popularTag ? null : popularTag)} key={popularTag}>#{popularTag}</button>)}</div>
          <h3>인기 후기</h3>
          <ol>{[...(data.content || [])].sort((left, right) => (right.likeCount || 0) - (left.likeCount || 0)).slice(0, 5).map((review) => <li key={review.reviewId}><button type="button" onClick={() => { window.location.href = `/reviews/${review.reviewId}`; }}>{review.reviewTitle}<small>★ {review.rating?.toFixed(1)}</small></button></li>)}</ol>
        </aside>
      </div>
    </main>
    <Footer />
  </>;
}

export default ReviewListPage;
