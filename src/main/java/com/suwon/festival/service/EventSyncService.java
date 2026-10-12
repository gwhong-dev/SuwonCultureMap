package com.suwon.festival.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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

  public int syncEvents() throws Exception {
    List<EventItem> items = fetchEventItems();
    int savedCount = 0;

    for (EventItem item : items) {
      if (eventRepository.existsByIdx(item.idx)) {
        continue; // 이미 저장된 행사는 건너뜀
      }

      if (item.coordinate == null || item.coordinate.lat == null || item.coordinate.lng == null) {
        continue; // 좌표 정보가 불완전한 행사는 건너뜀
      }

      Event event = Event.builder()
          .idx(item.idx)
          .title(HtmlUtils.htmlUnescape(item.title)) // &#39; 같은 이중 인코딩된 엔티티 복원
          .category("전체".equals(item.category) ? "기타" : item.category)
          .latitude(item.coordinate.lng) // lat/lng 뒤바뀜 주의!
          .longitude(item.coordinate.lat)
          .imageUrl(extractImageUrl(item))
          .info(HtmlUtils.htmlUnescape(extractInfo(item)))
          .pubDate(LocalDateTime.parse(item.pubDate, PUB_DATE_FORMAT))
          .startDate(item.eventDate != null ? LocalDate.parse(item.eventDate.sdate) : null)
          .endDate(item.eventDate != null ? LocalDate.parse(item.eventDate.edate) : null)
          .link(item.link)
          .build();

      eventRepository.save(event);
      savedCount++;
    }

    return savedCount;
  }

  private String extractImageUrl(EventItem item) {
    if (item.photos == null || item.photos.photoInfo == null || item.photos.photoInfo.isEmpty()) {
      return null;
    }
    return item.photos.photoInfo.get(0).photoUrl;
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