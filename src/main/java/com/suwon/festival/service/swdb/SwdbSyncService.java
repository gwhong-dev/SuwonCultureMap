package com.suwon.festival.service.swdb;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.SyncLogService;

// 수원문화DB 행사정보 → event (source=SWDB, source_key=reqIdx). 결정 · 규칙: docs/decisions/2026-11-13-swdb-crawling.md
//  - 스위치 swdb.sync.enabled (기본 false): 꺼져 있으면 요청을 보내지 않음. 이미 저장된 행은 그대로 둠
//  - 하루 1회: 예약 동기화(06 · 18시)마다 불리지만 마지막 성공 뒤 20시간이 안 지났으면 건너뜀 (실패한 날은 다음 예약에 다시)
//  - 목록은 1쪽부터, 한 쪽 전체가 시작일 180일보다 오래되면 멈춤. 상세는 "처음 보는 행사" 와 "아직 안 끝난 저장 행사" 만
//  - 거르기(저장 안 함): 이미 끝남 · 제목에 "모집" · 기간 이상 · 수원 밖 · 좌표를 못 찾음
//  - 저장 안 하는 것: 소개글 · 주관단체 및 개인(개인 이름) · 문의처(휴대폰 번호) · 이미지 파일(원본 주소만)
//  - 같은 행사가 재단 API 에 있으면 합치기(EventGrouper)가 재단 것을 대표로 보여 줌 (출처 우선순위 재단 → 인스타 → 그 밖)
@Service
public class SwdbSyncService {

  private static final Logger log = LoggerFactory.getLogger(SwdbSyncService.class);
  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  static final int MAX_PAGES = 40;          // 11/13 기준 33쪽. 구조가 바뀌어 끝을 못 알아채도 여기서 멈춤
  static final int RECENT_DAYS = 180;       // 목록 날짜(시작일)가 이보다 오래된 행사는 상세를 읽지 않음
  static final int MAX_PERIOD_DAYS = 366;   // 기간이 1년을 넘으면 잘못 들어온 값으로 봄
  static final Duration MIN_INTERVAL = Duration.ofHours(20);
  static final String IMAGE_BASE = "https://www.swcf.or.kr";
  static final String DEFAULT_CATEGORY = "기타";

  public enum Skip { ENDED, RECRUIT, BAD_PERIOD, OUTSIDE, NO_COORD, PARSE, ERROR }

  public record Result(boolean ran, String message, int listed, int saved, int updated, Map<Skip, Integer> skipped) {

    static Result notRun(String message) {
      return new Result(false, message, 0, 0, 0, Map.of());
    }

    public String skippedText() {
      return skipped.entrySet().stream().filter(e -> e.getValue() > 0)
          .map(e -> e.getKey().name() + " " + e.getValue()).collect(Collectors.joining(", "));
    }
  }

  private final SwdbClient client;
  private final SwdbPlaceResolver placeResolver;
  private final EventRepository eventRepository;
  private final SyncLogService syncLogService;
  private final boolean enabled;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();
  private volatile LocalDateTime lastRunAt;

  @Autowired
  public SwdbSyncService(SwdbClient client, SwdbPlaceResolver placeResolver, EventRepository eventRepository,
      SyncLogService syncLogService, @Value("${swdb.sync.enabled:false}") boolean enabled) {
    this(client, placeResolver, eventRepository, syncLogService, enabled, Clock.system(KST));
  }

  SwdbSyncService(SwdbClient client, SwdbPlaceResolver placeResolver, EventRepository eventRepository,
      SyncLogService syncLogService, boolean enabled, Clock clock) {
    this.client = client;
    this.placeResolver = placeResolver;
    this.eventRepository = eventRepository;
    this.syncLogService = syncLogService;
    this.enabled = enabled;
    this.clock = clock;
  }

  public boolean isEnabled() {
    return enabled;
  }

