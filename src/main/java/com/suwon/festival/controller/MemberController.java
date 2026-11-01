package com.suwon.festival.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.service.KakaoAuthService;
import com.suwon.festival.service.MemberService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final KakaoAuthService kakaoAuthService;

    @DeleteMapping("/me")
    public String withdraw(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        List<String> kakaoUserIds = memberService.withdraw(memberId); // 여기서 커밋됨
        try {
            session.invalidate();
        } catch (IllegalStateException alreadyInvalid) {
            // 다른 탭의 요청이 먼저 세션을 끝낸 경우 → 탈퇴는 이미 끝났으니 그대로 진행
        }
        boolean unlinked = kakaoUserIds.stream().map(kakaoAuthService::unlink).reduce(true, Boolean::logicalAnd);
        return unlinked
                ? "회원 탈퇴가 완료됐어요."
                : "회원 탈퇴가 완료됐어요. 카카오 앱 연결은 해제하지 못했으니 카카오계정 설정에서 직접 해제해 주세요.";
    }
}
