import { apiClient } from './apiClient';

export const requestPayment = (data) => apiClient.post('/payments/ready', data);
export const confirmPayment = (data) => apiClient.post('/payments/confirm', data);
export const getMyPayments = () => apiClient.get('/payments/me');