  // 예약 동기화에서 부름: 하루 1회만 실제로 돎
  public Result syncIfDue() {
    LocalDateTime last = lastRunAt;
    if (last != null && Duration.between(last, LocalDateTime.now(clock)).compareTo(MIN_INTERVAL) < 0) {
      return Result.notRun("오늘 이미 실행함 (" + last.toLocalTime().truncatedTo(ChronoUnit.MINUTES) + ")");
    }
    return sync();
  }

  public Result sync() {
    if (!enabled) return Result.notRun("스위치 꺼짐 (swdb.sync.enabled)");
    if (!running.compareAndSet(false, true)) return Result.notRun("이미 실행 중");
    LocalDateTime startedAt = syncLogService.now();
    int listed = 0;
    try {
      Map<Skip, Integer> skipped = new EnumMap<>(Skip.class);
      LocalDate today = LocalDate.now(clock);
      LocalDate recent = today.minusDays(RECENT_DAYS);
      int saved = 0, updated = 0;

      for (int page = 1; page <= MAX_PAGES; page++) {
        List<SwdbHtmlParser.ListItem> items = SwdbHtmlParser.parseList(client.listPage(page));
        if (items.isEmpty()) break;
        listed += items.size();
        boolean anyRecent = false;
        for (SwdbHtmlParser.ListItem item : items) {
          boolean isRecent = item.listDate() != null && !item.listDate().isBefore(recent);
          anyRecent |= isRecent;
          Outcome outcome;
          try {
            outcome = syncItem(item, isRecent, today);
          } catch (RuntimeException e) {
            // 한 행의 저장 오류(DB 제약 등)가 그날 실행 전체를 멈추지 않게 그 행만 건너뜀 (검증 지적)
            log.warn("수원문화DB 행사 처리 실패 reqIdx={} {}", item.reqIdx(), e.getClass().getSimpleName());
            outcome = Outcome.SKIPPED_ERROR;
          }
          switch (outcome) {
            case SAVED -> saved++;
            case UPDATED -> updated++;
            case NOT_READ -> { }
            case SKIPPED_ENDED -> count(skipped, Skip.ENDED);
            case SKIPPED_RECRUIT -> count(skipped, Skip.RECRUIT);
            case SKIPPED_BAD_PERIOD -> count(skipped, Skip.BAD_PERIOD);
            case SKIPPED_OUTSIDE -> count(skipped, Skip.OUTSIDE);
            case SKIPPED_NO_COORD -> count(skipped, Skip.NO_COORD);
            case SKIPPED_PARSE -> count(skipped, Skip.PARSE);
            case SKIPPED_ERROR -> count(skipped, Skip.ERROR);
          }
        }
        if (!anyRecent) break; // 이 쪽 전체가 오래된 행사 → 뒤쪽은 더 오래됨 (등록 순 목록)
      }

      Result result = new Result(true, null, listed, saved, updated, skipped);
      // 끝까지 돈 실행만 "오늘 실행함"으로 기록 → 06시에 목록 요청이 실패하면 18시에 다시 시도 (검증 지적)
      lastRunAt = LocalDateTime.now(clock);
      syncLogService.success(SyncLog.Source.SWDB, startedAt, listed, saved, updated,
          result.skippedText().isEmpty() ? null : "건너뜀 " + result.skippedText());
      return result;
    } catch (RuntimeException e) {
      syncLogService.fail(SyncLog.Source.SWDB, startedAt, listed, e);
      throw e;
    } finally {
      running.set(false);
    }
  }

  private enum Outcome {
    SAVED, UPDATED, NOT_READ, SKIPPED_ENDED, SKIPPED_RECRUIT, SKIPPED_BAD_PERIOD, SKIPPED_OUTSIDE,
    SKIPPED_NO_COORD, SKIPPED_PARSE, SKIPPED_ERROR
  }

