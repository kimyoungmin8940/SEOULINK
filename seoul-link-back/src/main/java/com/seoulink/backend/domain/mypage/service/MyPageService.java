package com.seoulink.backend.domain.mypage.service;

import com.seoulink.backend.domain.member.dto.response.MemberLoginResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyCourseResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyPageResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyTravelTypeResponse;
import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.member.entity.Member;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.chatbot.repository.ChatbotHistoryRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
import com.seoulink.backend.domain.review.repository.ReviewLikeRepository;
import com.seoulink.backend.domain.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyPageService {

    private final MemberRepository memberRepository;
    private final TravelCourseRepository travelCourseRepository;
    private final PaymentRepository paymentRepository;
    private final ChatbotHistoryRepository chatbotHistoryRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    public MyPageService(
            MemberRepository memberRepository,
            TravelCourseRepository travelCourseRepository,
            PaymentRepository paymentRepository,
            ChatbotHistoryRepository chatbotHistoryRepository,
            ReviewRepository reviewRepository,
            ReviewLikeRepository reviewLikeRepository
    ) {
        this.memberRepository = memberRepository;
        this.travelCourseRepository = travelCourseRepository;
        this.paymentRepository = paymentRepository;
        this.chatbotHistoryRepository = chatbotHistoryRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
    }

    public MyPageResponse getMyPage(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        MemberLoginResponse memberResponse = new MemberLoginResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getName(),
                member.getNickname()
        );
        MyTravelTypeResponse travelTypeResponse = null;

        List<MyCourseResponse> courses = travelCourseRepository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .limit(5)
                .map(MyCourseResponse::new)
                .toList();

        return new MyPageResponse(
                memberResponse,
                travelTypeResponse,
                courses,
                paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream().limit(5).toList(),
                chatbotHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream().limit(5).toList(),
                reviewRepository.findByIsDeletedOrderByCreatedAtDesc("N")
                        .stream()
                        .filter(review -> memberId.equals(review.getMemberId()))
                        .limit(5)
                        .map(review -> new ReviewResponse(review, reviewLikeRepository.countByReviewId(review.getReviewId())))
                        .toList()
        );
    }
}
