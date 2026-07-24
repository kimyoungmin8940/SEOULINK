import { useEffect, useMemo, useState } from 'react';
import { Heart, MessageCircle, PenLine, Search, Star } from 'lucide-react';
import Header from '../../components/common/Header';
import Footer from '../../components/common/Footer';
import { getPopularTags, getReviews, likeReview } from '../../api/reviewApi';
import { authStore } from '../../store/authStore';
import '../../styles/review-pages.css';

const categories = ['전체', '혼자 여행', '데이트', '가족 여행', '맛집', '야경'];
const fallback =
  'https://images.unsplash.com/photo-1538485399081-7c897b8c333d?auto=format&fit=crop&w=900&q=80';

function ReviewListPage() {
  const [data, setData] = useState({ content: [], number: 0, totalPages: 0 });
  const [sort, setSort] = useState('date');
  const [keyword, setKeyword] = useState('');
  const [tag, setTag] = useState('전체');
  const [popular, setPopular] = useState([]);
  const [error, setError] = useState('');
  const member = authStore.getMember();

  // 검색어·정렬·로그인 회원 정보를 반영해 리뷰 목록을 불러온다.
  const load = async (page = 0, term = keyword) => {
    try {
      setError('');
      setData(
        await getReviews({
          page,
          sort,
          keyword: term,
          memberId: member?.memberId,
        }),
      );
    } catch (requestError) {
      setError(requestError.message || '후기를 불러오지 못했습니다.');
    }
  };

  useEffect(() => {
    // 정렬 조건이 바뀌면 목록과 태그 필터 데이터를 함께 갱신한다.
    load();
    getPopularTags().then(setPopular).catch(() => setPopular([]));
  }, [sort]);

  const reviews = useMemo(
    () =>
      tag === '전체'
        ? data.content
        : (data.content || []).filter((review) =>
            (review.tags || []).includes(tag),
          ),
    [data, tag],
  );

  const like = async (reviewId) => {
    if (!member) {
      window.location.href = '/login';
      return;
    }

    // 좋아요 처리 후 현재 페이지를 다시 조회해 수치를 동기화한다.
    await likeReview(reviewId, member.memberId);
    load(data.number);
  };

  return (
    <>
      <Header />
      <main className="review-hub">
        <div className="breadcrumbs">홈　›　여행 후기</div>
        <header className="review-hero">
          <div>
            <h1>서울 여행 후기</h1>
            <p>서울에서의 특별한 순간을 다른 여행자들과 공유해보세요.</p>
          </div>
          <a className="primary-action" href="/reviews/write">
            <PenLine size={18} /> 후기 작성하기
          </a>
        </header>

        <nav className="review-category-tabs">
          {categories.map((item) => (
            <button
              key={item}
              className={tag === item ? 'active' : ''}
              onClick={() => setTag(item)}
            >
              {item}
            </button>
          ))}
        </nav>

        <div className="review-layout">
          <section>
            <div className="review-controls">
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  load(0);
                }}
              >
                <Search size={18} />
                <input
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="후기 검색"
                />
                <button>검색</button>
              </form>
              <select value={sort} onChange={(event) => setSort(event.target.value)}>
                <option value="date">최신순</option>
                <option value="likes">인기순</option>
                <option value="views">조회순</option>
                <option value="rating">평점순</option>
              </select>
            </div>

            {error ? (
              <p className="review-message">{error}</p>
            ) : (
              <div className="travel-review-grid">
                {reviews.map((review) => (
                  <article className="travel-review-card" key={review.reviewId}>
                    <button
                      className="review-cover"
                      onClick={() => {
                        window.location.href = `/reviews/${review.reviewId}`;
                      }}
                    >
                      <img
                        src={review.imageUrls?.[0] || review.placeImageUrl || fallback}
                        alt=""
                      />
                      <span>{review.tags?.[0] || '서울 여행'}</span>
                    </button>
                    <div className="travel-review-content">
                      <button
                        className="review-title-link"
                        onClick={() => {
                          window.location.href = `/reviews/${review.reviewId}`;
                        }}
                      >
                        {review.reviewTitle}
                      </button>
                      <div className="review-author-line">
                        <span>{review.authorName}</span>
                        <span>
                          <Star size={15} fill="currentColor" />{' '}
                          {review.rating?.toFixed(1)}
                        </span>
                      </div>
                      <p>{review.reviewContent}</p>
                      <div className="review-card-info">
                        <button
                          onClick={() => like(review.reviewId)}
                          className={review.likedByMe ? 'liked' : ''}
                        >
                          <Heart
                            size={16}
                            fill={review.likedByMe ? 'currentColor' : 'none'}
                          />{' '}
                          {review.likeCount}
                        </button>
                        <span>
                          <MessageCircle size={16} /> {review.commentCount}
                        </span>
                        <time>{review.createdAt?.slice(0, 10)}</time>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}

            {!error && reviews.length === 0 && (
              <p className="review-message">아직 등록된 후기가 없습니다.</p>
            )}

            <div className="review-pagination">
              {Array.from({ length: data.totalPages || 0 }, (_, index) => (
                <button
                  className={index === data.number ? 'active' : ''}
                  onClick={() => load(index)}
                  key={index}
                >
                  {index + 1}
                </button>
              ))}
            </div>
          </section>

          <aside className="review-sidebar">
            <h3>인기 태그</h3>
            <div className="tag-cloud">
              {popular.map((popularTag) => (
                <button onClick={() => setTag(popularTag)} key={popularTag}>
                  #{popularTag}
                </button>
              ))}
            </div>
            <h3>이번 주 인기 후기</h3>
            <ol>
              {[...(data.content || [])]
                .sort((left, right) => (right.likeCount || 0) - (left.likeCount || 0))
                .slice(0, 5)
                .map((review) => (
                  <li key={review.reviewId}>
                    <button
                      onClick={() => {
                        window.location.href = `/reviews/${review.reviewId}`;
                      }}
                    >
                      {review.reviewTitle}
                      <small>★ {review.rating?.toFixed(1)}</small>
                    </button>
                  </li>
                ))}
            </ol>
          </aside>
        </div>
      </main>
      <Footer />
    </>
  );
}

export default ReviewListPage;
