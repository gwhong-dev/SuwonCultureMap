package com.suwon.festival.service.instagram;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.service.merge.EventMatcher;
import com.suwon.festival.service.merge.MergeCandidate;

// 다시 처리한 게시물에서 더 이상 나오지 않는 블록 내리기 (설계 문서 7번 #13)
// 키(제목 + 시작일 + 장소)가 바뀌면 옛 블록이 남아 같은 자리 마커가 2개 되고, 합치기가 게시물을 모음 글로 오판함
// 원칙: 틀리게 지우기가 남겨 두기보다 손해 → 확실할 때만 내림
//  - 이번 캡션에 새로 READY 가 된 행사 중 같은 행사(EventMatcher)가 있으면 그 행사가 뒤를 이음 → 내림
//    이미 같은 글에 따로 있던 행사는 제목이 비슷해도 다른 행사이므로 뒤를 잇는 행사로 보지 않음 (본편 ↔ 앙코르)
//  - 뒤를 잇는 행사가 없으면 이번 처리가 "확정"일 때만 내림: READY 가 1개 이상이고, 나머지는 공지(SKIPPED)뿐.
//    못 읽은 블록 · 좌표 실패 · 날짜 없음이 있으면 그중 하나가 이 행사의 새 모습일 수 있어 다음 실행으로 미룸
//    (SKIPPED 는 지금 나오지 않음. AI 를 붙이면 일시적인 "행사 아님"이 멀쩡한 행사를 내리지 않는지 다시 볼 것)
//  - 옛 제목이 캡션에 그대로 있으면 미룸: 날짜 라벨을 못 읽으면 블록 자체가 안 나와 "확정"으로 보일 수 있음
//  - 찜은 뒤를 잇는 행사로 옮김. 옮길 곳이 없으면 행사를 그대로 둠 (찜을 잃지 않음, 찜이 풀리면 다음 실행에서 내림)
//    숨김(merged_into_id)으로 처리하지 않는 이유: 합치기는 매번 전체를 다시 계산해 그 값을 되돌림
// 수집 범위 밖으로 밀려난 게시물은 여기까지 오지 않으므로 그대로 남음 · 게시물 처리와 같은 트랜잭션 (실패하면 함께 되돌림)
@Component
class StaleBlockRetirer {

  private static final Logger log = LoggerFactory.getLogger(StaleBlockRetirer.class);

  static final String REASON = "캡션에서 사라짐";

  private static final Set<InstagramBlock.Status> CONCLUSIVE = EnumSet.of(
      InstagramBlock.Status.READY, InstagramBlock.Status.SKIPPED);

  private final EventRepository eventRepository;
  private final FavoriteRepository favoriteRepository;

  StaleBlockRetirer(EventRepository eventRepository, FavoriteRepository favoriteRepository) {
    this.eventRepository = eventRepository;
    this.favoriteRepository = favoriteRepository;
  }

  // 이번 처리 결과. ready: 이번 READY 행사 전체, fresh: 그중 이번에 새로 READY 가 된 행사, statuses: 이번 블록 상태들
  record Current(List<Event> ready, List<Event> fresh, Set<InstagramBlock.Status> statuses, String caption) {

    boolean conclusive() {
      return !ready.isEmpty() && CONCLUSIVE.containsAll(statuses);
    }

    boolean mentions(String title) {
      return BlockKeys.norm(caption).contains(BlockKeys.norm(title));
    }
  }

  // stale: 이번 캡션에서 나오지 않은 블록들. 내린 블록의 로그 줄을 돌려줌
  List<String> retire(String mediaId, Collection<InstagramBlock> stale, Current current, LocalDateTime now) {
    List<String> logLines = new ArrayList<>();
    // RETIRED · EXPIRED(지난 행사로 지움)는 이미 행사가 없음 → 그대로
    List<InstagramBlock> live = stale.stream().filter(b -> b.getStatus() != InstagramBlock.Status.RETIRED
        && b.getStatus() != InstagramBlock.Status.EXPIRED).toList();
    long liveEvents = live.stream().filter(b -> b.getEvent() != null).count();

    for (InstagramBlock block : live) {
      Event event = block.getEvent();
      if (event == null) {
        // 행사가 없는 블록은 상태만 바꿈. 지도에 영향이 없고, 같은 키가 돌아오면 다시 살아남
        block.update(InstagramBlock.Status.RETIRED, REASON, null, now);
        logLines.add(BlockLog.retiredLine(mediaId, block.getBlockKey(), "-"));
        continue;
      }
      Event successor = successorOf(event, current.fresh());
      if (successor == null && !current.conclusive()) continue;
      if (successor == null && liveEvents == 1 && current.ready().size() == 1 && current.fresh().size() == 1
          && sameDateOrPlace(event, current.fresh().get(0))) {
        // 행사 하나짜리 글에서 제목만 크게 바뀜 (제목 규칙 변경 등) → 찜을 옮길 곳으로 봄. 날짜도 장소도 다르면 글을 재활용한 다른 행사
        successor = current.fresh().get(0);
      }
      if (successor == null && current.mentions(event.getTitle())) continue;
      List<Favorite> favorites = favoriteRepository.findByEventId(event.getId());
      if (successor == null && !favorites.isEmpty()) {
        log.warn("찜이 걸려 있고 옮길 행사가 없어 내리지 않음 eventId={} 찜 {}건", event.getId(), favorites.size());
        continue;
      }
      for (Favorite favorite : favorites) moveFavorite(favorite, successor);

      // FK: 이 행사를 대표로 삼던 행의 숨김을 먼저 해제. 합치기는 다음 계산에서 다시 맞춰짐
      eventRepository.findByMergedIntoId(event.getId()).forEach(hidden -> hidden.mergeInto(null));
      block.update(InstagramBlock.Status.RETIRED, REASON, null, now);
      eventRepository.delete(event);
      logLines.add(BlockLog.retiredLine(mediaId, block.getBlockKey(), event.getTitle()));
    }
    return logLines;
  }

  private static Event successorOf(Event event, List<Event> fresh) {
    MergeCandidate candidate = MergeCandidate.from(event, false);
    return fresh.stream()
        .filter(r -> EventMatcher.isSameEvent(candidate, MergeCandidate.from(r, false)))
        .findFirst().orElse(null);
  }

  private static boolean sameDateOrPlace(Event a, Event b) {
    return (a.getStartDate() != null && a.getStartDate().equals(b.getStartDate()))
        || (a.getPlace() != null && a.getPlace().equals(b.getPlace()));
  }

  // 이미 뒤 행사를 찜한 회원이면 옛 찜만 지움 ((회원, 행사) unique)
  private void moveFavorite(Favorite favorite, Event successor) {
    if (favoriteRepository.existsByMemberIdAndEventId(favorite.getMember().getId(), successor.getId())) {
      favoriteRepository.delete(favorite);
    } else {
      favorite.moveTo(successor);
    }
  }
}
