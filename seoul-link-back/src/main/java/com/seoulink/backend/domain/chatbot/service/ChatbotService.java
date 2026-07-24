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
/**
 * 도메인 규칙과 트랜잭션을 처리하는 서비스입니다.
 */
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

        // 기간권은 남은 횟수가 아니라 결제 완료 상태와 만료 시각으로 사용 가능 여부를 판단한다.
        Payment payment = paymentRepository
                .findFirstByMemberIdAndPaymentStatusAndExpiredAtAfterOrderByPaidAtDesc(
                        request.getMemberId(),
                        "PAID",
                        LocalDateTime.now()
                )
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 챗봇 기간권이 필요합니다."));

        String answer = openAiChatbotService.generateCourseRecommendation(request);


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

    // 최근 생성 순서로 회원의 챗봇 대화 이력을 조회해 화면에 전달한다.
    public List<ChatbotHistory> getHistories(Long memberId) {
        return chatbotHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    // AI 응답 전체는 대화 이력에 남기고, 코스 설명은 DB 컬럼 길이에 맞춰 저장한다.
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

    // 결제 이용권과 대화 이력이 존재하지 않는 회원에게 연결되지 않도록 먼저 검증한다.
    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }
}
