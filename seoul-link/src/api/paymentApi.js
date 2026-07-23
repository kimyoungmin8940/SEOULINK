import { apiClient } from './apiClient';

export const requestPayment = (data) => apiClient.post('/payments/ready', data);
export const confirmPayment = (data) => apiClient.post('/payments/complete', data);
export const getMyPayments = (memberId) => apiClient.get(`/payments?memberId=${memberId}`);
export const deletePaymentHistory = (paymentId, memberId) => apiClient.delete(`/payments/${paymentId}?memberId=${memberId}`);
