package com.suwon.festival.service.instagram;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.service.caption.CaptionAiClient;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.caption.CaptionParser.BlockResult;
import com.suwon.festival.service.caption.ParsedEvent;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.schedule.EventScheduleWriter;
import com.suwon.festival.service.schedule.ScheduleExtractor;
import com.suwon.festival.service.schedule.ScheduleInput;
import com.suwon.festival.service.schedule.ScheduleResult;

// 인스타 5단계: 게시물 1개 → 블록 정리 → 좌표 → 저장 (게시물 하나가 한 트랜잭션)
//  - 게시물 1개에서 오류가 나도 그 게시물만 되돌리고 다음 게시물은 계속 (InstagramSyncService 가 잡음)
//  - 캡션 원문은 DB · 로그에 남기지 않음 (설계 문서 8번)
//  - 이미 READY 인 블록은 키(제목 + 시작일 + 장소)가 같으므로 좌표를 다시 찾지 않고 나머지 값만 갱신 → 카카오 호출 절약
//  - NO_COORD · 영구 실패(HTTP 4xx, 401 · 403 · 429 제외) ERROR 블록은 마지막 검색(updatedAt) 뒤 RETRY_AFTER 동안 다시 검색하지 않음
//    (키에 장소명이 들어 있어 키가 같으면 같은 장소명) → 동기화마다 같은 실패 검색을 반복하지 않음. updatedAt 은 그대로 둠
//  - READY 행사는 진행 날짜도 매번 다시 계산해 저장 (규칙을 고치면 창 안의 글은 다음 동기화에 반영)
@Component
public class InstagramPostProcessor {

  private static final Logger BLOCK_LOG = LoggerFactory.getLogger(BlockLog.LOGGER_NAME);
  private static final Logger log = LoggerFactory.getLogger(InstagramPostProcessor.class);
  static final String NO_SOURCE_KEY = "저장 키(sourceKey) 없음 — 저장 안 함";

  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Pattern MEDIA_ID = Pattern.compile("^[0-9]{1,64}$");
  // 하루짜리로 읽혔어도 다른 날이 있다는 말 ("11. 7.(토)부터 매주 토요일" · "11. 30.(월)까지" · "외 3회") → "그날만"으로 저장하지 않음
  // 실패한 장소 검색을 다시 하기까지 기다리는 시간 (동기화 하루 2번 → 약 6번 중 1번만 검색)
  static final java.time.Duration RETRY_AFTER = java.time.Duration.ofDays(3);
  // 다시 해도 같은 결과일 HTTP 실패 (400 · 404 …). 429(호출 한도)는 잠깐, 401 · 403(키 설정)은 키를 고치면 바로 풀려야 해서 제외
  private static final Pattern PERMANENT_HTTP = Pattern.compile("\\(HTTP 4(?!29|01|03)\\d\\d\\)");
  private static final Pattern MORE_DAYS = Pattern.compile("부터|까지|매주|매일|외\\s*\\d|\\d\\s*회|회차");

  // 게시물 1개 처리 결과 (개수만)
  public record Outcome(boolean newMedia, Map<InstagramBlock.Status, Integer> blocks,
      int eventsSaved, int eventsUpdated, int duplicateBlocks) {
  }

  private final InstagramMediaRepository mediaRepository;
  private final InstagramBlockRepository blockRepository;
  private final EventRepository eventRepository;
  private final PlaceGeocoder geocoder;
  private final CaptionParser parser;
  private final Clock clock;
  private final StaleBlockRetirer retirer;
  private final EventScheduleWriter scheduleWriter;

  @Autowired
  public InstagramPostProcessor(InstagramMediaRepository mediaRepository, InstagramBlockRepository blockRepository,
      EventRepository eventRepository, PlaceGeocoder geocoder, ObjectProvider<CaptionAiClient> ai,
      StaleBlockRetirer retirer, EventScheduleWriter scheduleWriter) {
    // AI 구현이 아직 없음 → null → 규칙으로 못 읽은 블록은 PENDING (AI 를 붙이면 다음 실행에서 다시 처리)
    this(mediaRepository, blockRepository, eventRepository, geocoder, new CaptionParser(ai.getIfAvailable()),
        Clock.system(KST), retirer, scheduleWriter);
  }

  InstagramPostProcessor(InstagramMediaRepository mediaRepository, InstagramBlockRepository blockRepository,
      EventRepository eventRepository, PlaceGeocoder geocoder, CaptionParser parser, Clock clock,
      StaleBlockRetirer retirer, EventScheduleWriter scheduleWriter) {
    this.mediaRepository = mediaRepository;
    this.blockRepository = blockRepository;
    this.eventRepository = eventRepository;
    this.geocoder = geocoder;
    this.parser = parser;
    this.clock = clock;
    this.retirer = retirer;
    this.scheduleWriter = scheduleWriter;
  }

