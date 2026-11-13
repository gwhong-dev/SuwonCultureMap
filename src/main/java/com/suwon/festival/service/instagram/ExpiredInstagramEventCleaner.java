package com.suwon.festival.service.instagram;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.service.caption.NonTouristFilter;

// 종료 7일 지난 인스타 행사 삭제 (InstagramExpiry). 인스타 동기화 끝(잠금 안)에서 사진 정리 전에 실행 → 사진도 같은 실행에서 지워짐
// 재단 행사는 건드리지 않음. 순서 (FK):
//  1. 찜: 이 행사가 다른 행사에 합쳐져 숨겨진 행이고 대표가 남으면 대표로 옮김(이미 찜했으면 지움), 아니면 지움
//  2. 이 행사를 대표로 삼던 숨긴 행: 숨김 해제 (합치기는 다음 계산에서 다시 맞춰짐)
//  3. 진행 날짜(event_date) 지움
//  4. 블록: EXPIRED + 행사 연결 끊기 → 같은 게시물이 다시 수집돼도 지난 날짜면 행사를 만들지 않음 (InstagramPostProcessor)
//  5. 행사 지움 → 사진 파일은 이어서 cleanupImages 가 DB 에 없는 파일로 지움
// 한 트랜잭션: 실패하면 모두 되돌리고 다음 동기화에서 다시
@Component
public class ExpiredInstagramEventCleaner {

  private static final Logger log = LoggerFactory.getLogger(ExpiredInstagramEventCleaner.class);
  static final String REASON = "종료 " + InstagramExpiry.KEEP_DAYS + "일 지남 — 행사 삭제";

  private final InstagramExpiry expiry;
  private final EventRepository eventRepository;
  private final FavoriteRepository favoriteRepository;
  private final EventDateRepository eventDateRepository;
  private final InstagramBlockRepository blockRepository;

  public ExpiredInstagramEventCleaner(InstagramExpiry expiry, EventRepository eventRepository,
      FavoriteRepository favoriteRepository, EventDateRepository eventDateRepository,
      InstagramBlockRepository blockRepository) {
    this.expiry = expiry;
    this.eventRepository = eventRepository;
    this.favoriteRepository = favoriteRepository;
    this.eventDateRepository = eventDateRepository;
    this.blockRepository = blockRepository;
  }

  // 지운 행사 수
  @Transactional
  public int clean() {
    if (!expiry.isEnabled()) return 0;
    LocalDate cutoff = expiry.cutoff();
    List<Event> expired = eventRepository.findExpired(Event.SOURCE_INSTAGRAM, cutoff);
    if (expired.isEmpty()) return 0;
    Set<Long> ids = expired.stream().map(Event::getId).collect(Collectors.toSet());
    LocalDateTime now = LocalDateTime.now(InstagramPostProcessor.KST);

    int favoritesDeleted = 0, favoritesMoved = 0;
    for (Event event : expired) {
      Event representative = event.getMergedIntoId() == null || ids.contains(event.getMergedIntoId()) ? null
          : eventRepository.findById(event.getMergedIntoId()).orElse(null);
      for (Favorite favorite : favoriteRepository.findByEventId(event.getId())) {
        if (representative != null
            && !favoriteRepository.existsByMemberIdAndEventId(favorite.getMember().getId(), representative.getId())) {
          favorite.moveTo(representative);
          favoritesMoved++;
        } else {
          favoriteRepository.delete(favorite);
          favoritesDeleted++;
        }
      }
    }
    eventRepository.findByMergedIntoIdIn(ids).forEach(hidden -> hidden.mergeInto(null));
    expired.forEach(e -> e.mergeInto(null)); // 지울 행끼리 서로 가리키는 경우도 FK 순서 걱정 없게
    eventDateRepository.deleteAll(eventDateRepository.findByEventIdInOrderByRunDate(ids));
    List<InstagramBlock> blocks = blockRepository.findByEventIdIn(ids);
    blocks.forEach(b -> b.update(InstagramBlock.Status.EXPIRED, REASON, null, now));
    eventRepository.flush(); // 찜 · 숨김 해제 · 블록 끊기를 먼저 반영한 뒤 행사 삭제
    eventRepository.deleteAll(expired);
    eventRepository.flush();

    log.info("지난 인스타 행사 삭제: 행사 {}건 (기준 {} 이하) · 찜 삭제 {} · 대표로 옮김 {} · 블록 {}",
        expired.size(), cutoff, favoritesDeleted, favoritesMoved, blocks.size());
    return expired.size();
  }

  // 관광 대상 아닌 인스타 행사 삭제 (NonTouristFilter, 11/13 사용자 결정)
  // 동기화가 다시 처리하는 건 계정마다 최근 글 몇 개뿐 → 그 밖의 오래된 글로 이미 올라간 행사도 여기서 내림
  // 찜이 걸린 행사는 남김 (찜을 잃지 않음). 블록은 SKIPPED + 이유로 바꾸고 행사 연결을 끊음 → 다시 처리돼도 행사를 안 만듦
  // 순서 (FK)는 clean() 과 같음: 숨김 해제 → 진행 날짜 → 블록 끊기 → 행사 삭제. 한 트랜잭션
  @Transactional
  public int cleanNonTourist() {
    Map<Long, String> reasons = new HashMap<>();
    List<Event> targets = new ArrayList<>();
    for (Event event : eventRepository.findBySource(Event.SOURCE_INSTAGRAM)) {
      String reason = NonTouristFilter.reason(event.getTitle(), event.getPlace());
      if (reason == null) continue;
      if (!favoriteRepository.findByEventId(event.getId()).isEmpty()) {
        log.warn("관광 대상 아니지만 찜이 걸려 있어 남김 eventId={}", event.getId());
        continue;
      }
      reasons.put(event.getId(), reason);
      targets.add(event);
    }
    if (targets.isEmpty()) return 0;
    Set<Long> ids = reasons.keySet();
    LocalDateTime now = LocalDateTime.now(InstagramPostProcessor.KST);

    eventRepository.findByMergedIntoIdIn(ids).forEach(hidden -> hidden.mergeInto(null));
    targets.forEach(e -> e.mergeInto(null));
    eventDateRepository.deleteAll(eventDateRepository.findByEventIdInOrderByRunDate(ids));
    List<InstagramBlock> blocks = blockRepository.findByEventIdIn(ids);
    blocks.forEach(b -> b.update(InstagramBlock.Status.SKIPPED, reasons.get(b.getEvent().getId()), null, now));
    eventRepository.flush();
    eventRepository.deleteAll(targets);
    eventRepository.flush();

    log.info("관광 대상 아닌 인스타 행사 삭제: 행사 {}건 · 블록 {}", targets.size(), blocks.size());
    return targets.size();
  }
}
