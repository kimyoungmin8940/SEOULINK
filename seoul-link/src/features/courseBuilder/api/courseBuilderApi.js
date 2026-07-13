const API_BASE_URL = "http://localhost:8080/api";

export async function saveCourseBuilderCourse(requestBody) {
    const response = await fetch(`${API_BASE_URL}/course-builder/courses`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "코스 저장에 실패했습니다.");
    }

    return response.json();
}

export async function fetchCourseBuilderDbPlaces({ theme, region, limit }) {
    const searchParams = new URLSearchParams();

    if (theme) {
        searchParams.set("theme", theme);
    }

    if (region) {
        searchParams.set("region", region);
    }

    if (limit) {
        searchParams.set("limit", String(limit));
    }

    const response = await fetch(
        `${API_BASE_URL}/course-builder/places?${searchParams.toString()}`
    );

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "DB 장소를 불러오지 못했습니다.");
    }

    return response.json();
}

export async function calculateCourseBuilderRoutes(requestBody) {
    const response = await fetch(`${API_BASE_URL}/course-builder/routes`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || "이동거리 계산에 실패했습니다.");
    }

    return response.json();
}
