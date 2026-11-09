package com.suwon.festival.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.service.FavoriteService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

  private final FavoriteService favoriteService;

  @PostMapping("/{eventId}")
  public String addFavorite(@PathVariable Long eventId, HttpSession session) {
    Long memberId = getMemberId(session);
    favoriteService.addFavorite(memberId, eventId);
    return "즐겨찾기 추가 완료";
  }

  @DeleteMapping("/{eventId}")
  public String removeFavorite(@PathVariable Long eventId, HttpSession session) {
    Long memberId = getMemberId(session);
    favoriteService.removeFavorite(memberId, eventId);
    return "즐겨찾기 삭제 완료";
  }

  // 비회원 찜 합치기 (로그인 직후 화면이 이 기기 찜 번호를 보냄) → 더한 개수
  // 본문 크기는 MergeBodyLimitFilter(413), 숫자 아닌 값은 JSON 읽기 실패(400), 개수 초과는 여기서 400
  // 탭 2개가 동시에 합치면 늦은 쪽은 고유 제약 위반 → GlobalExceptionHandler 가 409 (화면은 기기 목록을 남겨 다음에 다시 합침)
  @PostMapping(value = "/merge", produces = "application/json")
  public MergeResult mergeFavorites(@RequestBody MergeRequest request, HttpSession session) {
    Long memberId = getMemberId(session);
    List<Long> eventIds = request == null ? null : request.eventIds();
    if (eventIds != null && eventIds.size() > FavoriteService.MERGE_MAX) {
      throw new IllegalArgumentException("합칠 찜이 너무 많습니다.");
    }
    return new MergeResult(favoriteService.mergeFavorites(memberId, eventIds));
  }

  public record MergeRequest(List<Long> eventIds) {
  }

  public record MergeResult(int added) {
  }

  @GetMapping(produces = "application/json")
  public List<EventResponse> getFavorites(HttpSession session) {
    Long memberId = getMemberId(session);
    return favoriteService.getFavoriteEvents(memberId);
  }

  private Long getMemberId(HttpSession session) {
    Long memberId = (Long) session.getAttribute("memberId");
    if (memberId == null) {
      throw new IllegalArgumentException("로그인이 필요합니다.");
    }
    return memberId;
  }
}