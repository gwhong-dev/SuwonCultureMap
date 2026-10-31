package com.suwon.festival.service.instagram;

import java.time.Clock;
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

// 인스타 5단계: 게시물 1개 → 블록 정리 → 좌표 → 저장 (게시물 하나가 한 트랜잭션)
//  - 게시물 1개에서 오류가 나도 그 게시물만 되돌리고 다음 게시물은 계속 (InstagramSyncService 가 잡음)
//  - 캡션 원문은 DB · 로그에 남기지 않음 (설계 문서 8번)
//  - 이미 READY 인 블록은 키(제목 + 시작일 + 장소)가 같으므로 좌표를 다시 찾지 않고 나머지 값만 갱신 → 카카오 호출 절약
@Component
public class InstagramPostProcessor {

  private static final Logger BLOCK_LOG = LoggerFactory.getLogger(BlockLog.LOGGER_NAME);

  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Pattern MEDIA_ID = Pattern.compile("^[0-9]{1,64}$");

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

  @Autowired
  public InstagramPostProcessor(InstagramMediaRepository mediaRepository, InstagramBlockRepository blockRepository,
      EventRepository eventRepository, PlaceGeocoder geocoder, ObjectProvider<CaptionAiClient> ai) {
    // AI 구현이 아직 없음 → null → 규칙으로 못 읽은 블록은 PENDING (AI 를 붙이면 다음 실행에서 다시 처리)
    this(mediaRepository, blockRepository, eventRepository, geocoder, new CaptionParser(ai.getIfAvailable()),
        Clock.system(KST));
  }

  InstagramPostProcessor(InstagramMediaRepository mediaRepository, InstagramBlockRepository blockRepository,
      EventRepository eventRepository, PlaceGeocoder geocoder, CaptionParser parser, Clock clock) {
    this.mediaRepository = mediaRepository;
    this.blockRepository = blockRepository;
    this.eventRepository = eventRepository;
    this.geocoder = geocoder;
    this.parser = parser;
    this.clock = clock;
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

      switch (r.status()) {
        case PARSED -> {
          ParsedEvent e = r.event();
          String sourceKey = BlockKeys.sourceKey(post.id(), key);
          Event linked = block != null && block.getStatus() == InstagramBlock.Status.READY ? block.getEvent() : null;
          if (linked != null) {
            // 키가 같으면 장소도 같음 → 좌표는 그대로 두고 나머지만 갱신
            linked.updateFrom(toEvent(e, linked.getLatitude(), linked.getLongitude(), post, postedAt, now, sourceKey));
            event = linked;
            status = InstagramBlock.Status.READY;
            reason = null;
            updated++;
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

      // 예전에 READY 였던 블록이 이번엔 READY 가 아니어도 행사 연결은 남겨 둠 (지도에서 내리는 처리는 나중에 — PROGRESS "나중에 할 일")
      if (event == null && block != null) event = block.getEvent();

      if (block == null) {
        block = blockRepository.save(InstagramBlock.builder()
            .media(media).blockKey(key).status(status).reason(reason).updatedAt(now).build());
        if (event != null) block.update(status, reason, event, now);
      } else {
        block.update(status, reason, event, now);
      }
      counts.merge(status, 1, Integer::sum);
      logLines.add(BlockLog.line(post.id(), key, r, status, reason));
    }

    media.markProcessed(now);
    logAfterCommit(logLines);
    return new Outcome(isNew, counts, saved, updated, duplicates);
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

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }
}
