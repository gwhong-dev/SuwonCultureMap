package com.suwon.festival.service.landmark;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.suwon.festival.entity.Landmark;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.LandmarkRepository;
import com.suwon.festival.service.SyncLogService;
import com.suwon.festival.service.place.PlaceGeocoder;

// 재단 명소(59_C 수원화성 시설물 · 63_C 화성행궁 시설물) → landmark 테이블 (V10)
//  - 하루 1회: 자동 동기화(06 · 18시) 때 마지막 성공이 DUE_AFTER(20시간) 넘었을 때만 (ScheduledSyncJob)
//  - 지우지 않고 갱신 → id 유지. 응답에서 빠진 곳도 지우지 않음 (last_seen_at 이 멈춤)
//  - 좌표 없는 곳: 카카오 장소 검색(PlaceGeocoder)으로 보충, 못 찾으면 null (목록에만). 한 번 보충한 좌표는 다시 검색하지 않음
//  - 행마다 각자 저장(트랜잭션) — 한 곳의 오류가 나머지를 막지 않음 (EventSyncService 와 같은 이유로 @Transactional 을 붙이지 않음)
//  - 이력: sync_log 에 LANDMARK 1줄. 실패 사유는 코드 + 예외 종류만 (호출 주소에 인증키가 들어 있음)
@Service
public class LandmarkSyncService {

  private static final Logger log = LoggerFactory.getLogger(LandmarkSyncService.class);
  static final Duration DUE_AFTER = Duration.ofHours(20);

