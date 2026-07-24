export const API_BASE_URL = (
    import.meta.env?.VITE_API_BASE_URL || "http://localhost:8080/api"
).replace(/\/+$/, "");

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
    const isFormData =
        typeof FormData !== 'undefined' && fetchOptions.body instanceof FormData;

    let response;
    try {
        response = await fetch(`${API_BASE_URL}${path}`, {
            ...fetchOptions,
            headers: {
                ...(isFormData ? {} : { "Content-Type": "application/json" }),
                ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
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
        const hasStructuredError = body
            && typeof body === "object"
            && typeof body.code === "string"
            && typeof body.message === "string";

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
        method: "GET",
    }),
    post: (path, body, options = {}) => request(path, {
        ...options,
        method: "POST",
        body: JSON.stringify(body),
    }),
    postForm: (path, formData, options = {}) => request(path, {
        ...options,
        method: "POST",
        body: formData,
    }),    put: (path, body, options = {}) => request(path, {
        ...options,
        method: "PUT",
        body: JSON.stringify(body),
    }),
    patch: (path, body, options = {}) => request(path, {
        ...options,
        method: "PATCH",
        body: JSON.stringify(body),
    }),
    delete: (path, options = {}) => request(path, {
        ...options,
        method: "DELETE",
    }),
};
