package com.suwon.festival.service.schedule;

import java.time.LocalDate;

// 행사 1건에서 진행 날짜를 뽑는 데 쓰는 값 (설계 docs/event-schedule.md 3번)
// timeText: XML "시간" 칸 / weekdayText: XML "요일" 칸 / body: 본문(description)을 글자로 정리한 것 — 한 줄에 한 항목, 표 칸은 "|" 로 구분
public record ScheduleInput(LocalDate start, LocalDate end, String timeText, String weekdayText, String body) {
}
