package com.suwon.festival.service.merge;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.service.instagram.InstagramSwitch;

import lombok.RequiredArgsConstructor;

// 같은 행사 합치기 실행 (설계 docs/event-merge.md 5번)
// - 숨긴 행까지 전체를 읽어 매번 처음부터 계산 → 몇 번 돌려도 같은 결과, 기준에서 벗어난 행은 숨김 해제
// - 판단은 EventGrouper(순수 계산), 여기서는 DB 읽기 · 반영만
@Service
@RequiredArgsConstructor
public class EventMergeService {

  private static final Logger log = LoggerFactory.getLogger(EventMergeService.class);

  // 행사로 읽힌 블록 (지도에 못 올라간 것 포함). 공지(SKIPPED) · 못 읽은 블록은 행사 수에 안 셈
  private static final Set<InstagramBlock.Status> EVENT_BLOCKS = EnumSet.of(
      InstagramBlock.Status.PARSED, InstagramBlock.Status.NO_DATE, InstagramBlock.Status.NO_COORD,
      InstagramBlock.Status.ERROR, InstagramBlock.Status.READY);

  private final EventRepository eventRepository;
  private final InstagramBlockRepository blockRepository;
  private final InstagramSwitch instagramSwitch;

  // 계산만 하고 저장하지 않음 → 자동 적용 전에 실데이터로 잘못 합쳐지는 쌍을 눈으로 확인 (응답이므로 인스타 스위치를 따름)
  @Transactional(readOnly = true)
  public EventMergeResult preview() {
    List<Event> events = eventRepository.findAll().stream().filter(instagramSwitch::isVisible).toList();
    return toResult(events, group(events));
  }

  // 계산 결과를 merged_into_id 에 반영. 재단 · 인스타 동기화 끝에 호출 (설계 7번 4번 PR)
  @Transactional
  public EventMergeResult recompute() {
    List<Event> events = eventRepository.findAll();
    EventGrouper.Result grouped = group(events);

    Map<Long, Long> representativeByHiddenId = new HashMap<>();
    for (EventGrouper.MergeGroup g : grouped.merged()) {
      g.hidden().forEach(h -> representativeByHiddenId.put(h.id(), g.representative().id()));
    }
    for (Event event : events) {
      Long target = representativeByHiddenId.get(event.getId()); // 묶음에 없거나 대표면 null → 숨김 해제
      if (!Objects.equals(target, event.getMergedIntoId())) {
        event.mergeInto(target);
      }
    }

    EventMergeResult result = toResult(events, grouped);
    log.info("같은 행사 합치기: 묶음 {} · 숨김 {} · 충돌 {}", result.groupCount(), result.hiddenCount(), result.conflictCount());
    grouped.conflicts().forEach(c -> log.warn("재단 행사가 2개 이상 낀 묶음이라 합치지 않음: id {}",
        c.stream().map(MergeCandidate::id).toList()));
    return result;
  }

  private EventGrouper.Result group(List<Event> events) {
    Set<Long> roundupEventIds = roundupEventIds();
    return EventGrouper.group(events.stream()
        .map(e -> MergeCandidate.from(e, roundupEventIds.contains(e.getId())))
        .toList());
  }

  // 행사 블록이 2개 이상인 게시물(모음 글)에서 나온 행사 id
  private Set<Long> roundupEventIds() {
    Map<Long, List<InstagramBlock>> byMedia = blockRepository.findAll().stream()
        .filter(b -> EVENT_BLOCKS.contains(b.getStatus()))
        .collect(Collectors.groupingBy(b -> b.getMedia().getId()));

    Set<Long> ids = new HashSet<>();
    for (List<InstagramBlock> blocks : byMedia.values()) {
      if (blocks.size() >= 2) {
        blocks.stream().filter(b -> b.getEvent() != null).forEach(b -> ids.add(b.getEvent().getId()));
      }
    }
    return ids;
  }

  private static EventMergeResult toResult(List<Event> events, EventGrouper.Result grouped) {
    Map<Long, Event> byId = events.stream().collect(Collectors.toMap(Event::getId, Function.identity()));
    List<EventMergeResult.Group> groups = grouped.merged().stream()
        .map(g -> new EventMergeResult.Group(item(byId, g.representative()), items(byId, g.hidden())))
        .toList();
    List<List<EventMergeResult.Item>> conflicts = grouped.conflicts().stream()
        .map(c -> items(byId, c))
        .toList();
    return EventMergeResult.of(groups, conflicts);
  }

  private static List<EventMergeResult.Item> items(Map<Long, Event> byId, List<MergeCandidate> candidates) {
    return candidates.stream().map(c -> item(byId, c)).toList();
  }

  private static EventMergeResult.Item item(Map<Long, Event> byId, MergeCandidate candidate) {
    return EventMergeResult.Item.from(byId.get(candidate.id()));
  }
}
