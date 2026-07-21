// 백엔드 API를 호출할 때 공통으로 사용하는 fetch 기반 클라이언트입니다.
// VITE_API_BASE_URL은 기존 프로젝트 규칙대로 `/api`까지 포함합니다.

export const API_BASE_URL = (
    // 개발 환경에서는 Vite가 /api를 Spring Boot(8080)로 프록시합니다.
    // 프론트와 백을 서로 다른 포트로 실행해도 브라우저 CORS 오류 없이 호출됩니다.
    import.meta.env?.VITE_API_BASE_URL || '/api'
).replace(/\/+$/, '');

/** 화면에서 HTTP 상태와 백엔드 오류 코드를 함께 구분할 수 있는 공통 오류입니다. */
export class ApiError extends Error {
    constructor(status, code, message, details = null) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
        this.details = details;
    }
}

async function parseResponseBody(response) {
    if (response.status === 204) {
        return null;
    }

    const text = await response.text();
    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

async function request(path, options = {}) {
    const accessToken = typeof localStorage === 'undefined'
        ? null
        : localStorage.getItem('accessToken');
    const { headers: optionHeaders, ...fetchOptions } = options;

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            ...fetchOptions,
            headers: {
                'Content-Type': 'application/json',
                ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
                ...optionHeaders,
            },
        });
    } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') {
            throw error;
        }

        throw new ApiError(
            0,
            'NETWORK_ERROR',
            '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',
            error,
        );
    }

    const body = await parseResponseBody(response);
    if (!response.ok) {
        const hasStructuredError = body
            && typeof body === 'object'
            && typeof body.code === 'string'
            && typeof body.message === 'string';

        throw new ApiError(
            response.status,
            hasStructuredError ? body.code : `HTTP_${response.status}`,
            hasStructuredError
                ? body.message
                : `API 요청에 실패했습니다. (${response.status})`,
            body,
        );
    }

    return body;
}

export const apiClient = {
    get: (path, options = {}) => request(path, {
        ...options,
        method: 'GET',
    }),
    post: (path, body, options = {}) => request(path, {
        ...options,
        method: 'POST',
        body: JSON.stringify(body),
    }),
    patch: (path, body, options = {}) => request(path, {
        ...options,
        method: 'PATCH',
        body: JSON.stringify(body),
    }),
    delete: (path, options = {}) => request(path, {
        ...options,
        method: 'DELETE',
    }),
};
