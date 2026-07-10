import { apiClient } from './apiClient';

export const signup = (data) => apiClient.post('/auth/signup', data);
export const login = (data) => apiClient.post('/auth/login', data);
export const getMyInfo = () => apiClient.get('/members/me');
