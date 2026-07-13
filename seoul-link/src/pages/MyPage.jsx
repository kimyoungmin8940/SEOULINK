import {
    Bot,
    Bookmark,
    CalendarDays,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    CreditCard,
    Edit3,
    Eye,
    Heart,
    Map,
    MessageCircle,
    PenLine,
    Route,
    Star,
    User,
} from "lucide-react";
import Header from "../component/Header";
import PageBackground from "../component/PageBackground";
import "../styles/MyPage.css";

const reviews = [
    {
        id: 1,
        title: "북촌 골목에서 만난 조용한 오후",
        place: "북촌 한옥마을",
        date: "2026.07.01",
        status: "게시중",
        rating: "4.8",
        likes: 128,
        comments: 23,
        views: "2,340",
        image: "/images/seoul-login-bg.png",
    },
    {
        id: 2,
        title: "한강 노을 피크닉 후기",
        place: "여의도 한강공원",
        date: "2026.06.30",
        status: "게시중",
        rating: "4.9",
        likes: 156,
        comments: 31,
        views: "3,827",
        image: "/images/seoul-login-bg.png",
    },
    {
        id: 3,
        title: "성수 카페 거리 추천",
        place: "성수동 카페거리",
        date: "2026.06.29",
        status: "게시중",
        rating: "4.7",
        likes: 102,
        comments: 18,
        views: "1,912",
        image: "/images/seoul-login-bg.png",
    },
    {
        id: 4,
        title: "남산 야경 코스 솔직 후기",
        place: "남산공원",
        date: "2026.06.28",
        status: "게시중",
        rating: "4.8",
        likes: 141,
        comments: 27,
        views: "2,741",
        image: "/images/seoul-login-bg.png",
    },
    {
        id: 5,
        title: "호텔에서 시작한 서울 여행",
        place: "신라스테이 광화문",
        date: "2026.06.25",
        status: "임시저장",
        rating: "-",
        likes: "-",
        comments: "-",
        views: "-",
        image: "/images/seoul-login-bg.png",
    },
];

const comments = [
    { name: "여행좋아", text: "사진이 정말 예뻐요! 다음에 저도 꼭 가볼게요.", time: "2시간 전" },
    { name: "서울탐방러", text: "정보 감사합니다! 루트도 참고할게요.", time: "5시간 전" },
    { name: "카페러버", text: "저도 이 카페 다녀왔는데 완전 공감해요!", time: "1일 전" },
    { name: "sunny_day", text: "야경 사진이 최고네요.", time: "1일 전" },
];

