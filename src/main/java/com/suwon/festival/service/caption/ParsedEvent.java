package com.suwon.festival.service.caption;

import java.time.LocalDate;

// 블록 1개를 정리한 결과 (규칙 · AI 공통 모양, 설계 문서 5번 출력 칸)
// 모르는 칸은 null. isEvent 가 null 이면 "행사인지 모름"
public record ParsedEvent(
    Boolean isEvent,
    String title,
    String category,
    LocalDate startDate,
    LocalDate endDate,
    String timeText,
    String placeName) {
}
