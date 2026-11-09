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
        // 합쳐 숨긴 행에서 확인된 회차. 대표가 날짜 모름일 때만 채움 (표시만, 필터 · 캘린더는 안 씀)
        List<LocalDate> confirmedDates,
        // 장소 · 시간 원문 칸. 인스타 행사는 info 가 없어 상세 화면이 이 값으로 장소 · 시간을 보여 줌
        String place,
        String timeText,
        // 상세 정보 분해 (V7): 요금 · 문의처 · 주최 원문. 재단 행사만, 없거나 아직 동기화 전이면 null
        String fee,
        String contact,
        String host,
        String source,          // SWCF / INSTAGRAM
        String sourceAccount,   // 인스타 행사를 올린 계정 이름 (재단 행사는 null)
        String district) {      // 장안구 · 권선구 · 팔달구 · 영통구. 수원 밖 · 아직 모름은 null
}
