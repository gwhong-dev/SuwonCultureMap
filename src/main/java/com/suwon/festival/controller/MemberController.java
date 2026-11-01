package com.suwon.festival.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.MemberSignupRequest;
import com.suwon.festival.entity.Member;
import com.suwon.festival.service.MemberService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody MemberSignupRequest request) {
        Member member = memberService.signup(request.nickname());

        return ResponseEntity.ok("회원가입 완료 : " + member.getNickname());
    }

    @DeleteMapping("/me")
    public String withdraw(HttpSession session) {
        Long memberId = (Long) session.getAttribute("memberId");
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        memberService.withdraw(memberId);
        session.invalidate();
        return "회원 탈퇴 완료";
    }
}
