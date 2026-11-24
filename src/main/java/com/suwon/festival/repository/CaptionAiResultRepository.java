package com.suwon.festival.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.CaptionAiResult;

// 캡션 AI 결과 보관 (키 = 블록 원문 해시)
public interface CaptionAiResultRepository extends JpaRepository<CaptionAiResult, String> {
}
