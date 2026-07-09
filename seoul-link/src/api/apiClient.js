// apiClient.js
// 백엔드 API를 호출할 때 공통으로 사용할 fetch 기반 클라이언트입니다.
// 나중에 axios를 쓰기로 하면 이 파일만 axiosInstance 형태로 바꾸면 됩니다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

async function request(path, options = {}) {
    const accessToken = localStorage.getItem('accessToken');

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
            ...options.headers,
        },
    });

    if (!response.ok) {
        throw new Error(`API 요청 실패: ${response.status}`);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

export const apiClient = {
    get: (path) => request(path),
    post: (path, body) => request(path, {
        method: 'POST',
        body: JSON.stringify(body),
    }),
    patch: (path, body) => request(path, {
        method: 'PATCH',
        body: JSON.stringify(body),
    }),
    delete: (path) => request(path, {
        method: 'DELETE',
    }),
};