  private final LandmarkClient client;
  private final LandmarkRepository repository;
  private final PlaceGeocoder geocoder;
  private final SyncLogService syncLogService;
  private final Clock clock;

  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public LandmarkSyncService(LandmarkClient client, LandmarkRepository repository, PlaceGeocoder geocoder,
      SyncLogService syncLogService) {
    this(client, repository, geocoder, syncLogService, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  LandmarkSyncService(LandmarkClient client, LandmarkRepository repository, PlaceGeocoder geocoder,
      SyncLogService syncLogService, Clock clock) {
    this.client = client;
    this.repository = repository;
    this.geocoder = geocoder;
    this.syncLogService = syncLogService;
    this.clock = clock;
  }

  // 마지막 성공이 20시간 안이면 false (06 · 18시 두 번 도는 자동 동기화에서 하루 한 번만 받게)
  public boolean isDue() {
    Optional<LocalDateTime> last = syncLogService.lastSuccessAt(SyncLog.Source.LANDMARK);
    return last.isEmpty() || !last.get().plus(DUE_AFTER).isAfter(now());
  }

  public boolean isEmpty() {
    return repository.count() == 0;
  }

  public LandmarkSyncResult sync() {
    if (!client.configured(LandmarkFeed.FORTRESS) && !client.configured(LandmarkFeed.PALACE)) {
      log.info("명소 동기화 건너뜀 · 인증키 없음 (swcf.api.key.facility · swcf.api.key.palace-facility)");
      return LandmarkSyncResult.notConfigured();
    }
    if (!running.compareAndSet(false, true)) {
      log.info("명소 동기화가 이미 실행 중 → 이번 실행은 건너뜀");
      return LandmarkSyncResult.skippedAlreadyRunning();
    }
    LocalDateTime startedAt = syncLogService.now();
    try {
      Counter c = new Counter();
      for (LandmarkFeed feed : LandmarkFeed.values()) {
        syncFeed(feed, c);
      }
      LandmarkSyncResult result = c.result();
      if (result.ok()) {
        syncLogService.success(SyncLog.Source.LANDMARK, startedAt, result.fetched(), result.created(),
            result.updated(), "좌표 보충 " + result.geocoded() + " · 좌표 없음 " + result.noCoord()
                + (result.skipped() > 0 ? " · 건너뜀 " + result.skipped() : "")
                + (result.skippedFeeds().isEmpty() ? "" : " · 키 없음 " + String.join(", ", result.skippedFeeds())));
      } else {
        syncLogService.fail(SyncLog.Source.LANDMARK, startedAt, result.fetched(),
            "실패 " + String.join(", ", result.failedFeeds()));
      }
      return result;
    } finally {
      running.set(false);
    }
  }

  private void syncFeed(LandmarkFeed feed, Counter c) {
    if (!client.configured(feed)) {
      // 실패로 세지 않음: 키를 하나만 넣은 운영에서 매번 FAIL → 하루 두 번 받고 경고가 쌓이는 것을 막음. 저장된 행은 그대로
      log.info("명소 {} 건너뜀 · 인증키 없음", feed.code);
      c.skippedFeeds.add(feed.code);
      return;
    }
    List<LandmarkParser.Parsed> items;
    try {
      items = LandmarkParser.parse(client.fetch(feed), feed);
    } catch (Exception e) {
      String type = errorType(e);
      log.warn("명소 {} 받기 실패 {}", feed.code, type); // 메시지는 남기지 않음 (주소에 인증키)
      c.failedFeeds.add(feed.code + "(" + type + ")");
      return;
    }
    if (items.isEmpty()) {
      log.warn("명소 {} 응답이 0건", feed.code);
    }
    int skipped = 0;
    for (LandmarkParser.Parsed item : items) {
      c.fetched++;
      try {
        syncItem(feed, item, c);
      } catch (RuntimeException e) {
        skipped++;
        log.warn("명소 건너뜀: {} idx={} {}", feed.code, item.sourceKey(), e.getClass().getSimpleName());
      }
    }
    c.skipped += skipped;
    // 전부 실패면 행 문제가 아니라 DB 장애 · 형식 변경 → 성공으로 넘기지 않음
    if (skipped > 0 && skipped == items.size()) {
      c.failedFeeds.add(feed.code + "(모든 행 실패 " + skipped + "건)");
    }
  }

  private void syncItem(LandmarkFeed feed, LandmarkParser.Parsed item, Counter c) {
    LocalDateTime now = now();
    Landmark existing = repository.findBySourceAndSourceKey(feed.source, item.sourceKey()).orElse(null);
    Landmark target = existing == null ? Landmark.create(feed.source, item.sourceKey(), item.content(), now) : existing;
    boolean changed = existing != null && existing.apply(item.content());

    if (item.lat() != null) {
      changed |= target.setCoordinates(item.lat(), item.lng(), Landmark.COORD_API) && existing != null;
    } else if (Landmark.COORD_API.equals(target.getCoordSource())) {
      // 재단이 좌표를 지웠거나 범위 밖 값으로 바꿈 → 옛 재단 좌표를 지도에 계속 찍지 않게 비우고 아래에서 다시 보충
      changed |= target.setCoordinates(null, null, null) && existing != null;
    }
    if (item.lat() == null && !target.hasCoordinates()) {
      // 재단 좌표가 없고 저장된 좌표도 없을 때만 검색 (한 번 보충한 좌표는 그대로 둠 → 호출 수 절약)
      double[] found = geocode(feed, target.getName());
      if (found != null) {
        target.setCoordinates(found[0], found[1], Landmark.COORD_KAKAO);
        changed |= existing != null;
        c.geocoded++;
      }
    }
    if (!target.hasCoordinates()) c.noCoord++;

    target.markSeen(now);
    if (changed) target.touchUpdated(now);
    repository.save(target);
    if (existing == null) {
      c.created++;
    } else if (changed) {
      c.updated++;
    }
  }

  // 1) 이름 그대로 2) "수원화성 북옹성" 처럼 종류를 붙여서 — 둘 다 결과 이름이 검색어와 정확히 같고, 명소 범위 안일 때만 인정
  //  (PlaceGeocoder 는 검색어가 결과 이름을 품어도 인정함 → "수원화성 X" 가 "수원화성" 자체에 찍히지 않게 여기서 한 번 더 거름)
  double[] geocode(LandmarkFeed feed, String name) {
    String ko = LandmarkParser.koreanName(name);
    if (ko.isBlank()) return null;
    List<String> queries = new ArrayList<>();
    queries.add(ko);
    if (!squash(ko).startsWith(squash(feed.kind))) queries.add(feed.kind + " " + ko);
    for (String q : queries) {
      PlaceGeocoder.Result r = geocoder.geocode(q);
      if (r.status() == PlaceGeocoder.Status.ERROR) {
        log.warn("명소 좌표 검색 실패: {} · {}", ko, r.reason()); // 다음 동기화에서 다시 시도
        return null;
      }
      if (r.status() == PlaceGeocoder.Status.FOUND && squash(r.place().name()).equals(squash(q))
          && feed.inArea(r.place().lat(), r.place().lng())) {
        return new double[] { r.place().lat(), r.place().lng() };
      }
    }
    return null;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  private static String squash(String s) {
    return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private static String errorType(Throwable e) {
    Throwable cause = e.getCause();
    return e.getClass().getSimpleName() + (cause == null || cause == e ? "" : " ← " + cause.getClass().getSimpleName());
  }

  private static final class Counter {
    int fetched, created, updated, skipped, geocoded, noCoord;
    final List<String> failedFeeds = new ArrayList<>();
    final List<String> skippedFeeds = new ArrayList<>();

    LandmarkSyncResult result() {
      return new LandmarkSyncResult(true, false, fetched, created, updated, skipped, geocoded, noCoord, failedFeeds,
          skippedFeeds);
    }
  }
}
