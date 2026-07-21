package com.seoulink.backend.domain.chatbot.service;

import com.seoulink.backend.domain.chatbot.dto.request.ChatbotRequest;
import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import com.seoulink.backend.domain.payment.entity.Payment;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.chatbot.repository.ChatbotHistoryRepository;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatbotService {

    private final ChatbotHistoryRepository chatbotHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final TravelCourseRepository travelCourseRepository;
    private final OpenAiChatbotService openAiChatbotService;

    public ChatbotService(
            ChatbotHistoryRepository chatbotHistoryRepository,
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            TravelCourseRepository travelCourseRepository,
            OpenAiChatbotService openAiChatbotService
    ) {
        this.chatbotHistoryRepository = chatbotHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.travelCourseRepository = travelCourseRepository;
        this.openAiChatbotService = openAiChatbotService;
    }

    @Transactional
    public ChatbotHistory ask(ChatbotRequest request) {
        validateMemberExists(request.getMemberId());

        Payment payment = paymentRepository
                .findFirstByMemberIdAndPaymentStatusAndRemainCountGreaterThanOrderByPaidAtDesc(
                        request.getMemberId(),
                        "PAID",
                        0
                )
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 챗봇 이용권이 필요합니다."));

        if (payment.getExpiredAt() == null || payment.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("챗봇 이용권이 만료되었습니다.");
        }

        String answer = openAiChatbotService.generateCourseRecommendation(request);

        payment.useOneCount();

        TravelCourse course = saveChatbotCourse(request, answer, payment);

        ChatbotHistory history = new ChatbotHistory();
        history.setMemberId(request.getMemberId());
        history.setPaymentId(payment.getPaymentId());
        history.setCourseId(course.getCourseId());
        history.setQuestion(request.getQuestion());
        history.setTravelConcept(request.getTravelConcept());
        history.setAnswer(answer);
        history.setCourseSummary(course.getDescription());

        return chatbotHistoryRepository.save(history);
    }

    public List<ChatbotHistory> getHistories(Long memberId) {
        return chatbotHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    private TravelCourse saveChatbotCourse(ChatbotRequest request, String answer, Payment payment) {
        TravelCourse course = TravelCourse.builder()
                .memberId(request.getMemberId())
                .paymentId(payment.getPaymentId())
                .title(request.getTravelConcept())
                .description(answer.length() > 1000 ? answer.substring(0, 1000) : answer)
                .courseType("CHATBOT")
                .publicStatus("N")
                .viewCount(0L)
                .build();
        return travelCourseRepository.save(course);
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }
}
