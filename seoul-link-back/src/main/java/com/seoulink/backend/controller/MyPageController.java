package com.seoulink.backend.controller;

import com.seoulink.backend.service.MyPageService;
import org.springframework.web.bind.annotation.*;
import com.seoulink.backend.dto.response.MyPageResponse;

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
}