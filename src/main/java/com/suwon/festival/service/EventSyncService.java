package com.suwon.festival.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.dto.xml.ContentInfoDetail;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.dto.xml.SwcfRssResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.schedule.EventScheduleWriter;
import com.suwon.festival.service.schedule.ScheduleBodyText;
import com.suwon.festival.service.schedule.ScheduleReader;
import com.suwon.festival.service.schedule.ScheduleResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSyncService {

  private final RestTemplate restTemplate;
  private final EventRepository eventRepository;
  private final EventScheduleWriter eventScheduleWriter;

  @Value("${swcf.api.key.event}")
  private String apiKey;

  // 예약 동기화와 로컬 POST /api/events/sync 가 겹치면 뒤의 것을 건너뜀 (InstagramSyncService 와 같은 방식, 서버 1대 기준)
  //  겹치면 같은 재단 행사를 양쪽이 새로 넣으려다 한쪽이 고유 제약으로 실패할 수 있음
  private final AtomicBoolean running = new AtomicBoolean();

  private static final DateTimeFormatter PUB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public String fetchRawXml() {
    String url = "http://www.swcf.or.kr/openAPI/?CG=29_EV&openAPIKey=" + apiKey;
    return restTemplate.getForObject(url, String.class);
  }

  public List<EventItem> fetchEventItems() throws Exception {
    return parseItems(fetchRawXml());
  }

  public static List<EventItem> parseItems(String xml) throws Exception {
    XmlMapper xmlMapper = new XmlMapper();
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    SwcfRssResponse response = xmlMapper.readValue(xml, SwcfRssResponse.class);
    // 점검 안내 · 오류 페이지처럼 행사 목록 모양이 아니면 원인을 알 수 있게 실패 (예전엔 NPE 로 500)
    if (response == null || response.channel == null) {
      throw new IllegalStateException("재단 응답에 channel 이 없음 (점검 · 오류 페이지일 수 있음)");
    }
    // 행사 0건이면 item 이 아예 없음 → 빈 목록 (동기화는 지우지 않으므로 기존 행사는 그대로)
    return response.channel.item == null ? List.of() : response.channel.item;
  }

  public SyncResult syncEvents() throws Exception {
    if (!running.compareAndSet(false, true)) {
      log.info("재단 동기화가 이미 실행 중 → 이번 실행은 건너뜀");
      return SyncResult.skippedAlreadyRunning();
    }
    try {
      String xml = fetchRawXml();
      return syncItems(parseItems(xml), ScheduleBodyText.byIdx(xml));
    } finally {
      running.set(false);
    }
  }

  SyncResult syncItems(List<EventItem> items) {
    return syncItems(items, Map.of());
  }

  // 받아 온 재단 행사 목록을 DB 에 반영 (테스트에서 목록을 직접 넣을 수 있게 분리). bodies: 행사별 본문 (진행 날짜 추출용, 저장 안 함)
  // ⚠️ 여기에 @Transactional 을 붙이지 말 것: 행 하나의 DB 오류가 전체 트랜잭션을 rollback-only 로 만들어
  //    건너뛰기(try · catch)가 의미 없어지고 뒤 행사 저장까지 조용히 사라짐. 저장은 행마다 각자 트랜잭션
  SyncResult syncItems(List<EventItem> items, Map<Long, String> bodies) {
    int savedCount = 0;
    int updatedCount = 0;
    int skippedCount = 0;

    for (EventItem item : items) {
      // 한 행의 형식 오류(날짜 등) 때문에 그 뒤 행사까지 저장되지 않던 문제 → 그 행만 건너뜀
      try {
        switch (syncItem(item, bodies)) {
          case SAVED -> savedCount++;
          case UPDATED -> updatedCount++;
          case UNCHANGED, IGNORED -> { }
        }
      } catch (RuntimeException e) {
        skippedCount++;
        log.warn("재단 행사 건너뜀: idx={} {}: {}", item.idx, e.getClass().getSimpleName(), e.getMessage());
      }
    }
    if (items.isEmpty()) {
      log.warn("재단 응답에 행사가 0건");
    }
    // 전부 실패면 행 문제가 아니라 DB 장애 · 형식 변경 같은 전체 문제 → 성공으로 넘기지 않음
    if (skippedCount > 0 && skippedCount == items.size()) {
      throw new IllegalStateException("재단 행사 " + skippedCount + "건 모두 처리 실패 (DB · 응답 형식 확인)");
    }

    return new SyncResult(savedCount, updatedCount, skippedCount);
  }

  private enum ItemOutcome { SAVED, UPDATED, UNCHANGED, IGNORED }

  private ItemOutcome syncItem(EventItem item, Map<Long, String> bodies) {
    // 재단 번호가 없으면 건너뜀. V2 에서 idx 가 NULL 허용이 되어(인스타 행사용) DB 가 더는 막아 주지 않음
    // → 그대로 두면 findByIdx(null) 이 인스타 행사와 맞물려 덮어쓰거나 동기화가 멈출 수 있음
    if (item.idx == null) {
      return ItemOutcome.IGNORED;
    }
    if (item.coordinate == null || item.coordinate.lat == null || item.coordinate.lng == null) {
      return ItemOutcome.IGNORED; // 좌표 정보가 불완전한 행사는 건너뜀
    }

    Event latest = Event.builder()
        .idx(item.idx)
        .title(HtmlUtils.htmlUnescape(item.title)) // &#39; 같은 이중 인코딩된 엔티티 복원
        .category("전체".equals(item.category) ? "기타" : item.category)
        .latitude(item.coordinate.lng) // lat/lng 뒤바뀜 주의!
        .longitude(item.coordinate.lat)
        .imageUrl(extractImageUrl(item))
        .info(InfoParser.clean(extractInfo(item))) // 엔티티 복원 + <br> 등 HTML 태그 제거
        .pubDate(LocalDateTime.parse(item.pubDate, PUB_DATE_FORMAT))
        .startDate(item.eventDate != null ? LocalDate.parse(item.eventDate.sdate) : null)
        .endDate(item.eventDate != null ? LocalDate.parse(item.eventDate.edate) : null)
        .link(item.link)
        .place(InfoParser.findPlace(details(item))) // 캘린더 B안: 장소·시간은 합치기 전 XML 쌍에서 바로 꺼냄
        .timeText(InfoParser.findTimeText(details(item)))
        .fee(InfoParser.findFee(details(item))) // 상세 정보 분해 (V7)
        .contact(InfoParser.findContact(details(item)))
        .host(InfoParser.findHost(details(item)))
        .build();

    Event existing = eventRepository.findByIdx(item.idx).orElse(null);
    ScheduleResult schedule = readSchedule(item, bodies.get(item.idx));

    if (existing == null) {
      Event saved = eventRepository.save(latest); // 처음 보는 행사 → 새로 저장
      if (schedule != null) {
        eventScheduleWriter.apply(saved.getId(), schedule, InfoParser.findFullTimeText(details(item)));
      }
      return ItemOutcome.SAVED;
    }

    // 이미 저장된 행사인데
    // ① 재단 쪽 내용이 수정됐거나 (pubDate가 더 최신)
    // ② 예전에 저장할 때 link가 비어 있었는데 지금은 값이 있으면 (link 컬럼 추가 전에 저장된 행사)
    // ③ 저장된 제목이 지금 제목과 다르면 (htmlUnescape 적용 전에 &#39; 그대로 저장된 행사)
    // ④ 정리 방식이 바뀌어 info·장소·시간·요금·문의·주최가 달라졌으면 (태그 제거 적용 전 / 칸 추가(V7 포함) 전에 저장된 행사)
    // → 최신 값으로 갱신. 모두 아니면 그대로 건너뜀
    boolean changed = latest.getPubDate().isAfter(existing.getPubDate());
    boolean linkMissing = existing.getLink() == null && latest.getLink() != null;
    boolean titleFixed = !Objects.equals(existing.getTitle(), latest.getTitle());
    boolean detailFixed = !Objects.equals(existing.getInfo(), latest.getInfo())
        || !Objects.equals(existing.getPlace(), latest.getPlace())
        || !Objects.equals(existing.getTimeText(), latest.getTimeText())
        || !Objects.equals(existing.getFee(), latest.getFee())
        || !Objects.equals(existing.getContact(), latest.getContact())
        || !Objects.equals(existing.getHost(), latest.getHost());

    // ⑤ 기간이 달라졌으면 (진행 날짜만 고쳐져 기간 밖 날짜가 나가지 않게)
    boolean periodFixed = !Objects.equals(existing.getStartDate(), latest.getStartDate())
        || !Objects.equals(existing.getEndDate(), latest.getEndDate());
    // ⑥ 이미지 주소가 달라졌으면 (https 변환 전에 http:// 로 저장된 행사)
    boolean imageFixed = !Objects.equals(existing.getImageUrl(), latest.getImageUrl());
    boolean rowChanged = changed || linkMissing || titleFixed || detailFixed || periodFixed || imageFixed;
    if (rowChanged) {
      existing.updateFrom(latest);
      eventRepository.save(existing);
    }
    // 진행 날짜는 위 조건과 별개로 매번 다시 계산 (규칙을 고치면 다음 동기화에 전부 반영)
    // 본문을 못 받았거나 추출이 실패해 모름이 된 것은 "일정이 없어짐"이 아님 → 저장된 값을 유지
    boolean bodyMissing = bodies.get(item.idx) == null;
    boolean keepStored = schedule == null
        || (bodyMissing && schedule.status() == ScheduleResult.Status.UNKNOWN);
    if (keepStored && Event.SCHEDULE_DATES.equals(existing.getScheduleStatus())) {
      log.warn("진행 날짜 유지: idx={} 본문 없음 또는 추출 실패", item.idx);
    }
    boolean scheduleChanged = !keepStored
        && eventScheduleWriter.apply(existing.getId(), schedule, InfoParser.findFullTimeText(details(item)));
    return rowChanged || scheduleChanged ? ItemOutcome.UPDATED : ItemOutcome.UNCHANGED;
  }

  // 추출 오류 하나로 행사 동기화 전체가 멈추지 않게 함. 실패는 null (정규식이 아주 긴 글에서 StackOverflowError 를 낼 수 있음)
  private ScheduleResult readSchedule(EventItem item, String body) {
    try {
      return ScheduleReader.read(item, body);
    } catch (RuntimeException | StackOverflowError e) {
      log.warn("진행 날짜 추출 실패: idx={} {}", item.idx, e.getClass().getSimpleName());
      return null;
    }
  }

  private String extractImageUrl(EventItem item) {
    if (item.photos == null || item.photos.photoInfo == null || item.photos.photoInfo.isEmpty()) {
      return null;
    }
    return toHttps(item.photos.photoInfo.get(0).photoUrl);
  }

  // 재단 XML 이미지 주소가 http:// 로 옴 → https 화면(운영)에서 Mixed Content(보안 연결 화면 속 비보안 이미지) 경고 · 차단
  // → 저장할 때 https:// 로 바꿔 둠 (11/3 결정). 다른 형식(https · 상대 주소 · null)은 그대로
  static String toHttps(String url) {
    if (url != null && url.regionMatches(true, 0, "http://", 0, 7)) {
      return "https://" + url.substring(7);
    }
    return url;
  }

  private List<ContentInfoDetail> details(EventItem item) {
    return item.contentInfo == null ? null : item.contentInfo.contentInfoDetail;
  }

  private String extractInfo(EventItem item) {
    if (item.contentInfo == null || item.contentInfo.contentInfoDetail == null) {
      return null; // description은 매핑 안 하기로 함 (EventItem.java 주석 참고)
    }
    return item.contentInfo.contentInfoDetail.stream()
        .map(detail -> detail.infoItem + ": " + detail.infoContent)
        .collect(Collectors.joining("\n"));
  }
}