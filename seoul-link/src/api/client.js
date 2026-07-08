const BASE_URL = "http://localhost:8080/api";

async function parseResponse(response) {
    const text = await response.text();

    if (!response.ok) {
        throw new Error(text || `HTTP ${response.status}`);
    }

    if (!text) {
        return null;
    }

    return JSON.parse(text);
}

export async function apiGet(path) {
    const response = await fetch(`${BASE_URL}${path}`);
    return parseResponse(response);
}

export async function apiPost(path, body) {
    const response = await fetch(`${BASE_URL}${path}`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
    });

    return parseResponse(response);
}