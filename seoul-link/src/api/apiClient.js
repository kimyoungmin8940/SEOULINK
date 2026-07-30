// 백엔드 API를 호출할 때 공통으로 사용하는 fetch 기반 클라이언트입니다.
// VITE_API_BASE_URL은 기존 프로젝트 규칙대로 `/api`까지 포함합니다.

export const API_BASE_URL = (
    import.meta.env?.VITE_API_BASE_URL || "/api"
).replace(/\/+$/, "");

// 일반 API는 Vite의 /api 프록시를 사용하지만, OAuth는 브라우저가 백엔드의
// 리다이렉트 엔드포인트로 직접 이동해야 한다.
export const BACKEND_ORIGIN = (
    import.meta.env?.VITE_BACKEND_ORIGIN || "http://localhost:8080"
).replace(/\/+$/, "");

/** 화면에서 HTTP 상태와 백엔드 오류 코드를 함께 구분할 수 있는 공통 오류입니다. */
export class ApiError extends Error {
    constructor(status, code, message, details = null) {
        super(message);
        this.name = "ApiError";
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
    const accessToken = typeof localStorage === "undefined"
        ? null
        : localStorage.getItem("accessToken");

    const { headers: optionHeaders, ...fetchOptions } = options;

    const isFormData = typeof FormData !== "undefined"
        && fetchOptions.body instanceof FormData;

    let response;

    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            ...fetchOptions,
            headers: {
                ...(isFormData
                    ? {}
                    : { "Content-Type": "application/json" }),
                ...(accessToken
                    ? { Authorization: `Bearer ${accessToken}` }
                    : {}),
                ...optionHeaders,
            },
        });
    } catch (error) {
        if (error instanceof Error && error.name === "AbortError") {
            throw error;
        }

        throw new ApiError(
            0,
            "NETWORK_ERROR",
            "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
            error,
        );
    }

    const body = await parseResponseBody(response);

    if (!response.ok) {
        const hasErrorCode = body
            && typeof body === "object"
            && typeof body.code === "string";
        const hasErrorMessage = body
            && typeof body === "object"
            && typeof body.message === "string";

        throw new ApiError(
            response.status,
            hasErrorCode ? body.code : `HTTP_${response.status}`,
            hasErrorMessage
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
        method: "GET",
    }),

    post: (path, body, options = {}) => request(path, {
        ...options,
        method: "POST",
        body: JSON.stringify(body),
    }),

    // 사진 파일처럼 FormData를 보내는 요청에 사용합니다.
    postForm: (path, formData, options = {}) => request(path, {
        ...options,
        method: "POST",
        body: formData,
    }),

    patch: (path, body, options = {}) => request(path, {
        ...options,
        method: "PATCH",
        body: JSON.stringify(body),
    }),

    put: (path, body, options = {}) => request(path, {
        ...options,
        method: "PUT",
        body: JSON.stringify(body),
    }),

    delete: (path, options = {}) => request(path, {
        ...options,
        method: "DELETE",
    }),
};