  @Transactional
  public Outcome process(String account, InstagramPost post) {
    if (post.id() == null || !MEDIA_ID.matcher(post.id()).matches()) {
      throw new IllegalArgumentException("게시물 id 형식이 다름"); // id 값은 메시지에 넣지 않음
    }
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime postedAt = post.timestamp() == null ? null
        : post.timestamp().atZoneSameInstant(KST).toLocalDateTime();

    InstagramMedia media = mediaRepository.findByMediaId(post.id()).orElse(null);
    boolean isNew = media == null;
    if (isNew) {
      media = mediaRepository.save(InstagramMedia.builder()
          .mediaId(post.id()).account(account).permalink(post.permalink())
          .postedAt(postedAt).collectedAt(now).build());
    } else {
      media.recollect(post.permalink(), postedAt, now);
    }

    Map<String, InstagramBlock> existing = new HashMap<>();
    for (InstagramBlock b : blockRepository.findByMedia(media)) existing.put(b.getBlockKey(), b);

    Map<InstagramBlock.Status, Integer> counts = new EnumMap<>(InstagramBlock.Status.class);
    Set<String> seen = new HashSet<>();
    int saved = 0, updated = 0, duplicates = 0;
    List<String> logLines = new java.util.ArrayList<>();
    List<Event> ready = new java.util.ArrayList<>();
    List<Event> fresh = new java.util.ArrayList<>(); // 그중 이번에 새로 READY 가 된 행사 (READY 로 이어진 블록 제외)

    List<BlockResult> results = parser.parse(post.caption());
    for (BlockResult r : results) {
      String key = BlockKeys.of(r);
      if (!seen.add(key)) {
        duplicates++; // 같은 글 안에 같은 행사가 두 번 → 한 번만
        continue;
      }
      InstagramBlock block = existing.get(key);
      InstagramBlock.Status status;
      String reason = r.reason();
      Event event = null;
      boolean waiting = false; // 검색 대기 중이라 이전 상태 · 시각을 그대로 둠

      switch (r.status()) {
        case PARSED -> {
          ParsedEvent e = r.event();
          String sourceKey = BlockKeys.sourceKey(post.id(), key);
          Event linked = block != null && block.getStatus() == InstagramBlock.Status.READY ? block.getEvent() : null;
          if (!BlockKeys.isValidSourceKey(sourceKey)) {
            // 키가 비면 unique 로 중복을 못 막음 → 행사 저장 · 장소 검색 없이 FAILED 로 집계 (재단 행사는 이 경로를 안 탐)
            log.warn("인스타 블록 저장 건너뜀: {} (게시물 {})", NO_SOURCE_KEY, post.id());
            status = InstagramBlock.Status.FAILED;
            reason = NO_SOURCE_KEY;
          } else if (linked != null) {
            // 키가 같으면 장소도 같음 → 좌표는 그대로 두고 나머지만 갱신
            linked.updateFrom(toEvent(e, linked.getLatitude(), linked.getLongitude(), post, postedAt, now, sourceKey));
            event = linked;
            status = InstagramBlock.Status.READY;
            reason = null;
            updated++;
          } else if (block != null && waitBeforeRetry(block, now)) {
            status = block.getStatus();
            reason = block.getReason();
            waiting = true;
          } else {
            PlaceGeocoder.Result geo = geocoder.geocode(e.placeName());
            switch (geo.status()) {
              case FOUND -> {
                Event latest = toEvent(e, geo.place().lat(), geo.place().lng(), post, postedAt, now, sourceKey);
                Event found = eventRepository.findBySourceAndSourceKey(Event.SOURCE_INSTAGRAM, sourceKey).orElse(null);
                if (found == null) {
                  event = eventRepository.save(latest);
                  saved++;
                } else {
                  found.updateFrom(latest);
                  event = found;
                  updated++;
                }
                fresh.add(event);
                status = InstagramBlock.Status.READY;
                reason = null;
              }
              case NO_COORD -> {
                status = InstagramBlock.Status.NO_COORD;
                reason = geo.reason();
              }
              default -> {
                status = InstagramBlock.Status.ERROR;
                reason = geo.reason();
              }
            }
          }
        }
        case NO_DATE -> status = InstagramBlock.Status.NO_DATE;
        case PENDING -> status = InstagramBlock.Status.PENDING;
        case SKIPPED -> status = InstagramBlock.Status.SKIPPED;
        default -> status = InstagramBlock.Status.FAILED;
      }

      if (status == InstagramBlock.Status.READY) {
        ready.add(event);
        scheduleWriter.apply(event.getId(), schedule(r.event()), null);
      }
      // 예전에 READY 였던 블록이 이번엔 READY 가 아니어도 행사 연결은 남겨 둠
      if (event == null && block != null) event = block.getEvent();

      if (block == null) {
        block = blockRepository.save(InstagramBlock.builder()
            .media(media).blockKey(key).status(status).reason(reason).updatedAt(now).build());
        if (event != null) block.update(status, reason, event, now);
      } else {
        block.update(status, reason, event, waiting ? block.getUpdatedAt() : now);
      }
      counts.merge(status, 1, Integer::sum);
      logLines.add(BlockLog.line(post.id(), key, r, status, waiting ? reason + " (재검색 대기)" : reason));
    }

    // 캡션을 못 받았거나 블록이 하나도 안 나오면 판단할 근거가 없음 → 아무것도 내리지 않음
    if (!results.isEmpty()) {
      existing.keySet().removeAll(seen);
      List<String> retired = retirer.retire(post.id(), existing.values(),
          new StaleBlockRetirer.Current(ready, fresh, counts.keySet(), post.caption()), now);
      if (!retired.isEmpty()) counts.put(InstagramBlock.Status.RETIRED, retired.size());
      logLines.addAll(retired);
    }

    media.markProcessed(now);
    logAfterCommit(logLines);
    return new Outcome(isNew, counts, saved, updated, duplicates);
  }

