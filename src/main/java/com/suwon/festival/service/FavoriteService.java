package com.suwon.festival.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.instagram.InstagramSwitch;

import lombok.RequiredArgsConstructor;

// 화면엔 합치기의 대표 행만 보임 → 찜도 대표 기준으로 판단 (숨긴 행을 찜한 기록은 원본 그대로 남김)
@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final MemberRepository memberRepository;
  private final EventRepository eventRepository;
  private final EventService eventService;
  private final InstagramSwitch instagramSwitch;

  @Transactional
  public void addFavorite(Long memberId, Long eventId) {
    Event event = eventRepository.findById(eventId)
        .map(this::representativeOf)
        .filter(instagramSwitch::isVisible)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 행사입니다."));

    if (!favoritesOf(memberId, event.getId()).isEmpty()) {
      throw new IllegalArgumentException("이미 즐겨찾기한 행사입니다.");
    }

    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

    Favorite favorite = Favorite.builder()
        .member(member)
        .event(event)
        .createdAt(LocalDateTime.now())
        .build();

    favoriteRepository.save(favorite);
  }

  // 비회원 찜 합치기: 로그인 전에 이 기기에 담아 둔 행사 번호들을 계정 찜에 더함. 더한 개수를 돌려줌
  // 없는 행사 · 숨김(인스타 스위치) · 이미 찜한 것 · 같은 대표로 모이는 번호는 건너뜀 (오류 없이)
  public static final int MERGE_MAX = 100;

  @Transactional
  public int mergeFavorites(Long memberId, List<Long> eventIds) {
    if (eventIds == null || eventIds.isEmpty()) return 0;
    Member member = memberRepository.findById(memberId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    Set<Long> owned = new HashSet<>();
    favoriteRepository.findByMemberId(memberId)
        .forEach(favorite -> owned.add(representativeOf(favorite.getEvent()).getId()));
    int added = 0;
    for (Long eventId : eventIds.stream().filter(java.util.Objects::nonNull).distinct().limit(MERGE_MAX).toList()) {
      Event event = eventRepository.findById(eventId)
          .map(this::representativeOf)
          .filter(instagramSwitch::isVisible)
          .orElse(null);
      if (event == null || !owned.add(event.getId())) continue;
      favoriteRepository.save(Favorite.builder()
          .member(member)
          .event(event)
          .createdAt(LocalDateTime.now())
          .build());
      added++;
    }
    return added;
  }

  @Transactional
  public void removeFavorite(Long memberId, Long eventId) {
    Long representativeId = eventRepository.findById(eventId)
        .map(event -> representativeOf(event).getId())
        .orElse(eventId);
    List<Favorite> favorites = favoritesOf(memberId, representativeId);
    if (favorites.isEmpty()) {
      throw new IllegalArgumentException("즐겨찾기하지 않은 행사입니다.");
    }

    favoriteRepository.deleteAll(favorites); // 같은 대표로 모이는 찜이 여러 개면 모두 지움 (안 그러면 취소해도 별이 남음)
  }

  @Transactional(readOnly = true)
  public List<EventResponse> getFavoriteEvents(Long memberId) {
    Set<Long> seen = new HashSet<>();
    return eventService.toResponses(favoriteRepository.findByMemberId(memberId).stream()
        .map(Favorite::getEvent)
        .map(this::representativeOf)
        .filter(event -> seen.add(event.getId())) // 같은 대표로 모이면 1개만
        .toList());
  }

  // 이 회원의 찜 중 해당 대표로 모이는 것들 (대표 자체를 찜했거나, 합쳐지기 전에 숨긴 행을 찜한 경우)
  private List<Favorite> favoritesOf(Long memberId, Long representativeId) {
    return favoriteRepository.findByMemberId(memberId).stream()
        .filter(favorite -> representativeId.equals(representativeOf(favorite.getEvent()).getId()))
        .toList();
  }

  // 숨긴 행이면 대표, 아니면 자기 자신 (대표는 항상 묶음 끝이라 한 번만 따라감. FK 가 대표의 존재를 보장)
  private Event representativeOf(Event event) {
    Long mergedIntoId = event.getMergedIntoId();
    return mergedIntoId == null ? event : eventRepository.findById(mergedIntoId).orElseThrow();
  }
}
