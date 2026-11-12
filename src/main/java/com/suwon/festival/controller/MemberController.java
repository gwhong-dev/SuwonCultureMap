package com.suwon.festival.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.service.KakaoAuthService;
import com.suwon.festival.service.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;
    private final KakaoAuthService kakaoAuthService;

    @DeleteMapping("/me")
    public String withdraw(HttpSession session) {
        Long memberId = loggedInMemberId(session);
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

    // 닉네임 바꾸기: { "nickname": "..." } → 저장된(정리된) 닉네임. 규칙 위반 · 중복이면 400 + 안내 문구
    @PatchMapping(value = "/me/nickname", produces = "application/json")
    public Map<String, String> changeNickname(@RequestBody Map<String, String> body, HttpSession session) {
        Long memberId = loggedInMemberId(session);
        return Map.of("nickname", memberService.changeNickname(memberId, body.get("nickname")));
    }

    // 마이페이지 "다시 뽑기": 겹치지 않는 랜덤 닉네임 제안 (저장은 안 함, 저장은 위 PATCH)
    @GetMapping(value = "/me/nickname/suggestion", produces = "application/json")
    public Map<String, String> suggestNickname(HttpSession session) {
        loggedInMemberId(session);
        return Map.of("nickname", memberService.suggestNickname());
    }

    private Long loggedInMemberId(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return memberId;
    }
}