  // 같은 키(= 같은 장소명)로 최근에 검색해 실패했으면 다시 검색하지 않음
  static boolean waitBeforeRetry(InstagramBlock block, LocalDateTime now) {
    boolean retryLater = switch (block.getStatus()) {
      case NO_COORD -> true;
      case ERROR -> block.getReason() != null && PERMANENT_HTTP.matcher(block.getReason()).find();
      default -> false;
    };
    return retryLater && block.getUpdatedAt() != null && block.getUpdatedAt().plus(RETRY_AFTER).isAfter(now);
  }

  // 블록 로그는 커밋된 뒤에 찍음 → 롤백된 게시물의 "저장 안 된 상태"가 로그에 남지 않게
  private static void logAfterCommit(List<String> lines) {
    if (!BLOCK_LOG.isInfoEnabled() || lines.isEmpty()) return;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          lines.forEach(BLOCK_LOG::info);
        }
      });
    } else {
      lines.forEach(BLOCK_LOG::info);
    }
  }

  // 진행 날짜: 재단과 같은 추출기 · 검사로 날짜 줄(+ 시간 원문)을 읽음 (설계 docs/event-schedule.md 9번)
  //  하루짜리 → 그날 / 여러 회차 · 기간 + 매주 요일 → 펼침 / 그 밖의 여러 날 → 모름 (매일로 단정하지 않음)
  static ScheduleResult schedule(ParsedEvent e) {
    LocalDate start = e.startDate();
    boolean oneDay = start != null && (e.endDate() == null || start.equals(e.endDate()));
    if (oneDay && (MORE_DAYS.matcher(nullToEmpty(e.scheduleText())).find() || MORE_DAYS.matcher(nullToEmpty(e.timeText())).find())) {
      return new ScheduleResult(ScheduleResult.Status.UNKNOWN, List.of(), null, false, "하루짜리인데 다른 날을 뜻하는 말이 있음");
    }
    return ScheduleExtractor.extract(new ScheduleInput(e.startDate(), e.endDate(), e.scheduleText(), e.timeText(), null));
  }

  private static Event toEvent(ParsedEvent e, double lat, double lng, InstagramPost post, LocalDateTime postedAt,
      LocalDateTime now, String sourceKey) {
    return Event.builder()
        .idx(null)
        .title(cut(e.title(), 255))
        .category(e.category() == null ? "기타" : e.category())
        .latitude(lat)
        .longitude(lng)
        .pubDate(postedAt != null ? postedAt : now) // 게시 시각이 없으면 처리 시각
        .startDate(e.startDate())
        .endDate(e.endDate())
        .link(cut(post.permalink(), 1000))
        .place(cut(e.placeName(), 500))
        .timeText(cut(e.timeText(), 1000))
        .source(Event.SOURCE_INSTAGRAM)
        .sourceKey(sourceKey)
        .build();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}
