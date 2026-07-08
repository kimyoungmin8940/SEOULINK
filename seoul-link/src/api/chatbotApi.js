import { apiGet, apiPost } from "./client";

export const chatbotApi = {
    ask({ memberId, question, travelConcept }) {
        return apiPost("/chatbot/ask", {
            memberId,
            question,
            travelConcept,
        });
    },

    histories(memberId) {
        return apiGet(`/chatbot/histories?memberId=${memberId}`);
    },

    payments(memberId) {
        return apiGet(`/payments?memberId=${memberId}`);
    },

    buyPass(memberId) {
        const now = Date.now();

        return apiPost("/payments", {
            memberId,
            productName: "AI 여행권",
            amount: 19900,
            paymentMethod: "CARD",
            paymentProvider: "TEST",
            orderId: `TEST_${memberId}_${now}`,
            paymentKey: `TEST_PAYMENT_${now}`,
        });
    },
};