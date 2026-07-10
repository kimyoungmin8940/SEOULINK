const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL ||
    "http://localhost:8080/api";

async function parseResponse(response) {
    const text = await response.text();

    let data = null;

    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = text;
        }
    }

    if (!response.ok) {
        if (data && typeof data === "object") {
            const validationMessage = data.errors
                ? Object.values(data.errors).join("\n")
                : null;

            throw new Error(
                validationMessage ||
                data.message ||
                `API 요청에 실패했습니다. (${response.status})`
            );
        }

        throw new Error(
            data ||
            `API 요청에 실패했습니다. (${response.status})`
        );
    }

    return data;
}

async function request(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers,
        },
    });

    return parseResponse(response);
}

export const apiClient = {
    get(path) {
        return request(path);
    },

    post(path, body) {
        return request(path, {
            method: "POST",
            body: JSON.stringify(body),
        });
    },

    put(path, body) {
        return request(path, {
            method: "PUT",
            body: JSON.stringify(body),
        });
    },

    patch(path, body) {
        return request(path, {
            method: "PATCH",
            body: JSON.stringify(body),
        });
    },

    delete(path) {
        return request(path, {
            method: "DELETE",
        });
    },
};