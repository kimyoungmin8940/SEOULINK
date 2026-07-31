import { apiClient } from '../../../api/apiClient';

export const saveCourseBuilderCourse = (requestBody) =>
    apiClient.post('/course-builder/courses', requestBody);

export const updateCourseBuilderCourse = (courseId, requestBody) =>
    apiClient.put(`/course-builder/courses/${courseId}`, requestBody);

export const fetchCourseBuilderCourse = (courseId, memberId) =>
    apiClient.get(`/courses/${courseId}?memberId=${memberId}`);

export function fetchCourseBuilderDbPlaces({ theme, region, limit }) {
    const searchParams = new URLSearchParams();
    if (theme) searchParams.set('theme', theme);
    if (region) searchParams.set('region', region);
    if (limit) searchParams.set('limit', String(limit));
    return apiClient.get(`/course-builder/places?${searchParams.toString()}`);
}

export const calculateCourseBuilderRoutes = (requestBody) =>
    apiClient.post('/course-builder/routes', requestBody);
