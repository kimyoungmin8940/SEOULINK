import { apiClient } from './apiClient';

export const getRecommendedCourses = () => apiClient.get('/courses/recommend');
export const getCourses = () => apiClient.get('/courses');
export const getCourseDetail = (courseId) => apiClient.get(`/courses/${courseId}`);
export const saveCourse = (courseId) => apiClient.post('/courses/save', { courseId });
export const createCustomCourse = (data) => apiClient.post('/courses/custom', data);
