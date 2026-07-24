import seongsuCafeImage from '../assets/images/reviews/demo-seongsu-cafe.png';
import seoulForestImage from '../assets/images/reviews/demo-seoul-forest.png';
import namsanSunsetImage from '../assets/images/reviews/demo-namsan-sunset.png';

/** 실제 작성 후기가 없을 때 마이페이지와 상세 화면에서 보여줄 예시 데이터입니다. */
export const demoMyReviews = [
  {
    reviewId: 'demo-seongsu-cafe',
    reviewTitle: '성수 골목에서 찾은 조용한 카페들',
    reviewContent: '해 질 무렵 골목을 천천히 걸으며 작은 카페를 둘러봤어요. 분위기가 좋아서 다음에도 다시 가고 싶어요.',
    rating: 4.8,
    likeCount: 24,
    commentCount: 3,
    createdAt: '2026-07-20T10:30:00',
    visitDate: '2026-07-19',
    placeName: '성수 카페거리',
    companion: '친구와 함께',
    authorName: '민들레님',
    imageUrls: [seongsuCafeImage],
    tags: ['카페 투어', '성수', '감성'],
    demoComments: [
      { commentId: 'demo-comment-1', content: '사진 분위기가 정말 좋아요. 저도 가보고 싶어요!', createdAt: '2026-07-21T09:20:00' },
      { commentId: 'demo-comment-2', content: '다음 성수 나들이 때 참고할게요.', createdAt: '2026-07-21T14:10:00' },
    ],
  },
  {
    reviewId: 'demo-seoul-forest',
    reviewTitle: '서울숲 벚꽃 산책, 천천히 걷기 좋은 오후',
    reviewContent: '햇살이 좋은 오후에 서울숲을 걸었어요. 산책로가 한적하고 꽃이 예뻐서 쉬어가기 좋았습니다.',
    rating: 4.6,
    likeCount: 18,
    commentCount: 2,
    createdAt: '2026-07-15T13:00:00',
    visitDate: '2026-07-14',
    placeName: '서울숲',
    companion: '혼자',
    authorName: '민들레님',
    imageUrls: [seoulForestImage],
    tags: ['산책', '자연', '서울숲'],
    demoComments: [
      { commentId: 'demo-comment-3', content: '벚꽃 시즌에 꼭 가보고 싶은 코스예요.', createdAt: '2026-07-16T11:40:00' },
    ],
  },
  {
    reviewId: 'demo-namsan-sunset',
    reviewTitle: '남산에서 만난 서울의 가장 따뜻한 노을',
    reviewContent: '해가 지는 시간에 전망대에 올랐는데 도시의 불빛과 노을이 함께 보여 오래 기억에 남을 것 같아요.',
    rating: 5.0,
    likeCount: 31,
    commentCount: 4,
    createdAt: '2026-07-08T18:20:00',
    visitDate: '2026-07-07',
    placeName: '남산 서울타워',
    companion: '연인과 함께',
    authorName: '민들레님',
    imageUrls: [namsanSunsetImage],
    tags: ['야경', '노을', '데이트'],
    demoComments: [
      { commentId: 'demo-comment-4', content: '노을 시간대가 정말 멋지네요!', createdAt: '2026-07-09T08:50:00' },
      { commentId: 'demo-comment-5', content: '좋은 정보 감사합니다.', createdAt: '2026-07-09T16:30:00' },
    ],
  },
];

export const findDemoReview = (reviewId) =>
  demoMyReviews.find((review) => review.reviewId === reviewId);
