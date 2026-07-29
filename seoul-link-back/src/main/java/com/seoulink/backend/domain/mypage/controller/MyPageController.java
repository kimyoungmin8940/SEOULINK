package com.seoulink.backend.domain.mypage.controller;

import com.seoulink.backend.domain.mypage.service.MyPageService;
import org.springframework.web.bind.annotation.*;
import com.seoulink.backend.domain.mypage.dto.response.MyPageResponse;
import com.seoulink.backend.domain.mypage.dto.response.MyCommentResponse;

import java.util.List;

@RestController
@RequestMapping("/api/mypage")
public class MyPageController {

    private final MyPageService myPageService;

    public MyPageController(MyPageService myPageService) {
        this.myPageService = myPageService;
    }

    @GetMapping("/{memberId}")
    public MyPageResponse getMyPage(@PathVariable Long memberId) {
        return myPageService.getMyPage(memberId);
    }

    @GetMapping("/{memberId}/comments")
    public List<MyCommentResponse> getMyComments(@PathVariable Long memberId) {
        return myPageService.getMyComments(memberId);
    }
}
