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
        List<LocalDate> dates) {
}
