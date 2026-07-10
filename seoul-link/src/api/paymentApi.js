import { apiClient } from './apiClient';

export const requestPayment = (data) => apiClient.post('/payments/ready', data);
export const confirmPayment = (paymentId) => apiClient.post('/payments/complete', { paymentId });
export const getMyPayments = () => apiClient.get('/payments/me');
