package com.seoulink.backend.service;

import com.seoulink.backend.dto.response.MemberLoginResponse;
import com.seoulink.backend.dto.response.MyCourseResponse;
import com.seoulink.backend.dto.response.MyPageResponse;
import com.seoulink.backend.dto.response.MyTravelTypeResponse;
import com.seoulink.backend.dto.response.ReviewResponse;
import com.seoulink.backend.entity.Member;
import com.seoulink.backend.entity.SurveyResult;
import com.seoulink.backend.entity.TravelCourse;
import com.seoulink.backend.entity.TravelTypeMaster;
import com.seoulink.backend.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyPageService {

    private final MemberRepository memberRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final TravelTypeMasterRepository travelTypeMasterRepository;
    private final TravelCourseRepository travelCourseRepository;
    private final PaymentRepository paymentRepository;
    private final ChatbotHistoryRepository chatbotHistoryRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    public MyPageService(
            MemberRepository memberRepository,
            SurveyResultRepository surveyResultRepository,
            TravelTypeMasterRepository travelTypeMasterRepository,
            TravelCourseRepository travelCourseRepository,
            PaymentRepository paymentRepository,
            ChatbotHistoryRepository chatbotHistoryRepository,
            ReviewRepository reviewRepository,
            ReviewLikeRepository reviewLikeRepository
    ) {
        this.memberRepository = memberRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.travelTypeMasterRepository = travelTypeMasterRepository;
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

        List<SurveyResult> results = surveyResultRepository.findResultsByMemberId(memberId);

        SurveyResult surveyResult = results.isEmpty() ? null : results.get(0);

        if (surveyResult != null) {
            TravelTypeMaster type = travelTypeMasterRepository.findById(surveyResult.getTravelCode())
                    .orElse(null);

            if (type != null) {
                travelTypeResponse = new MyTravelTypeResponse(
                        type.getTravelCode(),
                        type.getTypeTitle(),
                        type.getTypeDescription(),
                        type.getImageUrl()
                );
            }
        }

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