  private Outcome syncItem(SwdbHtmlParser.ListItem item, boolean isRecent, LocalDate today) {
    if (isRecruitment(item.title())) return Outcome.SKIPPED_RECRUIT;
    Event existing = eventRepository.findBySourceAndSourceKey(Event.SOURCE_SWDB, item.reqIdx()).orElse(null);
    // 상세를 읽는 경우: 처음 보는 최근 행사 · 아직 안 끝난 저장 행사 (끝난 저장 행사는 그대로 둠)
    boolean storedOngoing = existing != null && existing.getEndDate() != null && !existing.getEndDate().isBefore(today);
    if (existing == null ? !isRecent : !storedOngoing) return Outcome.NOT_READ;

    SwdbHtmlParser.Detail detail;
    try {
      detail = SwdbHtmlParser.parseDetail(client.detailPage(item.reqIdx()));
    } catch (RuntimeException e) {
      log.warn("수원문화DB 상세 읽기 실패 reqIdx={} {}", item.reqIdx(), e.getClass().getSimpleName());
      return Outcome.SKIPPED_ERROR;
    }
    if (detail == null) {
      log.warn("수원문화DB 상세 형식을 못 읽음 reqIdx={}", item.reqIdx());
      return Outcome.SKIPPED_PARSE;
    }
    if (detail.endDate().isBefore(today)) return Outcome.SKIPPED_ENDED;
    if (detail.endDate().isBefore(detail.startDate())
        || ChronoUnit.DAYS.between(detail.startDate(), detail.endDate()) > MAX_PERIOD_DAYS) {
      return Outcome.SKIPPED_BAD_PERIOD;
    }

    String rawPlace = firstNonBlank(detail.place(), item.address());
    SwdbPlaceResolver.Result place = placeResolver.resolve(rawPlace);
    switch (place.status()) {
      case OUTSIDE -> {
        return Outcome.SKIPPED_OUTSIDE;
      }
      case NO_COORD -> {
        log.info("수원문화DB 행사 좌표 없음 reqIdx={} 이유={}", item.reqIdx(), place.reason());
        return Outcome.SKIPPED_NO_COORD;
      }
      case ERROR -> {
        log.warn("수원문화DB 행사 좌표 검색 실패 reqIdx={} {}", item.reqIdx(), place.reason());
        return Outcome.SKIPPED_ERROR;
      }
      case FOUND -> { }
    }

    Event latest = Event.builder()
        .source(Event.SOURCE_SWDB)
        .sourceKey(item.reqIdx())
        .title(cut(item.title(), 255))
        .category(firstNonBlank(detail.category(), item.category(), DEFAULT_CATEGORY))
        .latitude(place.lat())
        .longitude(place.lng())
        .imageUrl(imageUrl(item.imagePath()))
        .info(place.note()) // 여러 장소 · 넓은 범위 안내만 (소개글 원문은 저장하지 않음)
        .pubDate(Objects.requireNonNullElseGet(SwdbHtmlParser.registeredAt(item.reqIdx()),
            () -> LocalDateTime.now(clock)))
        .startDate(detail.startDate())
        .endDate(detail.endDate())
        .link(HttpSwdbClient.detailUrl(item.reqIdx()))
        .place(place.place())
        .timeText(detail.timeText())
        .build();

    if (existing == null) {
      eventRepository.save(latest);
      return Outcome.SAVED;
    }
    existing.updateFrom(latest);
    eventRepository.save(existing);
    return Outcome.UPDATED;
  }

  // 참가자 · 강사 · 단체 모집 공고는 찾아갈 행사가 아님
  static boolean isRecruitment(String title) {
    return title != null && title.contains("모집");
  }

  static String imageUrl(String path) {
    if (path == null || !path.startsWith("/swdb/")) return null;
    String url = IMAGE_BASE + path;
    return url.length() <= 1000 ? url : null;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.strip();
    }
    return null;
  }

  private static String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static void count(Map<Skip, Integer> skipped, Skip key) {
    skipped.merge(key, 1, Integer::sum);
  }
}
