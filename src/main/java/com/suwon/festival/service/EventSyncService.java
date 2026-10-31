package com.suwon.festival.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventSyncService {

  private final RestTemplate restTemplate;
  private final EventRepository eventRepository;

  @Value("${swcf.api.key.event}")
  private String apiKey;

  private static final DateTimeFormatter PUB_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public String fetchRawXml() {
    String url = "http://www.swcf.or.kr/openAPI/?CG=29_EV&openAPIKey=" + apiKey;
    return restTemplate.getForObject(url, String.class);
  }

  public List<EventItem> fetchEventItems() throws Exception {
    String xml = fetchRawXml();
    XmlMapper xmlMapper = new XmlMapper();
    xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    SwcfRssResponse response = xmlMapper.readValue(xml, SwcfRssResponse.class);
    return response.channel.item;
  }

  public SyncResult syncEvents() throws Exception {
    return syncItems(fetchEventItems());
  }

  // 받아 온 재단 행사 목록을 DB 에 반영 (테스트에서 목록을 직접 넣을 수 있게 분리)
  SyncResult syncItems(List<EventItem> items) {
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

      if (existing == null) {
        eventRepository.save(latest); // 처음 보는 행사 → 새로 저장
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

      if (changed || linkMissing || titleFixed || detailFixed) {
        existing.updateFrom(latest);
        eventRepository.save(existing);
        updatedCount++;
      }
    }

    return new SyncResult(savedCount, updatedCount);
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