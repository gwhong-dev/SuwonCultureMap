package com.suwon.festival.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.instagram.InstagramSwitch;
import com.suwon.festival.service.transit.NearbyTransitService;
import com.suwon.festival.service.transit.NearbyTransitService.NearbyTransit;

import lombok.RequiredArgsConstructor;

// 오시는 길: 행사 장소 근처 버스 정류장(버스 번호) · 역 (로그인 불필요, 읽기 전용)
// - 좌표를 직접 받지 않고 행사 번호로만 받음: 아무 좌표나 받으면 우리 서버가 남의 호출을 대신 내 주는 통로가 되고
//   캐시 키가 끝없이 늘어남 (설계 docs/transit-directions.md 4번) → 우리 DB 행사 좌표만
// - 좌표 없는 행사 · 키 없음 · 외부 실패 = 빈 목록 (화면은 칸을 숨김)
@RestController
@RequestMapping("/api/events/{id}")
@RequiredArgsConstructor
public class TransitController {

  private final EventRepository eventRepository;
  private final InstagramSwitch instagramSwitch;
  private final NearbyTransitService transitService;

  @GetMapping(value = "/transit", produces = "application/json")
  public NearbyTransit transit(@PathVariable Long id) {
    Event event = eventRepository.findById(id)
        .filter(instagramSwitch::isVisible)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."));
    // 좌표는 필수 칸이지만 0 이면 위치를 모르는 것 (화면 renderDetailMap 도 0 을 "없음"으로 봄)
    Double lat = event.getLatitude();
    Double lng = event.getLongitude();
    if (lat == null || lng == null || lat == 0 || lng == 0) return NearbyTransit.EMPTY;
    return transitService.find(lat, lng);
  }
}
