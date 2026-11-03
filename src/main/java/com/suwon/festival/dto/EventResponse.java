package com.suwon.festival.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        String category,
        Double latitude,
        Double longitude,
        String imageUrl,
        String info,
        LocalDateTime pubDate,
        LocalDate startDate,
        LocalDate endDate,
        String link,
        String scheduleStatus,
        String scheduleText,
        boolean scheduleHolidayNote,
        List<LocalDate> dates,
        // 장소 · 시간 원문 칸. 인스타 행사는 info 가 없어 상세 화면이 이 값으로 장소 · 시간을 보여 줌
        String place,
        String timeText,
        String source,          // SWCF / INSTAGRAM
        String sourceAccount) { // 인스타 행사를 올린 계정 이름 (재단 행사는 null)
}
