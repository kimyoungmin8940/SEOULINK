import { apiClient } from '../../../api/apiClient';

export const saveCourseBuilderCourse = (requestBody) =>
    apiClient.post('/course-builder/courses', requestBody);

export function fetchCourseBuilderDbPlaces({ theme, region, limit }) {
    const searchParams = new URLSearchParams();
    if (theme) searchParams.set('theme', theme);
    if (region) searchParams.set('region', region);
    if (limit) searchParams.set('limit', String(limit));
    return apiClient.get(`/course-builder/places?${searchParams.toString()}`);
}

export const calculateCourseBuilderRoutes = (requestBody) =>
    apiClient.post('/course-builder/routes', requestBody);
