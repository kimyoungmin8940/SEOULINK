package com.seoulink.backend.domain.mypage.service;

import com.seoulink.backend.domain.member.dto.response.MemberLoginResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyCourseResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyCommentResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyPageResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyTravelTypeResponse;
import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.member.entity.Member;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.chatbot.repository.ChatbotHistoryRepository;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import com.seoulink.backend.domain.review.repository.ReviewCommentRepository;
import com.seoulink.backend.domain.review.repository.ReviewLikeRepository;
import com.seoulink.backend.domain.review.repository.ReviewRepository;
import com.seoulink.backend.domain.survey.repository.SurveyAnswerRepository;
import com.seoulink.backend.domain.survey.repository.SurveyOptionRepository;
import com.seoulink.backend.domain.survey.repository.SurveyQuestionRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelTypeMasterRepository;
import com.seoulink.backend.domain.traveltype.repository.TravelTypePlaceRepository;
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
    private final ReviewCommentRepository reviewCommentRepository;
    private final PlaceRepository placeRepository;
    private final TravelSurveyRepository travelSurveyRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final TravelTypeMasterRepository travelTypeMasterRepository;

    public MyPageService(
            MemberRepository memberRepository,
            TravelCourseRepository travelCourseRepository,
            PaymentRepository paymentRepository,
            ChatbotHistoryRepository chatbotHistoryRepository,
            ReviewRepository reviewRepository,
            ReviewLikeRepository reviewLikeRepository,
            ReviewCommentRepository reviewCommentRepository,
            PlaceRepository placeRepository,
            TravelSurveyRepository travelSurveyRepository,
            SurveyResultRepository surveyResultRepository,
            TravelTypeMasterRepository travelTypeMasterRepository
    ) {
        this.memberRepository = memberRepository;
        this.travelCourseRepository = travelCourseRepository;
        this.paymentRepository = paymentRepository;
        this.chatbotHistoryRepository = chatbotHistoryRepository;
        this.reviewRepository = reviewRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.placeRepository = placeRepository;
        this.travelSurveyRepository = travelSurveyRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.travelTypeMasterRepository = travelTypeMasterRepository;
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
        MyTravelTypeResponse travelTypeResponse = travelSurveyRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(memberId)
                .flatMap(survey -> surveyResultRepository
                        .findBySurveyId(survey.getSurveyId())
                        .map(result -> {
                            var travelType = travelTypeMasterRepository
                                    .findByTravelCode(result.getTravelCode())
                                    .orElse(null);

                            return new MyTravelTypeResponse(
                                    survey.getSurveyId(),
                                    result.getResultId(),
                                    result.getTravelCode(),
                                    travelType == null ? null : travelType.getTypeTitle(),
                                    travelType == null ? null : travelType.getTypeDescription(),
                                    travelType == null ? null : travelType.getImageUrl()
                            );
                        }))
                .orElse(null);

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

    public List<MyCommentResponse> getMyComments(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        return reviewCommentRepository.findByMemberIdAndIsDeletedOrderByCreatedAtDesc(memberId, "N")
                .stream()
                .map(comment -> reviewRepository.findById(comment.getReviewId())
                        .filter(review -> "N".equals(review.getIsDeleted()))
                        .map(review -> new MyCommentResponse(
                                comment,
                                review,
                                placeRepository.findById(review.getPlaceId())
                                        .map(place -> place.getName())
                                        .orElse("서울 여행지")
                        )))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
