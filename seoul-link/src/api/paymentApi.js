import { apiClient } from './apiClient';

// 결제 화면은 주문 생성 → 외부 결제창 호출 → 승인/실패 콜백 순서로 이 API를 사용한다.
// 서버가 계산한 금액을 기준으로 처리하므로, 클라이언트에서 금액을 임의로 확정하지 않는다.

/** 토스 결제창을 열기 전에 서버에 READY 상태의 주문을 생성한다. */
export const requestPayment = (data) =>
  apiClient.post('/payments/ready', data);

/** 결제 성공 후 토스 승인 정보를 서버에서 검증한다. */
export const confirmPayment = (data) =>
  apiClient.post('/payments/complete', data);

/** 결제창에서 취소하거나 승인에 실패한 READY 주문을 최종 상태로 변경한다. */
export const recordPaymentFailure = ({ orderId, reason, canceled = false }) =>
  apiClient.patch(
    `/payments/fail?orderId=${encodeURIComponent(orderId)}&reason=${encodeURIComponent(reason)}&canceled=${canceled}`,
    {},
  );

/** 회원별 결제 이력을 조회한다. */
export const getMyPayments = (memberId) =>
  apiClient.get(`/payments?memberId=${memberId}`);

/** 결제 이력은 남기고 상태만 CANCELED로 변경한다. */
export const cancelPayment = (paymentId, memberId, reason = '사용자 요청') =>
    apiClient.patch(
        `/payments/${paymentId}/cancel?memberId=${memberId}&reason=${encodeURIComponent(reason)}`,
        {},
    );
