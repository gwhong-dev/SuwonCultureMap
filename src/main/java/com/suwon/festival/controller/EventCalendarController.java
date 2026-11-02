package com.suwon.festival.controller;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.entity.Event;
import com.suwon.festival.service.EventCalendarService;

import lombok.RequiredArgsConstructor;

// 행사 → 캘린더 저장 (로그인 불필요, 읽기 전용)
@RestController
@RequestMapping("/api/events/{id}")
@RequiredArgsConstructor
public class EventCalendarController {

  private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

  private final EventCalendarService calendarService;

  // 📱 아이폰: Safari가 이 응답을 받으면 "캘린더에 추가" 화면을 띄움 (PC는 .ics 파일로 받음)
  @GetMapping("/calendar.ics")
  // date: 진행 날짜 행사에서 고른 날 (YYYY-MM-DD, 없으면 기간 전체)
  public ResponseEntity<String> ics(@PathVariable Long id, @RequestParam(required = false) LocalDate date) {
    Event event = calendarService.findSchedulable(id);
    calendarService.checkRunDate(event, date);
    return ResponseEntity.ok()
        .contentType(TEXT_CALENDAR)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"suwon-event-" + id + (date != null ? "-" + date : "") + ".ics\"")
        .body(calendarService.toIcs(event, date, Instant.now()));
  }

  // 💻 구글 캘린더: 제목·날짜가 채워진 "일정 추가" 화면으로 이동
  @GetMapping("/calendar/google")
  public ResponseEntity<Void> google(@PathVariable Long id, @RequestParam(required = false) LocalDate date) {
    Event event = calendarService.findSchedulable(id);
    calendarService.checkRunDate(event, date);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(calendarService.toGoogleUrl(event, date)))
        .build();
  }
}
