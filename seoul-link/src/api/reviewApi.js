import { API_BASE_URL, apiClient } from './apiClient';

/** 빈 값은 제외해 리뷰 목록 조회용 쿼리 문자열을 만든다. */
const query = (params) =>
  new URLSearchParams(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  ).toString();

/** 정렬·검색 조건을 반영한 리뷰 목록을 조회한다. */
export const getReviews = (params = {}) =>
  apiClient.get(
    `/reviews?${query({ page: 0, size: 9, sort: 'date', ...params })}`,
  );

/** 선택한 리뷰와 로그인 회원의 좋아요 상태를 조회한다. */
export const getReviewDetail = (reviewId, memberId) =>
  apiClient.get(`/reviews/${reviewId}?${query({ memberId })}`);

/** 리뷰 화면의 인기 태그를 조회한다. */
export const getPopularTags = () => apiClient.get('/reviews/popular-tags');

/** 리뷰에 등록된 댓글 목록을 조회한다. */
export const getReviewComments = (reviewId) =>
  apiClient.get(`/reviews/${reviewId}/comments`);

/** 선택한 이미지 파일을 업로드하고 리뷰에 저장할 URL 목록을 받는다. */
export const uploadReviewImages = async (files) => {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));

  const response = await apiClient.postForm('/review-images', formData);
  const backendBaseUrl = API_BASE_URL.replace(/\/api$/, '');

  return {
    imageUrls: response.imageUrls.map(
      (imageUrl) => `${backendBaseUrl}${imageUrl}`,
    ),
  };
};
/** 새 리뷰를 등록한다. */
export const createReview = (data) => apiClient.post('/reviews', data);

/** 작성자 본인의 리뷰를 수정한다. */
export const updateReview = (reviewId, data) =>
  apiClient.patch(`/reviews/${reviewId}`, data);

/** 작성자 본인의 리뷰를 삭제한다. */
export const deleteReview = (reviewId, memberId) =>
  apiClient.delete(`/reviews/${reviewId}?${query({ memberId })}`);

/** 로그인 회원 기준으로 리뷰 좋아요를 토글한다. */
export const likeReview = (reviewId, memberId) =>
  apiClient.post(`/reviews/${reviewId}/likes?memberId=${memberId}`);

/** 리뷰에 새 댓글을 등록한다. */
export const createComment = (reviewId, data) =>
  apiClient.post(`/reviews/${reviewId}/comments`, data);
