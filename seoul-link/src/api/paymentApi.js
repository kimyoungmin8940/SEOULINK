import { apiClient } from './apiClient';

export const requestPayment = (data) => apiClient.post('/payments/ready', data);
export const confirmPayment = (data) => apiClient.post('/payments/complete', data);
export const getMyPayments = () => apiClient.get('/payments/me');
