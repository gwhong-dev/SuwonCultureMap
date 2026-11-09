package com.suwon.festival.controller;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
  // 탭 2개가 동시에 합치면 늦은 쪽은 고유 제약 위반 → 아래 handleConflict 가 409 (화면은 기기 목록을 남겨 다음에 다시 합침)
  @PostMapping(value = "/merge", produces = "application/json")
  public MergeResult mergeFavorites(@RequestBody MergeRequest request, HttpSession session) {
    Long memberId = getMemberId(session);
    List<Long> eventIds = request == null ? null : request.eventIds();
    if (eventIds != null && eventIds.size() > FavoriteService.MERGE_MAX) {
      throw new IllegalArgumentException("합칠 찜이 너무 많습니다.");
    }
    return new MergeResult(favoriteService.mergeFavorites(memberId, eventIds));
  }

  // 찜 API 에서만: 고유 제약 위반(같은 찜을 동시에 저장) → 500 대신 409. 먼저 저장한 쪽이 이미 반영됨
  // (전역으로 두면 다른 API 의 NOT NULL · FK 위반까지 409 로 가려져서 이 컨트롤러로 좁힘)
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<String> handleConflict(DataIntegrityViolationException e) {
    log.warn("찜 저장 충돌 (동시 요청?): {}", e.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 처리된 요청입니다. 새로 고침해 주세요.");
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