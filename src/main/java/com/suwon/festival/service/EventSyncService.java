package com.suwon.festival.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import java.time.LocalDate;

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
    return response.channel.item;
  }

  public SyncResult syncEvents() throws Exception {
    String xml = fetchRawXml();
    return syncItems(parseItems(xml), ScheduleBodyText.byIdx(xml));
  }

  SyncResult syncItems(List<EventItem> items) {
    return syncItems(items, Map.of());
  }

  // 받아 온 재단 행사 목록을 DB 에 반영 (테스트에서 목록을 직접 넣을 수 있게 분리). bodies: 행사별 본문 (진행 날짜 추출용, 저장 안 함)
  SyncResult syncItems(List<EventItem> items, Map<Long, String> bodies) {
    int savedCount = 0;
    int updatedCount = 0;

    for (EventItem item : items) {
      // 재단 번호가 없으면 건너뜀. V2 에서 idx 가 NULL 허용이 되어(인스타 행사용) DB 가 더는 막아 주지 않음
      // → 그대로 두면 findByIdx(null) 이 인스타 행사와 맞물려 덮어쓰거나 동기화가 멈출 수 있음
      if (item.idx == null) {
        continue;
      }
      if (item.coordinate == null || item.coordinate.lat == null || item.coordinate.lng == null) {
        continue; // 좌표 정보가 불완전한 행사는 건너뜀
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
          .build();

      Event existing = eventRepository.findByIdx(item.idx).orElse(null);
      ScheduleResult schedule = readSchedule(item, bodies.get(item.idx));

      if (existing == null) {
        Event saved = eventRepository.save(latest); // 처음 보는 행사 → 새로 저장
        eventScheduleWriter.apply(saved.getId(), schedule, InfoParser.findFullTimeText(details(item)));
        savedCount++;
        continue;
      }

      // 이미 저장된 행사인데
      // ① 재단 쪽 내용이 수정됐거나 (pubDate가 더 최신)
      // ② 예전에 저장할 때 link가 비어 있었는데 지금은 값이 있으면 (link 컬럼 추가 전에 저장된 행사)
      // ③ 저장된 제목이 지금 제목과 다르면 (htmlUnescape 적용 전에 &#39; 그대로 저장된 행사)
      // ④ 정리 방식이 바뀌어 info·장소·시간이 달라졌으면 (태그 제거 적용 전 / 장소·시간 칸 추가 전에 저장된 행사)
      // → 최신 값으로 갱신. 모두 아니면 그대로 건너뜀
      boolean changed = latest.getPubDate().isAfter(existing.getPubDate());
      boolean linkMissing = existing.getLink() == null && latest.getLink() != null;
      boolean titleFixed = !Objects.equals(existing.getTitle(), latest.getTitle());
      boolean detailFixed = !Objects.equals(existing.getInfo(), latest.getInfo())
          || !Objects.equals(existing.getPlace(), latest.getPlace())
          || !Objects.equals(existing.getTimeText(), latest.getTimeText());

      boolean rowChanged = changed || linkMissing || titleFixed || detailFixed;
      if (rowChanged) {
        existing.updateFrom(latest);
        eventRepository.save(existing);
      }
      // 진행 날짜는 위 조건과 별개로 매번 다시 계산 (규칙을 고치면 다음 동기화에 전부 반영)
      boolean scheduleChanged = eventScheduleWriter.apply(existing.getId(), schedule, InfoParser.findFullTimeText(details(item)));
      if (rowChanged || scheduleChanged) {
        updatedCount++;
      }
    }

    return new SyncResult(savedCount, updatedCount);
  }

  // 추출 오류 하나로 행사 동기화 전체가 멈추지 않게 함. 모름으로 두면 화면은 원문을 보여 줌
  private ScheduleResult readSchedule(EventItem item, String body) {
    try {
      return ScheduleReader.read(item, body);
    } catch (RuntimeException e) {
      log.warn("진행 날짜 추출 실패: idx={} {}", item.idx, e.getClass().getSimpleName());
      return new ScheduleResult(ScheduleResult.Status.UNKNOWN, List.of(), null, false, "추출 오류");
    }
  }

  private String extractImageUrl(EventItem item) {
    if (item.photos == null || item.photos.photoInfo == null || item.photos.photoInfo.isEmpty()) {
      return null;
    }
    return item.photos.photoInfo.get(0).photoUrl;
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