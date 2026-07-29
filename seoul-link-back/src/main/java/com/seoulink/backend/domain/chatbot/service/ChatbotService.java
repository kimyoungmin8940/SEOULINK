package com.seoulink.backend.domain.chatbot.service;

import com.seoulink.backend.domain.chatbot.dto.request.ChatbotRequest;
import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import com.seoulink.backend.domain.payment.entity.Payment;
import com.seoulink.backend.domain.chatbot.repository.ChatbotHistoryRepository;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
/**
 * 도메인 규칙과 트랜잭션을 처리하는 서비스입니다.
 */
public class ChatbotService {

    private final ChatbotHistoryRepository chatbotHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final OpenAiChatbotService openAiChatbotService;

    public ChatbotService(
            ChatbotHistoryRepository chatbotHistoryRepository,
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            OpenAiChatbotService openAiChatbotService
    ) {
        this.chatbotHistoryRepository = chatbotHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.openAiChatbotService = openAiChatbotService;
    }

    @Transactional
    public ChatbotHistory ask(ChatbotRequest request) {
        validateMemberExists(request.getMemberId());

        // 기간권은 남은 횟수가 아니라 결제 완료 상태와 만료 시각으로 사용 가능 여부를 판단한다.
        Payment payment = paymentRepository
                .findFirstByMemberIdAndPaymentStatusAndExpiredAtAfterOrderByPaidAtDesc(
                        request.getMemberId(),
                        "PAID",
                        LocalDateTime.now()
                )
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 챗봇 기간권이 필요합니다."));

        OpenAiChatbotService.ChatbotRecommendation recommendation =
                openAiChatbotService.generateCourseRecommendation(request);


        ChatbotHistory history = new ChatbotHistory();
        history.setMemberId(request.getMemberId());
        history.setPaymentId(payment.getPaymentId());
        history.setQuestion(request.getQuestion());
        history.setTravelConcept(request.getTravelConcept());
        history.setAnswer(recommendation.answer());
        history.setCourseSummary(recommendation.courseSummary());

        return chatbotHistoryRepository.save(history);
    }

    // 최근 생성 순서로 회원의 챗봇 대화 이력을 조회해 화면에 전달한다.
    public List<ChatbotHistory> getHistories(Long memberId) {
        return chatbotHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    // 결제 이용권과 대화 이력이 존재하지 않는 회원에게 연결되지 않도록 먼저 검증한다.
    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }
}
