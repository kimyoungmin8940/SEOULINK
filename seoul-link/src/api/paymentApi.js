import { apiClient } from './apiClient';

/** 토스 결제창을 열기 전에 서버에 READY 상태의 주문을 생성한다. */
export const requestPayment = (data) =>
  apiClient.post('/payments/ready', data);

/** 결제 성공 후 토스 승인 정보를 서버에서 검증한다. */
export const confirmPayment = (data) =>
  apiClient.post('/payments/complete', data);

/** 회원별 결제 이력을 조회한다. */
export const getMyPayments = (memberId) =>
  apiClient.get(`/payments?memberId=${memberId}`);

/** 실제 환불이 아닌 서비스 내 결제 이력만 삭제한다. */
export const deletePaymentHistory = (paymentId, memberId) =>
  apiClient.delete(`/payments/${paymentId}?memberId=${memberId}`);