export default function MyPage() {
    const member = JSON.parse(localStorage.getItem("member") || "{}");

    return (
        <PageBackground>
            <Header />

            <main className="mypage-review-page">
                <aside className="mypage-sidebar">
                    <h2>마이페이지</h2>

                    <nav className="mypage-menu">
                        <button type="button">
                            <User size={19} />
                            내 정보
                        </button>
                        <button type="button">
                            <Map size={19} />
                            여행 유형
                        </button>
                        <button type="button">
                            <Bookmark size={19} />
                            저장 코스
                        </button>
                        <button type="button">
                            <Route size={19} />
                            직접 만든 코스
                        </button>
                        <button type="button">
                            <MessageCircle size={19} />
                            챗봇 내역
                        </button>
                        <button type="button">
                            <CreditCard size={19} />
                            결제 내역
                        </button>
                        <button className="active" type="button">
                            <Star size={19} />
                            내 후기
                        </button>
                    </nav>

                    <div className="premium-box">
                        <strong>프리미엄 이용 중</strong>
                        <p>AI 챗봇, 저장, 맞춤 추천을 무제한으로 이용하세요!</p>
                        <button type="button">이용권 관리</button>
                    </div>

                    <div className="sidebar-illust" />
                </aside>

                <section className="mypage-content">
                    <div className="mypage-top">
                        <div>
                            <h1>내가 쓴 후기</h1>
                            <p>작성한 후기를 관리하고 활동 현황을 확인해보세요.</p>
                        </div>

                        <button className="write-review-btn" type="button">
                            <PenLine size={18} />
                            새 후기 작성
                        </button>
                    </div>

                    <div className="summary-grid">
                        <article>
                            <span className="summary-icon blue">
                                <Edit3 size={24} />
                            </span>
                            <div>
                                <p>작성한 후기</p>
                                <strong>8<span>개</span></strong>
                            </div>
                        </article>

                        <article>
                            <span className="summary-icon pink">
                                <Heart size={24} />
                            </span>
                            <div>
                                <p>받은 좋아요</p>
                                <strong>124<span>개</span></strong>
                            </div>
                        </article>

                        <article>
                            <span className="summary-icon green">
                                <MessageCircle size={24} />
                            </span>
                            <div>
                                <p>댓글</p>
                                <strong>36<span>개</span></strong>
                            </div>
                        </article>

                        <article>
                            <span className="summary-icon purple">
                                <Eye size={24} />
                            </span>
                            <div>
                                <p>조회수</p>
                                <strong>2,840<span>회</span></strong>
                            </div>
                        </article>
                    </div>

                    <section className="review-board">
                        <div className="review-toolbar">
                            <div className="review-tabs">
                                <button className="active" type="button">전체 (8)</button>
                                <button type="button">게시중 (7)</button>
                                <button type="button">임시저장 (1)</button>
                            </div>

                            <button className="sort-btn" type="button">
                                최신순
                                <ChevronDown size={16} />
                            </button>
                        </div>

                        <div className="review-list">
                            {reviews.map((review) => (
                                <article className="review-item" key={review.id}>
                                    <img src={review.image} alt={review.title} />

                                    <div className="review-main">
                                        <div className="review-title-row">
                                            <div>
                                                <h3>{review.title}</h3>
                                                <p>{review.place}</p>
                                            </div>
                                            <span className={review.status === "게시중" ? "status open" : "status draft"}>
                                                {review.status}
                                            </span>
                                        </div>

                                        <div className="review-meta">
                                            <span className="stars">★★★★★</span>
                                            <span>{review.rating}</span>
                                            <span><Heart size={15} /> {review.likes}</span>
                                            <span><MessageCircle size={15} /> {review.comments}</span>
                                            <span><Eye size={15} /> {review.views}</span>
                                        </div>
                                    </div>

                                    <time>{review.date}</time>

                                    <div className="review-actions">
                                        <button type="button">수정</button>
                                        <button className="danger" type="button">삭제</button>
                                        <button className="outline" type="button">
                                            상세보기
                                            <ChevronRight size={15} />
                                        </button>
                                    </div>
                                </article>
                            ))}
                        </div>
                    </section>

                    <div className="pagination">
                        <button type="button"><ChevronLeft size={18} /></button>
                        <button className="active" type="button">1</button>
                        <button type="button">2</button>
                        <button type="button"><ChevronRight size={18} /></button>
                    </div>
                </section>

                <aside className="mypage-right">
                    <section className="activity-card">
                        <h3>후기 활동</h3>
                        <p>최근 30일 기준</p>

                        <div className="chart-box">
                            <div className="chart-line blue-line" />
                            <div className="chart-line pink-line" />
                            <div className="chart-line green-line" />
                        </div>

                        <div className="chart-labels">
                            <span>좋아요</span>
                            <span>댓글</span>
                            <span>조회수</span>
                        </div>

                        <button type="button">
                            상세 통계 보기
                            <ChevronRight size={16} />
                        </button>
                    </section>

                    <section className="comment-card">
                        <div className="comment-head">
                            <h3>최근 댓글</h3>
                            <button type="button">더보기</button>
                        </div>

                        <div className="comment-list">
                            {comments.map((comment) => (
                                <article key={comment.name}>
                                    <div className="comment-avatar">{comment.name[0]}</div>
                                    <div>
                                        <strong>{comment.name}</strong>
                                        <p>{comment.text}</p>
                                        <span>{comment.time}</span>
                                    </div>
                                </article>
                            ))}
                        </div>

                        <button className="all-comment-btn" type="button">
                            내 댓글 전체 보기
                            <ChevronRight size={16} />
                        </button>
                    </section>
                </aside>
            </main>
        </PageBackground>
    );
}