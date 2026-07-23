export const wait = (milliseconds) => new Promise((resolve) => window.setTimeout(resolve, milliseconds));

export const escapeHtml = (value) => {
    if (value === null || value === undefined) return "";

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
};

export const normalizeSearchText = (value) => String(value || "").trim().replace(/\s+/g, "").toLowerCase();

export const includesAnyWord = (text, wordList) => {
    const normalizedText = normalizeSearchText(text);

    return wordList.some((word) => {
        const normalizedWord = normalizeSearchText(word);
        return normalizedWord && normalizedText.includes(normalizedWord);
    });
};
